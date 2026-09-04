package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.ImageLoader
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.COMPACT_TUNING_DELAY_MS
import at.bernhardberger.tvhplayer.core.COMPACT_TUNING_FADE_IN_MS
import at.bernhardberger.tvhplayer.core.COMPACT_TUNING_MINIMUM_OPAQUE_MS
import at.bernhardberger.tvhplayer.core.CompactTuningVisibilityAction
import at.bernhardberger.tvhplayer.core.activeRecordingChannelIds
import at.bernhardberger.tvhplayer.core.ChannelKeyAction
import at.bernhardberger.tvhplayer.core.ChannelPickAction
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingDecision
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.core.MediaPlaybackAction
import at.bernhardberger.tvhplayer.core.PlaybackStatusPresentation
import at.bernhardberger.tvhplayer.core.PlaybackRecoverySecondaryAction
import at.bernhardberger.tvhplayer.core.PlaybackRecoverySurface
import at.bernhardberger.tvhplayer.core.PlaybackRetryCommand
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerBackAction
import at.bernhardberger.tvhplayer.core.PlayerAutoHideContext
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.channelPickAction
import at.bernhardberger.tvhplayer.core.mediaPlaybackAction
import at.bernhardberger.tvhplayer.core.playbackStatusPresentation
import at.bernhardberger.tvhplayer.core.compactTuningVisibilityAction
import at.bernhardberger.tvhplayer.core.playbackRecoveryUiModel
import at.bernhardberger.tvhplayer.core.playbackChannelKeyAction
import at.bernhardberger.tvhplayer.core.playbackSuppressesRevealingKey
import at.bernhardberger.tvhplayer.core.playerControlsAutoHideEligible
import at.bernhardberger.tvhplayer.core.playerBackAction
import at.bernhardberger.tvhplayer.core.playerForegroundLayer
import at.bernhardberger.tvhplayer.core.playerParentConsumesRecoveryKey
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.playerKeyAction
import at.bernhardberger.tvhplayer.core.playerKeyActionStartsOpeningCycle
import at.bernhardberger.tvhplayer.core.liveInfoRecordingCompletion
import at.bernhardberger.tvhplayer.core.liveInfoRecordingDecision
import at.bernhardberger.tvhplayer.core.liveInfoRecordingDismissed
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvhplayer.core.seekStepMs
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandDisposition
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssueCategory
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.currentLivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.resolveLivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val CHANNEL_NUMBER_TIMEOUT_MS = 1_500L
private const val COMPLETE_CHANNEL_NUMBER_TIMEOUT_MS = 250L

internal fun SubscriptionIssue.messageResource(): Int = when (this) {
    SubscriptionIssue.NO_INPUT -> R.string.tvh_no_input
    SubscriptionIssue.INVALID_TARGET -> R.string.tvh_target_invalid
    SubscriptionIssue.NO_FREE_ADAPTER -> R.string.tvh_no_free_adapter
    SubscriptionIssue.MUX_NOT_ENABLED -> R.string.tvh_mux_not_enabled
    SubscriptionIssue.TUNING_FAILED -> R.string.tvh_tuning_failed
    SubscriptionIssue.BAD_SIGNAL -> R.string.tvh_bad_signal
    SubscriptionIssue.SCRAMBLED -> R.string.tvh_scrambled
    SubscriptionIssue.SUBSCRIPTION_OVERRIDDEN -> R.string.tvh_subscription_overridden
    SubscriptionIssue.USER_ACCESS,
    SubscriptionIssue.USER_LIMIT,
    SubscriptionIssue.NO_DISK_SPACE -> R.string.player_playback_failed
    SubscriptionIssue.WEAK_STREAM -> R.string.tvh_bad_signal
    else -> if (category == SubscriptionIssueCategory.INPUT_OR_SIGNAL) {
        R.string.tvh_bad_signal
    } else {
        R.string.player_playback_failed
    }
}

internal data class TimeshiftCommandCompletion(
    val feedback: String?,
    val applyFeedback: Boolean,
    val rollbackPlayWhenReady: Boolean?,
)

internal fun timeshiftCommandCompletion(
    commandToken: Long,
    currentToken: Long,
    feedbackToken: Long = commandToken,
    currentFeedbackToken: Long = currentToken,
    result: TimeshiftCommandResult,
    unavailableText: String,
    rollbackPlayWhenReady: Boolean?,
): TimeshiftCommandCompletion? {
    if (commandToken != currentToken) return null
    val rejected = result.disposition == TimeshiftCommandDisposition.NOT_ACCEPTED
    return TimeshiftCommandCompletion(
        feedback = unavailableText.takeIf { rejected },
        applyFeedback = feedbackToken == currentFeedbackToken,
        rollbackPlayWhenReady = rollbackPlayWhenReady.takeIf { rejected },
    )
}

internal suspend fun stopPlaybackAndClose(
    stopPlayback: suspend () -> Unit,
    closePlayer: () -> Unit,
) {
    stopPlayback()
    closePlayer()
}

internal suspend fun startInitialLivePlayback(
    startPlayback: suspend () -> PlaybackTargetResult?,
    onResolved: (PlaybackTargetResult?) -> Unit,
) {
    onResolved(startPlayback())
}

val bottomGradient = Brush.verticalGradient(
    0f to Color.Transparent,
    0.35f to Color.Black.copy(alpha = 0.35f),
    0.70f to Color.Black.copy(alpha = 0.75f),
    1f to Color.Black.copy(alpha = 0.92f)
)

internal data class PlayerTopScrimTone(
    val topAlpha: Float,
    val middleStop: Float,
    val middleAlpha: Float,
    val endAlpha: Float,
)

internal val playerTopScrimTone = PlayerTopScrimTone(
    topAlpha = 0.88f,
    middleStop = 0.58f,
    middleAlpha = 0.64f,
    endAlpha = 0.08f,
)

val topGradient = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = playerTopScrimTone.topAlpha),
    playerTopScrimTone.middleStop to Color.Black.copy(
        alpha = playerTopScrimTone.middleAlpha,
    ),
    1f to Color.Black.copy(alpha = playerTopScrimTone.endAlpha),
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoPlayerViewModel: VideoPlayerViewModel = koinViewModel(),
    selection: ChannelSelectionStore = koinInject(),
    lastPlayedChannelStore: LastPlayedChannelStore = koinInject(),
    settingsStore: PlayerSettingsStore = koinInject(),
    channelsVm: ChannelsViewModel = koinViewModel(),
    imageLoader: ImageLoader = koinInject(),
    session: TvheadendSession = koinInject(),
    channelId: ChannelId,
    channelName: String,
    timeshiftAllowed: Boolean = true,
    showStop: Boolean = true,
    recordingActionsAllowed: Boolean = true,
    playerCloseAllowed: Boolean = true,
    fullPlaybackOptionsAvailable: Boolean = true,
    recoverySecondaryAction: PlaybackRecoverySecondaryAction =
        PlaybackRecoverySecondaryAction.CLOSE,
    onReconnect: () -> Unit,
    onUnlock: () -> Unit = {},
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val layerState = rememberLivePlayerLayerState()

    val settings by settingsStore.playerSettings.collectAsStateWithLifecycle(
        initialValue = PlayerSettings(audioLanguage = null, subtitleLanguage = null)
    )

    val connState by videoPlayerViewModel.connectionState.collectAsStateWithLifecycle()
    val playbackState by videoPlayerViewModel.playbackState.collectAsStateWithLifecycle()
    val playingLiveChannelId by
        videoPlayerViewModel.playingLiveChannelId.collectAsStateWithLifecycle()
    val livePlaybackObservation by
        videoPlayerViewModel.livePlaybackObservation.collectAsStateWithLifecycle()
    val activeLivePlayback = livePlaybackObservation as? LivePlaybackObservation.Active
    val sdkTimeshiftState = activeLivePlayback?.timeshiftState ?: LiveTimeshiftState.Unavailable
    val subscriptionFailure = activeLivePlayback?.subscriptionIssue
    val diagnostics by videoPlayerViewModel.diagnostics.collectAsStateWithLifecycle()
    val effectiveTimeshiftState = if (
        timeshiftAllowed
    ) {
        sdkTimeshiftState.toAppPresentation()
    } else {
        AppTimeshiftState()
    }
    val channels by channelsVm.channels.collectAsStateWithLifecycle()
    val observation by videoPlayerViewModel.observation.collectAsStateWithLifecycle()
    val currentSession = observation.currentSession
    val dvrEntries = observation.dvrEntries()
    val recordingChannelIds = remember(dvrEntries) { activeRecordingChannelIds(dvrEntries) }
    val canModifyRecordings = currentSession != null
    val orderedChannelIds = remember(channels) { channels.map { it.id } }
    val channelNumbers = remember(channels) {
        channels.associate { it.id to it.number?.toInt() }
    }
    val selectedInitId by selection.selectedId.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf(selectedInitId) }

    var connectionLost by remember { mutableStateOf(false) }
    var screenActive by remember { mutableStateOf(false) }
    var channelNumberInput by remember { mutableStateOf("") }
    var timeshiftCommandToken by remember { mutableLongStateOf(0L) }
    var restoreToLiveAfterReconnect by remember { mutableStateOf(false) }
    var restoreOptionsFocus by remember { mutableStateOf(false) }
    var restoreInfoFocus by remember { mutableStateOf(false) }
    var restoreRecordFocus by remember { mutableStateOf(false) }
    var infoRecordingState by remember {
        mutableStateOf<LiveInfoRecordingState>(LiveInfoRecordingState.Idle)
    }
    val rootFocus = remember { FocusRequester() }

    var currentChannelId by remember { mutableStateOf(channelId) }
    var currentChannelName by remember { mutableStateOf(channelName) }
    var requestedLiveSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    val authorizedLiveSelection = resolveLivePlaybackSelection(
        observation = observation,
        channelId = currentChannelId,
        requestedSelection = requestedLiveSelection,
    )
    var initialPlaybackResolved by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val timeshiftUnavailableText = stringResource(R.string.timeshift_unavailable)
    val timeshiftReconnectLiveText = stringResource(R.string.timeshift_reconnect_live)
    val timeshiftSeekClampedText = stringResource(R.string.timeshift_seek_clamped)
    val player = remember { videoPlayerViewModel.getPlayerInstance() }
    val timelineState = rememberLiveTimelinePresentationState(player)
    val nowSec = timelineState.nowEpochSec
    var aspectRatio by remember { mutableStateOf(settings.aspectRatio) }

    fun dispatchTimeshiftCommand(
        rollbackPlayWhenReady: Boolean? = null,
        command: suspend () -> TimeshiftCommandResult,
    ) {
        timeshiftCommandToken += 1L
        val commandToken = timeshiftCommandToken
        val feedbackToken = timelineState.beginFeedbackOperation()
        scope.launch {
            val result = command()
            val completion = timeshiftCommandCompletion(
                commandToken = commandToken,
                currentToken = timeshiftCommandToken,
                feedbackToken = feedbackToken,
                currentFeedbackToken = timelineState.feedbackToken,
                result = result,
                unavailableText = timeshiftUnavailableText,
                rollbackPlayWhenReady = rollbackPlayWhenReady,
            ) ?: return@launch
            if (completion.applyFeedback) {
                timelineState.applyFeedback(feedbackToken, completion.feedback)
            }
            when (completion.rollbackPlayWhenReady) {
                true -> videoPlayerViewModel.play()
                false -> videoPlayerViewModel.pause()
                null -> Unit
            }
        }
    }

    LaunchedEffect(settings.aspectRatio) {
        aspectRatio = settings.aspectRatio
    }

    DisposableEffect(layerState.statsVisible) {
        videoPlayerViewModel.setDiagnosticsEnabled(layerState.statsVisible)
        onDispose {
            if (layerState.statsVisible) videoPlayerViewModel.setDiagnosticsEnabled(false)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> screenActive = true
                Lifecycle.Event.ON_STOP -> screenActive = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var lastPlayedChannelId by remember { mutableStateOf<ChannelId?>(null) }
    LaunchedEffect(
        screenActive,
        currentChannelId,
        authorizedLiveSelection,
        requestedLiveSelection,
    ) {
        if (!screenActive) {
            lastPlayedChannelId = null
            return@LaunchedEffect
        }

        if (lastPlayedChannelId == currentChannelId) return@LaunchedEffect
        if (
            playingLiveChannelId == currentChannelId &&
            playbackState !is AppPlaybackState.Idle &&
            playbackState !is AppPlaybackState.Failed
        ) {
            lastPlayedChannelId = currentChannelId
            requestedLiveSelection = null
            initialPlaybackResolved = true
            return@LaunchedEffect
        }

        if (playbackState is AppPlaybackState.Failed && requestedLiveSelection == null) {
            return@LaunchedEffect
        }
        if (initialPlaybackResolved && requestedLiveSelection == null) return@LaunchedEffect
        val playbackSelection = authorizedLiveSelection ?: return@LaunchedEffect
        startInitialLivePlayback(
            startPlayback = { videoPlayerViewModel.playChannel(playbackSelection) },
            onResolved = { result ->
                initialPlaybackResolved = true
                if (result?.isStarted == true) {
                    lastPlayedChannelId = currentChannelId
                    requestedLiveSelection = null
                }
            },
        )
    }

    LaunchedEffect(playingLiveChannelId, currentChannelId) {
        if (playingLiveChannelId == currentChannelId) {
            lastPlayedChannelStore.setChannelId(currentChannelId)
        }
    }

    fun openInfo() {
        restoreInfoFocus = false
        restoreRecordFocus = false
        layerState.openInfo()
    }

    fun dismissRecordingDialog() {
        infoRecordingState = liveInfoRecordingDismissed(infoRecordingState)
        layerState.dismissRecordingConfirmation()
        restoreRecordFocus = true
    }

    fun closeInfo() {
        infoRecordingState = liveInfoRecordingDismissed(infoRecordingState)
        layerState.dismissRecordingConfirmation()
        restoreRecordFocus = false
        layerState.closeInfo()
        restoreInfoFocus = true
    }

    fun openChannelDrawer() {
        selectedId = browsingFocusChannelId(
            visibleChannels = channels,
            currentFocusId = currentChannelId,
        )
        layerState.openChannelDrawer()
    }

    fun queueTimeshiftSeek(deltaMs: Long) {
        timelineState.queueRelativeSeek(
            state = effectiveTimeshiftState,
            requestedDeltaMs = deltaMs,
            unavailableText = timeshiftUnavailableText,
            clampedText = timeshiftSeekClampedText,
            seekRelative = videoPlayerViewModel::seekTimeshift,
        )
    }

    fun tuneChannel(channel: Channel): Boolean {
        val channelId = channel.id
        channelNumberInput = ""
        selection.setSelected(channelId)
        selectedId = channelId

        val pickAction = channelPickAction(currentChannelId, channelId)
        if (pickAction == ChannelPickAction.CLOSE_DRAWER) {
            layerState.closeChannelDrawer()
            return true
        }

        val playbackSelection = currentLivePlaybackSelection(observation, channelId)
            ?: return true
        timeshiftCommandToken += 1L
        timelineState.invalidateForSourceChange()
        requestedLiveSelection = playbackSelection
        currentChannelId = channelId
        currentChannelName = channel.name.orEmpty()
        timelineState.clearFeedback()

        layerState.closeChannelDrawer()
        layerState.showControls()
        return true
    }

    fun tuneAdjacentChannel(direction: Int): Boolean {
        val adjacentId = ChannelNavigation.adjacentId(
            orderedIds = orderedChannelIds,
            currentId = currentChannelId,
            direction = direction,
        ) ?: return false

        val channel = channels.firstOrNull { it.id == adjacentId } ?: return false
        return tuneChannel(channel)
    }

    fun tuneEnteredChannel(): Boolean {
        if (channelNumberInput.isEmpty()) return false

        val channelId = ChannelNavigation.idForNumber(
            orderedIds = orderedChannelIds,
            channelNumbers = channelNumbers,
            enteredNumber = channelNumberInput,
        )
        channelNumberInput = ""

        val channel = channels.firstOrNull { it.id == channelId }
        return channel?.let(::tuneChannel) ?: true
    }

    LaunchedEffect(channelNumberInput) {
        if (channelNumberInput.isEmpty()) return@LaunchedEffect
        delay(
            if (channelNumberInput.length == 3) {
                COMPLETE_CHANNEL_NUMBER_TIMEOUT_MS
            } else {
                CHANNEL_NUMBER_TIMEOUT_MS
            }
        )
        tuneEnteredChannel()
    }

    val nowEvent = remember(observation, currentChannelId, nowSec) {
        observation.eventAt(
            currentChannelId,
            kotlin.time.Instant.fromEpochSeconds(nowSec),
        )
    }
    val nextEvent = remember(observation, currentChannelId, nowSec) {
        observation.nextEvent(
            currentChannelId,
            kotlin.time.Instant.fromEpochSeconds(nowSec),
        )
    }
    val currentChannel = remember(observation, currentChannelId) {
        observation.channel(currentChannelId)
    }
    val currentChannelNumber = remember(channels, currentChannelId) {
        ChannelNavigation.numberForId(
            orderedChannelIds,
            channelNumbers,
            currentChannelId,
        )
    }
    val currentRecording = remember(observation, nowEvent?.id) {
        nowEvent?.let { observation.dvrEntryForEvent(it.id) }
    }
    val currentRecordingTarget = currentSession?.let { capability ->
        nowEvent?.programmeRecordingTarget(capability)
    }
    val optimisticRecordingTarget = when (val state = infoRecordingState) {
        is LiveInfoRecordingState.Dispatching -> state.target
        is LiveInfoRecordingState.Succeeded -> state.target
        LiveInfoRecordingState.Idle,
        is LiveInfoRecordingState.Confirming,
        is LiveInfoRecordingState.Failed -> null
    }
    val optimisticRecordingMatchesCurrent = optimisticRecordingTarget != null &&
        optimisticRecordingTarget == currentRecordingTarget
    val infoRecordingScheduled = currentRecording != null || optimisticRecordingMatchesCurrent
    val canRecordFromInfo = recordingActionsAllowed &&
        canModifyRecordings &&
        infoRecordingState !is LiveInfoRecordingState.Dispatching &&
        !(infoRecordingState is LiveInfoRecordingState.Succeeded &&
            optimisticRecordingMatchesCurrent)
    val recordActionEligible = !infoRecordingScheduled && canRecordFromInfo
    val currentSubscriptionFailure = subscriptionFailure
    val statusPresentation = playbackStatusPresentation(
        connectionAvailable = connState is ConnectionState.Connected,
        playbackStarting = playbackState is AppPlaybackState.Starting,
        playbackRecovering = playbackState is AppPlaybackState.Recovering,
        playbackPlaying = playbackState is AppPlaybackState.Playing,
        playbackFailed = playbackState is AppPlaybackState.Failed ||
            currentSubscriptionFailure != null,
    )
    val recoveryVisible = screenActive &&
        statusPresentation == PlaybackStatusPresentation.FULL_RECOVERY
    val recoveryUiModel = playbackRecoveryUiModel(
        surface = PlaybackRecoverySurface.LIVE,
        connectionAvailable = connState is ConnectionState.Connected,
        retryTargetAvailable = playbackState is AppPlaybackState.Recovering ||
            currentSubscriptionFailure != null,
        secondaryAction = recoverySecondaryAction,
    )
    val recoveryHasRetry = recoveryUiModel.retryCommand != PlaybackRetryCommand.NONE
    val recoverySafeActionIsExit =
        recoveryUiModel.secondaryAction == PlaybackRecoverySecondaryAction.EXIT_SIMPLE_TV
    fun currentPlayerForegroundContext() =
        layerState.foregroundContext(
            numberEntryVisible = channelNumberInput.isNotEmpty(),
            recoveryVisible = recoveryVisible,
            terminalErrorVisible = false,
            seekPreviewPhase = timelineState.seekPreviewPhase(layerState.controlsVisible),
        )
    val foregroundContext = currentPlayerForegroundContext()
    val confirmationVisible = foregroundContext.confirmationVisible
    val infoVisible = foregroundContext.infoVisible
    val showDrawer = foregroundContext.channelDrawerVisible
    val seekPreviewPhase = foregroundContext.seekPreviewPhase
    val foregroundLayer = playerForegroundLayer(foregroundContext)
    val autoHideEligible = playerControlsAutoHideEligible(
        PlayerAutoHideContext(
            controlsVisible = layerState.controlsVisible,
            playbackProgressing = timelineState.playbackProgressing &&
                !effectiveTimeshiftState.paused,
            playbackStable = connState is ConnectionState.Connected &&
                playbackState is AppPlaybackState.Playing &&
                statusPresentation == PlaybackStatusPresentation.NONE,
            seekPending = timelineState.seekPending,
            modalVisible = layerState.optionsPage != null ||
                layerState.infoOpen ||
                showDrawer ||
                channelNumberInput.isNotEmpty(),
            recoveryVisible = recoveryVisible,
            actionableErrorVisible = connState is ConnectionState.Error ||
                playbackState is AppPlaybackState.Failed,
        )
    )
    SideEffect {
        layerState.updateAutoHideEligibility(autoHideEligible)
    }
    PlayerRootFocusEffect(foregroundLayer, rootFocus)
    var compactTuningVisible by remember { mutableStateOf(false) }

    LaunchedEffect(screenActive, statusPresentation, compactTuningVisible) {
        when (
            compactTuningVisibilityAction(
                screenActive = screenActive,
                presentation = statusPresentation,
                currentlyVisible = compactTuningVisible,
            )
        ) {
            CompactTuningVisibilityAction.KEEP_HIDDEN -> Unit
            CompactTuningVisibilityAction.SHOW_AFTER_DELAY -> {
                delay(COMPACT_TUNING_DELAY_MS)
                compactTuningVisible = true
            }
            CompactTuningVisibilityAction.KEEP_VISIBLE -> Unit
            CompactTuningVisibilityAction.HIDE_AFTER_MINIMUM -> {
                delay(COMPACT_TUNING_FADE_IN_MS + COMPACT_TUNING_MINIMUM_OPAQUE_MS)
                compactTuningVisible = false
            }
            CompactTuningVisibilityAction.HIDE_IMMEDIATELY -> {
                compactTuningVisible = false
            }
        }
    }

    LiveInfoRecordingValidityEffect(
        state = infoRecordingState,
        currentEvent = nowEvent,
        actionEligible = recordActionEligible,
        confirmationVisible = layerState.recordingConfirmationVisible,
        onInvalidated = {
            infoRecordingState = LiveInfoRecordingState.Idle
            layerState.dismissRecordingConfirmation()
            restoreRecordFocus = true
        },
    )

    fun activateInfoRecording() {
        when (
            val decision = liveInfoRecordingDecision(
                state = infoRecordingState,
                currentEvent = nowEvent,
                actionEligible = recordActionEligible,
            )
        ) {
            is LiveInfoRecordingDecision.Dispatch -> {
                infoRecordingState = LiveInfoRecordingState.Dispatching(decision.target)
                scope.launch {
                    val result = session.dvrRepository.scheduleEntry(
                        decision.target.currentSession,
                        DvrScheduleRequest(
                            schedule = DvrSchedule.Programme(decision.target.eventId),
                            title = decision.target.title,
                        ),
                    )
                    val completion = liveInfoRecordingCompletion(
                        state = infoRecordingState,
                        result = result,
                        infoOpen = layerState.infoOpen,
                    )
                    infoRecordingState = completion.state
                    if (completion.showResult) layerState.showRecordingConfirmation()
                }
            }
            LiveInfoRecordingDecision.Invalidate -> {
                infoRecordingState = LiveInfoRecordingState.Idle
                layerState.dismissRecordingConfirmation()
                restoreRecordFocus = true
            }
            LiveInfoRecordingDecision.Ignore -> Unit
        }
    }

    fun dispatchRecoveryRetry() {
        when (recoveryUiModel.retryCommand) {
            PlaybackRetryCommand.RECONNECT -> onReconnect()
            PlaybackRetryCommand.RETRY_LIVE -> videoPlayerViewModel.retryLiveNow()
            PlaybackRetryCommand.RESUME_RECORDING,
            PlaybackRetryCommand.NONE -> Unit
        }
    }

    LaunchedEffect(connState, screenActive) {
        if (!screenActive) return@LaunchedEffect

        when (connState) {
            is ConnectionState.Connected -> {
                if (connectionLost) {
                    connectionLost = false
                    layerState.showControls()

                    videoPlayerViewModel.retryLiveNow()
                    if (restoreToLiveAfterReconnect) {
                        timelineState.showFeedback(timeshiftReconnectLiveText)
                        restoreToLiveAfterReconnect = false
                    }
                }
            }

            is ConnectionState.Connecting,
            is ConnectionState.Disconnected,
            is ConnectionState.Error -> {
                if (!connectionLost) {
                    connectionLost = true
                    restoreToLiveAfterReconnect =
                        effectiveTimeshiftState.available &&
                            !timeshiftPositionPresentation(
                                effectiveTimeshiftState
                            ).atLiveEdge
                    layerState.showControls()
                    videoPlayerViewModel.stop()
                    lastPlayedChannelId = null
                }
            }
        }
    }

    val handlePlaybackBack: () -> Unit = {
        when (
            playerBackAction(
                surface = PlayerSurface.LIVE,
                playerCloseAllowed = playerCloseAllowed,
                foregroundLayer = playerForegroundLayer(currentPlayerForegroundContext()),
            )
        ) {
            PlayerBackAction.DISMISS_CONFIRMATION -> dismissRecordingDialog()
            PlayerBackAction.CLOSE_INFO -> closeInfo()
            PlayerBackAction.RETURN_TO_OPTIONS_ROOT ->
                layerState.showOptionsPage(PlaybackOptionsPage.ROOT)
            PlayerBackAction.CLOSE_OPTIONS -> {
                layerState.closeOptions()
                restoreOptionsFocus = true
            }
            PlayerBackAction.CLEAR_NUMBER_ENTRY -> channelNumberInput = ""
            PlayerBackAction.CLOSE_CHANNEL_DRAWER -> layerState.closeChannelDrawer()
            PlayerBackAction.CLOSE_PLAYER -> onClose()
            PlayerBackAction.CANCEL_PENDING_SEEK -> timelineState.cancelPendingSeek()
            PlayerBackAction.DISMISS_SEEK_FEEDBACK ->
                timelineState.dismissDispatchedFeedback()
            PlayerBackAction.HIDE_CONTROLS -> layerState.hideControls()
            PlayerBackAction.HIDE_STATS -> layerState.updateStatsVisibility(false)
            PlayerBackAction.CONSUME_WITHOUT_CHANGE -> Unit
        }
    }
    PlayerBackHandler(handlePlaybackBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (playbackSuppressesRevealingKey(layerState.revealingKeyCode, keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        layerState.endOpeningKeyCycle(keyCode)
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (recoveryVisible) {
                    return@onPreviewKeyEvent playerParentConsumesRecoveryKey(keyCode)
                }
                if (layerState.infoOpen) return@onPreviewKeyEvent false
                if (layerState.optionsPage != null) return@onPreviewKeyEvent false

                val mediaAction = mediaPlaybackAction(
                    keyCode = event.nativeKeyEvent.keyCode,
                    playKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    pauseKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    toggleKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                )
                if (
                    mediaAction != MediaPlaybackAction.NONE &&
                    effectiveTimeshiftState.available
                ) {
                    when (mediaAction) {
                        MediaPlaybackAction.PLAY -> {
                            videoPlayerViewModel.play()
                            dispatchTimeshiftCommand(
                                rollbackPlayWhenReady = false,
                                command = videoPlayerViewModel::resumeTimeshift,
                            )
                        }
                        MediaPlaybackAction.PAUSE -> {
                            videoPlayerViewModel.pause()
                            dispatchTimeshiftCommand(
                                rollbackPlayWhenReady = true,
                                command = videoPlayerViewModel::pauseTimeshift,
                            )
                        }
                        MediaPlaybackAction.TOGGLE -> {
                            if (effectiveTimeshiftState.paused || !player.playWhenReady) {
                                videoPlayerViewModel.play()
                                dispatchTimeshiftCommand(
                                    rollbackPlayWhenReady = false,
                                    command = videoPlayerViewModel::resumeTimeshift,
                                )
                            } else {
                                videoPlayerViewModel.pause()
                                dispatchTimeshiftCommand(
                                    rollbackPlayWhenReady = true,
                                    command = videoPlayerViewModel::pauseTimeshift,
                                )
                            }
                        }
                        MediaPlaybackAction.NONE -> Unit
                    }
                    layerState.showControls()
                    return@onPreviewKeyEvent true
                }

                ChannelNavigation.digitForKeyCode(event.nativeKeyEvent.keyCode)?.let { digit ->
                    channelNumberInput = ChannelNavigation.appendDigit(channelNumberInput, digit)
                    return@onPreviewKeyEvent true
                }

                ChannelNavigation.directionForKeyCode(event.nativeKeyEvent.keyCode)?.let { direction ->
                    channelNumberInput = ""
                    when (playbackChannelKeyAction(browserVisible = showDrawer)) {
                        ChannelKeyAction.TUNE ->
                            return@onPreviewKeyEvent tuneAdjacentChannel(direction)
                        ChannelKeyAction.PAGE_LIST -> Unit
                    }
                }

                if (channelNumberInput.isNotEmpty()) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> tuneEnteredChannel()
                        else -> false
                    }
                }

                if (showDrawer) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionRight -> {
                            layerState.closeChannelDrawer()
                            true
                        }
                        else -> false
                    }
                }

                val keyAction = playerKeyAction(
                    PlayerKeyContext(
                        surface = PlayerSurface.LIVE,
                        controlsVisible = layerState.controlsVisible,
                        seekbarFocused = false,
                        timeshiftAvailable = effectiveTimeshiftState.available,
                        playerCloseAllowed = playerCloseAllowed,
                        optionsOpen = layerState.optionsPage != null,
                        statsOpen = layerState.statsVisible,
                        infoOpen = layerState.infoOpen,
                        drawerOpen = showDrawer,
                    ),
                    keyCode = keyCode,
                )
                if (playerKeyActionStartsOpeningCycle(keyAction)) {
                    layerState.beginOpeningKeyCycle(keyCode)
                }
                when (keyAction) {
                    PlayerKeyAction.DISMISS_OVERLAY_ONLY -> {
                        when {
                            layerState.infoOpen -> {
                                closeInfo()
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent true
                        }
                    }
                    PlayerKeyAction.REVEAL_CONTROLS -> {
                        layerState.showControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE -> {
                        if (effectiveTimeshiftState.paused || !player.playWhenReady) {
                            videoPlayerViewModel.play()
                            dispatchTimeshiftCommand(
                                rollbackPlayWhenReady = false,
                                command = videoPlayerViewModel::resumeTimeshift,
                            )
                        } else {
                            videoPlayerViewModel.pause()
                            dispatchTimeshiftCommand(
                                rollbackPlayWhenReady = true,
                                command = videoPlayerViewModel::pauseTimeshift,
                            )
                        }
                        layerState.showControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.OPEN_CHANNELS -> {
                        openChannelDrawer()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.OPEN_INFO -> {
                        openInfo()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.SEEK_BACK -> {
                        queueTimeshiftSeek(-seekStepMs(event.nativeKeyEvent.repeatCount))
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.SEEK_FORWARD -> {
                        queueTimeshiftSeek(seekStepMs(event.nativeKeyEvent.repeatCount))
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.HIDE_CONTROLS -> {
                        layerState.hideControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.CLOSE_PLAYER -> {
                        onClose()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.PASS_THROUGH -> Unit
                }
                false
            }
            .focusRequester(rootFocus)
            .playerRootSemantics(stringResource(R.string.player_live_tv_surface))
            .focusable()
    ) {
        AnimatedVisibility(
            visible = foregroundLayer == PlayerForegroundLayer.CHANNEL_DRAWER,
            enter = slideInHorizontally(tween(LIVE_PLAYER_LAYER_TRANSITION_MS)) { -it },
            exit = slideOutHorizontally(tween(LIVE_PLAYER_LAYER_TRANSITION_MS)) { -it },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
        ) {
            ChannelDrawer(
                channels = channels,
                selectedId = selectedId,
                playingChannelId = currentChannelId,
                recordingChannelIds = recordingChannelIds,
                nowSec = nowSec,
                channelsVm = channelsVm,
                imageLoader = imageLoader,
                currentSession = currentSession,
                onFocusChannel = { selectedId = it },
                onPickChannel = { tuneChannel(it) },
                onCloseDrawer = layerState::closeChannelDrawer,
            )
        }

        PlayerControlsLayer(
            visible = foregroundLayer == PlayerForegroundLayer.CONTROLS,
            modalVisible = layerState.optionsPage != null || layerState.infoOpen,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            OverlayControlsTv(
                imageLoader = imageLoader,
                currentSession = currentSession,
                channelNumber = currentChannelNumber,
                channelName = currentChannelName,
                piconPath = currentChannel?.icon,
                nowEvent = nowEvent,
                nextEvent = nextEvent,
                nowSec = nowSec,
                controlsVisible = layerState.controlsVisible,
                optionsOpen = layerState.optionsPage != null,
                onOpenChannels = {
                    openChannelDrawer()
                },
                onOpenInfo = {
                    openInfo()
                },
                onStopPlayback = {
                    scope.launch {
                        stopPlaybackAndClose(
                            stopPlayback = videoPlayerViewModel::stop,
                            closePlayer = onClose,
                        )
                    }
                },
                onUserInteraction = layerState::onUserInteraction,
                onOpenOptions = {
                    restoreOptionsFocus = false
                    layerState.openOptions()
                },
                timeshiftState = effectiveTimeshiftState,
                timeshiftFeedback = timelineState.feedback,
                onToggleTimeshiftPause = {
                    if (effectiveTimeshiftState.paused) {
                        videoPlayerViewModel.play()
                        dispatchTimeshiftCommand(
                            rollbackPlayWhenReady = false,
                            command = videoPlayerViewModel::resumeTimeshift,
                        )
                    } else {
                        videoPlayerViewModel.pause()
                        dispatchTimeshiftCommand(
                            rollbackPlayWhenReady = true,
                            command = videoPlayerViewModel::pauseTimeshift,
                        )
                    }
                },
                onSeekTimeshift = { deltaMs ->
                    queueTimeshiftSeek(deltaMs)
                },
                onGoLive = {
                    timeshiftCommandToken += 1L
                    val commandToken = timeshiftCommandToken
                    val feedbackToken = timelineState.beginFeedbackOperation()
                    scope.launch {
                        val result = videoPlayerViewModel.goLive()
                        if (commandToken != timeshiftCommandToken) return@launch
                        val resumeResult = if (result.isAccepted) {
                            videoPlayerViewModel.resumeTimeshift()
                        } else {
                            result
                        }
                        val completion = timeshiftCommandCompletion(
                            commandToken = commandToken,
                            currentToken = timeshiftCommandToken,
                            feedbackToken = feedbackToken,
                            currentFeedbackToken = timelineState.feedbackToken,
                            result = resumeResult,
                            unavailableText = timeshiftUnavailableText,
                            rollbackPlayWhenReady = null,
                        ) ?: return@launch
                        if (completion.applyFeedback) {
                            timelineState.applyFeedback(feedbackToken, completion.feedback)
                        }
                        if (resumeResult.isAccepted) {
                            videoPlayerViewModel.play()
                        }
                    }
                },
                showStop = showStop,
                restoreInfoFocus = restoreInfoFocus,
                onInfoFocusRestored = { restoreInfoFocus = false },
                restoreOptionsFocus = restoreOptionsFocus,
                onOptionsFocusRestored = { restoreOptionsFocus = false },
            )
        }

        if (
            foregroundLayer == PlayerForegroundLayer.PENDING_SEEK_PREVIEW ||
            foregroundLayer == PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW
        ) {
            TimeshiftSeekPreview(
                state = effectiveTimeshiftState,
                decision = requireNotNull(timelineState.preview).decision,
                nowEpochSec = nowSec,
                programmeStartSec = nowEvent?.start?.epochSeconds,
                programmeStopSec = nowEvent?.stop?.epochSeconds,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (
            layerState.infoOpen &&
            (foregroundLayer == PlayerForegroundLayer.INFO ||
                foregroundLayer == PlayerForegroundLayer.CONFIRMATION)
        ) {
            LiveProgrammeInfoOverlay(
                event = nowEvent,
                channelIdentity = buildString {
                    currentChannelNumber?.let { number -> append("$number • ") }
                    append(currentChannelName)
                },
                channelName = currentChannelName,
                recordingScheduled = infoRecordingScheduled,
                canRecord = recordActionEligible,
                recordingState = infoRecordingState,
                confirmationVisible = confirmationVisible,
                restoreRecordFocus = restoreRecordFocus,
                onRecord = {
                    val event = nowEvent ?: return@LiveProgrammeInfoOverlay
                    val capability = currentSession ?: return@LiveProgrammeInfoOverlay
                    infoRecordingState = LiveInfoRecordingState.Confirming(
                        event.programmeRecordingTarget(capability)
                    )
                    layerState.showRecordingConfirmation()
                    restoreRecordFocus = false
                },
                onRecordingActivate = ::activateInfoRecording,
                onRecordingDismiss = ::dismissRecordingDialog,
                onClose = ::closeInfo,
                piconContent = {
                    PiconBox(
                        imageLoader = imageLoader,
                        currentSession = currentSession,
                        piconPath = currentChannel?.icon,
                        modifier = Modifier.width(96.dp).height(54.dp),
                    )
                },
                onRecordFocusRestored = { restoreRecordFocus = false },
            )
        }

        if (foregroundLayer == PlayerForegroundLayer.STATS) {
            PlaybackStatsOverlay(
                diagnostics = diagnostics,
                aspectRatio = aspectRatio,
                timeshiftState = effectiveTimeshiftState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 36.dp, end = 48.dp),
            )
        }

        layerState.optionsPage?.let { page ->
            PlaybackOptionsSheet(
                page = page,
                player = player,
                tracksResolving =
                    playbackState is AppPlaybackState.Starting ||
                        playbackState is AppPlaybackState.Recovering,
                aspectRatio = aspectRatio,
                statsVisible = layerState.statsVisible,
                showSimpleTvExit = recoverySafeActionIsExit,
                fullOptionsAvailable = fullPlaybackOptionsAvailable,
                onPageChange = layerState::showOptionsPage,
                onAspectRatioChange = { mode ->
                    aspectRatio = mode
                    scope.launch { settingsStore.setAspectRatio(mode) }
                },
                onStatsVisibleChange = layerState::updateStatsVisibility,
                onSimpleTvExit = {
                    layerState.closeOptions()
                    restoreOptionsFocus = true
                    onUnlock()
                },
            )
        }

        AnimatedVisibility(
            visible = foregroundLayer == PlayerForegroundLayer.NUMBER_ENTRY,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(48.dp)
        ) {
            Surface(
                colors = SurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.78f),
                    contentColor = Color.White,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = channelNumberInput,
                    fontSize = 56.sp,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                )
            }
        }

        CompactTuningStatus(
            visible = compactTuningVisible,
            label = stringResource(R.string.player_tuning_channel, currentChannelName),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
                .testTag("player-tuning-status"),
        )

        TvRecoveryOverlay(
            visible = foregroundLayer == PlayerForegroundLayer.RECOVERY,
            message = stringResource(
                when {
                    currentSubscriptionFailure != null ->
                        currentSubscriptionFailure.messageResource()
                    recoverySafeActionIsExit && connState !is ConnectionState.Connected ->
                        R.string.simple_tv_recovery_connection
                    recoverySafeActionIsExit -> R.string.simple_tv_recovery_playback
                    connState !is ConnectionState.Connected -> R.string.player_connection_recovering
                    playbackState is AppPlaybackState.Failed -> R.string.player_playback_failed
                    else -> R.string.player_playback_recovering
                }
            ),
            hint = if (recoverySafeActionIsExit) {
                stringResource(
                    if (recoveryHasRetry) {
                        R.string.simple_tv_recovery_hint
                    } else {
                        R.string.simple_tv_recovery_exit_hint
                    }
                )
            } else {
                null
            },
            opaque = recoverySafeActionIsExit,
            primaryActionLabel = if (recoveryHasRetry) {
                stringResource(R.string.retry)
            } else {
                stringResource(
                    if (recoverySafeActionIsExit) R.string.simple_tv_unlock else R.string.close
                )
            },
            onPrimaryAction = if (recoveryHasRetry) {
                ::dispatchRecoveryRetry
            } else if (recoverySafeActionIsExit) {
                onUnlock
            } else {
                onClose
            },
            secondaryActionLabel = if (recoveryHasRetry) {
                stringResource(
                    if (recoverySafeActionIsExit) R.string.simple_tv_unlock else R.string.close
                )
            } else {
                null
            },
            onSecondaryAction = if (!recoveryHasRetry) {
                null
            } else if (recoverySafeActionIsExit) {
                onUnlock
            } else {
                onClose
            },
        )
    }
}

private fun SessionObservation.dvrEntries(): List<DvrEntry> = when (val state = dvrState) {
    is DvrRepositoryState.Current -> state.snapshot.entries
    DvrRepositoryState.Empty,
    is DvrRepositoryState.Stale,
    is DvrRepositoryState.Synchronizing -> emptyList()
}
