@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How often a playing target's covered first frame is checked for a further rendered frame. */
internal const val MOTION_POLL_MILLIS = 20L

/**
 * [AppPlaybackRuntime]'s publication of player state, diagnostics and video presentation, and its
 * debug seek diagnostics. This is the only writer of [state]: the runtime decides which state its
 * commands reach and publishes it through the named `publish…` methods. The runtime owns the
 * active target and its epoch; it handles player errors ([publishPlayerErrorFromPlayer]), because
 * they end a pending live pause, and publishes the resulting failure here.
 */
internal class PlaybackPresentationPublisher(
    private val player: ExoPlayer,
    private val session: TvheadendSession,
    private val policy: PlaybackRuntimePolicy,
    private val startupBuffer: StartupBufferController?,
    private val targetCommands: PlaybackTargetCommandSerialization,
    private val recoveryAttempts: LiveRecoveryAttemptRunner,
    private val livePlaybackObservation: StateFlow<LivePlaybackObservation>,
    private val _activeTarget: StateFlow<AppPlaybackTarget?>,
    private val activeTargetEpoch: () -> Long?,
    private val targetInstallationInProgress: () -> Boolean,
    private val publishPlayerErrorFromPlayer: () -> Unit,
) {
    private val _state = MutableStateFlow<AppPlaybackState>(AppPlaybackState.Idle)
    val state = _state.asStateFlow()
    private val _diagnostics = MutableStateFlow(AppPlaybackDiagnostics())
    private val _videoPresentation = MutableStateFlow(AppVideoPresentation())
    private var diagnosticsEnabled = false
    private var targetFrameListener: Player.Listener? = null
    private var presentationBeforeInstall: AppVideoPresentation? = null
    private var staleVideoFormat: Format? = null

    // Guarded by targetCommands.runIfOpen: set by the frame listener while an install runs, read
    // by the install's finally block. The replaced target's listener can see the next target's
    // first frame before that target's presentation begins, and still-queued frames of the
    // replaced source after the next one is set: a frame is the decoder counters it was rendered
    // on (see [frameStands]).
    private var frameDuringInstall = false
    private var installFrameCounters: DecoderCounters? = null

    // The presented target's evidence for a moving picture, kept by its frame listener: Media3 can
    // render a first frame while it still buffers, so the cover lifts only once the target has
    // rendered its first frame and is ready, in either order.
    private var targetFrameRendered = false
    private var targetFrameCounters: DecoderCounters? = null
    private var targetReady = false

    // A stopped player is a deliberate pause only once the started target's play intent has been
    // settled and while no play waits for the server to resume the stream: until then the cover
    // of a stopped player still waits for a moving picture.
    private var startIntentPending = false
    private var serverResumePending = false

    // A ready first frame of a playing target still stands until the audio clock runs, so its
    // cover also waits for the video decoder to render a further frame. Polled on the player
    // looper only from that point to the lift, for the one epoch it watches.
    private val motionHandler = Handler(player.applicationLooper)
    private var motionWatchEpoch: Long? = null
    private var motionBaseline = 0
    private val motionPoll = object : Runnable {
        override fun run() {
            targetCommands.runIfOpen {
                val epoch = motionWatchEpoch ?: return@runIfOpen
                if (targetInstallationInProgress()) return@runIfOpen stopMotionWatch()
                liftCoverOnReadyPicture(epoch)
                if (motionWatchEpoch == epoch) motionHandler.postDelayed(this, MOTION_POLL_MILLIS)
            }
        }
    }
    val diagnostics = _diagnostics.asStateFlow()
    val videoPresentation = _videoPresentation.asStateFlow()

    private var diagnosticDecoderName = "unknown"
    private var diagnosticDecoderGeneration = 0
    val seekDiagnosticsListener = if (policy.seekDiagnostics) {
        object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                diagnosticDecoderName = decoderName.take(80).replace(Regex("[^A-Za-z0-9._-]"), "_")
                diagnosticDecoderGeneration++
            }
        }
    } else null

    fun setDiagnosticsEnabled(enabled: Boolean) {
        if (!targetCommands.isOpen()) return
        diagnosticsEnabled = enabled
        publishDiagnostics()
    }

    /** Runs [seek] and, for a paused seek with seek diagnostics on, logs one bounded line about it. */
    suspend fun diagnoseSeekLocked(
        selection: at.bernhardberger.tvheadend.sdk.media3.TimeshiftSeekSelection,
        seek: suspend () -> at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult,
    ): at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult {
        val diagnose = policy.seekDiagnostics && !player.playWhenReady
        fun counters(): String {
            val value = player.videoDecoderCounters ?: return "none"
            value.ensureUpdated()
            return "${value.queuedInputBufferCount}/${value.renderedOutputBufferCount}/" +
                "${value.skippedOutputBufferCount}/${value.droppedBufferCount}"
        }
        val before = if (diagnose) counters() else ""
        val generation = diagnosticDecoderGeneration
        val started = android.os.SystemClock.elapsedRealtime()
        return seek().also { result ->
            if (diagnose) {
                val completed = result as? at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Completed
                val format = player.videoFormat
                val videoSelected = player.currentTracks.groups.any {
                    it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSelected
                }
                // One bounded line per paused UI seek. No channel, profile, endpoint,
                // raw error, subscription identity or credential-bearing payload.
                android.util.Log.i("TvhSeek", "outcome=${completed?.command ?: result.javaClass.simpleName} " +
                    "seek=${completed?.seekCommand} buffering=${completed?.buffering} pause=${completed?.pauseRestoration} " +
                    "elapsedMs=${android.os.SystemClock.elapsedRealtime() - started} " +
                    "countsQueuedRenderedSkippedDropped=$before->${counters()} " +
                    "decoder=$diagnosticDecoderName generation=$generation->$diagnosticDecoderGeneration " +
                    "videoSelected=$videoSelected size=${format?.width}x${format?.height} state=${player.playbackState} " +
                    "playWhenReady=${player.playWhenReady} bufferedMs=${player.totalBufferedDuration} " +
                    "requestedDeltaMs=${selection.displacement.inWholeMilliseconds}")
            }
        }
    }

    /**
     * Covers the video as a target install begins, before the old target is paused or a new
     * source is set, so the old picture cannot show while the next target starts. Also records
     * the old target's video format: its first-frame events can still be queued on the player
     * looper when the next target's frame listener is registered.
     */
    fun coverVideoForTargetInstallLocked() {
        targetCommands.runIfOpen {
            stopMotionWatch()
            val current = _videoPresentation.value
            presentationBeforeInstall = current
            staleVideoFormat = player.videoFormat
            frameDuringInstall = false
            installFrameCounters = null
            _videoPresentation.value = current.copy(visible = false)
            if (policy.trace.enabled) policy.trace.videoCoverShown()
        }
    }

    /**
     * Uncovers the old target's picture when an install failed and the old target stays. A first
     * frame the old target rendered during the install counts while its decoder still renders: the
     * player does not report it again. A picture that was not
     * yet ready stays covered until the old target's frame listener sees it ready. A target that
     * [resumesPlaying] was paused for the attempt: its picture stands until its audio runs again,
     * so it stays covered until the picture moves, as for a new target.
     */
    fun restoreVideoAfterFailedInstallLocked(resumesPlaying: Boolean) {
        targetCommands.runIfOpen {
            val previous = presentationBeforeInstall ?: return@runIfOpen
            if (frameDuringInstall) {
                targetFrameRendered = true
                targetFrameCounters = installFrameCounters
            }
            presentationBeforeInstall = null
            staleVideoFormat = null
            frameDuringInstall = false
            installFrameCounters = null
            if (_videoPresentation.value != previous.copy(visible = false)) return@runIfOpen
            if (resumesPlaying) return@runIfOpen liftCoverOnReadyPicture(previous.epoch, playing = true)
            val restored = when {
                previous.visible -> previous
                frameStands() && targetReady ->
                    previous.onFirstFrame(frameEpoch = previous.epoch, activeTargetEpoch = activeTargetEpoch())
                else -> return@runIfOpen
            }
            _videoPresentation.value = restored
            if (policy.trace.enabled && restored.visible) policy.trace.videoCoverLifted(restored.epoch)
        }
    }

    fun beginTargetPresentationLocked(epoch: Long) {
        targetCommands.runIfOpen {
            targetFrameListener?.let(player::removeListener)
            stopMotionWatch()
            _videoPresentation.value = _videoPresentation.value.beginTarget(epoch)
            val staleFormat = staleVideoFormat
            presentationBeforeInstall = null
            staleVideoFormat = null
            // A frame from before the begin may be the next source's: it is carried with the
            // counters it was rendered on, so it counts only if that decoder is the new source's.
            // A frame without counters cannot show that.
            frameDuringInstall = frameDuringInstall && installFrameCounters != null
            if (!frameDuringInstall) installFrameCounters = null
            targetFrameRendered = false
            targetFrameCounters = null
            targetReady = player.playbackState == Player.STATE_READY
            startIntentPending = true
            targetFrameListener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (!targetCommands.isOpen()) return
                    // Until the new source reports its own format, a first frame is the old one's.
                    if (staleFormat != null && player.videoFormat === staleFormat) return
                    if (targetInstallationInProgress()) {
                        targetCommands.runIfOpen {
                            frameDuringInstall = true
                            installFrameCounters = player.videoDecoderCounters
                        }
                        return
                    }
                    noteFirstFrame(epoch, player.videoDecoderCounters)
                    liftCoverOnReadyPicture(epoch)
                }

                // Paused targets count too: READY does not depend on playWhenReady.
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (!targetCommands.isOpen()) return
                    targetReady = playbackState == Player.STATE_READY
                    if (!targetInstallationInProgress()) liftCoverOnReadyPicture(epoch)
                }

                // A pause shows a ready picture; a play starts watching it for motion.
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!targetCommands.isOpen()) return
                    if (!targetInstallationInProgress()) liftCoverOnReadyPicture(epoch)
                }
            }.also(player::addListener)
        }
    }

    /**
     * The start of the target [epoch] ends, also when applying its play intent was cut short. A
     * first frame its source rendered while the install still ran counts now. Its cover then
     * follows the rule for any first frame. A start whose play was cut short ([playStillPending])
     * still has the install's stopped player, so its cover keeps waiting for a moving picture
     * until a play or the viewer's pause ([settlePausedPictureLocked]).
     */
    fun presentStartedTargetLocked(epoch: Long?, playStillPending: Boolean) {
        targetCommands.runIfOpen {
            val presented = _videoPresentation.value.epoch
            if (targetFrameListener == null || presentationBeforeInstall != null || epoch != presented) return@runIfOpen
            // The target's own frame since is newer; a carried frame's decoder may be gone.
            if (frameDuringInstall && !targetFrameRendered && installFrameCounters === player.videoDecoderCounters) {
                noteFirstFrame(presented, installFrameCounters)
            }
            frameDuringInstall = false
            installFrameCounters = null
            startIntentPending = playStillPending
            liftCoverOnReadyPicture(presented)
        }
    }

    /** The viewer paused the presented target: its ready picture shows, also after a cut-short start. */
    fun settlePausedPictureLocked() {
        targetCommands.runIfOpen {
            if (targetFrameListener == null) return@runIfOpen
            startIntentPending = false
            liftCoverOnReadyPicture(_videoPresentation.value.epoch)
        }
    }

    /**
     * A play of the presented target that waits for the server to resume the stream before the
     * player plays [held]: meanwhile its stopped player is no deliberate pause. Its end looks at
     * the picture again.
     */
    fun holdPlayingPictureLocked(held: Boolean) {
        targetCommands.runIfOpen {
            serverResumePending = held
            if (!held && targetFrameListener != null) liftCoverOnReadyPicture(_videoPresentation.value.epoch)
        }
    }

    private fun noteFirstFrame(epoch: Long, counters: DecoderCounters?) {
        if (policy.trace.enabled && !targetFrameRendered && epoch == activeTargetEpoch()) {
            val live = livePlaybackObservation.value as? LivePlaybackObservation.Active
            policy.trace.firstVideoFrame(epoch, player.videoFormat, live?.diagnostics?.source?.adapterName)
        }
        targetFrameRendered = true
        targetFrameCounters = counters
    }

    /**
     * Whether the target's first frame still stands: it was rendered on the decoder counters the
     * player reports now. Media3 creates counters per video renderer enable and reports them
     * before that enable's first frame and until after its last, in order with its frames. A new
     * source disables the old renderer before it reports its own READY, so a still-queued frame
     * of the replaced source never stands for a ready new target.
     */
    private fun frameStands() = targetFrameRendered && targetFrameCounters === player.videoDecoderCounters

    /**
     * A paused picture shows once ready; a playing one waits until [pictureAdvanced]. An install
     * in progress decides its own cover.
     */
    private fun liftCoverOnReadyPicture(
        epoch: Long,
        playing: Boolean = startIntentPending || serverResumePending || player.playWhenReady,
    ) {
        if (presentationBeforeInstall != null) return
        if (!frameStands() || !targetReady || _videoPresentation.value.visible) return stopMotionWatch()
        if (playing && !pictureAdvanced(epoch)) return
        stopMotionWatch()
        _videoPresentation.value = _videoPresentation.value.onFirstFrame(
            frameEpoch = epoch,
            activeTargetEpoch = activeTargetEpoch(),
        )
        if (policy.trace.enabled && _videoPresentation.value.visible) policy.trace.videoCoverLifted(epoch)
    }

    /**
     * Whether the video decoder rendered a frame since the watch of [epoch] began, which starts on
     * the first call. Without decoder counters there is no frame to count and the ready first
     * frame suffices, as for a paused picture. A stopped player renders no further frame: the
     * watch waits for its play.
     */
    private fun pictureAdvanced(epoch: Long): Boolean {
        val counters = player.videoDecoderCounters ?: return true
        if (!player.playWhenReady) {
            stopMotionWatch()
            return false
        }
        counters.ensureUpdated()
        val rendered = counters.renderedOutputBufferCount
        if (motionWatchEpoch != epoch) {
            stopMotionWatch()
            motionWatchEpoch = epoch
            motionBaseline = rendered
            motionHandler.postDelayed(motionPoll, MOTION_POLL_MILLIS)
            return false
        }
        if (rendered <= motionBaseline) return false
        if (policy.trace.enabled) policy.trace.videoPictureAdvancing(epoch)
        return true
    }

    private fun stopMotionWatch() {
        motionWatchEpoch = null
        motionHandler.removeCallbacks(motionPoll)
    }

    fun endTargetPresentationLocked(epoch: Long) {
        targetCommands.runIfOpen {
            targetFrameListener?.let(player::removeListener)
            targetFrameListener = null
            stopMotionWatch()
            presentationBeforeInstall = null
            staleVideoFormat = null
            frameDuringInstall = false
            installFrameCounters = null
            targetFrameRendered = false
            targetFrameCounters = null
            targetReady = false
            startIntentPending = false
            _videoPresentation.value = _videoPresentation.value.beginTarget(epoch)
        }
    }

    /** Detach, inside the runtime's final idle wait. */
    fun removeTargetFrameListenerLocked() {
        targetFrameListener?.let(player::removeListener)
        targetFrameListener = null
        stopMotionWatch()
    }

    /*
     * The runtime's state publications. Plain writes, as the runtime made them before: each caller
     * keeps its own lock and presentation-epoch discipline, and publishes diagnostics itself.
     */

    fun publishIdle() {
        _state.value = AppPlaybackState.Idle
    }

    fun publishStarting() {
        _state.value = AppPlaybackState.Starting
    }

    fun publishRecovering(reason: PlaybackRecoveryReason, retryDelayMillis: Long) {
        _state.value = AppPlaybackState.Recovering(reason = reason, retryDelayMillis = retryDelayMillis)
    }

    fun publishFailed(failure: AppPlaybackState.Failed) {
        _state.value = failure
    }

    fun publishPlayerState(recoveryResolved: Boolean = false) {
        targetCommands.runIfOpen {
            if (player.playerError == null) {
                _state.value = if (recoveryResolved) {
                    playerStateAfterRecoveryResolution(
                        currentState = _state.value,
                        playbackState = player.playbackState,
                        isPlaying = player.isPlaying,
                    )
                } else {
                    playerReportedPlaybackState(
                        currentState = _state.value,
                        recoveryAttemptInProgress = recoveryAttempts.ownsPlayerState(
                            activeTarget = _activeTarget.value,
                            activeTargetEpoch = activeTargetEpoch(),
                            observation = session.observation.value,
                        ),
                        playbackState = player.playbackState,
                        isPlaying = player.isPlaying,
                    )
                }
            }
            publishDiagnosticsUnderAccessLock()
        }
    }

    fun publishInstalledPlayerStateLocked() {
        targetCommands.runIfOpen {
            if (player.playerError != null) {
                publishPlayerErrorFromPlayer()
                return@runIfOpen
            }
            _state.value = playerReportedPlaybackState(
                currentState = AppPlaybackState.Starting,
                recoveryAttemptInProgress = false,
                playbackState = player.playbackState,
                isPlaying = player.isPlaying,
            )
            publishDiagnosticsUnderAccessLock()
        }
    }

    fun publishDiagnostics() {
        if (!targetCommands.runIfOpen { publishDiagnosticsUnderAccessLock() }) {
            _diagnostics.value = AppPlaybackDiagnostics(source = source(), state = _state.value)
        }
    }

    /**
     * Publishes diagnostics read from the player. The caller holds at least the access lock
     * (`runIfOpen`), so the player is still attached; this may run while a serialized command is
     * suspended, so it must not rely on the command lock. The runtime's player-error publication
     * calls it after the failure and the live pause are published.
     */
    fun publishDiagnosticsUnderAccessLock() {
        // Player-state, target-install and error callbacks also enter here directly.
        if (!diagnosticsEnabled) {
            _diagnostics.value = AppPlaybackDiagnostics(source = source(), state = _state.value)
            return
        }
        val activeTarget = _activeTarget.value
        val activeLiveObservation =
            livePlaybackObservation.value as? LivePlaybackObservation.Active
        val video = player.videoFormat
        val audio = player.audioFormat
        _diagnostics.value = AppPlaybackDiagnostics(
            source = source(activeTarget),
            state = _state.value,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it >= 0L },
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
            video = video?.let {
                AppPlaybackFormatDiagnostics(
                    codec = it.codecs,
                    resolution = if (it.width > 0 && it.height > 0) "${it.width}×${it.height}" else null,
                    frameRate = it.frameRate.takeIf { rate -> rate > 0f },
                    sampleMimeType = it.sampleMimeType,
                    width = it.width.takeIf { width -> width > 0 },
                    height = it.height.takeIf { height -> height > 0 },
                )
            },
            videoDecoderCounters = player.videoDecoderCounters?.snapshotVideoDecoderCounters(),
            audio = audio?.let {
                AppPlaybackFormatDiagnostics(
                    codec = it.codecs,
                    language = it.language,
                    channelCount = it.channelCount.takeIf { count -> count > 0 },
                    sampleRateHz = it.sampleRate.takeIf { rate -> rate > 0 },
                    sampleMimeType = it.sampleMimeType,
                )
            },
            live = liveDiagnosticsForTarget(
                activeTarget = activeTarget,
                diagnostics = activeLiveObservation?.diagnostics,
            ),
            startupBuffer = startupBuffer?.inEffect?.value?.takeIf { activeTarget is AppPlaybackTarget.Live },
        )
    }

    private fun source(activeTarget: AppPlaybackTarget? = _activeTarget.value) = when (activeTarget) {
        is AppPlaybackTarget.Live -> AppPlaybackSource.LIVE_TV
        is AppPlaybackTarget.Recording -> AppPlaybackSource.RECORDING
        else -> AppPlaybackSource.NONE
    }
}

internal fun androidx.media3.exoplayer.DecoderCounters.snapshotVideoDecoderCounters(): AppVideoDecoderCounters {
    ensureUpdated()
    return AppVideoDecoderCounters(
        renderedOutputBufferCount = renderedOutputBufferCount,
        droppedBufferCount = droppedBufferCount,
        skippedOutputBufferCount = skippedOutputBufferCount,
    )
}
