@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackOptions
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackStopResult
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.core.StreamProfileDiscovery
import at.bernhardberger.tvhplayer.data.DvrEntry
import at.bernhardberger.tvhplayer.data.RecordingProgressCapability
import at.bernhardberger.tvhplayer.data.RecordingPlaybackIntent
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    data class Live(val serviceId: Int) : AppPlaybackTarget
    data class Recording(val recordingId: Int) : AppPlaybackTarget
}
enum class AppPlaybackCommandResult {
    SUBMITTED,
    STOPPED,
    ALREADY_STOPPED,
    NOT_RUNNING,
    SHUT_DOWN,
    NOT_READY,
    RECORDING_PROGRESS_UNSUPPORTED,
    TARGET_UNAVAILABLE,
    GROWING_RECORDING_RESUME_UNSUPPORTED,
    GROWING_RECORDING_DEFERRED,
    PLAYER_UNAVAILABLE,
    REJECTED,
    UNAVAILABLE,
    ALREADY_PENDING,
    NOT_ACKNOWLEDGED,
    ACKNOWLEDGEMENT_TIMEOUT,
    PENDING_QUEUE_OVERFLOW,
    UNCERTAIN_REQUEST_OUTCOME,
    UNRECOGNIZED_ACKNOWLEDGEMENT,
    RESUMED_SEGMENT_UNANCHORABLE,
    SUBSCRIPTION_ENDED,
    SERVER_REJECTED,
    ACCESS_DENIED,
    CONNECTION_LIMIT,
    TIMEOUT,
    TRANSPORT_UNAVAILABLE,
    NOT_SUPPORTED,
}
data class AppTimeshiftState(
    val available: Boolean = false,
    val paused: Boolean = false,
    val bufferStartMs: Long = 0L,
    val positionMs: Long = 0L,
    val liveEdgeMs: Long = 0L,
)
sealed interface AppTimeshiftSeekResult {
    data class Applied(val targetMs: Long, val deltaMs: Long, val clamped: Boolean) : AppTimeshiftSeekResult
    data class Unavailable(val reason: AppPlaybackCommandResult) : AppTimeshiftSeekResult
}
enum class AppLivePlaybackIssue {
    INVALID_TARGET, NO_FREE_ADAPTER, MUX_NOT_ENABLED, TUNING_FAILED, BAD_SIGNAL,
    SCRAMBLED, OVERRIDDEN, ACCESS_DENIED, CONNECTION_LIMIT, WEAK_STREAM,
    NO_DISK_SPACE, UNKNOWN, NO_INPUT,
}
enum class AppRecordingProgressState { INACTIVE, AVAILABLE, SAVING, DEGRADED, READ_ONLY, UNSUPPORTED }
enum class AppPlaybackSource { NONE, LIVE_TV, RECORDING }
enum class AppPlaybackThermalLevel { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN }
data class AppPlaybackFormatDiagnostics(
    val codec: String?,
    val resolution: String? = null,
    val frameRate: Float? = null,
    val language: String? = null,
    val channelCount: Int? = null,
    val sampleRateHz: Int? = null,
)
data class AppPlaybackOutputMode(val width: Int, val height: Int, val refreshRateHz: Float)
data class AppPlaybackSystemDiagnostics(
    val outputMode: AppPlaybackOutputMode? = null,
    val thermalLevel: AppPlaybackThermalLevel? = null,
    val appPssBytes: Long? = null,
    val lowMemory: Boolean? = null,
)
data class AppPlaybackDiagnostics(
    val source: AppPlaybackSource = AppPlaybackSource.NONE,
    val state: AppPlaybackState = AppPlaybackState.Idle,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val bufferedMs: Long = 0L,
    val video: AppPlaybackFormatDiagnostics? = null,
    val videoDecoder: String? = null,
    val renderedFrames: Int = 0,
    val droppedFrames: Int = 0,
    val audio: AppPlaybackFormatDiagnostics? = null,
    val audioDecoder: String? = null,
    val audioUnderruns: Int = 0,
    val readRateBitsPerSecond: Long? = null,
    val system: AppPlaybackSystemDiagnostics? = null,
)

/** App presentation adapter over the released coordinator and app-owned player. */
class AppPlaybackRuntime(
    val player: ExoPlayer,
    private val coordinator: TvheadendPlaybackCoordinator,
    private val settings: PlayerSettingsStore,
    private val streamProfileDiscovery: StreamProfileDiscovery,
    private val recordingProgressCapability: StateFlow<RecordingProgressCapability>,
    private val scope: CoroutineScope,
) {
    private val commandGate = AppPlaybackCommandGate()
    private val _submittedTarget = MutableStateFlow<AppPlaybackTarget?>(null)
    private val _state = MutableStateFlow<AppPlaybackState>(AppPlaybackState.Idle)
    private val _activeLive = MutableStateFlow<Int?>(null)
    private val _playingLive = MutableStateFlow<Int?>(null)
    private val _activeRecording = MutableStateFlow<Int?>(null)
    private val _diagnostics = MutableStateFlow(AppPlaybackDiagnostics())
    private var diagnosticsEnabled = false
    private var lastRecordingRequest: Pair<DvrEntry, RecordingPlaybackIntent>? = null
    private var recoveryJob: Job? = null

    val submittedTarget = _submittedTarget.asStateFlow()
    val state = _state.asStateFlow()
    val activeLiveServiceId = _activeLive.asStateFlow()
    val playingLiveServiceId = _playingLive.asStateFlow()
    val activeRecordingId = _activeRecording.asStateFlow()
    val timeshiftState: StateFlow<AppTimeshiftState> = coordinator.timeshiftState
        .map { it.toApp() }
        .stateIn(scope, SharingStarted.Eagerly, coordinator.timeshiftState.value.toApp())
    val livePlaybackIssue: StateFlow<AppLivePlaybackIssue?> = coordinator.subscriptionIssue
        .map { it?.toApp() }
        .stateIn(scope, SharingStarted.Eagerly, coordinator.subscriptionIssue.value?.toApp())
    val recordingProgressState: StateFlow<AppRecordingProgressState> = combine(
        _activeRecording,
        recordingProgressCapability,
    ) { recordingId, capability ->
        if (recordingId == null) AppRecordingProgressState.INACTIVE else capability.toPlaybackState()
    }.stateIn(scope, SharingStarted.Eagerly, AppRecordingProgressState.INACTIVE)
    val diagnostics = _diagnostics.asStateFlow()

    private val settingsJob = scope.launch {
        settings.playerSettings.distinctUntilChanged().collect(::applyPlayerSettings)
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = publishPlayerState()
        override fun onIsPlayingChanged(isPlaying: Boolean) = publishPlayerState()
        override fun onPlayerError(error: PlaybackException) {
            _state.value = AppPlaybackState.Failed(
                if (_activeRecording.value != null) AppPlaybackFailureReason.RECORDING_READ_FAILED
                else AppPlaybackFailureReason.OTHER,
            )
            publishDiagnostics()
        }
    }

    init {
        player.addListener(listener)
    }

    suspend fun playLive(serviceId: Int): AppPlaybackCommandResult = commandGate.run {
        playLiveLocked(serviceId, recovering = false)
    }

    private suspend fun playLiveLocked(
        serviceId: Int,
        recovering: Boolean,
    ): AppPlaybackCommandResult {
        val target = AppPlaybackTarget.Live(serviceId)
        _submittedTarget.value = target
        _activeLive.value = null
        _playingLive.value = null
        _activeRecording.value = null
        if (!recovering) _state.value = AppPlaybackState.Starting
        val playerSettings = settings.playerSettings.first()
        val profile = prepareSelectedStreamProfile(
            playerSettings.profile,
            streamProfileDiscovery::discover,
        )
        val result = coordinator.setLiveTarget(
            ChannelId(serviceId.toLong()),
            LivePlaybackOptions(
                streamProfileId = profile,
                timeshiftPeriod = if (playerSettings.timeshiftEnabled) 2.hours else kotlin.time.Duration.ZERO,
            ),
        )
        val appResult = result.toAppCommandResult()
        return if (appResult == AppPlaybackCommandResult.SUBMITTED) {
            _activeLive.value = serviceId
            _playingLive.value = serviceId
            publishDiagnostics()
            appResult
        } else {
            _submittedTarget.value = null
            _state.value = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER)
            appResult
        }
    }

    suspend fun playRecording(
        entry: DvrEntry,
        intent: RecordingPlaybackIntent,
    ): AppPlaybackCommandResult = commandGate.run {
        playRecordingLocked(entry, intent)
    }

    private suspend fun playRecordingLocked(
        entry: DvrEntry,
        intent: RecordingPlaybackIntent,
    ): AppPlaybackCommandResult {
        val target = AppPlaybackTarget.Recording(entry.id)
        lastRecordingRequest = entry to intent
        _submittedTarget.value = target
        _state.value = AppPlaybackState.Starting
        _activeLive.value = null
        _playingLive.value = null
        _activeRecording.value = null
        val start = if (intent == RecordingPlaybackIntent.FromBeginning) {
            RecordingPlaybackStart.START_OVER
        } else {
            RecordingPlaybackStart.RESUME
        }
        val result = coordinator.setRecordingTarget(DvrEntryId(entry.id.toLong()), start)
        val appResult = result.toAppCommandResult()
        return if (appResult == AppPlaybackCommandResult.SUBMITTED) {
            _activeRecording.value = entry.id
            publishDiagnostics()
            appResult
        } else {
            _submittedTarget.value = null
            _state.value = AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED)
            appResult
        }
    }

    suspend fun stop(): AppPlaybackCommandResult = commandGate.run {
        recoveryJob?.cancel()
        recoveryJob = null
        val result = coordinator.stop().toAppCommandResult()
        _submittedTarget.value = null
        _activeLive.value = null
        _playingLive.value = null
        _activeRecording.value = null
        _state.value = AppPlaybackState.Idle
        publishDiagnostics()
        result
    }

    suspend fun retryLive(): AppPlaybackCommandResult = commandGate.run {
        _activeLive.value?.let { playLiveLocked(it, recovering = true) }
            ?: AppPlaybackCommandResult.UNAVAILABLE
    }
    suspend fun retryRecording(): AppPlaybackCommandResult = commandGate.run {
        lastRecordingRequest?.let { (entry, intent) -> playRecordingLocked(entry, intent) }
            ?: AppPlaybackCommandResult.UNAVAILABLE
    }
    suspend fun pauseTimeshift() = commandGate.run { coordinator.pauseTimeshift().toAppCommandResult() }
    suspend fun resumeTimeshift() = commandGate.run { coordinator.resumeTimeshift().toAppCommandResult() }
    suspend fun seekTimeshift(deltaMs: Long): AppTimeshiftSeekResult = commandGate.run {
        val before = timeshiftState.value
        val result = coordinator.seekTimeshift(deltaMs.milliseconds).toAppCommandResult()
        if (result != AppPlaybackCommandResult.SUBMITTED) {
            return@run AppTimeshiftSeekResult.Unavailable(result)
        }
        val target = (before.positionMs + deltaMs).coerceIn(before.bufferStartMs, before.liveEdgeMs)
        AppTimeshiftSeekResult.Applied(target, target - before.positionMs, target != before.positionMs + deltaMs)
    }
    suspend fun goLive(): AppTimeshiftSeekResult = commandGate.run {
        val before = timeshiftState.value
        val result = coordinator.returnToLive().toAppCommandResult()
        if (result != AppPlaybackCommandResult.SUBMITTED) {
            return@run AppTimeshiftSeekResult.Unavailable(result)
        }
        AppTimeshiftSeekResult.Applied(0L, -before.positionMs, false)
    }
    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun recordingPaused() = Unit
    fun recordingSeekSettled() = Unit
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
        val serviceId = _activeLive.value ?: return
        _state.value = AppPlaybackState.Recovering(retryDelayMillis = 0L)
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            val result = retryLive()
            if (result != AppPlaybackCommandResult.SUBMITTED && _activeLive.value == serviceId) {
                _state.value = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER)
            }
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
        _activeLive.value != null -> AppPlaybackSource.LIVE_TV
        _activeRecording.value != null -> AppPlaybackSource.RECORDING
        else -> AppPlaybackSource.NONE
    }
}

internal class AppPlaybackCommandGate {
    private val mutex = Mutex()

    suspend fun <T> run(command: suspend () -> T): T = mutex.withLock { command() }
}

internal suspend fun prepareSelectedStreamProfile(
    value: String,
    discover: suspend () -> StreamProfilesResult,
): StreamProfileId? {
    val profile = value.takeIf { it.isNotBlank() }?.let { selected ->
        runCatching { StreamProfileId(selected) }.getOrNull()
    }
    if (profile != null) discover()
    return profile
}

fun droppedFramePercentage(renderedFrames: Int, droppedFrames: Int): Float? {
    val total = renderedFrames.toLong() + droppedFrames.toLong()
    return if (total <= 0L) null else droppedFrames * 100f / total
}

private fun LiveTimeshiftState.toApp(): AppTimeshiftState = when (this) {
    LiveTimeshiftState.Unavailable -> AppTimeshiftState()
    is LiveTimeshiftState.Available -> {
        val behind = positionBehindLive?.inWholeMilliseconds?.coerceAtLeast(0L) ?: 0L
        val buffered = bufferedDuration?.inWholeMilliseconds?.coerceAtLeast(behind)
            ?: grantedPeriod.inWholeMilliseconds
        AppTimeshiftState(true, serverPaused == true, -buffered, -behind, 0L)
    }
}

internal fun SubscriptionIssue.toApp(): AppLivePlaybackIssue = when (this) {
    SubscriptionIssue.INVALID_TARGET -> AppLivePlaybackIssue.INVALID_TARGET
    SubscriptionIssue.NO_FREE_ADAPTER -> AppLivePlaybackIssue.NO_FREE_ADAPTER
    SubscriptionIssue.MUX_NOT_ENABLED -> AppLivePlaybackIssue.MUX_NOT_ENABLED
    SubscriptionIssue.TUNING_FAILED -> AppLivePlaybackIssue.TUNING_FAILED
    SubscriptionIssue.BAD_SIGNAL -> AppLivePlaybackIssue.BAD_SIGNAL
    SubscriptionIssue.SCRAMBLED -> AppLivePlaybackIssue.SCRAMBLED
    SubscriptionIssue.SUBSCRIPTION_OVERRIDDEN -> AppLivePlaybackIssue.OVERRIDDEN
    SubscriptionIssue.USER_ACCESS -> AppLivePlaybackIssue.ACCESS_DENIED
    SubscriptionIssue.USER_LIMIT -> AppLivePlaybackIssue.CONNECTION_LIMIT
    SubscriptionIssue.WEAK_STREAM -> AppLivePlaybackIssue.WEAK_STREAM
    SubscriptionIssue.NO_DISK_SPACE -> AppLivePlaybackIssue.NO_DISK_SPACE
    SubscriptionIssue.UNKNOWN -> AppLivePlaybackIssue.UNKNOWN
}

internal fun PlaybackTargetResult.toAppCommandResult(): AppPlaybackCommandResult = when (this) {
    PlaybackTargetResult.STARTED -> AppPlaybackCommandResult.SUBMITTED
    PlaybackTargetResult.NOT_RUNNING -> AppPlaybackCommandResult.NOT_RUNNING
    PlaybackTargetResult.SHUT_DOWN -> AppPlaybackCommandResult.SHUT_DOWN
    PlaybackTargetResult.NOT_READY -> AppPlaybackCommandResult.NOT_READY
    PlaybackTargetResult.RECORDING_PROGRESS_UNSUPPORTED ->
        AppPlaybackCommandResult.RECORDING_PROGRESS_UNSUPPORTED
    PlaybackTargetResult.TARGET_UNAVAILABLE -> AppPlaybackCommandResult.TARGET_UNAVAILABLE
    PlaybackTargetResult.GROWING_RECORDING_RESUME_UNSUPPORTED ->
        AppPlaybackCommandResult.GROWING_RECORDING_RESUME_UNSUPPORTED
    PlaybackTargetResult.GROWING_RECORDING_DEFERRED ->
        AppPlaybackCommandResult.GROWING_RECORDING_DEFERRED
    PlaybackTargetResult.PLAYER_UNAVAILABLE -> AppPlaybackCommandResult.PLAYER_UNAVAILABLE
}

internal fun PlaybackStopResult.toAppCommandResult(): AppPlaybackCommandResult = when (this) {
    PlaybackStopResult.STOPPED -> AppPlaybackCommandResult.STOPPED
    PlaybackStopResult.ALREADY_STOPPED -> AppPlaybackCommandResult.ALREADY_STOPPED
    PlaybackStopResult.NOT_RUNNING -> AppPlaybackCommandResult.NOT_RUNNING
    PlaybackStopResult.SHUT_DOWN -> AppPlaybackCommandResult.SHUT_DOWN
    PlaybackStopResult.PLAYER_UNAVAILABLE -> AppPlaybackCommandResult.PLAYER_UNAVAILABLE
}

internal fun TimeshiftCommandResult.toAppCommandResult(): AppPlaybackCommandResult = when (this) {
    TimeshiftCommandResult.ACCEPTED -> AppPlaybackCommandResult.SUBMITTED
    TimeshiftCommandResult.REJECTED -> AppPlaybackCommandResult.REJECTED
    TimeshiftCommandResult.UNAVAILABLE -> AppPlaybackCommandResult.UNAVAILABLE
    TimeshiftCommandResult.ALREADY_PENDING -> AppPlaybackCommandResult.ALREADY_PENDING
    TimeshiftCommandResult.NOT_ACKNOWLEDGED -> AppPlaybackCommandResult.NOT_ACKNOWLEDGED
    TimeshiftCommandResult.ACKNOWLEDGEMENT_TIMEOUT -> AppPlaybackCommandResult.ACKNOWLEDGEMENT_TIMEOUT
    TimeshiftCommandResult.PENDING_QUEUE_OVERFLOW -> AppPlaybackCommandResult.PENDING_QUEUE_OVERFLOW
    TimeshiftCommandResult.UNCERTAIN_REQUEST_OUTCOME -> AppPlaybackCommandResult.UNCERTAIN_REQUEST_OUTCOME
    TimeshiftCommandResult.UNRECOGNIZED_ACKNOWLEDGEMENT ->
        AppPlaybackCommandResult.UNRECOGNIZED_ACKNOWLEDGEMENT
    TimeshiftCommandResult.RESUMED_SEGMENT_UNANCHORABLE ->
        AppPlaybackCommandResult.RESUMED_SEGMENT_UNANCHORABLE
    TimeshiftCommandResult.SUBSCRIPTION_ENDED -> AppPlaybackCommandResult.SUBSCRIPTION_ENDED
    TimeshiftCommandResult.SERVER_REJECTED -> AppPlaybackCommandResult.SERVER_REJECTED
    TimeshiftCommandResult.ACCESS_DENIED -> AppPlaybackCommandResult.ACCESS_DENIED
    TimeshiftCommandResult.CONNECTION_LIMIT -> AppPlaybackCommandResult.CONNECTION_LIMIT
    TimeshiftCommandResult.TIMEOUT -> AppPlaybackCommandResult.TIMEOUT
    TimeshiftCommandResult.TRANSPORT_UNAVAILABLE -> AppPlaybackCommandResult.TRANSPORT_UNAVAILABLE
    TimeshiftCommandResult.NOT_SUPPORTED -> AppPlaybackCommandResult.NOT_SUPPORTED
    TimeshiftCommandResult.NOT_RUNNING -> AppPlaybackCommandResult.NOT_RUNNING
    TimeshiftCommandResult.SHUT_DOWN -> AppPlaybackCommandResult.SHUT_DOWN
}

private fun RecordingProgressCapability.toPlaybackState(): AppRecordingProgressState = when (this) {
    RecordingProgressCapability.Disconnected -> AppRecordingProgressState.DEGRADED
    RecordingProgressCapability.Unsupported -> AppRecordingProgressState.UNSUPPORTED
    RecordingProgressCapability.ReadOnly -> AppRecordingProgressState.READ_ONLY
    RecordingProgressCapability.Full -> AppRecordingProgressState.AVAILABLE
}

private val Int.hours get() = this.seconds * 3_600
