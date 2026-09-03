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
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.job
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

internal fun currentLivePlaybackSelection(
    observation: SessionObservation,
    channelId: ChannelId,
): LivePlaybackSelection? {
    val currentSession = observation.currentSession ?: return null
    if (observation.channel(channelId) == null) return null
    return LivePlaybackSelection(currentSession, channelId)
}

internal fun resolveLivePlaybackSelection(
    observation: SessionObservation,
    channelId: ChannelId,
    requestedSelection: LivePlaybackSelection?,
): LivePlaybackSelection? {
    val current = currentLivePlaybackSelection(observation, channelId) ?: return null
    return requestedSelection?.takeIf { requested ->
        requested.channelId == channelId &&
            requested.currentSession === current.currentSession
    } ?: current
}

internal fun currentRecordingPlaybackSelection(
    observation: SessionObservation,
    recordingId: DvrEntryId,
): RecordingPlaybackSelection? {
    val currentSession = observation.currentSession ?: return null
    if (observation.dvrEntry(recordingId) == null) return null
    return RecordingPlaybackSelection(currentSession, recordingId)
}

internal fun recordingRouteNeedsRestoration(
    routeSelection: RecordingPlaybackSelection,
    activeTarget: AppPlaybackTarget?,
    selectedRecording: RecordingPlaybackSelection?,
): Boolean =
    activeTarget != AppPlaybackTarget.Recording(routeSelection.recordingId) ||
        selectedRecording?.let { selected ->
            selected.recordingId == routeSelection.recordingId &&
                selected.currentSession === routeSelection.currentSession
        } != true

data class AppTimeshiftState(
    val available: Boolean = false,
    val paused: Boolean = false,
    val bufferStartMs: Long = 0L,
    val positionMs: Long = 0L,
    val liveEdgeMs: Long = 0L,
)

private val FIXED_LIVE_TIMESHIFT_PERIOD = 2.hours

internal fun requestedLiveTimeshiftPeriod(timeshiftEnabled: Boolean): Duration =
    if (timeshiftEnabled) FIXED_LIVE_TIMESHIFT_PERIOD else Duration.ZERO

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

internal data class AppVideoPresentation(
    val epoch: Long = 0L,
    val visible: Boolean = false,
)

internal fun AppVideoPresentation.beginTarget(epoch: Long) =
    AppVideoPresentation(epoch = epoch)

internal fun AppVideoPresentation.onFirstFrame(
    frameEpoch: Long,
    activeTargetEpoch: Long?,
): AppVideoPresentation = if (
    epoch == frameEpoch && activeTargetEpoch == frameEpoch
) {
    copy(visible = true)
} else {
    this
}

internal sealed interface ForegroundPlaybackAction {
    data object None : ForegroundPlaybackAction
    data object StopLive : ForegroundPlaybackAction
    data object PauseRecording : ForegroundPlaybackAction
    data class ResumeLive(val channelId: ChannelId) : ForegroundPlaybackAction
    data object ResumeRecording : ForegroundPlaybackAction
}

internal class PlaybackTargetCommandSerialization {
    private val mutex = Mutex()

    suspend fun <T> serialize(command: suspend () -> T): T = mutex.withLock {
        command()
    }

    suspend fun <Request, Result> retryRecording(
        currentRequest: () -> Request?,
        retry: suspend (Request) -> Result,
    ): Result? = mutex.withLock {
        currentRequest()?.let { retry(it) }
    }

    suspend fun <Result> restoreRecordingIfNeeded(
        targetMatches: () -> Boolean,
        restore: suspend () -> Result,
    ): Result? = mutex.withLock {
        if (targetMatches()) null else restore()
    }
}

internal suspend fun requestBoundLiveTarget(
    requestPlayIntent: () -> Unit,
    installTarget: suspend () -> PlaybackTargetResult,
): PlaybackTargetResult {
    requestPlayIntent()
    return installTarget()
}

internal suspend fun completePlaybackTargetInstallation(
    installTarget: suspend () -> PlaybackTargetResult,
    presentationStillCurrent: () -> Boolean,
    activeTarget: () -> AppPlaybackTarget?,
    onStarted: () -> Unit,
    onFailed: (PlaybackTargetResult) -> Unit,
): PlaybackTargetResult {
    val result = installTarget()
    if (!presentationStillCurrent()) return result
    if (result == PlaybackTargetResult.STARTED) {
        onStarted()
    } else if (activeTarget() == null) {
        onFailed(result)
    }
    return result
}

internal fun activePlayerTargetIsHealthy(
    playerErrorPresent: Boolean,
    playbackState: Int,
): Boolean =
    !playerErrorPresent &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED

internal suspend fun executeForegroundPlaybackAction(
    action: ForegroundPlaybackAction,
    stopLive: suspend () -> Unit,
    pauseRecording: () -> Unit,
    resumeLive: suspend (ChannelId) -> Unit,
    resumeRecording: () -> Unit,
) {
    when (action) {
        ForegroundPlaybackAction.None -> Unit
        ForegroundPlaybackAction.StopLive -> stopLive()
        ForegroundPlaybackAction.PauseRecording -> pauseRecording()
        is ForegroundPlaybackAction.ResumeLive -> resumeLive(action.channelId)
        ForegroundPlaybackAction.ResumeRecording -> resumeRecording()
    }
}

private sealed interface BackgroundedPlaybackTarget {
    data class Live(val channelId: ChannelId) : BackgroundedPlaybackTarget
    data class Recording(
        val recordingId: DvrEntryId,
        val targetEpoch: Long,
        val resumeOnForeground: Boolean,
    ) : BackgroundedPlaybackTarget
}

internal data class LiveRecoveryFence(
    val target: AppPlaybackTarget?,
    val targetEpoch: Long?,
) {
    fun matches(
        activeTarget: AppPlaybackTarget?,
        activeTargetEpoch: Long?,
        selectionChannelId: ChannelId?,
    ): Boolean {
        val liveTarget = target as? AppPlaybackTarget.Live ?: return false
        return targetEpoch != null &&
            activeTarget == liveTarget &&
            activeTargetEpoch == targetEpoch &&
            selectionChannelId == liveTarget.channelId
    }
}

internal class ForegroundPlaybackLifecycle {
    private var foreground = true
    private var backgroundedTarget: BackgroundedPlaybackTarget? = null

    fun onBackgrounded(
        activeTarget: AppPlaybackTarget?,
        activeTargetEpoch: Long?,
        recordingPlayWhenReady: Boolean,
    ): ForegroundPlaybackAction {
        if (!foreground) return ForegroundPlaybackAction.None
        foreground = false
        return rememberBackgroundedTarget(
            activeTarget = activeTarget,
            activeTargetEpoch = activeTargetEpoch,
            recordingPlayWhenReady = recordingPlayWhenReady,
        )
    }

    fun onForegrounded(
        activeTarget: AppPlaybackTarget?,
        activeTargetEpoch: Long?,
    ): ForegroundPlaybackAction {
        if (foreground) return ForegroundPlaybackAction.None
        foreground = true
        val target = backgroundedTarget
        backgroundedTarget = null
        return when (target) {
            is BackgroundedPlaybackTarget.Live ->
                ForegroundPlaybackAction.ResumeLive(target.channelId)
            is BackgroundedPlaybackTarget.Recording -> if (
                target.resumeOnForeground &&
                activeTarget == AppPlaybackTarget.Recording(target.recordingId) &&
                activeTargetEpoch == target.targetEpoch
            ) {
                ForegroundPlaybackAction.ResumeRecording
            } else {
                ForegroundPlaybackAction.None
            }
            null -> ForegroundPlaybackAction.None
        }
    }

    fun onExplicitStop() {
        backgroundedTarget = null
    }

    fun onTargetStarted(
        activeTarget: AppPlaybackTarget,
        activeTargetEpoch: Long,
        recordingPlayWhenReady: Boolean,
    ): ForegroundPlaybackAction = if (foreground) {
        ForegroundPlaybackAction.None
    } else {
        rememberBackgroundedTarget(
            activeTarget = activeTarget,
            activeTargetEpoch = activeTargetEpoch,
            recordingPlayWhenReady = recordingPlayWhenReady,
        )
    }

    private fun rememberBackgroundedTarget(
        activeTarget: AppPlaybackTarget?,
        activeTargetEpoch: Long?,
        recordingPlayWhenReady: Boolean,
    ): ForegroundPlaybackAction {
        backgroundedTarget = when {
            activeTarget is AppPlaybackTarget.Live && activeTargetEpoch != null ->
                BackgroundedPlaybackTarget.Live(activeTarget.channelId)
            activeTarget is AppPlaybackTarget.Recording && activeTargetEpoch != null ->
                BackgroundedPlaybackTarget.Recording(
                    recordingId = activeTarget.recordingId,
                    targetEpoch = activeTargetEpoch,
                    resumeOnForeground = recordingPlayWhenReady,
                )
            else -> null
        }
        return when (backgroundedTarget) {
            is BackgroundedPlaybackTarget.Live -> ForegroundPlaybackAction.StopLive
            is BackgroundedPlaybackTarget.Recording -> ForegroundPlaybackAction.PauseRecording
            null -> ForegroundPlaybackAction.None
        }
    }
}

/** App presentation adapter over the released coordinator and app-owned player. */
class AppPlaybackRuntime(
    val player: ExoPlayer,
    private val session: TvheadendSession,
    private val coordinator: TvheadendPlaybackCoordinator,
    private val settings: PlayerSettingsStore,
    private val profileOwner: AppProfileOwner,
    private val scope: CoroutineScope,
) {
    private val targetCommands = PlaybackTargetCommandSerialization()
    private val foregroundPlaybackLifecycle = ForegroundPlaybackLifecycle()
    private val presentationEpoch = PlaybackPresentationEpoch()
    private val _state = MutableStateFlow<AppPlaybackState>(AppPlaybackState.Idle)
    private val _activeTarget = MutableStateFlow<AppPlaybackTarget?>(null)
    private val _recordingSelection = MutableStateFlow<RecordingPlaybackSelection?>(null)
    private val _recordingAdmission = MutableStateFlow<RecordingPlaybackAdmission?>(null)
    private val _diagnostics = MutableStateFlow(AppPlaybackDiagnostics())
    private val _videoPresentation = MutableStateFlow(AppVideoPresentation())
    private var diagnosticsEnabled = false
    @Volatile
    private var activeTargetEpoch: Long? = null
    @Volatile
    private var targetInstallationInProgress = false
    private var lastLiveChannelId: ChannelId? = null
    private var lastRecordingRequest: Pair<DvrEntryId, RecordingPlaybackStart>? = null
    private var recoveryJob: Job? = null
    private var targetFrameListener: Player.Listener? = null

    val state = _state.asStateFlow()
    val activeTarget = _activeTarget.asStateFlow()
    val recordingSelection = _recordingSelection.asStateFlow()
    val recordingAdmission = _recordingAdmission.asStateFlow()
    val timeshiftState: StateFlow<LiveTimeshiftState> = coordinator.timeshiftState
    val livePlaybackIssue: StateFlow<SubscriptionIssue?> = coordinator.subscriptionIssue
    val diagnostics = _diagnostics.asStateFlow()
    internal val videoPresentation = _videoPresentation.asStateFlow()

    private val settingsJob = scope.launch {
        settings.playerSettings.distinctUntilChanged().collect(::applyPlayerSettings)
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!targetInstallationInProgress) publishPlayerState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!targetInstallationInProgress) publishPlayerState()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!targetInstallationInProgress) publishPlayerError()
        }
    }

    init {
        player.addListener(listener)
    }

    suspend fun playLive(selection: LivePlaybackSelection): PlaybackTargetResult? =
        targetCommands.serialize {
            lastLiveChannelId = selection.channelId
            val result = playLive(
                channelId = selection.channelId,
                recovering = false,
            )
            result
        }

    private suspend fun playLive(
        channelId: ChannelId,
        recovering: Boolean,
        expectedPresentationEpoch: Long = presentationEpoch.snapshot(),
    ): PlaybackTargetResult? {
        presentationEpoch.publishIfCurrent(expectedPresentationEpoch) {
            if (!recovering && _activeTarget.value == null) {
                _state.value = AppPlaybackState.Starting
            }
        }
        val playerSettings = settings.playerSettings.first()
        val profileSelection = currentLivePlaybackSelection(session.observation.value, channelId)
        if (profileSelection == null) {
            return completeUnavailableTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = unavailableLiveTargetResult(channelId),
                failureReason = AppPlaybackFailureReason.OTHER,
            )
        }
        val streamProfileId = profileOwner.selectedStreamProfileIdFor(profileSelection.currentSession)
        val selection = currentLivePlaybackSelection(session.observation.value, channelId)
        if (selection == null) {
            return completeUnavailableTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = unavailableLiveTargetResult(channelId),
                failureReason = AppPlaybackFailureReason.OTHER,
            )
        }
        if (selection.currentSession !== profileSelection.currentSession) {
            return completeUnavailableTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = PlaybackTargetResult.NOT_READY,
                failureReason = AppPlaybackFailureReason.OTHER,
            )
        }
        var committed = false
        val result = installTargetForPresentation(
            expectedPresentationEpoch = expectedPresentationEpoch,
            installTarget = {
                when (
                    val binding = session.bindLivePlayback(
                        selection.currentSession,
                        selection.channelId,
                    )
                ) {
                    is PlaybackBindingResult.Bound -> requestBoundLiveTarget(
                        requestPlayIntent = player::play,
                        installTarget = {
                            coordinator.setLiveTarget(
                                binding.binding,
                                LivePlaybackOptions(
                                    streamProfileId = streamProfileId,
                                    timeshiftPeriod = requestedLiveTimeshiftPeriod(
                                        playerSettings.timeshiftEnabled,
                                    ),
                                ),
                            )
                        },
                    )
                    PlaybackBindingResult.ObservationExpired -> PlaybackTargetResult.NOT_READY
                    PlaybackBindingResult.TargetUnavailable ->
                        PlaybackTargetResult.TARGET_UNAVAILABLE
                }
            },
            onStarted = started@{
                val epoch = presentationEpoch.beginIfCurrent(expectedPresentationEpoch)
                    ?: return@started
                presentationEpoch.publishIfCurrent(epoch) {
                    committed = true
                    activeTargetEpoch = epoch
                    _activeTarget.value = AppPlaybackTarget.Live(selection.channelId)
                    lastLiveChannelId = selection.channelId
                    _recordingSelection.value = null
                    _recordingAdmission.value = null
                    _state.value = AppPlaybackState.Starting
                    beginTargetPresentation(epoch)
                    publishInstalledPlayerState()
                }
            },
            onFailed = {
                publishTargetFailure(
                    expectedPresentationEpoch = expectedPresentationEpoch,
                    reason = AppPlaybackFailureReason.OTHER,
                )
            }
        )
        if (committed) applyBackgroundPolicyToStartedTarget(result)
        return result
    }

    suspend fun playRecording(
        selection: RecordingPlaybackSelection,
        start: RecordingPlaybackStart,
    ): PlaybackTargetResult? = targetCommands.serialize {
        playRecordingLocked(selection.recordingId, start)
    }

    suspend fun restoreRecordingRoute(
        selection: RecordingPlaybackSelection,
        start: RecordingPlaybackStart,
    ): PlaybackTargetResult? = targetCommands.restoreRecordingIfNeeded(
        targetMatches = {
            currentRecordingPlaybackSelection(
                observation = session.observation.value,
                recordingId = selection.recordingId,
            )?.let { currentSelection ->
                !recordingRouteNeedsRestoration(
                    routeSelection = currentSelection,
                    activeTarget = _activeTarget.value,
                    selectedRecording = _recordingSelection.value,
                )
            } == true
        },
        restore = {
            playRecordingLocked(selection.recordingId, start)
        },
    )

    private suspend fun playRecordingLocked(
        recordingId: DvrEntryId,
        start: RecordingPlaybackStart,
    ): PlaybackTargetResult? {
        val expectedPresentationEpoch = presentationEpoch.snapshot()
        lastRecordingRequest = recordingId to start
        presentationEpoch.publishIfCurrent(expectedPresentationEpoch) {
            if (_activeTarget.value == null) _state.value = AppPlaybackState.Starting
        }
        val selection = currentRecordingPlaybackSelection(session.observation.value, recordingId)
        if (selection == null) {
            val observation = session.observation.value
            return completeUnavailableRecordingTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = if (observation.currentSession == null) {
                    PlaybackTargetResult.NOT_READY
                } else {
                    PlaybackTargetResult.TARGET_UNAVAILABLE
                },
                admission = if (observation.currentSession == null) {
                    RecordingPlaybackAdmission.ObservationExpired
                } else {
                    RecordingPlaybackAdmission.TargetUnavailable
                },
            )
        }
        var admission: RecordingPlaybackAdmission? = null
        var committed = false
        val result = installTargetForPresentation(
            expectedPresentationEpoch = expectedPresentationEpoch,
            installTarget = {
                when (
                    val binding = session.bindRecordingPlayback(
                        selection.currentSession,
                        selection.recordingId,
                    )
                ) {
                    is PlaybackBindingResult.Bound -> {
                        admission = binding.binding.admission
                        coordinator.setRecordingTarget(binding.binding, start)
                    }
                    PlaybackBindingResult.ObservationExpired -> {
                        admission = RecordingPlaybackAdmission.ObservationExpired
                        PlaybackTargetResult.NOT_READY
                    }
                    PlaybackBindingResult.TargetUnavailable -> {
                        admission = RecordingPlaybackAdmission.TargetUnavailable
                        PlaybackTargetResult.TARGET_UNAVAILABLE
                    }
                }
            },
            onStarted = started@{
                val epoch = presentationEpoch.beginIfCurrent(expectedPresentationEpoch)
                    ?: return@started
                presentationEpoch.publishIfCurrent(epoch) {
                    committed = true
                    activeTargetEpoch = epoch
                    _activeTarget.value = AppPlaybackTarget.Recording(selection.recordingId)
                    _recordingSelection.value = selection
                    _recordingAdmission.value = admission
                    _state.value = AppPlaybackState.Starting
                    beginTargetPresentation(epoch)
                    publishInstalledPlayerState()
                }
            },
            onFailed = {
                publishTargetFailure(
                    expectedPresentationEpoch = expectedPresentationEpoch,
                    reason = AppPlaybackFailureReason.RECORDING_READ_FAILED,
                    recordingAdmission = admission,
                )
            }
        )
        if (committed) applyBackgroundPolicyToStartedTarget(result)
        return result
    }

    suspend fun stop(): PlaybackStopResult = targetCommands.serialize {
        foregroundPlaybackLifecycle.onExplicitStop()
        stopPlayback()
    }

    fun onAppBackgrounded() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            targetCommands.serialize {
                applyForegroundPlaybackAction(
                    foregroundPlaybackLifecycle.onBackgrounded(
                        activeTarget = _activeTarget.value,
                        activeTargetEpoch = activeTargetEpoch,
                        recordingPlayWhenReady = player.playWhenReady,
                    ),
                )
            }
        }
    }

    fun onAppForegrounded() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            targetCommands.serialize {
                applyForegroundPlaybackAction(
                    foregroundPlaybackLifecycle.onForegrounded(
                        activeTarget = _activeTarget.value,
                        activeTargetEpoch = activeTargetEpoch,
                    ),
                )
            }
        }
    }

    private suspend fun stopPlayback(): PlaybackStopResult {
        val epoch = presentationEpoch.begin()
        endTargetPresentation(epoch)
        val currentJob = currentCoroutineContext().job
        recoveryJob?.takeUnless { it === currentJob }?.cancel()
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

    suspend fun retryLive(): PlaybackTargetResult? = targetCommands.serialize {
        lastLiveChannelId?.let { channelId ->
            playLive(
                channelId = channelId,
                recovering = true,
            )
        }
    }
    suspend fun retryRecording(): PlaybackTargetResult? = targetCommands.retryRecording(
        currentRequest = { lastRecordingRequest },
    ) { (recordingId, start) ->
        playRecordingLocked(recordingId, start)
    }
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
        val channelId = (_activeTarget.value as? AppPlaybackTarget.Live)?.channelId
        val fence = LiveRecoveryFence(
            target = _activeTarget.value,
            targetEpoch = activeTargetEpoch,
        )
        recoveryJob?.cancel()
        recoveryJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            targetCommands.serialize {
                if (
                    !fence.matches(
                        activeTarget = _activeTarget.value,
                        activeTargetEpoch = activeTargetEpoch,
                        selectionChannelId =
                            (_activeTarget.value as? AppPlaybackTarget.Live)?.channelId,
                    ) || (_activeTarget.value as? AppPlaybackTarget.Live)?.channelId != channelId
                ) {
                    return@serialize
                }
                val targetEpoch = fence.targetEpoch ?: return@serialize
                val recoveryChannelId = channelId ?: return@serialize
                presentationEpoch.publishIfCurrent(targetEpoch) {
                    _state.value = AppPlaybackState.Recovering(retryDelayMillis = 0L)
                }
                playLive(
                    channelId = recoveryChannelId,
                    recovering = true,
                    expectedPresentationEpoch = targetEpoch,
                )
            }
        }
    }

    fun detach() {
        recoveryJob?.cancel()
        recoveryJob = null
        settingsJob.cancel()
        targetFrameListener?.let(player::removeListener)
        targetFrameListener = null
        player.removeListener(listener)
    }

    private suspend fun installTargetForPresentation(
        expectedPresentationEpoch: Long,
        installTarget: suspend () -> PlaybackTargetResult,
        onStarted: () -> Unit,
        onFailed: (PlaybackTargetResult) -> Unit,
    ): PlaybackTargetResult {
        targetInstallationInProgress = true
        return try {
            val result = completePlaybackTargetInstallation(
                installTarget = installTarget,
                presentationStillCurrent = {
                    presentationEpoch.isCurrent(expectedPresentationEpoch)
                },
                activeTarget = ::healthyActiveTarget,
                onStarted = onStarted,
                onFailed = onFailed,
            )
            if (
                result != PlaybackTargetResult.STARTED &&
                presentationEpoch.isCurrent(expectedPresentationEpoch) &&
                healthyActiveTarget() != null
            ) {
                publishPlayerState()
            }
            result
        } finally {
            targetInstallationInProgress = false
        }
    }

    private suspend fun completeUnavailableTarget(
        expectedPresentationEpoch: Long,
        result: PlaybackTargetResult,
        failureReason: AppPlaybackFailureReason,
    ): PlaybackTargetResult = installTargetForPresentation(
        expectedPresentationEpoch = expectedPresentationEpoch,
        installTarget = { result },
        onStarted = {},
        onFailed = {
            publishTargetFailure(
                expectedPresentationEpoch = expectedPresentationEpoch,
                reason = failureReason,
            )
        },
    )

    private suspend fun completeUnavailableRecordingTarget(
        expectedPresentationEpoch: Long,
        result: PlaybackTargetResult,
        admission: RecordingPlaybackAdmission,
    ): PlaybackTargetResult = installTargetForPresentation(
        expectedPresentationEpoch = expectedPresentationEpoch,
        installTarget = { result },
        onStarted = {},
        onFailed = {
            publishTargetFailure(
                expectedPresentationEpoch = expectedPresentationEpoch,
                reason = AppPlaybackFailureReason.RECORDING_READ_FAILED,
                recordingAdmission = admission,
            )
        },
    )

    private fun unavailableLiveTargetResult(channelId: ChannelId): PlaybackTargetResult {
        val observation = session.observation.value
        return if (observation.currentSession == null) {
            PlaybackTargetResult.NOT_READY
        } else if (observation.channel(channelId) == null) {
            PlaybackTargetResult.TARGET_UNAVAILABLE
        } else {
            PlaybackTargetResult.NOT_READY
        }
    }

    private fun publishTargetFailure(
        expectedPresentationEpoch: Long,
        reason: AppPlaybackFailureReason,
        recordingAdmission: RecordingPlaybackAdmission? = null,
    ) {
        if (healthyActiveTarget() != null) return
        val epoch = presentationEpoch.beginIfCurrent(expectedPresentationEpoch) ?: return
        presentationEpoch.publishIfCurrent(epoch) {
            endTargetPresentation(epoch)
            activeTargetEpoch = null
            _activeTarget.value = null
            _recordingSelection.value = null
            _recordingAdmission.value = recordingAdmission
            _state.value = AppPlaybackState.Failed(reason)
            publishDiagnostics()
        }
    }

    private fun healthyActiveTarget(): AppPlaybackTarget? =
        _activeTarget.value.takeIf {
            activePlayerTargetIsHealthy(
                playerErrorPresent = player.playerError != null,
                playbackState = player.playbackState,
            )
        }

    private suspend fun applyBackgroundPolicyToStartedTarget(result: PlaybackTargetResult?) {
        if (result != PlaybackTargetResult.STARTED) return
        val target = _activeTarget.value ?: return
        val targetEpoch = activeTargetEpoch ?: return
        applyForegroundPlaybackAction(
            foregroundPlaybackLifecycle.onTargetStarted(
                activeTarget = target,
                activeTargetEpoch = targetEpoch,
                recordingPlayWhenReady = player.playWhenReady,
            ),
        )
    }

    private suspend fun applyForegroundPlaybackAction(action: ForegroundPlaybackAction) {
        executeForegroundPlaybackAction(
            action = action,
            stopLive = { stopPlayback() },
            pauseRecording = player::pause,
            resumeLive = { channelId ->
                lastLiveChannelId = channelId
                playLive(channelId = channelId, recovering = false)
            },
            resumeRecording = player::play,
        )
    }

    private fun beginTargetPresentation(epoch: Long) {
        targetFrameListener?.let(player::removeListener)
        _videoPresentation.value = _videoPresentation.value.beginTarget(epoch)
        targetFrameListener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (targetInstallationInProgress) return
                _videoPresentation.value = _videoPresentation.value.onFirstFrame(
                    frameEpoch = epoch,
                    activeTargetEpoch = activeTargetEpoch,
                )
            }
        }.also(player::addListener)
    }

    private fun endTargetPresentation(epoch: Long) {
        targetFrameListener?.let(player::removeListener)
        targetFrameListener = null
        _videoPresentation.value = _videoPresentation.value.beginTarget(epoch)
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

    private fun publishInstalledPlayerState() {
        if (player.playerError != null) {
            publishPlayerError()
            return
        }
        _state.value = when {
            player.playbackState == Player.STATE_ENDED -> AppPlaybackState.Finished
            player.isPlaying -> AppPlaybackState.Playing
            else -> AppPlaybackState.Starting
        }
        publishDiagnostics()
    }

    private fun publishPlayerError() {
        _state.value = AppPlaybackState.Failed(
            if (_activeTarget.value is AppPlaybackTarget.Recording) {
                AppPlaybackFailureReason.RECORDING_READ_FAILED
            } else {
                AppPlaybackFailureReason.OTHER
            },
        )
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

    fun snapshot(): Long = synchronized(lock) { current }

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
