@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.PlaybackBindingResult
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackOptions
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackStopResult
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionDiagnostics
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

    /**
     * A target that has already presented is waiting for more media. This is a stall of the
     * watched content, not a new tune, so surfaces keep the playing presentation.
     */
    data object Buffering : AppPlaybackState
    data object Finished : AppPlaybackState

    /** The target has been presented and any Media3 buffering is a stall rather than a tune. */
    val presented: Boolean
        get() = this is Playing || this is Buffering
    data class Recovering(
        val reason: PlaybackRecoveryReason,
        val retryDelayMillis: Long,
    ) : AppPlaybackState
    data class Failed(
        val reason: AppPlaybackFailureReason,
        val targetResult: PlaybackTargetResult? = null,
        /** Media3 error code name for a player-reported failure; diagnostics only, never a message. */
        val playerErrorCode: String? = null,
        /** Last TVHeadend subscription issue seen before the target was retired, so the user sees why. */
        val subscriptionIssue: SubscriptionIssue? = null,
        /** Last SDK recovery reason that led to retiring the target. */
        val recoveryReason: PlaybackRecoveryReason? = null,
    ) : AppPlaybackState
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
    /**
     * Server reader shift behind the live edge, when reported. This is the timeshift
     * position TVHeadend serves; [positionMs] against [liveEdgeMs] additionally contains
     * the client's delivery and decode latency, which is not timeshift.
     */
    val serverBehindLiveMs: Long? = null,
    val capacityMs: Long? = null,
    val timingKnown: Boolean = available,
    val timeline: at.bernhardberger.tvheadend.sdk.media3.TimeshiftTimeline? = null,
    val playbackTarget: at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget? = null,
    val playbackSeek: at.bernhardberger.tvheadend.sdk.media3.TimeshiftSeekToken? = null,
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
    val live: LiveSubscriptionDiagnostics? = null,
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
    private val accessLock = Any()
    @Volatile
    private var closed = false

    suspend fun <T> serialize(
        onClosed: () -> T,
        command: suspend () -> T,
    ): T = mutex.withLock {
        if (closed) onClosed() else command()
    }

    suspend fun <Request, Result> retryRecording(
        onClosed: () -> Result,
        currentRequest: () -> Request?,
        retry: suspend (Request) -> Result,
    ): Result? = serialize(onClosed) { currentRequest()?.let { retry(it) } }

    suspend fun <Result> restoreRecordingIfNeeded(
        onClosed: () -> Result,
        targetMatches: () -> Boolean,
        restore: suspend () -> Result,
    ): Result? = serialize(onClosed) {
        if (targetMatches()) null else restore()
    }

    fun close(): Boolean = synchronized(accessLock) {
        if (closed) return@synchronized false
        closed = true
        true
    }

    fun isOpen(): Boolean = !closed

    fun runIfOpen(action: () -> Unit): Boolean = synchronized(accessLock) {
        if (closed) return@synchronized false
        action()
        true
    }

    fun <T> readIfOpen(read: () -> T): T? = synchronized(accessLock) {
        if (closed) null else read()
    }

    suspend fun awaitIdle(action: () -> Unit) {
        mutex.withLock {
            synchronized(accessLock, action)
        }
    }
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
    if (result.isStarted) {
        onStarted()
    } else if (activeTarget() == null) {
        onFailed(result)
    }
    return result
}

internal fun observedLivePlayIntent(
    activeTarget: AppPlaybackTarget?,
    serverPaused: Boolean?,
): Boolean? = if (activeTarget is AppPlaybackTarget.Live) serverPaused?.not() else null

internal fun liveDiagnosticsForTarget(
    activeTarget: AppPlaybackTarget?,
    diagnostics: LiveSubscriptionDiagnostics?,
): LiveSubscriptionDiagnostics? = diagnostics.takeIf { activeTarget is AppPlaybackTarget.Live }

internal fun activePlayerTargetIsHealthy(
    playerErrorPresent: Boolean,
    playbackState: Int,
): Boolean =
    !playerErrorPresent &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED

internal fun playerReportedPlaybackState(
    currentState: AppPlaybackState,
    recoveryAttemptInProgress: Boolean,
    playbackState: Int,
    isPlaying: Boolean,
): AppPlaybackState = when {
    recoveryAttemptInProgress -> currentState
    playbackState == Player.STATE_ENDED -> AppPlaybackState.Finished
    // Playing describes a ready target; Media3's play intent owns pause/progression.
    isPlaying || playbackState == Player.STATE_READY -> AppPlaybackState.Playing
    // A stall after presentation keeps the watched channel; only a first start is a tune.
    playbackState == Player.STATE_BUFFERING && currentState.presented -> AppPlaybackState.Buffering
    playbackState == Player.STATE_BUFFERING -> AppPlaybackState.Starting
    playbackState == Player.STATE_IDLE -> AppPlaybackState.Idle
    else -> currentState
}

internal fun playerStateAfterRecoveryResolution(
    currentState: AppPlaybackState,
    playbackState: Int,
    isPlaying: Boolean,
): AppPlaybackState = playerReportedPlaybackState(
        currentState = currentState,
        recoveryAttemptInProgress = false,
        playbackState = playbackState,
        isPlaying = isPlaying,
    )

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
    val reason: PlaybackRecoveryReason,
    val selection: LivePlaybackSelection,
    val targetEpoch: Long,
) {
    fun matches(
        activeTarget: AppPlaybackTarget?,
        activeTargetEpoch: Long?,
        observation: SessionObservation,
    ): Boolean {
        val currentSelection = currentLivePlaybackSelection(
            observation = observation,
            channelId = selection.channelId,
        ) ?: return false
        return activeTarget == AppPlaybackTarget.Live(selection.channelId) &&
            activeTargetEpoch == targetEpoch &&
            currentSelection.currentSession === selection.currentSession
    }
}

internal fun currentLiveRecoveryFence(
    reason: PlaybackRecoveryReason,
    observation: SessionObservation,
    activeTarget: AppPlaybackTarget?,
    activeTargetEpoch: Long?,
): LiveRecoveryFence? {
    val liveTarget = activeTarget as? AppPlaybackTarget.Live ?: return null
    val epoch = activeTargetEpoch ?: return null
    val selection = currentLivePlaybackSelection(observation, liveTarget.channelId) ?: return null
    return LiveRecoveryFence(reason, selection, epoch)
}

internal fun dispatchPlaybackRecovery(
    scope: CoroutineScope,
    reason: PlaybackRecoveryReason,
    recover: suspend (PlaybackRecoveryReason) -> Unit,
): Job = scope.launch { recover(reason) }

internal fun shouldRepublishPlayerStateAfterRecovery(
    result: PlaybackTargetResult?,
    fence: LiveRecoveryFence,
    activeTarget: AppPlaybackTarget?,
    activeTargetEpoch: Long?,
    observation: SessionObservation,
    healthyActiveTarget: AppPlaybackTarget?,
): Boolean =
    result?.isStarted != true &&
        healthyActiveTarget != null &&
        healthyActiveTarget == activeTarget &&
        fence.matches(activeTarget, activeTargetEpoch, observation)

internal class LiveRecoveryAttemptRunner(
    private val onResolved: (LiveRecoveryFence, PlaybackTargetResult?) -> Unit,
) {
    private var current: LiveRecoveryFence? = null

    /**
     * True while an attempt owns exactly the target described by the arguments.
     *
     * Recovery holds back player state so a retune does not flicker through the states of the
     * target it is replacing. That must stay scoped to the owned target: an attempt waiting out
     * its backoff would otherwise suppress the state of a different target the user has since
     * selected, leaving a playing channel presented as still starting.
     */
    fun ownsPlayerState(
        activeTarget: AppPlaybackTarget?,
        activeTargetEpoch: Long?,
        observation: SessionObservation,
    ): Boolean = current?.matches(activeTarget, activeTargetEpoch, observation) == true

    suspend fun run(
        fence: LiveRecoveryFence,
        recover: suspend () -> PlaybackTargetResult?,
    ) {
        current = fence
        var result: PlaybackTargetResult? = null
        try {
            result = recover()
        } finally {
            if (current === fence) {
                current = null
                onResolved(fence, result)
            }
        }
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
    private val audioSelection = SessionAudioSelection()
    private var audioWriteJob: Job? = null
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
    private val recoveryBackoff = LiveRecoveryBackoff()
    private val recoveryAttempts = LiveRecoveryAttemptRunner(::publishResolvedRecoveryPlayerState)
    private var targetFrameListener: Player.Listener? = null
    val state = _state.asStateFlow()
    val activeTarget = _activeTarget.asStateFlow()
    val recordingSelection = _recordingSelection.asStateFlow()
    val recordingAdmission = _recordingAdmission.asStateFlow()
    val livePlaybackObservation = coordinator.livePlaybackObservation
    val diagnostics = _diagnostics.asStateFlow()
    internal val videoPresentation = _videoPresentation.asStateFlow()

    private val settingsJob = scope.launch {
        settings.playerSettings.distinctUntilChanged().collect(::applyPlayerSettings)
    }

    private val livePlaybackObservationJob = scope.launch {
        livePlaybackObservation.collect {
            targetCommands.serialize(onClosed = {}) {
                val active = livePlaybackObservation.value as? LivePlaybackObservation.Active
                val timeshift = active?.timeshiftState as? LiveTimeshiftState.Available
                if (!targetInstallationInProgress) {
                    observedLivePlayIntent(
                        activeTarget = _activeTarget.value,
                        serverPaused = timeshift?.playbackPaused,
                    )?.let { player.playWhenReady = it }
                }
                publishDiagnostics()
            }
        }
    }

    private var diagnosticDecoderName = "unknown"
    private var diagnosticDecoderGeneration = 0
    private val seekDiagnosticsListener = if (at.bernhardberger.tvhplayer.BuildConfig.DEBUG) {
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

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!targetCommands.isOpen()) return
            audioSelection.useProfile(profileOwner.serverProfile.value, player)
            audioSelection.onMediaItemTransition(player)
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            if (!targetCommands.isOpen()) return
            audioSelection.useProfile(profileOwner.serverProfile.value, player)
            val profileId = profileOwner.audioProfileId ?: return
            val selected = audioSelection.rememberExplicitChoice(player) ?: return
            val previous = audioWriteJob
            audioWriteJob = scope.launch {
                previous?.join()
                try {
                    settings.audioChoices.write(profileId, selected.first, selected.second)
                } catch (_: java.io.IOException) {
                    // A storage failure must not interrupt otherwise valid playback.
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (targetInstallationInProgress || !targetCommands.isOpen()) return
            audioSelection.useProfile(profileOwner.serverProfile.value, player)
            audioSelection.restore(player)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!targetInstallationInProgress && targetCommands.isOpen()) publishPlayerState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!targetInstallationInProgress && targetCommands.isOpen()) publishPlayerState()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!targetInstallationInProgress && targetCommands.isOpen()) publishPlayerError()
        }
    }

    init {
        targetCommands.runIfOpen {
            player.addListener(listener)
            seekDiagnosticsListener?.let(player::addAnalyticsListener)
        }
    }

    suspend fun playLive(selection: LivePlaybackSelection): PlaybackTargetResult? =
        targetCommands.serialize(onClosed = { PlaybackTargetResult.SHUT_DOWN }) {
            lastLiveChannelId = selection.channelId
            recoveryBackoff.reset()
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
        recoverySelection: LivePlaybackSelection? = null,
    ): PlaybackTargetResult? {
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        presentationEpoch.publishIfCurrent(expectedPresentationEpoch) {
            if (!recovering && _activeTarget.value == null) {
                _state.value = AppPlaybackState.Starting
            }
        }
        val playerSettings = settings.playerSettings.first()
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        val profileSelection = currentLivePlaybackSelection(session.observation.value, channelId)
        if (profileSelection == null) {
            return completeUnavailableTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = unavailableLiveTargetResult(channelId),
                failureReason = AppPlaybackFailureReason.OTHER,
            )
        }
        if (
            recoverySelection != null &&
            profileSelection.currentSession !== recoverySelection.currentSession
        ) {
            return completeUnavailableTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = PlaybackTargetResult.NOT_READY,
                failureReason = AppPlaybackFailureReason.OTHER,
            )
        }
        val streamProfileId = profileOwner.selectedStreamProfileIdFor(profileSelection.currentSession)
        val audioProfile = profileOwner.serverProfile.value
        val audioProfileId = profileOwner.audioProfileId
        audioWriteJob?.join()
        val storedAudio = try {
            audioProfileId?.let { settings.audioChoices.read(it, channelId) }
        } catch (_: java.io.IOException) {
            null
        }
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        if (profileOwner.serverProfile.value !== audioProfile || profileOwner.audioProfileId != audioProfileId) {
            return PlaybackTargetResult.NOT_READY
        }
        audioSelection.useProfile(audioProfile, player)
        audioSelection.load(channelId, storedAudio)
        val selection = currentLivePlaybackSelection(session.observation.value, channelId)
        if (selection == null) {
            return completeUnavailableTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = unavailableLiveTargetResult(channelId),
                failureReason = AppPlaybackFailureReason.OTHER,
            )
        }
        if (
            selection.currentSession !== profileSelection.currentSession ||
            recoverySelection != null &&
            selection.currentSession !== recoverySelection.currentSession
        ) {
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
                if (!targetCommands.runIfOpen(player::play)) {
                    PlaybackTargetResult.SHUT_DOWN
                } else {
                    when (
                        val target = coordinator.setLiveTarget(
                            session = session,
                            currentSession = selection.currentSession,
                            channelId = selection.channelId,
                            options = LivePlaybackOptions(
                                streamProfileId = streamProfileId,
                                timeshiftPeriod = requestedLiveTimeshiftPeriod(
                                    playerSettings.timeshiftEnabled,
                                ),
                            ),
                        )
                    ) {
                        is LivePlaybackTargetResult.Bound -> target.result
                        LivePlaybackTargetResult.ObservationExpired -> PlaybackTargetResult.NOT_READY
                        LivePlaybackTargetResult.TargetUnavailable ->
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
                    _activeTarget.value = AppPlaybackTarget.Live(selection.channelId)
                    lastLiveChannelId = selection.channelId
                    _recordingSelection.value = null
                    _recordingAdmission.value = null
                    _state.value = AppPlaybackState.Starting
                    beginTargetPresentation(epoch)
                    publishInstalledPlayerState()
                }
            },
            onFailed = { targetResult ->
                publishTargetFailure(
                    expectedPresentationEpoch = expectedPresentationEpoch,
                    reason = AppPlaybackFailureReason.OTHER,
                    targetResult = targetResult,
                )
            }
        )
        if (committed) applyBackgroundPolicyToStartedTarget(result)
        return result
    }

    suspend fun playRecording(
        selection: RecordingPlaybackSelection,
        start: RecordingPlaybackStart,
    ): PlaybackTargetResult? = targetCommands.serialize(
        onClosed = { PlaybackTargetResult.SHUT_DOWN },
    ) {
        playRecordingLocked(selection.recordingId, start)
    }

    suspend fun restoreRecordingRoute(
        selection: RecordingPlaybackSelection,
        start: RecordingPlaybackStart,
    ): PlaybackTargetResult? = targetCommands.restoreRecordingIfNeeded(
        onClosed = { PlaybackTargetResult.SHUT_DOWN },
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
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
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
                if (!targetCommands.isOpen()) return@installTargetForPresentation PlaybackTargetResult.SHUT_DOWN
                when (
                    val binding = session.bindRecordingPlayback(
                        selection.currentSession,
                        selection.recordingId,
                    )
                ) {
                    is PlaybackBindingResult.Bound -> {
                        admission = binding.binding.admission
                        if (!targetCommands.isOpen()) {
                            PlaybackTargetResult.SHUT_DOWN
                        } else {
                            coordinator.setRecordingTarget(binding.binding, start)
                        }
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
            onFailed = { targetResult ->
                publishTargetFailure(
                    expectedPresentationEpoch = expectedPresentationEpoch,
                    reason = AppPlaybackFailureReason.RECORDING_READ_FAILED,
                    targetResult = targetResult,
                    recordingAdmission = admission,
                )
            }
        )
        if (committed) applyBackgroundPolicyToStartedTarget(result)
        return result
    }

    suspend fun stop(): PlaybackStopResult = targetCommands.serialize(
        onClosed = { PlaybackStopResult.SHUT_DOWN },
    ) {
        foregroundPlaybackLifecycle.onExplicitStop()
        recoveryBackoff.reset()
        stopPlayback()
    }

    fun onAppBackgrounded() {
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
                val recordingPlayWhenReady = targetCommands.readIfOpen { player.playWhenReady }
                    ?: return@serialize
                applyForegroundPlaybackAction(
                    foregroundPlaybackLifecycle.onBackgrounded(
                        activeTarget = _activeTarget.value,
                        activeTargetEpoch = activeTargetEpoch,
                        recordingPlayWhenReady = recordingPlayWhenReady,
                    ),
                )
            }
        }
    }

    fun onAppForegrounded() {
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
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
        if (!targetCommands.isOpen()) return PlaybackStopResult.SHUT_DOWN
        val epoch = presentationEpoch.begin()
        endTargetPresentation(epoch)
        val currentJob = currentCoroutineContext().job
        recoveryJob?.takeUnless { it === currentJob }?.cancel()
        recoveryJob = null
        val result = coordinator.stop()
        if (!targetCommands.isOpen()) return PlaybackStopResult.SHUT_DOWN
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

    suspend fun retryLive(): PlaybackTargetResult? = targetCommands.serialize(
        onClosed = { PlaybackTargetResult.SHUT_DOWN },
    ) {
        // An explicit retry is a user decision, so it refills the budget an exhausted target used.
        recoveryBackoff.reset()
        lastLiveChannelId?.let { channelId ->
            playLive(
                channelId = channelId,
                recovering = true,
            )
        }
    }
    suspend fun retryRecording(): PlaybackTargetResult? = targetCommands.retryRecording(
        onClosed = { PlaybackTargetResult.SHUT_DOWN },
        currentRequest = { lastRecordingRequest },
    ) { (recordingId, start) ->
        playRecordingLocked(recordingId, start)
    }
    suspend fun pauseTimeshift(): TimeshiftCommandResult = targetCommands.serialize(
        onClosed = { TimeshiftCommandResult.SHUT_DOWN },
    ) {
        coordinator.pauseTimeshift()
    }
    suspend fun resumeTimeshift(): TimeshiftCommandResult = targetCommands.serialize(
        onClosed = { TimeshiftCommandResult.SHUT_DOWN },
    ) {
        coordinator.resumeTimeshift()
    }
    suspend fun goLive(): TimeshiftCommandResult = targetCommands.serialize(
        onClosed = { TimeshiftCommandResult.SHUT_DOWN },
    ) {
        coordinator.returnToLive()
    }
    fun play() { targetCommands.runIfOpen(player::play) }
    suspend fun seekTimeshift(target: at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget) =
        targetCommands.serialize(
            onClosed = { at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Replaced },
        ) { coordinator.seekTimeshift(target) }

    suspend fun seekTimeshift(selection: at.bernhardberger.tvheadend.sdk.media3.TimeshiftSeekSelection) =
        targetCommands.serialize(
            onClosed = { at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Replaced },
        ) {
            val diagnose = at.bernhardberger.tvhplayer.BuildConfig.DEBUG && !player.playWhenReady
            fun counters(): String {
                val value = player.videoDecoderCounters ?: return "none"
                value.ensureUpdated()
                return "${value.queuedInputBufferCount}/${value.renderedOutputBufferCount}/" +
                    "${value.skippedOutputBufferCount}/${value.droppedBufferCount}"
            }
            val before = if (diagnose) counters() else ""
            val generation = diagnosticDecoderGeneration
            val started = android.os.SystemClock.elapsedRealtime()
            coordinator.seekTimeshift(selection).also { result ->
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
     * Samples the presented timeshift position.
     *
     * Segment replacement (including same-subscription restart) invalidates a sample.
     * [toAppPresentation] detects that from the history the sample carries. Rejecting every
     * observation that changed during the
     * round trip also rejected ordinary subscription diagnostics, which arrive several times a
     * second and blanked the timeline that often.
     */
    suspend fun sampleTimeshiftPresentation(): AppTimeshiftState {
        val sample = coordinator.timeshiftPlaybackPosition()
        return (livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState?.toAppPresentation(sample) ?: AppTimeshiftState()
    }
    fun pause() { targetCommands.runIfOpen(player::pause) }
    fun seekTo(positionMs: Long) { targetCommands.runIfOpen { player.seekTo(positionMs) } }
    fun setDiagnosticsEnabled(enabled: Boolean) {
        if (!targetCommands.isOpen()) return
        diagnosticsEnabled = enabled
        publishDiagnostics()
    }
    fun setRefreshRateMatchingEnabled(enabled: Boolean) {
        targetCommands.runIfOpen {
            player.setVideoChangeFrameRateStrategy(
                if (enabled) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF,
            )
        }
    }
    internal fun onRecoveryRequired(reason: PlaybackRecoveryReason) {
        dispatchPlaybackRecovery(scope, reason) { dispatchedReason ->
            val currentJob = currentCoroutineContext().job
            recoveryJob?.takeUnless { it === currentJob }?.cancel()
            recoveryJob = currentJob
            try {
                // Admit the attempt under the command lock, then wait for the backoff delay
                // without holding it so a channel change or Stop stays responsive meanwhile.
                val admitted = targetCommands.serialize(onClosed = { null }) {
                    val fence = currentLiveRecoveryFence(
                        reason = dispatchedReason,
                        observation = session.observation.value,
                        activeTarget = _activeTarget.value,
                        activeTargetEpoch = activeTargetEpoch,
                    ) ?: return@serialize null
                    if (!fence.matches(
                            activeTarget = _activeTarget.value,
                            activeTargetEpoch = activeTargetEpoch,
                            observation = session.observation.value,
                        )
                    ) {
                        return@serialize null
                    }
                    val attempt = recoveryBackoff.nextAttempt()
                    if (attempt == null) {
                        publishRecoveryExhausted(fence.reason)
                        return@serialize null
                    }
                    presentationEpoch.publishIfCurrent(fence.targetEpoch) {
                        _state.value = AppPlaybackState.Recovering(
                            reason = fence.reason,
                            retryDelayMillis = attempt.delayMillis,
                        )
                        publishDiagnostics()
                    }
                    fence to attempt
                }
                if (admitted != null) {
                    val (fence, attempt) = admitted
                    recoveryAttempts.run(fence) {
                        if (attempt.delayMillis > 0L) delay(attempt.delayMillis)
                        targetCommands.serialize(onClosed = { null }) {
                            if (!fence.matches(
                                    activeTarget = _activeTarget.value,
                                    activeTargetEpoch = activeTargetEpoch,
                                    observation = session.observation.value,
                                )
                            ) {
                                return@serialize null
                            }
                            playLive(
                                channelId = fence.selection.channelId,
                                recovering = true,
                                expectedPresentationEpoch = fence.targetEpoch,
                                recoverySelection = fence.selection,
                            )
                        }
                    }
                }
            } finally {
                if (recoveryJob === currentJob) recoveryJob = null
            }
        }
    }

    /**
     * Retires a target the SDK keeps reporting as stuck.
     *
     * Recovery cannot succeed indefinitely, and a still-running subscription keeps consuming a
     * tuner. Surfacing a failure lets the user choose, instead of retuning forever.
     */
    private suspend fun publishRecoveryExhausted(recoveryReason: PlaybackRecoveryReason) {
        // Read the issue before stopping: the observation leaves Active with the subscription.
        val issue = lastSubscriptionIssue()
        stopPlayback()
        if (!targetCommands.isOpen()) return
        _state.value = AppPlaybackState.Failed(
            reason = AppPlaybackFailureReason.OTHER,
            subscriptionIssue = issue,
            recoveryReason = recoveryReason,
        )
        publishDiagnostics()
    }

    private fun lastSubscriptionIssue(): SubscriptionIssue? =
        (livePlaybackObservation.value as? LivePlaybackObservation.Active)?.subscriptionIssue

    suspend fun detach() {
        if (!targetCommands.close()) return
        val pendingRecovery = recoveryJob
        recoveryJob = null
        pendingRecovery?.cancel()
        livePlaybackObservationJob.cancel()
        settingsJob.cancel()
        pendingRecovery?.join()
        livePlaybackObservationJob.join()
        settingsJob.join()
        targetCommands.awaitIdle {
            targetFrameListener?.let(player::removeListener)
            targetFrameListener = null
            player.removeListener(listener)
            seekDiagnosticsListener?.let(player::removeAnalyticsListener)
            audioSelection.clear(player)
        }
        audioWriteJob?.join()
    }

    private suspend fun installTargetForPresentation(
        expectedPresentationEpoch: Long,
        installTarget: suspend () -> PlaybackTargetResult,
        onStarted: () -> Unit,
        onFailed: (PlaybackTargetResult) -> Unit,
    ): PlaybackTargetResult {
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        targetInstallationInProgress = true
        return try {
            val result = completePlaybackTargetInstallation(
                installTarget = installTarget,
                presentationStillCurrent = {
                    targetCommands.isOpen() &&
                        presentationEpoch.isCurrent(expectedPresentationEpoch)
                },
                activeTarget = ::healthyActiveTarget,
                onStarted = onStarted,
                onFailed = onFailed,
            )
            if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
            if (
                !result.isStarted &&
                presentationEpoch.isCurrent(expectedPresentationEpoch) &&
                healthyActiveTarget() != null
            ) {
                publishPlayerState()
            }
            result
        } finally {
            targetInstallationInProgress = false
            if (targetCommands.isOpen()) {
                audioSelection.useProfile(profileOwner.serverProfile.value, player)
                (_activeTarget.value as? AppPlaybackTarget.Live)?.let {
                    audioSelection.activate(it.channelId, player)
                }
            }
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
        onFailed = { targetResult ->
            publishTargetFailure(
                expectedPresentationEpoch = expectedPresentationEpoch,
                reason = failureReason,
                targetResult = targetResult,
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
        onFailed = { targetResult ->
            publishTargetFailure(
                expectedPresentationEpoch = expectedPresentationEpoch,
                reason = AppPlaybackFailureReason.RECORDING_READ_FAILED,
                targetResult = targetResult,
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
        targetResult: PlaybackTargetResult,
        recordingAdmission: RecordingPlaybackAdmission? = null,
    ) {
        if (!targetCommands.isOpen()) return
        if (healthyActiveTarget() != null) return
        val epoch = presentationEpoch.beginIfCurrent(expectedPresentationEpoch) ?: return
        presentationEpoch.publishIfCurrent(epoch) {
            endTargetPresentation(epoch)
            activeTargetEpoch = null
            _activeTarget.value = null
            _recordingSelection.value = null
            _recordingAdmission.value = recordingAdmission
            _state.value = AppPlaybackState.Failed(reason, targetResult)
            publishDiagnostics()
        }
    }

    private fun healthyActiveTarget(): AppPlaybackTarget? = targetCommands.readIfOpen {
        _activeTarget.value.takeIf {
            activePlayerTargetIsHealthy(
                playerErrorPresent = player.playerError != null,
                playbackState = player.playbackState,
            )
        }
    }

    private suspend fun applyBackgroundPolicyToStartedTarget(result: PlaybackTargetResult?) {
        if (result?.isStarted != true) return
        val target = _activeTarget.value ?: return
        val targetEpoch = activeTargetEpoch ?: return
        val recordingPlayWhenReady = targetCommands.readIfOpen { player.playWhenReady } ?: return
        applyForegroundPlaybackAction(
            foregroundPlaybackLifecycle.onTargetStarted(
                activeTarget = target,
                activeTargetEpoch = targetEpoch,
                recordingPlayWhenReady = recordingPlayWhenReady,
            ),
        )
    }

    private suspend fun applyForegroundPlaybackAction(action: ForegroundPlaybackAction) {
        executeForegroundPlaybackAction(
            action = action,
            stopLive = { stopPlayback() },
            pauseRecording = { targetCommands.runIfOpen(player::pause) },
            resumeLive = { channelId ->
                lastLiveChannelId = channelId
                playLive(channelId = channelId, recovering = false)
            },
            resumeRecording = { targetCommands.runIfOpen(player::play) },
        )
    }

    private fun beginTargetPresentation(epoch: Long) {
        targetCommands.runIfOpen {
            targetFrameListener?.let(player::removeListener)
            _videoPresentation.value = _videoPresentation.value.beginTarget(epoch)
            targetFrameListener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (targetInstallationInProgress || !targetCommands.isOpen()) return
                    _videoPresentation.value = _videoPresentation.value.onFirstFrame(
                        frameEpoch = epoch,
                        activeTargetEpoch = activeTargetEpoch,
                    )
                }
            }.also(player::addListener)
        }
    }

    private fun endTargetPresentation(epoch: Long) {
        targetCommands.runIfOpen {
            targetFrameListener?.let(player::removeListener)
            targetFrameListener = null
            _videoPresentation.value = _videoPresentation.value.beginTarget(epoch)
        }
    }

    private fun applyPlayerSettings(value: PlayerSettings) {
        val audioLanguages: Array<String> = value.audioLanguage?.let { arrayOf(it) } ?: emptyArray()
        val subtitleLanguages: Array<String> =
            value.subtitleLanguage?.let { arrayOf(it) } ?: emptyArray()
        targetCommands.runIfOpen {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguages(*audioLanguages)
                .setPreferredTextLanguages(*subtitleLanguages)
                .build()
            player.setVideoChangeFrameRateStrategy(
                if (value.refreshRateMatchingEnabled) {
                    C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                } else {
                    C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                },
            )
        }
    }

    private fun publishPlayerState(recoveryResolved: Boolean = false) {
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
                            activeTargetEpoch = activeTargetEpoch,
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

    private fun publishResolvedRecoveryPlayerState(
        fence: LiveRecoveryFence,
        result: PlaybackTargetResult?,
    ) {
        if (shouldRepublishPlayerStateAfterRecovery(
                result = result,
                fence = fence,
                activeTarget = _activeTarget.value,
                activeTargetEpoch = activeTargetEpoch,
                observation = session.observation.value,
                healthyActiveTarget = healthyActiveTarget(),
            )
        ) {
            publishPlayerState(recoveryResolved = true)
        }
    }

    private fun publishInstalledPlayerState() {
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

    private fun publishPlayerError() {
        targetCommands.runIfOpen { publishPlayerErrorFromPlayer() }
    }

    private fun publishPlayerErrorFromPlayer() {
        _state.value = AppPlaybackState.Failed(
            reason = if (_activeTarget.value is AppPlaybackTarget.Recording) {
                AppPlaybackFailureReason.RECORDING_READ_FAILED
            } else {
                AppPlaybackFailureReason.OTHER
            },
            playerErrorCode = player.playerError?.errorCodeName,
            subscriptionIssue = lastSubscriptionIssue(),
        )
        publishDiagnosticsFromPlayer()
    }

    private fun publishDiagnostics() {
        if (!diagnosticsEnabled) {
            _diagnostics.value = AppPlaybackDiagnostics(source = source(), state = _state.value)
            return
        }
        if (!targetCommands.runIfOpen { publishDiagnosticsFromPlayer() }) {
            _diagnostics.value = AppPlaybackDiagnostics(source = source(), state = _state.value)
        }
    }

    private fun publishDiagnosticsFromPlayer() {
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
        )
    }

    private fun source(activeTarget: AppPlaybackTarget? = _activeTarget.value) = when (activeTarget) {
        is AppPlaybackTarget.Live -> AppPlaybackSource.LIVE_TV
        is AppPlaybackTarget.Recording -> AppPlaybackSource.RECORDING
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

fun LiveTimeshiftState.toAppPresentation(
    sample: at.bernhardberger.tvheadend.sdk.media3.TimeshiftPlaybackPosition =
        at.bernhardberger.tvheadend.sdk.media3.TimeshiftPlaybackPosition.Unavailable,
): AppTimeshiftState = when (this) {
    LiveTimeshiftState.Unavailable -> AppTimeshiftState()
    is LiveTimeshiftState.Available -> {
        val estimate =
            sample as? at.bernhardberger.tvheadend.sdk.media3.TimeshiftPlaybackPosition.Estimate
        // A sample taken from a stream segment that has since been replaced describes unrelated
        // content. Presenting it against this history would report a false distance behind live
        // and could authorise a seek on the successor derived from the predecessor's coordinate.
        val position = estimate
            ?.takeIf { it.timeline?.describesSameSegment(timeline) == true }
            ?.target
        AppTimeshiftState(
            available = true,
            paused = playbackPaused == true,
            bufferStartMs = timeline?.start?.inWholeMilliseconds ?: 0L,
            liveEdgeMs = timeline?.end?.inWholeMilliseconds ?: 0L,
            positionMs = position?.position?.inWholeMilliseconds ?: 0L,
            serverBehindLiveMs = positionBehindLive
                ?.takeIf { it.isFinite() && it >= Duration.ZERO }
                ?.inWholeMilliseconds,
            capacityMs = grantedPeriod.takeIf { it.isFinite() && it > Duration.ZERO }?.inWholeMilliseconds,
            // The latest status edge can lag a valid decoded-content coordinate.
            // Seekability still comes from the observed history, not this sample.
            timingKnown = timeline != null && position != null,
            timeline = timeline,
            playbackTarget = position,
            playbackSeek = estimate?.seek.takeIf { position != null },
        )
    }
}

internal fun measuredTimeshiftPresentation(
    bufferedDuration: Duration?,
    positionBehindLive: Duration?,
    serverPaused: Boolean?,
    grantedPeriod: Duration? = null,
): AppTimeshiftState {
    val buffered = bufferedDuration?.takeIf { it.isFinite() && it >= Duration.ZERO }
        ?.inWholeMilliseconds
    val behind = positionBehindLive?.takeIf { it.isFinite() && it >= Duration.ZERO }
        ?.inWholeMilliseconds
    return AppTimeshiftState(
        available = true,
        paused = serverPaused == true,
        bufferStartMs = -(buffered ?: 0L),
        positionMs = -(behind ?: 0L),
        serverBehindLiveMs = behind,
        capacityMs = grantedPeriod?.takeIf { it.isFinite() && it > Duration.ZERO }
            ?.inWholeMilliseconds,
        timingKnown = buffered != null && behind != null && behind <= buffered,
    )
}
