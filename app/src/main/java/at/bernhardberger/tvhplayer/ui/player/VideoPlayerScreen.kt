package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.tv.material3.Text
import coil3.ImageLoader
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.common.formatClock
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
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.RetainedMetadataAuthority
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.core.dvrSnapshotAuthority
import at.bernhardberger.tvheadend.sdk.core.dvrSnapshotForDisplay
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.currentLivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.resolveLivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import at.bernhardberger.tvhplayer.core.projectedTimeshiftState
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val CHANNEL_NUMBER_TIMEOUT_MS = 1_500L
private const val COMPLETE_CHANNEL_NUMBER_TIMEOUT_MS = 250L

/** Quiet time after the last CH+/CH- press before the shown channel is subscribed. */
private const val CHANNEL_ZAP_SETTLE_MS = 350L

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
    isCurrent: () -> Boolean,
    onRejected: suspend () -> Unit,
    onResolved: (PlaybackTargetResult?) -> Unit,
) {
    val result = startPlayback()
    kotlinx.coroutines.currentCoroutineContext().ensureActive()
    if (!isCurrent()) return
    if (result?.isStarted != true) onRejected()
    kotlinx.coroutines.currentCoroutineContext().ensureActive()
    if (isCurrent()) onResolved(result)
}

val bottomGradient = Brush.verticalGradient(
    0f to Color.Transparent,
    0.35f to Color.Black.copy(alpha = 0.70f),
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
    middleAlpha = 0.72f,
    endAlpha = 0f,
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
    onReconnect: () -> Unit,
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
    var infoOpenedFromRecord by remember { mutableStateOf(false) }
    var restoreRecordActionFocus by remember { mutableStateOf(false) }
    var infoRecordingState by remember {
        mutableStateOf<LiveInfoRecordingState>(LiveInfoRecordingState.Idle)
    }
    val rootFocus = remember { FocusRequester() }

    var currentChannelId by remember { mutableStateOf(channelId) }
    var currentChannelName by remember { mutableStateOf(channelName) }
    val confirmedPlayingChannelId = playingLiveChannelId.takeIf {
        it == currentChannelId && playbackState.presented
    }
    val player = remember { videoPlayerViewModel.getPlayerInstance() }
    val timelineState = rememberLiveTimelinePresentationState(player)
    var sampledTimeshiftState by remember { mutableStateOf(AppTimeshiftState()) }
    // Key on the identity of the live target, not on the observation value. The observation
    // carries subscription counters that change several times a second, and restarting the loop
    // on those blanked the timeline and the distance behind live continuously.
    LaunchedEffect(videoPlayerViewModel, activeLivePlayback != null, playingLiveChannelId) {
        sampledTimeshiftState = AppTimeshiftState()
        while (true) {
            timelineState.sampleTimeshiftPresentation(videoPlayerViewModel::sampleTimeshiftPresentation)?.let {
                sampledTimeshiftState = it
            }
            delay(250L)
        }
    }
    val effectiveTimeshiftState = if (confirmedPlayingChannelId != null) {
        sampledTimeshiftState
    } else {
        AppTimeshiftState()
    }
    var requestedLiveSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    val authorizedLiveSelection = resolveLivePlaybackSelection(
        observation = observation,
        channelId = currentChannelId,
        requestedSelection = requestedLiveSelection,
    )
    var initialPlaybackResolved by remember { mutableStateOf(false) }
    var liveRequestToken by remember { mutableLongStateOf(0L) }
    var zapSettlePending by remember { mutableStateOf(false) }
    var requestedChannelFailed by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val timeshiftUnavailableText = stringResource(R.string.timeshift_unavailable)
    val timeshiftReconnectLiveText = stringResource(R.string.timeshift_reconnect_live)
    val timeshiftSeekClampedText = stringResource(R.string.timeshift_seek_clamped)
    val timeshiftExpiredText = stringResource(R.string.timeshift_target_expired)
    val timeshiftReplacedText = stringResource(R.string.timeshift_target_replaced)
    val timeshiftUncertainText = stringResource(R.string.timeshift_seek_uncertain)
    LaunchedEffect(timelineState, effectiveTimeshiftState.timeline) {
        timelineState.updateTimeline(effectiveTimeshiftState.timeline)
    }
    val visibleSeekPreview = timelineState.previewForTimeline(effectiveTimeshiftState.timeline)
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
        liveRequestToken,
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
        val requestToken = liveRequestToken
        if (zapSettlePending) {
            delay(CHANNEL_ZAP_SETTLE_MS)
            zapSettlePending = false
        }
        startInitialLivePlayback(
            startPlayback = { videoPlayerViewModel.playChannel(playbackSelection) },
            isCurrent = { requestToken == liveRequestToken },
            onRejected = {
                requestedChannelFailed = true
                lastPlayedChannelId = null
                videoPlayerViewModel.stop()
            },
            onResolved = { result ->
                initialPlaybackResolved = true
                if (result?.isStarted == true) {
                    lastPlayedChannelId = currentChannelId
                    requestedChannelFailed = false
                }
                requestedLiveSelection = null
            },
        )
    }

    LaunchedEffect(confirmedPlayingChannelId) {
        if (confirmedPlayingChannelId != null) {
            lastPlayedChannelStore.setChannelId(currentChannelId)
        }
    }

    fun openInfo(fromRecord: Boolean = false) {
        infoOpenedFromRecord = fromRecord
        restoreInfoFocus = false
        restoreRecordActionFocus = false
        restoreRecordFocus = fromRecord
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
        restoreInfoFocus = !infoOpenedFromRecord
        restoreRecordActionFocus = infoOpenedFromRecord
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
            expiredText = timeshiftExpiredText,
            replacedText = timeshiftReplacedText,
            uncertainText = timeshiftUncertainText,
            seekContent = videoPlayerViewModel::seekTimeshift,
        )
    }

    fun tuneChannel(channel: Channel): Boolean {
        val channelId = channel.id
        channelNumberInput = ""
        selection.setSelected(channelId)
        selectedId = channelId

        val pickAction = channelPickAction(confirmedPlayingChannelId, channelId)
        if (pickAction == ChannelPickAction.CLOSE_DRAWER) {
            layerState.closeChannelDrawer()
            return true
        }

        val playbackSelection = currentLivePlaybackSelection(observation, channelId)
            ?: return true
        timeshiftCommandToken += 1L
        timelineState.invalidateForSourceChange()
        liveRequestToken += 1L
        requestedChannelFailed = false
        lastPlayedChannelId = null
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
        if (!tuneChannel(channel)) return false
        // Zapping shows the new channel immediately but subscribes only once the
        // remote settles, so a CH+/- burst does not open one tuner per key press.
        zapSettlePending = true
        return true
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
    val committedWindow = if (at.bernhardberger.tvhplayer.BuildConfig.PROGRAMME_WINDOW_B) {
        programmeWindow(effectiveTimeshiftState,
            mappingTimeline = visibleSeekPreview?.mappingTimeline ?: effectiveTimeshiftState.timeline,
        ) { observation.eventAt(currentChannelId, it) }
    } else null
    val displayedWindow = if (at.bernhardberger.tvhplayer.BuildConfig.PROGRAMME_WINDOW_B) {
        visibleSeekPreview?.let { preview ->
            programmeWindow(effectiveTimeshiftState, preview.target, preview.mappingTimeline) {
                observation.eventAt(currentChannelId, it)
            }
        } ?: committedWindow.takeIf { visibleSeekPreview == null }
    } else null
    val displayedNextEvent = if (committedWindow != null) {
        observation.nextEvent(currentChannelId, committedWindow.estimatedPosition)
    } else nextEvent
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
    val canRecordFromInfo = canModifyRecordings &&
        infoRecordingState !is LiveInfoRecordingState.Dispatching &&
        !(infoRecordingState is LiveInfoRecordingState.Succeeded &&
            optimisticRecordingMatchesCurrent)
    val recordActionEligible = !infoRecordingScheduled && canRecordFromInfo
    val currentSubscriptionFailure = subscriptionFailure.takeIf { playingLiveChannelId == currentChannelId }
        ?: (playbackState as? AppPlaybackState.Failed)?.subscriptionIssue
    val statusPresentation = playbackStatusPresentation(
        connectionAvailable = connState is ConnectionState.Connected,
        playbackStarting = playbackState is AppPlaybackState.Starting,
        playbackRecovering = playbackState is AppPlaybackState.Recovering,
        playbackPlaying = playbackState.presented,
        playbackFailed = requestedChannelFailed || playbackState is AppPlaybackState.Failed ||
            currentSubscriptionFailure != null,
    )
    val recoveryVisible = screenActive &&
        statusPresentation == PlaybackStatusPresentation.FULL_RECOVERY
    val recoveryUiModel = playbackRecoveryUiModel(
        surface = PlaybackRecoverySurface.LIVE,
        connectionState = connState,
        retryTargetAvailable = playingLiveChannelId == currentChannelId &&
            playbackState is AppPlaybackState.Recovering,
    )
    val recoveryHasRetry = recoveryUiModel.retryCommand != PlaybackRetryCommand.NONE
    val channelUnavailable = statusPresentation == PlaybackStatusPresentation.CHANNEL_UNAVAILABLE
    LaunchedEffect(channelUnavailable) {
        if (channelUnavailable) {
            timelineState.invalidateForSourceChange()
            layerState.showControls()
        }
    }
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
                        effectiveTimeshiftState.available && effectiveTimeshiftState.timingKnown &&
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
                seekPreviewPhase = timelineState.seekPreviewPhase(layerState.controlsVisible),
                surface = PlayerSurface.LIVE,
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
            PlayerBackAction.CLOSE_CHANNEL_DRAWER -> layerState.dismissChannelDrawer()
            PlayerBackAction.CLOSE_PLAYER -> onClose()
            PlayerBackAction.CANCEL_PENDING_SEEK -> timelineState.cancelPendingSeek()
            PlayerBackAction.DISMISS_SEEK_FEEDBACK ->
                timelineState.dismissDispatchedFeedback()
            PlayerBackAction.HIDE_CONTROLS -> layerState.hideControls()
            PlayerBackAction.HIDE_STATS -> layerState.updateStatsVisibility(false)
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
                if (keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                    // A focused Compose target swallows the Back key cycle on the TV before the
                    // activity can track it, so the BackHandler never fires. Decide Back here,
                    // where Left already works, and let the matching KeyUp be consumed above.
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        layerState.beginOpeningKeyCycle(keyCode)
                        handlePlaybackBack()
                    }
                    return@onPreviewKeyEvent true
                }
                if (foregroundLayer == PlayerForegroundLayer.RECOVERY) {
                    return@onPreviewKeyEvent playerParentConsumesRecoveryKey(keyCode)
                }
                if (layerState.infoOpen || layerState.optionsPage != null) {
                    if (event.key == Key.DirectionLeft && foregroundLayer != PlayerForegroundLayer.CONFIRMATION) {
                        layerState.beginOpeningKeyCycle(keyCode)
                        handlePlaybackBack()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                val mediaAction = mediaPlaybackAction(
                    keyCode = event.nativeKeyEvent.keyCode,
                    playKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    pauseKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    toggleKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    repeatCount = event.nativeKeyEvent.repeatCount,
                )
                if (
                    mediaAction != MediaPlaybackAction.NONE &&
                    effectiveTimeshiftState.available
                ) {
                    layerState.beginOpeningKeyCycle(keyCode)
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
                            if (!player.playWhenReady) {
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
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        channelNumberInput = ChannelNavigation.appendDigit(channelNumberInput, digit)
                    }
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
                        Key.DirectionCenter -> {
                            layerState.beginOpeningKeyCycle(keyCode)
                            tuneEnteredChannel()
                        }
                        else -> false
                    }
                }

                if (showDrawer) {
                    return@onPreviewKeyEvent false
                }

                val keyAction = playerKeyAction(
                    PlayerKeyContext(
                        surface = PlayerSurface.LIVE,
                        controlsVisible = layerState.controlsVisible,
                        seekbarFocused = false,
                        timeshiftAvailable = effectiveTimeshiftState.available,
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
                        if (timelineState.seekPending) {
                            timelineState.commitPendingSeek()
                            if (event.key == Key.DirectionDown) {
                                openChannelDrawer()
                                return@onPreviewKeyEvent true
                            }
                            restoreInfoFocus = event.key == Key.DirectionUp
                        }
                        layerState.showControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE -> {
                        if (!player.playWhenReady) {
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
        if (foregroundLayer == PlayerForegroundLayer.CHANNEL_DRAWER) {
            PlayerOverlayChrome(
                footerPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                headerContent = { modifier ->
                    PlayerIdentityHeader(
                        imageLoader = imageLoader, currentSession = currentSession,
                        piconPath = currentChannel?.icon,
                        eyebrow = listOfNotNull(currentChannelNumber?.toString(), currentChannelName).joinToString(" "),
                        title = "",
                        compact = true,
                        support = null,
                        clock = formatClock(nowSec), clockSupport = null,
                        modifier = modifier,
                    )
                },
            ) {}
        }
        AnimatedVisibility(
            visible = foregroundLayer == PlayerForegroundLayer.CHANNEL_DRAWER,
            enter = slideInVertically(tween(LIVE_PLAYER_LAYER_TRANSITION_MS)) { it },
            exit = slideOutVertically(tween(LIVE_PLAYER_LAYER_TRANSITION_MS)) { it },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            ChannelDrawer(
                channels = channels,
                selectedId = selectedId,
                playingChannelId = confirmedPlayingChannelId,
                recordingChannelIds = recordingChannelIds,
                nowEvent = { channelsVm.nowEvent(it, nowSec) },
                nextEvent = { channelsVm.nextEvent(it, nowSec) },
                imageLoader = imageLoader,
                currentSession = currentSession,
                onFocusChannel = { selectedId = it },
                onPickChannel = { tuneChannel(it) },
                onCloseDrawer = { keyCode ->
                    if (keyCode != null) layerState.beginOpeningKeyCycle(keyCode)
                    layerState.dismissChannelDrawer()
                },
            )
        }

        PlayerControlsLayer(
            visible = foregroundLayer == PlayerForegroundLayer.CONTROLS,
            modalVisible = layerState.optionsPage != null || layerState.infoOpen,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            OverlayControlsTv(
                restoreChannelAction = layerState.restoreChannelAction,
                onChannelActionRestored = layerState::onChannelActionRestored,
                onActionFocused = layerState::onActionFocused,
                imageLoader = imageLoader,
                currentSession = currentSession,
                channelNumber = currentChannelNumber,
                channelName = currentChannelName,
                piconPath = currentChannel?.icon,
                nowEvent = (committedWindow?.event ?: nowEvent).takeUnless { channelUnavailable },
                nextEvent = displayedNextEvent.takeUnless { channelUnavailable },
                committedTimeshiftState = effectiveTimeshiftState,
                committedWindow = committedWindow,
                programmeWindow = displayedWindow,
                previewing = visibleSeekPreview != null,
                channelsAvailable = channels.isNotEmpty(),
                nowSec = nowSec,
                controlsVisible = layerState.controlsVisible,
                optionsOpen = layerState.optionsPage != null,
                onOpenChannels = {
                    layerState.beginOpeningKeyCycle(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
                    openChannelDrawer()
                },
                onOpenInfo = {
                    openInfo()
                },
                onOpenRecord = { openInfo(fromRecord = true) },
                onStopPlayback = {
                    scope.launch {
                        stopPlaybackAndClose(
                            stopPlayback = videoPlayerViewModel::stop,
                            closePlayer = onClose,
                        )
                    }
                },
                onUserInteraction = layerState::onUserInteraction,
                onCommitSeek = timelineState::commitPendingSeek,
                onOpenOptions = {
                    restoreOptionsFocus = false
                    layerState.openOptions()
                },
                timeshiftState = visibleSeekPreview?.let {
                    projectedTimeshiftState(effectiveTimeshiftState, it.decision.targetMs)
                } ?: effectiveTimeshiftState,
                liveAvailable = !channelUnavailable,
                channelRecordingNow = currentChannelId in recordingChannelIds,
                nextScheduled = displayedNextEvent?.let { observation.dvrEntryForEvent(it.id) }?.state ==
                    at.bernhardberger.tvheadend.sdk.core.DvrEntryState.SCHEDULED,
                timeshiftFeedback = timelineState.feedback,
                paused = !player.playWhenReady,
                onToggleTimeshiftPause = {
                    if (!player.playWhenReady) {
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
                    timelineState.cancelPendingSeek()
                    timeshiftCommandToken += 1L
                    val commandToken = timeshiftCommandToken
                    val feedbackToken = timelineState.beginFeedbackOperation()
                    scope.launch {
                        val result = timelineState.positionCommand { videoPlayerViewModel.goLive() }
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
                restoreInfoFocus = restoreInfoFocus,
                onInfoFocusRestored = { restoreInfoFocus = false },
                restoreRecordActionFocus = restoreRecordActionFocus,
                onRecordActionFocusRestored = { restoreRecordActionFocus = false },
                restoreOptionsFocus = restoreOptionsFocus,
                onOptionsFocusRestored = { restoreOptionsFocus = false },
            )
        }

        if (
            visibleSeekPreview != null && (
                foregroundLayer == PlayerForegroundLayer.PENDING_SEEK_PREVIEW ||
                    foregroundLayer == PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW
                )
        ) {
            TimeshiftSeekPreview(
                state = effectiveTimeshiftState,
                decision = visibleSeekPreview.decision,
                feedback = timelineState.feedback,
                programmeWindow = displayedWindow,
                channelsAvailable = channels.isNotEmpty(),
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
                onPageChange = layerState::showOptionsPage,
                onAspectRatioChange = { mode ->
                    aspectRatio = mode
                    scope.launch { settingsStore.setAspectRatio(mode) }
                },
                onStatsVisibleChange = layerState::updateStatsVisibility,
            )
        }

        ChannelNumberOverlay(
            number = channelNumberInput.takeIf { foregroundLayer == PlayerForegroundLayer.NUMBER_ENTRY }.orEmpty(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(48.dp),
        )

        if (channelUnavailable && foregroundLayer in setOf(PlayerForegroundLayer.CONTROLS, PlayerForegroundLayer.NONE)) {
            val failedState = playbackState as? AppPlaybackState.Failed
            val failureDetail = listOfNotNull(
                failedState?.recoveryReason?.name,
                failedState?.playerErrorCode,
                failedState?.targetResult?.toString(),
            ).joinToString(" · ")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.78f), MaterialTheme.shapes.large)
                    .padding(24.dp)
                    .testTag("player-channel-unavailable"),
            ) {
                Text(
                    text = stringResource(currentSubscriptionFailure?.messageResource() ?: R.string.player_playback_failed),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                if (failureDetail.isNotEmpty()) {
                    Text(
                        text = failureDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
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
                    connState !is ConnectionState.Connected -> R.string.player_connection_recovering
                    playbackState is AppPlaybackState.Failed -> R.string.player_playback_failed
                    else -> R.string.player_playback_recovering
                }
            ),
            primaryActionLabel = if (recoveryHasRetry) {
                stringResource(R.string.retry)
            } else {
                stringResource(R.string.close)
            },
            onPrimaryAction = if (recoveryHasRetry) {
                ::dispatchRecoveryRetry
            } else {
                onClose
            },
            secondaryActionLabel = if (recoveryHasRetry) {
                stringResource(R.string.close)
            } else {
                null
            },
            onSecondaryAction = if (!recoveryHasRetry) {
                null
            } else {
                onClose
            },
        )
    }
}

private fun SessionObservation.dvrEntries(): List<DvrEntry> =
    dvrSnapshotForDisplay?.entries.orEmpty().takeIf {
        dvrSnapshotAuthority == RetainedMetadataAuthority.CURRENT
    }.orEmpty()
