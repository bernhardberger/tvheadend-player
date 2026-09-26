@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandDisposition
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendPlaybackCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * [AppPlaybackRuntime]'s audio focus and audio interruptions: explicit plays request focus, and a
 * focus loss pauses (holding the server for live timeshift) or mutes until focus returns. The
 * runtime owns the lock, the active target and its epoch, foreground and the recovery job.
 */
internal class AudioInterruptionController(
    private val player: ExoPlayer,
    private val coordinator: TvheadendPlaybackCoordinator,
    private val scope: CoroutineScope,
    private val targetCommands: PlaybackTargetCommandSerialization,
    private val audioFocus: PlaybackAudioFocus,
    private val audioSelection: SessionAudioSelection,
    private val livePauseController: LivePauseController,
    private val livePlaybackObservation: StateFlow<LivePlaybackObservation>,
    private val _activeTarget: StateFlow<AppPlaybackTarget?>,
    private val foreground: () -> Boolean,
    private val activeTargetEpoch: () -> Long?,
    private val targetInstallationInProgress: () -> Boolean,
    private val cancelRecoveryForInterruptionLocked: (currentJob: Job) -> Unit,
    /** Playback may continue: a live recovery the interruption stopped may retune now. */
    private val resumeInterruptedRecoveryLocked: () -> Unit,
) {
    private var focusGeneration = 0L
    var interruption: AudioInterruption? = null
        private set
    private var interruptionContent = AudioInterruptionContent.NONE
    private var interruptionHoldUnconfirmed = false
    var resumeAfterInterruption = false
    var interruptionPaused = false
        private set
    var interruptionMuted = false
        private set

    /**
     * A live recovery keeps an interruption mute: recovery is not viewer consent to resume audio.
     * Retires the old epoch's focus callback.
     */
    fun retainInterruptionMuteLocked() {
        focusGeneration++
        audioFocus.abandon()
        resumeAfterInterruption = false
    }

    fun currentInterruptionContent(): AudioInterruptionContent = when (_activeTarget.value) {
        is AppPlaybackTarget.Live -> if (
            (livePlaybackObservation.value as? LivePlaybackObservation.Active)?.timeshiftState is LiveTimeshiftState.Available
        ) AudioInterruptionContent.LIVE_TIMESHIFT else AudioInterruptionContent.LIVE
        is AppPlaybackTarget.Recording -> AudioInterruptionContent.RECORDING
        null -> AudioInterruptionContent.NONE
    }

    fun clearAudioInterruptionLocked() {
        focusGeneration++
        audioFocus.abandon()
        interruption = null
        interruptionHoldUnconfirmed = false
        interruptionContent = AudioInterruptionContent.NONE
        resumeAfterInterruption = false
        interruptionPaused = false
        setInterruptionMuted(false)
    }

    private fun setInterruptionMuted(muted: Boolean) {
        if (interruptionMuted == muted) return
        interruptionMuted = muted
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, muted).build()
        if (!muted && !targetInstallationInProgress()) audioSelection.restore(player)
    }

    fun preserveInterruptionMute() {
        if (interruptionMuted && C.TRACK_TYPE_AUDIO !in player.trackSelectionParameters.disabledTrackTypes) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        }
    }

    suspend fun playWithAudioFocusLocked(
        resumeTimeshift: Boolean = false,
        restoreSoundOnly: Boolean = false,
    ): TimeshiftCommandResult {
        if (!targetCommands.isOpen()) return TimeshiftCommandResult.SHUT_DOWN
        if (!foreground()) return TimeshiftCommandResult.UNAVAILABLE
        val epoch = activeTargetEpoch() ?: return TimeshiftCommandResult.UNAVAILABLE
        // Every explicit play retires a pause still pending for this target: it never reached
        // the server, so the play resumes locally only.
        val retiredPendingPause = !restoreSoundOnly && livePauseController.pendingLivePauseEpoch == epoch
        if (retiredPendingPause) livePauseController.clearPendingLivePauseLocked()
        val generation = ++focusGeneration
        val granted = audioFocus.request { event ->
            scope.launch {
                targetCommands.serialize(onClosed = {}) {
                    if (foreground() && epoch == activeTargetEpoch() && generation == focusGeneration) {
                        handleAudioInterruptionLocked(event)
                    }
                }
            }
        }
        if (!granted) {
            // Preserve a previous server hold even if timeshift availability has disappeared.
            if (!restoreSoundOnly) handleAudioInterruptionLocked(AudioInterruption.TRANSIENT_LOSS, wasPlaying = true)
            // Delayed gain is disabled: only another explicit request can resolve denial.
            resumeAfterInterruption = false
            return TimeshiftCommandResult.UNAVAILABLE
        }
        // Restoring sound only still releases a server hold whose acknowledgement was lost.
        val releaseServer = if (restoreSoundOnly) interruption != null && interruptionHoldUnconfirmed
        else resumeTimeshift && !retiredPendingPause ||
            interruption != null && interruptionContent == AudioInterruptionContent.LIVE_TIMESHIFT
        val result = if (releaseServer) coordinator.resumeTimeshift() else TimeshiftCommandResult.ACCEPTED
        interruption = null
        interruptionHoldUnconfirmed = false
        interruptionPaused = false
        resumeAfterInterruption = false
        setInterruptionMuted(false)
        player.play()
        resumeInterruptedRecoveryLocked()
        return result
    }

    private suspend fun handleAudioInterruptionLocked(event: AudioInterruption, wasPlaying: Boolean = player.playWhenReady) {
        val content = if (interruption != null) interruptionContent else currentInterruptionContent()
        val action = audioInterruptionAction(content, event, wasPlaying, resumeAfterInterruption)
        if (event == AudioInterruption.TRANSIENT_LOSS_CAN_DUCK) return
        if (event == AudioInterruption.GAIN) {
            when (action) {
                AudioInterruptionAction.RESUME -> {
                    if (content == AudioInterruptionContent.LIVE_TIMESHIFT &&
                        coordinator.resumeTimeshift() != TimeshiftCommandResult.ACCEPTED && interruptionPaused) return
                    interruptionPaused = false
                    setInterruptionMuted(false)
                    player.play()
                }
                AudioInterruptionAction.UNMUTE -> setInterruptionMuted(false)
                else -> return
            }
            interruption = null
            interruptionHoldUnconfirmed = false
            resumeAfterInterruption = false
            resumeInterruptedRecoveryLocked()
            return
        }
        val persistent = interruption == AudioInterruption.PERMANENT_LOSS || interruption == AudioInterruption.NOISY
        if (event != AudioInterruption.TRANSIENT_LOSS) resumeAfterInterruption = false
        else if (!persistent && interruption == null) resumeAfterInterruption = wasPlaying
        if (!persistent || event != AudioInterruption.TRANSIENT_LOSS) interruption = event
        interruptionContent = content
        when (action) {
            AudioInterruptionAction.PAUSE -> if (!interruptionPaused && !interruptionMuted) {
                val currentJob = currentCoroutineContext().job
                cancelRecoveryForInterruptionLocked(currentJob)
                interruptionPaused = true
                player.pause()
                val hold = if (content == AudioInterruptionContent.LIVE_TIMESHIFT) coordinator.pauseTimeshift() else null
                if (hold != null && hold != TimeshiftCommandResult.ACCEPTED) {
                    // Do not leave an unconfirmed server hold filling the pushed source queues.
                    // Keep the timeshift content kind so a subsequent resume also releases a
                    // server hold whose acknowledgement may have been lost.
                    interruptionHoldUnconfirmed = hold.disposition == TimeshiftCommandDisposition.UNCONFIRMED
                    setInterruptionMuted(true)
                    interruptionPaused = false
                    player.play()
                    // Consumption goes on muted, so the stopped recovery goes on too (and keeps the mute).
                    resumeInterruptedRecoveryLocked()
                }
            }
            AudioInterruptionAction.MUTE -> {
                setInterruptionMuted(true)
                // A denied initial request also needs to start video consumption.
                player.play()
                // Consumption goes on muted, so a recovery a Pause held goes on too.
                resumeInterruptedRecoveryLocked()
            }
            else -> Unit
        }
    }
}
