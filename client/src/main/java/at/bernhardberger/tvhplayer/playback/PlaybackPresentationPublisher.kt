@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [AppPlaybackRuntime]'s publication of player state, diagnostics and video presentation, and its
 * debug seek diagnostics. The runtime owns the state, the active target and its epoch; player
 * errors are published by the runtime, because they end a pending live pause.
 */
internal class PlaybackPresentationPublisher(
    private val player: ExoPlayer,
    private val session: TvheadendSession,
    private val policy: PlaybackRuntimePolicy,
    private val startupBuffer: StartupBufferController?,
    private val targetCommands: PlaybackTargetCommandSerialization,
    private val recoveryAttempts: LiveRecoveryAttemptRunner,
    private val livePlaybackObservation: StateFlow<LivePlaybackObservation>,
    private val _state: MutableStateFlow<AppPlaybackState>,
    private val _activeTarget: StateFlow<AppPlaybackTarget?>,
    private val activeTargetEpoch: () -> Long?,
    private val targetInstallationInProgress: () -> Boolean,
    private val publishPlayerErrorFromPlayer: () -> Unit,
) {
    private val _diagnostics = MutableStateFlow(AppPlaybackDiagnostics())
    private val _videoPresentation = MutableStateFlow(AppVideoPresentation())
    private var diagnosticsEnabled = false
    private var targetFrameListener: Player.Listener? = null
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

    fun beginTargetPresentationLocked(epoch: Long) {
        targetCommands.runIfOpen {
            targetFrameListener?.let(player::removeListener)
            _videoPresentation.value = _videoPresentation.value.beginTarget(epoch)
            targetFrameListener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (targetInstallationInProgress() || !targetCommands.isOpen()) return
                    val notYetVisible = policy.trace.enabled && !_videoPresentation.value.visible
                    _videoPresentation.value = _videoPresentation.value.onFirstFrame(
                        frameEpoch = epoch,
                        activeTargetEpoch = activeTargetEpoch(),
                    )
                    if (notYetVisible && epoch == activeTargetEpoch() && _videoPresentation.value.visible) {
                        val live = livePlaybackObservation.value as? LivePlaybackObservation.Active
                        policy.trace.firstVideoFrame(epoch, player.videoFormat, live?.diagnostics?.source?.adapterName)
                    }
                }
            }.also(player::addListener)
        }
    }

    fun endTargetPresentationLocked(epoch: Long) {
        targetCommands.runIfOpen {
            targetFrameListener?.let(player::removeListener)
            targetFrameListener = null
            _videoPresentation.value = _videoPresentation.value.beginTarget(epoch)
        }
    }

    /** Detach, inside the runtime's final idle wait. */
    fun removeTargetFrameListenerLocked() {
        targetFrameListener?.let(player::removeListener)
        targetFrameListener = null
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
            publishDiagnosticsFromPlayer()
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
            publishDiagnosticsFromPlayer()
        }
    }

    fun publishDiagnostics() {
        if (!diagnosticsEnabled) {
            _diagnostics.value = AppPlaybackDiagnostics(source = source(), state = _state.value)
            return
        }
        if (!targetCommands.runIfOpen { publishDiagnosticsFromPlayer() }) {
            _diagnostics.value = AppPlaybackDiagnostics(source = source(), state = _state.value)
        }
    }

    fun publishDiagnosticsFromPlayer() {
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
                )
            },
            audio = audio?.let {
                AppPlaybackFormatDiagnostics(
                    codec = it.codecs,
                    language = it.language,
                    channelCount = it.channelCount.takeIf { count -> count > 0 },
                    sampleRateHz = it.sampleRate.takeIf { rate -> rate > 0 },
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
