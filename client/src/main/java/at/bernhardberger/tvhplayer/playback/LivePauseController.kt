@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandDisposition
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendPlaybackCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * How long a pending live pause waits for the first picture before holding the server anyway:
 * below the SDK's preparation budget and well before the SDK sample queues fill.
 */
internal const val FIRST_PICTURE_BUDGET_MILLIS = 10_000L

/**
 * [AppPlaybackRuntime]'s live Pause: whether Pause is available for the active live target, and a
 * Pause pressed before the timeshift grant is known, until it is resolved or dropped. The runtime
 * owns the lock, the active target and its epoch, the playback state and the audio interruption.
 */
internal class LivePauseController(
    private val player: ExoPlayer,
    private val coordinator: TvheadendPlaybackCoordinator,
    private val scope: CoroutineScope,
    private val targetCommands: PlaybackTargetCommandSerialization,
    private val livePlaybackObservation: StateFlow<LivePlaybackObservation>,
    private val _state: StateFlow<AppPlaybackState>,
    private val _activeTarget: StateFlow<AppPlaybackTarget?>,
    private val activeTargetEpoch: () -> Long?,
    private val foreground: () -> Boolean,
    private val interruption: () -> AudioInterruption?,
    private val stopPausedRetune: suspend () -> Unit,
) {
    /**
     * A Pause pressed before the timeshift grant is known, for one live target epoch.
     * [failClosed] marks the paused retune of a kept channel: without a server pause it stops.
     * [pictureWaitExpired]: the first picture did not arrive within [FIRST_PICTURE_BUDGET_MILLIS],
     * so the grant alone resolves it; [pictureWait] is that budget.
     */
    private class PendingLivePause(val epoch: Long, val wasPlaying: Boolean, val failClosed: Boolean) {
        var pictureWaitExpired = false
        var pictureWait: Job? = null
    }
    private var pendingLivePause: PendingLivePause? = null
        set(value) {
            // Every way a pending pause ends (resolution, resume, retarget, stop, detach,
            // background, failure) also ends its first-picture budget.
            if (field !== value) field?.pictureWait?.cancel()
            field = value
        }
    var timeshiftRequestedEpoch: Long? = null
    var readyReachedEpoch: Long? = null
        private set
    private val _livePause = MutableStateFlow(LivePauseState())
    val livePause = _livePause.asStateFlow()
    private val _livePauseNotice = MutableStateFlow<LivePauseUnavailableNotice?>(null)
    val livePauseNotice = _livePauseNotice.asStateFlow()

    /** The epoch of the pending pause, if there is one. */
    val pendingLivePauseEpoch: Long? get() = pendingLivePause?.epoch

    fun consumeLivePauseNotice(notice: LivePauseUnavailableNotice) {
        _livePauseNotice.compareAndSet(notice, null)
    }

    /** Ends the pending pause without publishing, as installation, stop, detach and failures do. */
    fun dropPendingLivePauseLocked() {
        pendingLivePause = null
    }

    /**
     * Player error: a failed target never completes a pause pressed before its grant, so a
     * pending pause ends, unless it fails closed. Returns the epoch of one that fails closed, for
     * [launchPendingLivePauseResolution] once the failure is published.
     *
     * The caller holds at least the access lock (`runIfOpen`). This may run while a serialized
     * command is suspended, so it must not rely on the command lock.
     */
    fun onPlayerErrorUnderAccessLock(): Long? {
        val pending = pendingLivePause
        if (pending?.failClosed == false) pendingLivePause = null
        return pending?.takeIf { it.failClosed }?.epoch
    }

    fun currentLivePauseAvailability(): LivePauseAvailability {
        val epoch = activeTargetEpoch()
        return livePauseAvailability(
            liveTarget = epoch != null && _activeTarget.value is AppPlaybackTarget.Live,
            timeshiftRequested = epoch != null && timeshiftRequestedEpoch == epoch,
            timeshiftAvailable = (livePlaybackObservation.value as? LivePlaybackObservation.Active)
                ?.timeshiftState is LiveTimeshiftState.Available,
            readyReached = epoch != null && readyReachedEpoch == epoch,
        )
    }

    fun publishLivePause() {
        val epoch = activeTargetEpoch()
        _livePause.value = LivePauseState(
            availability = currentLivePauseAvailability(),
            pending = epoch != null && pendingLivePause?.epoch == epoch,
        )
    }

    /** Serialized. The pending pause waits for the first picture at most [FIRST_PICTURE_BUDGET_MILLIS]. */
    fun startPendingLivePauseLocked(epoch: Long, wasPlaying: Boolean, failClosed: Boolean) {
        val pending = PendingLivePause(epoch, wasPlaying = wasPlaying, failClosed = failClosed)
        pendingLivePause = pending
        publishLivePause()
        pending.pictureWait = scope.launch {
            delay(FIRST_PICTURE_BUDGET_MILLIS)
            targetCommands.serialize(onClosed = {}) {
                if (pendingLivePause !== pending || pending.epoch != activeTargetEpoch()) return@serialize
                // Resolving clears this pending pause; that must not cancel the resolution itself.
                pending.pictureWait = null
                pending.pictureWaitExpired = true
                resolvePendingLivePauseLocked()
                Unit
            }
        }
    }

    fun clearPendingLivePauseLocked() {
        if (pendingLivePause == null) return
        pendingLivePause = null
        publishLivePause()
    }

    /**
     * A live pause for [epoch] stays local and pending until the target first reached STATE_READY:
     * the grant is still undecided, or granted but a server hold now would stop the stream before
     * the first picture. The wait for the picture is bounded ([pictureWaitExpired]): past the
     * budget a granted pause holds the server, so its queues do not fill behind a paused player.
     *
     * Known limitation: [readyReachedEpoch] stays set for the whole epoch, so while the SDK
     * re-prepares the same target (no new epoch) a Pause sends the server pause at once, before
     * the new first picture.
     */
    fun awaitsFirstPicture(
        epoch: Long,
        availability: LivePauseAvailability,
        pictureWaitExpired: Boolean = false,
    ): Boolean =
        availability == LivePauseAvailability.STARTING ||
            availability == LivePauseAvailability.READY && readyReachedEpoch != epoch && !pictureWaitExpired

    /** The current target reached STATE_READY; without a grant by now it has none. */
    fun onTargetReady(epoch: Long?) {
        if (epoch == null || epoch != activeTargetEpoch() || readyReachedEpoch == epoch) return
        readyReachedEpoch = epoch
        publishLivePause()
        if (pendingLivePause?.epoch != epoch) return
        launchPendingLivePauseResolution(epoch)
    }

    fun launchPendingLivePauseResolution(epoch: Long) {
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
                if (epoch == activeTargetEpoch()) resolvePendingLivePauseLocked()
                Unit
            }
        }
    }

    /**
     * Serialized. Completes a pending pause once the grant is decided: one server pause when
     * timeshift became available, otherwise the pause is dropped without any server command.
     * A failed target drops it too; a paused kept-channel retune then stops (fail closed).
     */
    suspend fun resolvePendingLivePauseLocked(): Boolean {
        val pending = pendingLivePause ?: return false
        if (pending.epoch != activeTargetEpoch() || !foreground() || !targetCommands.isOpen()) {
            if (pending.epoch != activeTargetEpoch()) clearPendingLivePauseLocked()
            return false
        }
        if (_state.value is AppPlaybackState.Failed) {
            clearPendingLivePauseLocked()
            if (pending.failClosed) stopPausedRetune()
            return true
        }
        val availability = currentLivePauseAvailability()
        // The grant alone does not resolve it: STATE_READY does (see onTargetReady).
        if (awaitsFirstPicture(pending.epoch, availability, pending.pictureWaitExpired)) return false
        clearPendingLivePauseLocked()
        val rejected = if (availability == LivePauseAvailability.READY) {
            val result = coordinator.pauseTimeshift()
            // A player error while the pause was in flight found no pending pause to fail closed.
            if (pending.failClosed) result != TimeshiftCommandResult.ACCEPTED || _state.value is AppPlaybackState.Failed
            else result.disposition == TimeshiftCommandDisposition.NOT_ACCEPTED
        } else true
        if (!rejected || !targetCommands.isOpen() || pending.epoch != activeTargetEpoch()) return true
        if (pending.failClosed) {
            stopPausedRetune()
            return true
        }
        if (foreground() && interruption() == null) player.playWhenReady = pending.wasPlaying
        _livePauseNotice.value = LivePauseUnavailableNotice()
        return true
    }
}
