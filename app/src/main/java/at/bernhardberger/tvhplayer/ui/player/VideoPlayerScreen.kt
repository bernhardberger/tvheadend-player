@file:kotlin.OptIn(
    at.bernhardberger.tvheadend.playback.ExperimentalPlaybackDiagnosticsApi::class,
)

package at.bernhardberger.tvhplayer.ui.player

import android.os.SystemClock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import at.bernhardberger.tvhplayer.core.PlayerForegroundContext
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerSeekPreviewPhase
import at.bernhardberger.tvhplayer.core.TimeshiftSeekQueueState
import at.bernhardberger.tvhplayer.core.beginTimeshiftSeekDispatch
import at.bernhardberger.tvhplayer.core.cancelPendingTimeshiftSeek
import at.bernhardberger.tvhplayer.core.channelPickAction
import at.bernhardberger.tvhplayer.core.completeTimeshiftSeekDispatch
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
import at.bernhardberger.tvhplayer.core.queueTimeshiftSeek as enqueueTimeshiftSeek
import at.bernhardberger.tvhplayer.core.queuedTimeshiftSeekDecision
import at.bernhardberger.tvhplayer.core.liveInfoRecordingCompletion
import at.bernhardberger.tvhplayer.core.liveInfoRecordingDecision
import at.bernhardberger.tvhplayer.core.liveInfoRecordingDismissed
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvhplayer.core.seekStepMs
import at.bernhardberger.tvhplayer.core.SimpleTvCapability
import at.bernhardberger.tvhplayer.core.SimpleTvProfile
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvheadend.core.TimeshiftSeekDecision
import at.bernhardberger.tvheadend.core.TimeshiftState
import at.bernhardberger.tvheadend.core.timeshiftSeek
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvheadend.client.ConnectionState
import at.bernhardberger.tvheadend.client.DvrRuntime
import at.bernhardberger.tvheadend.core.Channel
import at.bernhardberger.tvheadend.playback.PlaybackSessionState
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.common.nextAfter
import at.bernhardberger.tvhplayer.ui.common.nowEvent
import at.bernhardberger.tvhplayer.ui.subscriptionFailureMessageResource
import at.bernhardberger.tvhplayer.ui.components.KeepScreenOn
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val CHANNEL_NUMBER_TIMEOUT_MS = 1_500L
private const val COMPLETE_CHANNEL_NUMBER_TIMEOUT_MS = 250L
private const val TIMESHIFT_SEEK_DEBOUNCE_MS = 400L
private const val TIMESHIFT_SEEK_FEEDBACK_MS = 950L

private data class LiveTimeshiftSeekPreview(
    val token: Int,
    val decision: TimeshiftSeekDecision,
    val dispatched: Boolean,
)

internal suspend fun stopPlaybackAndClose(
    stopPlayback: suspend () -> Unit,
    closePlayer: () -> Unit,
) {
    stopPlayback()
    closePlayer()
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
    dvrRepository: DvrRuntime = koinInject(),
    channelId: Int,
    channelName: String,
    serviceId: Int,
    simpleTvProfile: SimpleTvProfile = SimpleTvProfile(SimpleTvSettings(), false),
    onReconnect: () -> Unit,
    onUnlock: () -> Unit = {},
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val settings by settingsStore.playerSettings.collectAsStateWithLifecycle(
        initialValue = PlayerSettings(profile = "", audioLanguage = null, subtitleLanguage = null)
    )

    val connState by videoPlayerViewModel.connectionState.collectAsStateWithLifecycle()
    val playbackState by videoPlayerViewModel.playbackState.collectAsStateWithLifecycle()
    val playingLiveChannelId by
        videoPlayerViewModel.playingLiveChannelId.collectAsStateWithLifecycle()
    val timeshiftState by videoPlayerViewModel.timeshiftState.collectAsStateWithLifecycle()
    val subscriptionFailure by
        videoPlayerViewModel.liveSubscriptionFailure.collectAsStateWithLifecycle()
    val diagnostics by videoPlayerViewModel.diagnostics.collectAsStateWithLifecycle()
    val effectiveTimeshiftState = if (
        simpleTvProfile.allows(SimpleTvCapability.TIMESHIFT)
    ) {
        timeshiftState
    } else {
        TimeshiftState()
    }
    val channels by channelsVm.channels.collectAsStateWithLifecycle()
    val allChannels by channelsVm.allChannels.collectAsStateWithLifecycle()
    val dvrEntries by dvrRepository.entries.collectAsStateWithLifecycle()
    val recordingChannelIds = remember(dvrEntries) { activeRecordingChannelIds(dvrEntries) }
    val canModifyRecordings by dvrRepository.canModifyRecordings.collectAsStateWithLifecycle()
    val orderedChannelIds = remember(channels) { channels.map { it.channelId } }
    val channelNumbers = remember(channels) { channels.associate { it.channelId to it.number } }
    val selectedInitId by selection.selectedId.collectAsStateWithLifecycle()
    var selectedId by remember { mutableIntStateOf(selectedInitId) }

    var connectionLost by remember { mutableStateOf(false) }
    var screenActive by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var channelNumberInput by remember { mutableStateOf("") }
    var timeshiftFeedback by remember { mutableStateOf<String?>(null) }
    var timeshiftSeekQueue by remember { mutableStateOf(TimeshiftSeekQueueState()) }
    var timeshiftSeekJob by remember { mutableStateOf<Job?>(null) }
    var timeshiftSeekFeedbackJob by remember { mutableStateOf<Job?>(null) }
    var timeshiftSeekPreview by remember { mutableStateOf<LiveTimeshiftSeekPreview?>(null) }
    var timeshiftSeekToken by remember { mutableIntStateOf(0) }
    var timeshiftSeekQueuedAtMs by remember { mutableLongStateOf(0L) }
    var restoreToLiveAfterReconnect by remember { mutableStateOf(false) }
    var optionsPage by remember { mutableStateOf<PlaybackOptionsPage?>(null) }
    var restoreOptionsFocus by remember { mutableStateOf(false) }
    var statsVisible by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var restoreInfoFocus by remember { mutableStateOf(false) }
    var restoreRecordFocus by remember { mutableStateOf(false) }
    var recordingDialogVisible by remember { mutableStateOf(false) }
    var infoRecordingState by remember {
        mutableStateOf<LiveInfoRecordingState>(LiveInfoRecordingState.Idle)
    }
    var revealingKeyCode by remember { mutableStateOf<Int?>(null) }
    val rootFocus = remember { FocusRequester() }

    var currentChannelId by remember { mutableIntStateOf(channelId) }
    var currentServiceId by remember { mutableIntStateOf(serviceId) }
    var currentChannelName by remember { mutableStateOf(channelName) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val timeshiftUnavailableText = stringResource(R.string.timeshift_unavailable)
    val timeshiftReconnectLiveText = stringResource(R.string.timeshift_reconnect_live)
    val timeshiftSeekClampedText = stringResource(R.string.timeshift_seek_clamped)
    val player = remember { videoPlayerViewModel.getPlayerInstance() }
    val playerPlaybackProgressing = rememberPlayerPlaybackProgressing(player)
    var aspectRatio by remember { mutableStateOf(settings.aspectRatio) }

    LaunchedEffect(settings.aspectRatio) {
        aspectRatio = settings.aspectRatio
    }

    DisposableEffect(statsVisible) {
        videoPlayerViewModel.setDiagnosticsEnabled(statsVisible)
        onDispose {
            if (statsVisible) videoPlayerViewModel.setDiagnosticsEnabled(false)
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

    var lastPlayedServiceId by remember { mutableIntStateOf(-1) }
    LaunchedEffect(screenActive, currentServiceId) {
        if (!screenActive) {
            lastPlayedServiceId = -1
            return@LaunchedEffect
        }

        if (lastPlayedServiceId == currentServiceId) return@LaunchedEffect

        if (lastPlayedServiceId != -1) {
            videoPlayerViewModel.stop()
        }
        if (videoPlayerViewModel.playService(currentServiceId)) {
            lastPlayedServiceId = currentServiceId
        }
    }

    LaunchedEffect(playingLiveChannelId, currentChannelId, currentServiceId) {
        if (playingLiveChannelId == currentServiceId) {
            lastPlayedChannelStore.setChannelId(currentChannelId)
        }
    }

    KeepScreenOn(enabled = true)

    var interactionToken by remember { mutableIntStateOf(0) }
    val autoHideMs = 5000L

    fun showControls() {
        controlsVisible = true
        interactionToken++
    }

    fun hideControls() {
        controlsVisible = false
    }

    fun openInfo() {
        recordingDialogVisible = false
        restoreInfoFocus = false
        restoreRecordFocus = false
        infoOpen = true
        hideControls()
    }

    fun dismissRecordingDialog() {
        infoRecordingState = liveInfoRecordingDismissed(infoRecordingState)
        recordingDialogVisible = false
        restoreRecordFocus = true
    }

    fun closeInfo() {
        infoRecordingState = liveInfoRecordingDismissed(infoRecordingState)
        recordingDialogVisible = false
        restoreRecordFocus = false
        infoOpen = false
        showControls()
        restoreInfoFocus = true
    }

    fun openChannelDrawer() {
        selectedId = browsingFocusChannelId(
            visibleChannels = channels,
            currentFocusId = currentChannelId,
        ) ?: -1
        hideControls()
        drawerOpen = true
    }

    fun queueTimeshiftSeek(deltaMs: Long) {
        timeshiftSeekQueue = enqueueTimeshiftSeek(
            queue = timeshiftSeekQueue,
            state = effectiveTimeshiftState,
            requestedDeltaMs = deltaMs,
        )
        timeshiftSeekToken++
        val token = timeshiftSeekToken
        timeshiftSeekQueuedAtMs = SystemClock.uptimeMillis()
        timeshiftSeekPreview = LiveTimeshiftSeekPreview(
            token = token,
            decision = queuedTimeshiftSeekDecision(timeshiftSeekQueue),
            dispatched = false,
        )
        timeshiftSeekFeedbackJob?.cancel()
        timeshiftSeekFeedbackJob = null
        if (timeshiftSeekJob?.isActive == true) return
        timeshiftSeekJob = scope.launch {
            try {
                while (true) {
                    val debounceRemainingMs = (
                        timeshiftSeekQueuedAtMs + TIMESHIFT_SEEK_DEBOUNCE_MS -
                            SystemClock.uptimeMillis()
                        ).coerceAtLeast(0L)
                    if (debounceRemainingMs > 0L) delay(debounceRemainingMs)

                    val dispatch = beginTimeshiftSeekDispatch(timeshiftSeekQueue)
                    if (dispatch == null) {
                        timeshiftSeekQueue = cancelPendingTimeshiftSeek(timeshiftSeekQueue)
                        if (timeshiftSeekPreview?.dispatched == false) {
                            timeshiftSeekPreview = null
                        }
                        break
                    }
                    timeshiftSeekQueue = dispatch.queue
                    val dispatchToken = timeshiftSeekPreview?.token ?: timeshiftSeekToken
                    timeshiftSeekPreview = timeshiftSeekPreview
                        ?.takeIf { it.token == dispatchToken }
                        ?.copy(dispatched = true)

                    val decision = videoPlayerViewModel.seekTimeshift(dispatch.deltaMs)
                    timeshiftSeekQueue = completeTimeshiftSeekDispatch(
                        timeshiftSeekQueue,
                        decision,
                    )
                    val previewIsCurrent = timeshiftSeekPreview?.token == dispatchToken
                    if (decision != null && previewIsCurrent) {
                        timeshiftSeekPreview = timeshiftSeekPreview?.copy(decision = decision)
                    }
                    if (previewIsCurrent) {
                        timeshiftFeedback = if (decision?.clamped == true) {
                            timeshiftSeekClampedText
                        } else if (decision == null) {
                            timeshiftUnavailableText
                        } else {
                            null
                        }
                        timeshiftSeekFeedbackJob?.cancel()
                        timeshiftSeekFeedbackJob = scope.launch {
                            delay(TIMESHIFT_SEEK_FEEDBACK_MS)
                            if (timeshiftSeekPreview?.token == dispatchToken) {
                                timeshiftSeekPreview = null
                            }
                            timeshiftSeekFeedbackJob = null
                        }
                    }
                }
            } finally {
                if (timeshiftSeekQueue.dispatchInFlight) {
                    timeshiftSeekQueue = completeTimeshiftSeekDispatch(
                        timeshiftSeekQueue,
                        decision = null,
                    )
                }
                timeshiftSeekJob = null
            }
        }
    }

    fun tuneChannel(channel: Channel): Boolean {
        timeshiftSeekQueue = cancelPendingTimeshiftSeek(timeshiftSeekQueue)
        timeshiftSeekToken++
        timeshiftSeekFeedbackJob?.cancel()
        timeshiftSeekFeedbackJob = null
        timeshiftSeekPreview = null
        channelNumberInput = ""
        selection.setSelected(channel.channelId)
        selectedId = channel.channelId

        if (
            channelPickAction(currentChannelId, channel.channelId) == ChannelPickAction.CLOSE_DRAWER
        ) {
            drawerOpen = false
            return true
        }

        currentChannelId = channel.channelId
        currentServiceId = channel.channelId
        currentChannelName = channel.name
        timeshiftFeedback = null

        drawerOpen = false
        showControls()
        return true
    }

    fun tuneAdjacentChannel(direction: Int): Boolean {
        val adjacentId = ChannelNavigation.adjacentId(
            orderedIds = orderedChannelIds,
            currentId = currentChannelId,
            direction = direction,
        ) ?: return false

        val channel = channels.firstOrNull { it.channelId == adjacentId } ?: return false
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

        val channel = channels.firstOrNull { it.channelId == channelId }
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

    val epg by videoPlayerViewModel.epgForChannel(currentChannelId).collectAsStateWithLifecycle()

    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            nowSec = System.currentTimeMillis() / 1000L
            delay(1000L)
        }
    }

    val nowEvent = remember(epg, nowSec) { epg.nowEvent(nowSec) }
    val nextEvent = remember(epg, nowEvent) { epg.nextAfter(nowEvent) }
    val currentChannel = remember(allChannels, currentChannelId) {
        allChannels.firstOrNull { it.channelId == currentChannelId }
    }
    val currentChannelNumber = remember(channels, currentChannelId) {
        ChannelNavigation.numberForId(
            orderedChannelIds,
            channelNumbers,
            currentChannelId,
        )
    }
    val currentRecording = remember(dvrEntries, nowEvent?.eventId) {
        nowEvent?.let { event -> dvrEntries.firstOrNull { it.eventId == event.eventId } }
    }
    val currentRecordingTarget = nowEvent?.programmeRecordingTarget()
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
    val canRecordFromInfo = !simpleTvProfile.active &&
        canModifyRecordings &&
        infoRecordingState !is LiveInfoRecordingState.Dispatching &&
        !(infoRecordingState is LiveInfoRecordingState.Succeeded &&
            optimisticRecordingMatchesCurrent)
    val recordActionEligible = !infoRecordingScheduled && canRecordFromInfo
    val currentSubscriptionFailure = subscriptionFailure
    val statusPresentation = playbackStatusPresentation(
        connectionAvailable = connState is ConnectionState.Connected,
        playbackStarting = playbackState is PlaybackSessionState.Starting,
        playbackRecovering = playbackState is PlaybackSessionState.Recovering,
        playbackPlaying = playbackState is PlaybackSessionState.Playing,
        playbackFailed = playbackState is PlaybackSessionState.Failed ||
            currentSubscriptionFailure != null,
    )
    val recoveryVisible = screenActive &&
        statusPresentation == PlaybackStatusPresentation.FULL_RECOVERY
    val recoveryUiModel = playbackRecoveryUiModel(
        surface = PlaybackRecoverySurface.LIVE,
        connectionAvailable = connState is ConnectionState.Connected,
        retryTargetAvailable = playbackState is PlaybackSessionState.Recovering ||
            currentSubscriptionFailure != null,
        simpleTvActive = simpleTvProfile.active,
    )
    val recoveryHasRetry = recoveryUiModel.retryCommand != PlaybackRetryCommand.NONE
    val recoverySafeActionIsExit =
        recoveryUiModel.secondaryAction == PlaybackRecoverySecondaryAction.EXIT_SIMPLE_TV
    val confirmationVisible = infoOpen &&
        recordingDialogVisible &&
        infoRecordingState !is LiveInfoRecordingState.Idle
    val infoVisible = infoOpen && !confirmationVisible
    val showDrawer = drawerOpen && !controlsVisible && !infoOpen
    val seekPreviewPhase = when {
        controlsVisible || timeshiftSeekPreview == null -> PlayerSeekPreviewPhase.NONE
        requireNotNull(timeshiftSeekPreview).dispatched -> PlayerSeekPreviewPhase.DISPATCHED
        else -> PlayerSeekPreviewPhase.PENDING
    }
    val foregroundLayer = playerForegroundLayer(
        PlayerForegroundContext(
            confirmationVisible = confirmationVisible,
            infoVisible = infoVisible,
            optionsPage = optionsPage,
            numberEntryVisible = channelNumberInput.isNotEmpty(),
            channelDrawerVisible = showDrawer,
            recoveryVisible = recoveryVisible,
            terminalErrorVisible = false,
            seekPreviewPhase = seekPreviewPhase,
            controlsVisible = controlsVisible,
            statsEnabled = statsVisible,
        )
    )
    val autoHideEligible = playerControlsAutoHideEligible(
        PlayerAutoHideContext(
            controlsVisible = controlsVisible,
            playbackProgressing = playerPlaybackProgressing &&
                !effectiveTimeshiftState.paused,
            playbackStable = connState is ConnectionState.Connected &&
                playbackState is PlaybackSessionState.Playing &&
                statusPresentation == PlaybackStatusPresentation.NONE,
            seekPending = timeshiftSeekQueue.pendingDeltaMs != 0L,
            modalVisible = optionsPage != null ||
                infoOpen ||
                showDrawer ||
                channelNumberInput.isNotEmpty(),
            recoveryVisible = recoveryVisible,
            actionableErrorVisible = connState is ConnectionState.Error ||
                playbackState is PlaybackSessionState.Failed,
        )
    )
    PlayerControlsAutoHideEffect(
        eligible = autoHideEligible,
        interactionToken = interactionToken,
        timeoutMillis = autoHideMs,
        onHide = ::hideControls,
    )
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
        confirmationVisible = recordingDialogVisible,
        onInvalidated = {
            infoRecordingState = LiveInfoRecordingState.Idle
            recordingDialogVisible = false
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
                    val result = dvrRepository.scheduleEvent(decision.target.eventId)
                    val completion = liveInfoRecordingCompletion(
                        state = infoRecordingState,
                        result = result,
                        infoOpen = infoOpen,
                    )
                    infoRecordingState = completion.state
                    if (completion.showResult) recordingDialogVisible = true
                }
            }
            LiveInfoRecordingDecision.Invalidate -> {
                infoRecordingState = LiveInfoRecordingState.Idle
                recordingDialogVisible = false
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

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) drawerOpen = false
    }

    LaunchedEffect(connState, screenActive) {
        if (!screenActive) return@LaunchedEffect

        when (connState) {
            is ConnectionState.Connected -> {
                if (connectionLost) {
                    connectionLost = false
                    showControls()

                    if (videoPlayerViewModel.playService(currentServiceId)) {
                        lastPlayedServiceId = currentServiceId
                    }
                    if (restoreToLiveAfterReconnect) {
                        timeshiftFeedback = timeshiftReconnectLiveText
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
                    showControls()
                    videoPlayerViewModel.stop()
                    lastPlayedServiceId = -1
                }
            }
        }
    }

    val handlePlaybackBack: () -> Unit = {
        when (
            playerBackAction(
                surface = PlayerSurface.LIVE,
                simpleTvActive = simpleTvProfile.active,
                foregroundLayer = foregroundLayer,
            )
        ) {
            PlayerBackAction.DISMISS_CONFIRMATION -> dismissRecordingDialog()
            PlayerBackAction.CLOSE_INFO -> closeInfo()
            PlayerBackAction.RETURN_TO_OPTIONS_ROOT -> optionsPage = PlaybackOptionsPage.ROOT
            PlayerBackAction.CLOSE_OPTIONS -> {
                optionsPage = null
                restoreOptionsFocus = true
                interactionToken++
            }
            PlayerBackAction.CLEAR_NUMBER_ENTRY -> channelNumberInput = ""
            PlayerBackAction.CLOSE_CHANNEL_DRAWER -> drawerOpen = false
            PlayerBackAction.CLOSE_PLAYER -> onClose()
            PlayerBackAction.CANCEL_PENDING_SEEK -> {
                timeshiftSeekQueue = cancelPendingTimeshiftSeek(timeshiftSeekQueue)
                timeshiftSeekToken++
                timeshiftSeekFeedbackJob?.cancel()
                timeshiftSeekFeedbackJob = null
                timeshiftSeekPreview = null
            }
            PlayerBackAction.DISMISS_SEEK_FEEDBACK -> {
                timeshiftSeekFeedbackJob?.cancel()
                timeshiftSeekFeedbackJob = null
                timeshiftSeekPreview = null
            }
            PlayerBackAction.HIDE_CONTROLS -> hideControls()
            PlayerBackAction.HIDE_STATS -> statsVisible = false
            PlayerBackAction.CONSUME_WITHOUT_CHANGE -> Unit
        }
    }
    val dispatchBack = rememberPlayerBackDispatcher(handlePlaybackBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (dispatchBack(event)) return@onPreviewKeyEvent true
                val keyCode = event.nativeKeyEvent.keyCode
                if (playbackSuppressesRevealingKey(revealingKeyCode, keyCode)) {
                    if (event.type == KeyEventType.KeyUp) revealingKeyCode = null
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (recoveryVisible) {
                    return@onPreviewKeyEvent playerParentConsumesRecoveryKey(keyCode)
                }
                if (infoOpen) return@onPreviewKeyEvent false
                if (optionsPage != null) return@onPreviewKeyEvent false

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
                            player.play()
                            scope.launch {
                                if (!videoPlayerViewModel.resumeTimeshift()) {
                                    timeshiftFeedback =
                                        timeshiftUnavailableText
                                }
                            }
                        }
                        MediaPlaybackAction.PAUSE -> {
                            player.pause()
                            scope.launch {
                                if (!videoPlayerViewModel.pauseTimeshift()) {
                                    player.play()
                                    timeshiftFeedback =
                                        timeshiftUnavailableText
                                }
                            }
                        }
                        MediaPlaybackAction.TOGGLE -> {
                            if (effectiveTimeshiftState.paused || !player.playWhenReady) {
                                player.play()
                                scope.launch {
                                    if (!videoPlayerViewModel.resumeTimeshift()) {
                                        timeshiftFeedback =
                                            timeshiftUnavailableText
                                    }
                                }
                            } else {
                                player.pause()
                                scope.launch {
                                    if (!videoPlayerViewModel.pauseTimeshift()) {
                                        player.play()
                                        timeshiftFeedback =
                                            timeshiftUnavailableText
                                    }
                                }
                            }
                        }
                        MediaPlaybackAction.NONE -> Unit
                    }
                    showControls()
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
                    // List drawer: Right dismisses (edge-of-list exit). Large-card
                    // Simple TV grid needs Right for horizontal navigation — only
                    // Back (or a pick) closes there.
                    val largeCardDrawer = simpleTvProfile.active
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionRight -> {
                            if (largeCardDrawer) {
                                false
                            } else {
                                drawerOpen = false
                                true
                            }
                        }
                        else -> false
                    }
                }

                val keyAction = playerKeyAction(
                    PlayerKeyContext(
                        surface = PlayerSurface.LIVE,
                        controlsVisible = controlsVisible,
                        seekbarFocused = false,
                        timeshiftAvailable = effectiveTimeshiftState.available,
                        simpleTvActive = simpleTvProfile.active,
                        optionsOpen = optionsPage != null,
                        statsOpen = statsVisible,
                        infoOpen = infoOpen,
                        drawerOpen = showDrawer,
                    ),
                    keyCode = keyCode,
                )
                if (playerKeyActionStartsOpeningCycle(keyAction)) {
                    revealingKeyCode = keyCode
                }
                when (keyAction) {
                    PlayerKeyAction.DISMISS_OVERLAY_ONLY -> {
                        when {
                            infoOpen -> {
                                closeInfo()
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent true
                        }
                    }
                    PlayerKeyAction.REVEAL_CONTROLS -> {
                        infoOpen = false
                        showControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE -> {
                        if (effectiveTimeshiftState.paused || !player.playWhenReady) {
                            player.play()
                            scope.launch { videoPlayerViewModel.resumeTimeshift() }
                        } else {
                            player.pause()
                            scope.launch {
                                if (!videoPlayerViewModel.pauseTimeshift()) {
                                    player.play()
                                }
                            }
                        }
                        showControls()
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
                        hideControls()
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
            enter = slideInHorizontally(tween(180)) { -it },
            exit = slideOutHorizontally(tween(180)) { -it },
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
                onFocusChannel = { selectedId = it },
                onPickChannel = { tuneChannel(it) },
                onCloseDrawer = { drawerOpen = false },
                // Simple TV uses the shared large-card grid for quick select.
                largeCards = simpleTvProfile.active,
            )
        }

        PlayerControlsLayer(
            visible = foregroundLayer == PlayerForegroundLayer.CONTROLS,
            modalVisible = optionsPage != null || infoOpen,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            OverlayControlsTv(
                imageLoader = imageLoader,
                channelNumber = currentChannelNumber,
                channelName = currentChannelName,
                piconPath = currentChannel?.icon,
                nowEvent = nowEvent,
                nextEvent = nextEvent,
                nowSec = nowSec,
                controlsVisible = controlsVisible,
                optionsOpen = optionsPage != null,
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
                onUserInteraction = { interactionToken++ },
                onOpenOptions = {
                    restoreOptionsFocus = false
                    optionsPage = PlaybackOptionsPage.ROOT
                    controlsVisible = true
                },
                timeshiftState = effectiveTimeshiftState,
                timeshiftFeedback = timeshiftFeedback,
                onToggleTimeshiftPause = {
                    if (effectiveTimeshiftState.paused) {
                        player.play()
                        scope.launch {
                            if (!videoPlayerViewModel.resumeTimeshift()) {
                                timeshiftFeedback =
                                    timeshiftUnavailableText
                            }
                        }
                    } else {
                        player.pause()
                        scope.launch {
                            if (!videoPlayerViewModel.pauseTimeshift()) {
                                player.play()
                                timeshiftFeedback =
                                    timeshiftUnavailableText
                            }
                        }
                    }
                },
                onSeekTimeshift = { deltaMs ->
                    queueTimeshiftSeek(deltaMs)
                },
                onGoLive = {
                    scope.launch {
                        val decision = videoPlayerViewModel.goLive()
                        if (decision == null || !videoPlayerViewModel.resumeTimeshift()) {
                            timeshiftFeedback = timeshiftUnavailableText
                        } else {
                            player.play()
                            timeshiftFeedback = null
                        }
                    }
                },
                showStop = simpleTvProfile.allows(SimpleTvCapability.STOP),
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
                decision = requireNotNull(timeshiftSeekPreview).decision,
                nowEpochSec = nowSec,
                programmeStartSec = nowEvent?.start,
                programmeStopSec = nowEvent?.stop,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (
            infoOpen &&
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
                    infoRecordingState = LiveInfoRecordingState.Confirming(
                        event.programmeRecordingTarget()
                    )
                    recordingDialogVisible = true
                    restoreRecordFocus = false
                },
                onRecordingActivate = ::activateInfoRecording,
                onRecordingDismiss = ::dismissRecordingDialog,
                onClose = ::closeInfo,
                piconContent = {
                    PiconBox(
                        imageLoader = imageLoader,
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

        optionsPage?.let { page ->
            PlaybackOptionsSheet(
                page = page,
                player = player,
                tracksResolving =
                    playbackState is PlaybackSessionState.Starting ||
                        playbackState is PlaybackSessionState.Recovering,
                aspectRatio = aspectRatio,
                statsVisible = statsVisible,
                showSimpleTvExit = simpleTvProfile.active,
                simpleTvActive = simpleTvProfile.active,
                onPageChange = { optionsPage = it },
                onAspectRatioChange = { mode ->
                    aspectRatio = mode
                    scope.launch { settingsStore.setAspectRatio(mode) }
                },
                onStatsVisibleChange = { statsVisible = it },
                onSimpleTvExit = {
                    optionsPage = null
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
                        subscriptionFailureMessageResource(currentSubscriptionFailure)
                    simpleTvProfile.active && connState !is ConnectionState.Connected ->
                        R.string.simple_tv_recovery_connection
                    simpleTvProfile.active -> R.string.simple_tv_recovery_playback
                    connState !is ConnectionState.Connected -> R.string.player_connection_recovering
                    playbackState is PlaybackSessionState.Failed -> R.string.player_playback_failed
                    else -> R.string.player_playback_recovering
                }
            ),
            hint = if (simpleTvProfile.active) {
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
            opaque = simpleTvProfile.active,
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
