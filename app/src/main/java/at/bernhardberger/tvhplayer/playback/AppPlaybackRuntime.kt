@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.PlaybackBindingResult
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackOptions
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackStopResult
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AppPlaybackState {
    data object Idle : AppPlaybackState
    data object Starting : AppPlaybackState
    data object Playing : AppPlaybackState
    data object Finished : AppPlaybackState
    data class Recovering(val retryDelayMillis: Long) : AppPlaybackState
    data class Failed(val reason: AppPlaybackFailureReason) : AppPlaybackState
}

enum class AppPlaybackFailureReason { RECORDING_READ_FAILED, OTHER }
sealed interface AppPlaybackTarget {
    data class Live(val channelId: ChannelId) : AppPlaybackTarget
    data class Recording(val recordingId: DvrEntryId) : AppPlaybackTarget
}
data class LivePlaybackSelection(
    val currentSession: CurrentSessionObservation,
    val channelId: ChannelId,
)
data class RecordingPlaybackSelection(
    val currentSession: CurrentSessionObservation,
    val recordingId: DvrEntryId,
)
data class AppTimeshiftState(
    val available: Boolean = false,
    val paused: Boolean = false,
    val bufferStartMs: Long = 0L,
    val positionMs: Long = 0L,
    val liveEdgeMs: Long = 0L,
)
data class TimeshiftSeekDecision(
    val targetMs: Long,
    val deltaMs: Long,
    val clamped: Boolean,
)
enum class AppPlaybackSource { NONE, LIVE_TV, RECORDING }
data class AppPlaybackFormatDiagnostics(
    val codec: String?,
    val resolution: String? = null,
    val frameRate: Float? = null,
    val language: String? = null,
    val channelCount: Int? = null,
    val sampleRateHz: Int? = null,
)
data class AppPlaybackDiagnostics(
    val source: AppPlaybackSource = AppPlaybackSource.NONE,
    val state: AppPlaybackState = AppPlaybackState.Idle,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val bufferedMs: Long = 0L,
    val video: AppPlaybackFormatDiagnostics? = null,
    val audio: AppPlaybackFormatDiagnostics? = null,
)

/** App presentation adapter over the released coordinator and app-owned player. */
class AppPlaybackRuntime(
    val player: ExoPlayer,
    private val session: TvheadendSession,
    private val coordinator: TvheadendPlaybackCoordinator,
    private val settings: PlayerSettingsStore,
    private val profileOwner: AppProfileOwner,
    private val scope: CoroutineScope,
) {
    private val presentationEpoch = PlaybackPresentationEpoch()
    private val _state = MutableStateFlow<AppPlaybackState>(AppPlaybackState.Idle)
    private val _activeTarget = MutableStateFlow<AppPlaybackTarget?>(null)
    private val _recordingSelection = MutableStateFlow<RecordingPlaybackSelection?>(null)
    private val _recordingAdmission = MutableStateFlow<RecordingPlaybackAdmission?>(null)
    private val _diagnostics = MutableStateFlow(AppPlaybackDiagnostics())
    private var diagnosticsEnabled = false
    @Volatile
    private var activeTargetEpoch: Long? = null
    private var lastLiveSelection: LivePlaybackSelection? = null
    private var lastRecordingRequest: Pair<RecordingPlaybackSelection, RecordingPlaybackStart>? = null
    private var recoveryJob: Job? = null

    val state = _state.asStateFlow()
    val activeTarget = _activeTarget.asStateFlow()
    val recordingSelection = _recordingSelection.asStateFlow()
    val recordingAdmission = _recordingAdmission.asStateFlow()
    val timeshiftState: StateFlow<LiveTimeshiftState> = coordinator.timeshiftState
    val livePlaybackIssue: StateFlow<SubscriptionIssue?> = coordinator.subscriptionIssue
    val diagnostics = _diagnostics.asStateFlow()

    private val settingsJob = scope.launch {
        settings.playerSettings.distinctUntilChanged().collect(::applyPlayerSettings)
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = publishPlayerState()
        override fun onIsPlayingChanged(isPlaying: Boolean) = publishPlayerState()
        override fun onPlayerError(error: PlaybackException) {
            _state.value = AppPlaybackState.Failed(
                if (_activeTarget.value is AppPlaybackTarget.Recording) {
                    AppPlaybackFailureReason.RECORDING_READ_FAILED
                }
                else AppPlaybackFailureReason.OTHER,
            )
            publishDiagnostics()
        }
    }

    init {
        player.addListener(listener)
    }

    suspend fun playLive(selection: LivePlaybackSelection): PlaybackTargetResult? =
        playLive(selection, recovering = false)

    private suspend fun playLive(
        selection: LivePlaybackSelection,
        recovering: Boolean,
        epoch: Long = presentationEpoch.begin(),
    ): PlaybackTargetResult? {
        presentationEpoch.publishIfCurrent(epoch) {
            _activeTarget.value = null
            _recordingSelection.value = null
            _recordingAdmission.value = null
            activeTargetEpoch = null
            if (!recovering) _state.value = AppPlaybackState.Starting
        }
        lastLiveSelection = selection
        val playerSettings = settings.playerSettings.first()
        if (!presentationEpoch.isCurrent(epoch)) return null
        val streamProfileId = profileOwner.selectedStreamProfileIdFor(selection.currentSession)
        if (!presentationEpoch.isCurrent(epoch)) return null
        val result = when (
            val binding = session.bindLivePlayback(
                selection.currentSession,
                selection.channelId,
            )
        ) {
            is PlaybackBindingResult.Bound -> {
                player.play()
                coordinator.setLiveTarget(
                    binding.binding,
                    LivePlaybackOptions(
                        streamProfileId = streamProfileId,
                        timeshiftPeriod = if (playerSettings.timeshiftEnabled) 2.hours else kotlin.time.Duration.ZERO,
                    ),
                )
            }
            PlaybackBindingResult.ObservationExpired -> PlaybackTargetResult.NOT_READY
            PlaybackBindingResult.TargetUnavailable -> PlaybackTargetResult.TARGET_UNAVAILABLE
        }
        presentationEpoch.publishIfCurrent(epoch) {
            if (result == PlaybackTargetResult.STARTED) {
                _activeTarget.value = AppPlaybackTarget.Live(selection.channelId)
                activeTargetEpoch = epoch
                publishDiagnostics()
            } else {
                _state.value = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER)
            }
        }
        return result
    }

    suspend fun playRecording(
        selection: RecordingPlaybackSelection,
        start: RecordingPlaybackStart,
    ): PlaybackTargetResult? {
        val epoch = presentationEpoch.begin()
        lastRecordingRequest = selection to start
        presentationEpoch.publishIfCurrent(epoch) {
            _state.value = AppPlaybackState.Starting
            _activeTarget.value = null
            _recordingSelection.value = selection
            _recordingAdmission.value = null
            activeTargetEpoch = null
        }
        val result = when (
            val binding = session.bindRecordingPlayback(
                selection.currentSession,
                selection.recordingId,
            )
        ) {
            is PlaybackBindingResult.Bound -> {
                _recordingAdmission.value = binding.binding.admission
                coordinator.setRecordingTarget(binding.binding, start)
            }
            PlaybackBindingResult.ObservationExpired -> {
                _recordingAdmission.value = RecordingPlaybackAdmission.ObservationExpired
                PlaybackTargetResult.NOT_READY
            }
            PlaybackBindingResult.TargetUnavailable -> {
                _recordingAdmission.value = RecordingPlaybackAdmission.TargetUnavailable
                PlaybackTargetResult.TARGET_UNAVAILABLE
            }
        }
        presentationEpoch.publishIfCurrent(epoch) {
            if (result == PlaybackTargetResult.STARTED) {
                _activeTarget.value = AppPlaybackTarget.Recording(selection.recordingId)
                activeTargetEpoch = epoch
                publishDiagnostics()
            } else {
                _state.value = AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED)
            }
        }
        return result
    }

    suspend fun stop(): PlaybackStopResult {
        val epoch = presentationEpoch.begin()
        recoveryJob?.cancel()
        recoveryJob = null
        val result = coordinator.stop()
        presentationEpoch.publishIfCurrent(epoch) {
            _activeTarget.value = null
            _recordingSelection.value = null
            _recordingAdmission.value = null
            activeTargetEpoch = null
            _state.value = AppPlaybackState.Idle
            publishDiagnostics()
        }
        return result
    }

    suspend fun retryLive(): PlaybackTargetResult? =
        lastLiveSelection?.let { playLive(it, recovering = true) }
    suspend fun retryRecording(): PlaybackTargetResult? =
        lastRecordingRequest?.let { (selection, start) -> playRecording(selection, start) }
    suspend fun pauseTimeshift(): TimeshiftCommandResult = coordinator.pauseTimeshift()
    suspend fun resumeTimeshift(): TimeshiftCommandResult = coordinator.resumeTimeshift()
    suspend fun seekTimeshift(deltaMs: Long): TimeshiftCommandResult =
        coordinator.seekTimeshift(deltaMs.milliseconds)
    suspend fun goLive(): TimeshiftCommandResult = coordinator.returnToLive()
    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun setDiagnosticsEnabled(enabled: Boolean) {
        diagnosticsEnabled = enabled
        publishDiagnostics()
    }
    fun setRefreshRateMatchingEnabled(enabled: Boolean) {
        player.setVideoChangeFrameRateStrategy(
            if (enabled) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF,
        )
    }
    internal fun onRecoveryRequired(@Suppress("UNUSED_PARAMETER") reason: PlaybackRecoveryReason) {
        val selection = lastLiveSelection ?: return
        val targetEpoch = activeTargetEpoch ?: return
        val recoveryEpoch = presentationEpoch.beginIfCurrent(targetEpoch) ?: return
        presentationEpoch.publishIfCurrent(recoveryEpoch) {
            _state.value = AppPlaybackState.Recovering(retryDelayMillis = 0L)
        }
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            playLive(selection, recovering = true, epoch = recoveryEpoch)
        }
    }

    fun detach() {
        recoveryJob?.cancel()
        recoveryJob = null
        settingsJob.cancel()
        player.removeListener(listener)
    }

    private fun applyPlayerSettings(value: PlayerSettings) {
        val audioLanguages: Array<String> = value.audioLanguage?.let { arrayOf(it) } ?: emptyArray()
        val subtitleLanguages: Array<String> =
            value.subtitleLanguage?.let { arrayOf(it) } ?: emptyArray()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(*audioLanguages)
            .setPreferredTextLanguages(*subtitleLanguages)
            .build()
        setRefreshRateMatchingEnabled(value.refreshRateMatchingEnabled)
    }

    private fun publishPlayerState() {
        _state.value = when {
            player.playerError != null -> _state.value
            player.playbackState == Player.STATE_ENDED -> AppPlaybackState.Finished
            player.isPlaying -> AppPlaybackState.Playing
            player.playbackState == Player.STATE_BUFFERING -> AppPlaybackState.Starting
            player.playbackState == Player.STATE_IDLE -> AppPlaybackState.Idle
            else -> _state.value
        }
        publishDiagnostics()
    }

    private fun publishDiagnostics() {
        if (!diagnosticsEnabled) {
            _diagnostics.value = AppPlaybackDiagnostics(source = source(), state = _state.value)
            return
        }
        val video = player.videoFormat
        val audio = player.audioFormat
        _diagnostics.value = AppPlaybackDiagnostics(
            source = source(),
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
        )
    }

    private fun source() = when {
        _activeTarget.value is AppPlaybackTarget.Live -> AppPlaybackSource.LIVE_TV
        _activeTarget.value is AppPlaybackTarget.Recording -> AppPlaybackSource.RECORDING
        else -> AppPlaybackSource.NONE
    }
}

internal class PlaybackPresentationEpoch {
    private val lock = Any()
    private var current = 0L

    fun begin(): Long = synchronized(lock) {
        check(current < Long.MAX_VALUE) { "Playback presentation epoch exhausted" }
        ++current
    }

    fun isCurrent(epoch: Long): Boolean = synchronized(lock) { epoch == current }

    fun beginIfCurrent(epoch: Long): Long? = synchronized(lock) {
        if (epoch != current) return@synchronized null
        check(current < Long.MAX_VALUE) { "Playback presentation epoch exhausted" }
        ++current
    }

    fun publishIfCurrent(epoch: Long, publish: () -> Unit): Boolean = synchronized(lock) {
        if (epoch != current) return@synchronized false
        publish()
        true
    }
}

fun LiveTimeshiftState.toAppPresentation(): AppTimeshiftState = when (this) {
    LiveTimeshiftState.Unavailable -> AppTimeshiftState()
    is LiveTimeshiftState.Available -> measuredTimeshiftPresentation(
        bufferedDuration = bufferedDuration,
        positionBehindLive = positionBehindLive,
        serverPaused = serverPaused,
    )
}

internal fun measuredTimeshiftPresentation(
    bufferedDuration: Duration?,
    positionBehindLive: Duration?,
    serverPaused: Boolean?,
): AppTimeshiftState {
    val behind = positionBehindLive?.inWholeMilliseconds?.coerceAtLeast(0L) ?: 0L
    val buffered = bufferedDuration?.inWholeMilliseconds?.coerceAtLeast(behind) ?: behind
    return AppTimeshiftState(true, serverPaused == true, -buffered, -behind, 0L)
}

private val Int.hours get() = this.seconds * 3_600
