@file:OptIn(
    at.bernhardberger.tvheadend.playback.ExperimentalPlaybackDiagnosticsApi::class,
    at.bernhardberger.tvheadend.playback.ExperimentalRecordingCoordinationApi::class,
    kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class,
)

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.Player
import at.bernhardberger.tvheadend.core.DvrEntry
import at.bernhardberger.tvheadend.core.RecordingPlaybackIntent
import at.bernhardberger.tvheadend.core.SubscriptionFailureKind
import at.bernhardberger.tvheadend.core.TimeshiftSeekDecision
import at.bernhardberger.tvheadend.core.TimeshiftState
import at.bernhardberger.tvheadend.playback.PlaybackDiagnosticsSnapshot
import at.bernhardberger.tvheadend.playback.PlaybackDiagnosticsSource
import at.bernhardberger.tvheadend.playback.PlaybackFailureReason
import at.bernhardberger.tvheadend.playback.PlaybackFormatDiagnostics
import at.bernhardberger.tvheadend.playback.PlaybackRuntime
import at.bernhardberger.tvheadend.playback.PlaybackSessionState
import at.bernhardberger.tvheadend.playback.PlaybackSystemDiagnostics
import at.bernhardberger.tvheadend.playback.PlaybackThermalLevel
import at.bernhardberger.tvheadend.playback.RecordingProgressSyncState
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed interface AppPlaybackState {
    data object Idle : AppPlaybackState
    data object Starting : AppPlaybackState
    data object Playing : AppPlaybackState
    data object Finished : AppPlaybackState
    data class Recovering(val retryDelayMillis: Long) : AppPlaybackState
    data class Failed(val reason: AppPlaybackFailureReason) : AppPlaybackState
}

enum class AppPlaybackFailureReason {
    RECORDING_READ_FAILED,
    OTHER,
}

sealed interface AppPlaybackTarget {
    data class Live(val serviceId: Int) : AppPlaybackTarget
    data class Recording(val recordingId: Int) : AppPlaybackTarget
}

enum class AppPlaybackCommandResult {
    SUBMITTED,
    UNAVAILABLE,
}

data class AppTimeshiftState(
    val available: Boolean = false,
    val paused: Boolean = false,
    val bufferStartMs: Long = 0L,
    val positionMs: Long = 0L,
    val liveEdgeMs: Long = 0L,
)

sealed interface AppTimeshiftSeekResult {
    data class Applied(
        val targetMs: Long,
        val deltaMs: Long,
        val clamped: Boolean,
    ) : AppTimeshiftSeekResult

    data object Unavailable : AppTimeshiftSeekResult
}

enum class AppLivePlaybackIssue {
    INVALID_TARGET,
    NO_FREE_ADAPTER,
    MUX_NOT_ENABLED,
    TUNING_FAILED,
    BAD_SIGNAL,
    SCRAMBLED,
    OVERRIDDEN,
    NO_INPUT,
}

enum class AppRecordingProgressState {
    INACTIVE,
    AVAILABLE,
    SAVING,
    DEGRADED,
    READ_ONLY,
    UNSUPPORTED,
}

enum class AppPlaybackSource {
    NONE,
    LIVE_TV,
    RECORDING,
}

enum class AppPlaybackThermalLevel {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
}

data class AppPlaybackFormatDiagnostics(
    val codec: String?,
    val resolution: String? = null,
    val frameRate: Float? = null,
    val language: String? = null,
    val channelCount: Int? = null,
    val sampleRateHz: Int? = null,
)

data class AppPlaybackOutputMode(
    val width: Int,
    val height: Int,
    val refreshRateHz: Float,
)

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

/**
 * Application-owned playback surface. The predecessor runtime is a replaceable
 * target/source adapter until the released SDK cutover.
 */
class AppPlaybackRuntime internal constructor(
    private val backend: PlaybackRuntime,
) {
    private val lifecycleLock = Any()
    private val _submittedTarget = MutableStateFlow<AppPlaybackTarget?>(null)
    private var commandGeneration = 0L
    private var released = false
    private var releaseCompletion: CompletableDeferred<Unit>? = null

    val player: Player
        get() = backend.player
    val submittedTarget: StateFlow<AppPlaybackTarget?> = _submittedTarget.asStateFlow()
    val state: StateFlow<AppPlaybackState> = backend.state.mapState(::appPlaybackState)
    val activeLiveServiceId: StateFlow<Int?> = backend.activeChannelId
    val playingLiveServiceId: StateFlow<Int?> = backend.playingLiveChannelId
    val activeRecordingId: StateFlow<Int?> = backend.activeRecordingId
    val timeshiftState: StateFlow<AppTimeshiftState> =
        backend.timeshiftState.mapState(TimeshiftState::toAppState)
    val livePlaybackIssue: StateFlow<AppLivePlaybackIssue?> =
        backend.liveSubscriptionFailure.mapState { it?.toAppIssue() }
    val recordingProgressState: StateFlow<AppRecordingProgressState> =
        backend.recordingProgressSyncState.mapState(RecordingProgressSyncState::toAppState)
    val diagnostics: StateFlow<AppPlaybackDiagnostics> =
        backend.diagnostics.mapState(PlaybackDiagnosticsSnapshot::toAppDiagnostics)

    suspend fun playLive(serviceId: Int): AppPlaybackCommandResult {
        val generation = beginTargetCommand(AppPlaybackTarget.Live(serviceId))
            ?: return AppPlaybackCommandResult.UNAVAILABLE
        val accepted = backend.playLive(serviceId)
        return commandResult(generation, accepted)
    }

    suspend fun playRecording(
        entry: DvrEntry,
        intent: RecordingPlaybackIntent,
    ): AppPlaybackCommandResult {
        val generation = beginTargetCommand(AppPlaybackTarget.Recording(entry.id))
            ?: return AppPlaybackCommandResult.UNAVAILABLE
        backend.playRecording(entry, intent)
        return commandResult(generation, accepted = true)
    }

    suspend fun stop() {
        val generation = beginBarrierCommand() ?: return
        try {
            backend.stop()
        } finally {
            synchronized(lifecycleLock) {
                if (!released && commandGeneration == generation) {
                    _submittedTarget.value = null
                }
            }
        }
    }

    suspend fun retryLive(): AppPlaybackCommandResult {
        val generation = currentCommandGeneration()
            ?: return AppPlaybackCommandResult.UNAVAILABLE
        return commandResult(generation, backend.retryLive())
    }

    suspend fun retryRecording(): AppPlaybackCommandResult {
        val generation = currentCommandGeneration()
            ?: return AppPlaybackCommandResult.UNAVAILABLE
        return commandResult(generation, backend.retryRecording())
    }

    suspend fun pauseTimeshift(): AppPlaybackCommandResult {
        val generation = currentCommandGeneration()
            ?: return AppPlaybackCommandResult.UNAVAILABLE
        return commandResult(generation, backend.pauseTimeshift())
    }

    suspend fun resumeTimeshift(): AppPlaybackCommandResult {
        val generation = currentCommandGeneration()
            ?: return AppPlaybackCommandResult.UNAVAILABLE
        return commandResult(generation, backend.resumeTimeshift())
    }

    suspend fun seekTimeshift(deltaMs: Long): AppTimeshiftSeekResult {
        val generation = currentCommandGeneration()
            ?: return AppTimeshiftSeekResult.Unavailable
        val result = backend.seekTimeshift(deltaMs).toAppResult()
        return result.takeIf { commandIsCurrent(generation) }
            ?: AppTimeshiftSeekResult.Unavailable
    }

    suspend fun goLive(): AppTimeshiftSeekResult {
        val generation = currentCommandGeneration()
            ?: return AppTimeshiftSeekResult.Unavailable
        val result = backend.goLive().toAppResult()
        return result.takeIf { commandIsCurrent(generation) }
            ?: AppTimeshiftSeekResult.Unavailable
    }

    fun play() = withActivePlayer(Player::play)

    fun pause() = withActivePlayer(Player::pause)

    fun seekTo(positionMs: Long) = withActivePlayer { it.seekTo(positionMs) }

    fun recordingPaused() = withActiveRuntime(backend::recordingPaused)

    fun recordingSeekSettled() = withActiveRuntime(backend::recordingSeekSettled)

    fun setDiagnosticsEnabled(enabled: Boolean) = withActiveRuntime {
        backend.setDiagnosticsEnabled(enabled)
    }

    suspend fun release() {
        val (completion, ownsRelease) = synchronized(lifecycleLock) {
            releaseCompletion?.let { it to false } ?: CompletableDeferred<Unit>().let {
                releaseCompletion = it
                released = true
                commandGeneration++
                _submittedTarget.value = null
                it to true
            }
        }
        if (!ownsRelease) {
            completion.await()
            return
        }
        withContext(NonCancellable) {
            try {
                backend.release()
                completion.complete(Unit)
            } catch (error: Throwable) {
                completion.completeExceptionally(error)
                throw error
            }
        }
    }

    private fun beginTargetCommand(target: AppPlaybackTarget): Long? =
        synchronized(lifecycleLock) {
            if (released) return null
            (++commandGeneration).also { _submittedTarget.value = target }
        }

    private fun beginBarrierCommand(): Long? = synchronized(lifecycleLock) {
        if (released) return null
        ++commandGeneration
    }

    private fun currentCommandGeneration(): Long? = synchronized(lifecycleLock) {
        commandGeneration.takeUnless { released }
    }

    private fun commandIsCurrent(generation: Long): Boolean = synchronized(lifecycleLock) {
        !released && commandGeneration == generation
    }

    private fun commandResult(
        generation: Long,
        accepted: Boolean,
    ): AppPlaybackCommandResult = if (accepted && commandIsCurrent(generation)) {
        AppPlaybackCommandResult.SUBMITTED
    } else {
        AppPlaybackCommandResult.UNAVAILABLE
    }

    private inline fun withActivePlayer(command: (Player) -> Unit) =
        synchronized(lifecycleLock) {
            if (!released) command(player)
        }

    private inline fun withActiveRuntime(command: () -> Unit) = synchronized(lifecycleLock) {
        if (!released) command()
    }
}

fun droppedFramePercentage(renderedFrames: Int, droppedFrames: Int): Float? {
    val total = renderedFrames.toLong() + droppedFrames.toLong()
    return if (total <= 0) null else droppedFrames * 100f / total
}

private fun TimeshiftSeekDecision?.toAppResult(): AppTimeshiftSeekResult = this?.let {
    AppTimeshiftSeekResult.Applied(
        targetMs = it.targetMs,
        deltaMs = it.deltaMs,
        clamped = it.clamped,
    )
} ?: AppTimeshiftSeekResult.Unavailable

private fun appPlaybackState(state: PlaybackSessionState): AppPlaybackState = when (state) {
    PlaybackSessionState.Idle -> AppPlaybackState.Idle
    PlaybackSessionState.Starting -> AppPlaybackState.Starting
    PlaybackSessionState.Playing -> AppPlaybackState.Playing
    PlaybackSessionState.Finished -> AppPlaybackState.Finished
    is PlaybackSessionState.Recovering -> AppPlaybackState.Recovering(state.retryDelayMillis)
    is PlaybackSessionState.Failed -> AppPlaybackState.Failed(
        reason = if (state.reason == PlaybackFailureReason.RECORDING_READ_FAILED) {
            AppPlaybackFailureReason.RECORDING_READ_FAILED
        } else {
            AppPlaybackFailureReason.OTHER
        },
    )
}

private fun TimeshiftState.toAppState() = AppTimeshiftState(
    available = available,
    paused = paused,
    bufferStartMs = bufferStartMs,
    positionMs = positionMs,
    liveEdgeMs = liveEdgeMs,
)

private fun SubscriptionFailureKind.toAppIssue(): AppLivePlaybackIssue = when (this) {
    SubscriptionFailureKind.INVALID_TARGET -> AppLivePlaybackIssue.INVALID_TARGET
    SubscriptionFailureKind.NO_FREE_ADAPTER -> AppLivePlaybackIssue.NO_FREE_ADAPTER
    SubscriptionFailureKind.MUX_NOT_ENABLED -> AppLivePlaybackIssue.MUX_NOT_ENABLED
    SubscriptionFailureKind.TUNING_FAILED -> AppLivePlaybackIssue.TUNING_FAILED
    SubscriptionFailureKind.BAD_SIGNAL -> AppLivePlaybackIssue.BAD_SIGNAL
    SubscriptionFailureKind.SCRAMBLED -> AppLivePlaybackIssue.SCRAMBLED
    SubscriptionFailureKind.OVERRIDDEN -> AppLivePlaybackIssue.OVERRIDDEN
    SubscriptionFailureKind.NO_INPUT -> AppLivePlaybackIssue.NO_INPUT
}

private fun RecordingProgressSyncState.toAppState(): AppRecordingProgressState = when (this) {
    RecordingProgressSyncState.Inactive -> AppRecordingProgressState.INACTIVE
    RecordingProgressSyncState.Available -> AppRecordingProgressState.AVAILABLE
    RecordingProgressSyncState.Saving -> AppRecordingProgressState.SAVING
    RecordingProgressSyncState.Degraded -> AppRecordingProgressState.DEGRADED
    RecordingProgressSyncState.ReadOnly -> AppRecordingProgressState.READ_ONLY
    RecordingProgressSyncState.Unsupported -> AppRecordingProgressState.UNSUPPORTED
}

private fun PlaybackDiagnosticsSnapshot.toAppDiagnostics() = AppPlaybackDiagnostics(
    source = when (source) {
        PlaybackDiagnosticsSource.NONE -> AppPlaybackSource.NONE
        PlaybackDiagnosticsSource.LIVE_TV -> AppPlaybackSource.LIVE_TV
        PlaybackDiagnosticsSource.RECORDING -> AppPlaybackSource.RECORDING
    },
    state = appPlaybackState(state),
    isPlaying = isPlaying,
    positionMs = positionMs,
    durationMs = durationMs,
    bufferedMs = bufferedMs,
    video = video?.toAppDiagnostics(),
    videoDecoder = videoDecoder,
    renderedFrames = renderedFrames,
    droppedFrames = droppedFrames,
    audio = audio?.toAppDiagnostics(),
    audioDecoder = audioDecoder,
    audioUnderruns = audioUnderruns,
    readRateBitsPerSecond = readRateBitsPerSecond,
    system = system?.toAppDiagnostics(),
)

private fun PlaybackFormatDiagnostics.toAppDiagnostics() = AppPlaybackFormatDiagnostics(
    codec = codec,
    resolution = resolution,
    frameRate = frameRate,
    language = language,
    channelCount = channelCount,
    sampleRateHz = sampleRateHz,
)

private fun PlaybackSystemDiagnostics.toAppDiagnostics() = AppPlaybackSystemDiagnostics(
    outputMode = outputMode?.let {
        AppPlaybackOutputMode(
            width = it.width,
            height = it.height,
            refreshRateHz = it.refreshRateHz,
        )
    },
    thermalLevel = thermalLevel?.toAppLevel(),
    appPssBytes = appPssBytes,
    lowMemory = lowMemory,
)

private fun PlaybackThermalLevel.toAppLevel(): AppPlaybackThermalLevel = when (this) {
    PlaybackThermalLevel.NONE -> AppPlaybackThermalLevel.NONE
    PlaybackThermalLevel.LIGHT -> AppPlaybackThermalLevel.LIGHT
    PlaybackThermalLevel.MODERATE -> AppPlaybackThermalLevel.MODERATE
    PlaybackThermalLevel.SEVERE -> AppPlaybackThermalLevel.SEVERE
    PlaybackThermalLevel.CRITICAL -> AppPlaybackThermalLevel.CRITICAL
    PlaybackThermalLevel.EMERGENCY -> AppPlaybackThermalLevel.EMERGENCY
    PlaybackThermalLevel.SHUTDOWN -> AppPlaybackThermalLevel.SHUTDOWN
}

private fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> =
    object : StateFlow<R> {
        override val replayCache: List<R>
            get() = listOf(value)

        override val value: R
            get() = transform(this@mapState.value)

        override suspend fun collect(collector: FlowCollector<R>): Nothing =
            this@mapState.collect { collector.emit(transform(it)) }
    }
