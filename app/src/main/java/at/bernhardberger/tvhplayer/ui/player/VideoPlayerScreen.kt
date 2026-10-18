package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.screens.currentDvrConfigurations

import at.bernhardberger.tvhplayer.ui.TvOverlayBoxAlpha
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy

import at.bernhardberger.tvhplayer.ui.components.channelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.rememberPlaybackIntent

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvhplayer.core.livePauseUnavailable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.GlanceBadge
import at.bernhardberger.tvhplayer.core.ChannelNumberEntryReadiness
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.core.activeRecordingChannelIds
import at.bernhardberger.tvhplayer.core.ChannelKeyAction
import at.bernhardberger.tvhplayer.core.ChannelPickAction
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import at.bernhardberger.tvhplayer.core.programmeActions
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
import at.bernhardberger.tvhplayer.core.liveInterrupted
import at.bernhardberger.tvhplayer.core.liveInterruptionMessageShown
import at.bernhardberger.tvhplayer.core.playbackStatusPresentation
import at.bernhardberger.tvhplayer.core.playbackRecoveryUiModel
import at.bernhardberger.tvhplayer.core.playbackChannelKeyAction
import at.bernhardberger.tvhplayer.core.playbackSuppressesRevealingKey
import at.bernhardberger.tvhplayer.core.playerControlsAutoHideEligible
import at.bernhardberger.tvhplayer.core.playerBackAction
import at.bernhardberger.tvhplayer.core.playerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.LiveMediaKeyAction
import at.bernhardberger.tvhplayer.core.liveMediaKeyAction
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.playback.LivePauseState
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.playerKeyAction
import at.bernhardberger.tvhplayer.core.playerKeyActionStartsOpeningCycle
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
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
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
import at.bernhardberger.tvhplayer.core.liveBarEnd
import at.bernhardberger.tvhplayer.core.playerStateCell
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.ui.screens.postDvrFailure
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import at.bernhardberger.tvhplayer.ui.screens.currentDvrMutation
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val CHANNEL_NUMBER_TIMEOUT_MS = 1_500L
private const val COMPLETE_CHANNEL_NUMBER_TIMEOUT_MS = 250L
/** How long "No channel" stays readable after an entry that names no channel. */
private const val NO_CHANNEL_HOLD_MS = 2_000L

/**
 * The quiet time after a held channel key's last repeat before its series tunes the channel
 * reached. Every tune opens and releases a server subscription, so a tuner per press would cost
 * more than it shows.
 */
internal const val CHANNEL_ZAP_SETTLE_MS = 350L

/**
 * A channel key pressed within this time of the series' last press (the first press counts as the
 * series' at-once tune) belongs to the series: it moves the selection only, and the series tunes
 * once it has been quiet this long after a tapped press.
 */
internal const val CHANNEL_ZAP_SERIES_MS = 650L

/** From the first digit, how long an entry waits for its channel to become playable. */
internal const val CHANNEL_NUMBER_READY_BOUND_MS = 5_000L

private const val NO_DIRECT_LIVE_TOKEN = -1L

/**
 * Paces channel keys so a series tunes at most its first and its last channel: a press after a
 * quiet [seriesMs] tunes at once; a later press of the series, and every key repeat, only settles
 * ([seriesMs] after a tapped press, [settleMs] after a repeat), each superseding the one before.
 */
internal class ChannelZapPacer(
    private val settleMs: Long = CHANNEL_ZAP_SETTLE_MS,
    private val seriesMs: Long = CHANNEL_ZAP_SERIES_MS,
) {
    private var lastPressMs: Long? = null

    /** The settle delay for a press at [nowMs]; 0 tunes at once. A [repeat] never tunes at once. */
    fun settleDelayMs(nowMs: Long, repeat: Boolean = false): Long {
        val previous = lastPressMs
        lastPressMs = nowMs
        return when {
            repeat -> settleMs
            previous != null && nowMs - previous in 0 until seriesMs -> seriesMs
            else -> 0L
        }
    }
}

/**
 * Supersedes [pending] and schedules a tune: at once (before returning) for a [settleMs] of 0,
 * after it otherwise; `null` only supersedes. Returns the settling job, `null` when none.
 */
internal fun CoroutineScope.scheduleChannelZap(
    pending: Job?,
    settleMs: Long?,
    start: (settled: Boolean) -> Unit,
): Job? {
    pending?.cancel()
    if (settleMs == null) return null
    if (settleMs <= 0L) {
        start(false)
        return null
    }
    return launch {
        delay(settleMs)
        start(true)
    }
}

/**
 * Commits a typed channel number [entryDelayMs] after its last digit with the readiness
 * current then. An entry not yet playable (list not loaded, session not current) stays
 * pending until it is, at most [readyBoundMs] from the digit, then commits NOT_READY.
 */
internal suspend fun commitChannelNumberEntry(
    entryDelayMs: Long,
    readiness: Flow<ChannelNumberEntryReadiness>,
    readyBoundMs: Long = CHANNEL_NUMBER_READY_BOUND_MS,
    commit: suspend (atTimer: ChannelNumberEntryReadiness, settled: ChannelNumberEntryReadiness) -> Unit,
) {
    delay(entryDelayMs)
    val atTimer = readiness.first()
    val settled = if (atTimer != ChannelNumberEntryReadiness.NOT_READY) {
        atTimer
    } else {
        withTimeoutOrNull((readyBoundMs - entryDelayMs).coerceAtLeast(0L)) {
            readiness.first { it != ChannelNumberEntryReadiness.NOT_READY }
        } ?: ChannelNumberEntryReadiness.NOT_READY
    }
    commit(atTimer, settled)
}

/** Stamps a digit or CH key's down for the zap trace and returns its uptime. */
private fun profileZapKey(event: AndroidKeyEvent): Long {
    profileTrace("P49:zap:key:${event.keyCode}:repeat:${event.repeatCount}:time:${event.eventTime}") { }
    return event.eventTime.takeIf { it > 0L } ?: SystemClock.uptimeMillis()
}

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
    result: TimeshiftCommandResult?,
    unavailableText: String,
    rollbackPlayWhenReady: Boolean?,
    interruptionOwned: Boolean = false,
): TimeshiftCommandCompletion? {
    if (commandToken != currentToken) return null
    val rejected = result?.disposition == TimeshiftCommandDisposition.NOT_ACCEPTED
    return TimeshiftCommandCompletion(
        feedback = unavailableText.takeIf { rejected },
        applyFeedback = feedbackToken == currentFeedbackToken,
        rollbackPlayWhenReady = rollbackPlayWhenReady.takeIf { rejected && !interruptionOwned },
    )
}

internal fun dispatchTimeshiftPlaybackAction(
    action: MediaPlaybackAction,
    playWhenReady: Boolean,
    dispatch: (resume: Boolean, rollbackPlayWhenReady: Boolean?) -> Unit,
) {
    val resolved = if (action == MediaPlaybackAction.TOGGLE) {
        if (playWhenReady) MediaPlaybackAction.PAUSE else MediaPlaybackAction.PLAY
    } else action
    when (resolved) {
        MediaPlaybackAction.PLAY -> dispatch(true, false)
        MediaPlaybackAction.PAUSE -> dispatch(false, null)
        else -> Unit
    }
}

/**
 * Live Pause availability for the viewer's channel. The runtime state describes its installed target,
 * except while the viewer's latest channel change is pending: then it describes that channel, whose
 * Pause the runtime holds. A requested channel the runtime does not track yet offers nothing.
 */
internal fun liveTransportAvailability(state: LivePauseState, installedIsRequested: Boolean): LivePauseAvailability =
    state.availability.takeIf { state.selectionPending || installedIsRequested } ?: LivePauseAvailability.NONE

/** The viewer's Play/Pause intent for live: the pending channel change's, else the installed player's. */
internal fun livePlayWhenReady(state: LivePauseState, playerPlayWhenReady: Boolean): Boolean =
    if (state.selectionPending) !state.pending else playerPlayWhenReady && !state.pending

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
    withdrawn: () -> Boolean = { false },
) {
    val result = startPlayback()
    kotlinx.coroutines.currentCoroutineContext().ensureActive()
    if (!isCurrent()) return
    // The viewer's Stop came after this selection: nothing started and nothing failed.
    if (result?.isStarted != true && withdrawn()) return
    if (result?.isStarted != true) onRejected()
    kotlinx.coroutines.currentCoroutineContext().ensureActive()
    if (isCurrent()) onResolved(result)
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
@Composable
fun VideoPlayerScreen(
    videoPlayerViewModel: VideoPlayerViewModel = koinViewModel(),
    selection: ChannelSelectionStore = koinInject(),
    lastPlayedChannelStore: LastPlayedChannelStore = koinInject(),
    settingsStore: PlayerSettingsStore = koinInject(),
    channelsVm: ChannelsViewModel,
    session: TvheadendSession = koinInject(),
    channelId: ChannelId,
    channelName: String,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
    playbackRuntime: at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime = koinInject(),
    contentAllowed: Boolean = true,
    startupTarget: at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget? = null,
    onStartupOutcome: (at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget, at.bernhardberger.tvhplayer.core.MainStartupPlaybackOutcome) -> Boolean = { _, _ -> false },
    notices: NoticeCenter = koinInject(),
    uiSettingsStore: at.bernhardberger.tvhplayer.settings.UiSettingsStore = koinInject(),
    onOpenConnectionSettings: () -> Unit = {},
    connectionUiState: at.bernhardberger.tvhplayer.core.ConnectionUiState? = null,
) {
    val scope = rememberCoroutineScope()
    val layerState = rememberLivePlayerLayerState()
    val playerClose = rememberPlayerClose(onClose)
    // First, before any playback effect: a user stop since the last intent closes the screen.
    val screenEntry = rememberPlayerEntry(playbackRuntime::enterPlayerScreen, playerClose)
    val stopAndClose: () -> Unit = {
        scope.launch {
            stopPlaybackAndClose(
                stopPlayback = videoPlayerViewModel::stop,
                closePlayer = playerClose::close,
            )
        }
    }
    CloseOnSessionStop(playbackRuntime.sessionStops, playerClose)

    val settings by settingsStore.playerSettings.collectAsStateWithLifecycle(
        initialValue = PlayerSettings()
    )

    val connState by videoPlayerViewModel.connectionState.collectAsStateWithLifecycle()
    val playbackState by videoPlayerViewModel.playbackState.collectAsStateWithLifecycle()
    val activeTarget by videoPlayerViewModel.activeTarget.collectAsStateWithLifecycle()
    val videoPresentation by playbackRuntime.videoPresentation.collectAsStateWithLifecycle()
    val playingLiveChannelId by
        videoPlayerViewModel.playingLiveChannelId.collectAsStateWithLifecycle()
    val livePlaybackObservation by
        videoPlayerViewModel.livePlaybackObservation.collectAsStateWithLifecycle()
    val activeLivePlayback = livePlaybackObservation as? LivePlaybackObservation.Active
    val sdkTimeshiftState = activeLivePlayback?.timeshiftState ?: LiveTimeshiftState.Unavailable
    val subscriptionFailure = activeLivePlayback?.subscriptionIssue
    val diagnostics by videoPlayerViewModel.diagnostics.collectAsStateWithLifecycle()
    val channelScope by channelsVm.scope.collectAsStateWithLifecycle()
    // One source: the rows come from the scope whose observation their saved logos use.
    val channels = channelScope.scope.visibleChannels
    val channelsTitle = channelScope.scope.let { scope -> scope.tags.firstOrNull { it.id == scope.activeTagId } }
        ?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.all_channels)
    val observation by videoPlayerViewModel.observation.collectAsStateWithLifecycle()
    val currentSession = observation.currentSession
    // Opened before its session was playable: the entry start below tunes once one is current.
    val serverWait = rememberLivePlayerServerWait(observation, connectionUiState, playerClose, onReconnect, onOpenConnectionSettings)
    val channelBrowser = remember(currentSession) { PlayerChannelBrowserState() }
    val dvrEntries = observation.dvrEntries()
    val recordingChannelIds = remember(dvrEntries) { activeRecordingChannelIds(dvrEntries) }
    val canModifyRecordings = currentSession != null
    val orderedChannelIds = remember(channels) { channels.map { it.id } }
    val channelNumbers = remember(channels) {
        channels.associate { it.id to it.visibleChannelNumber }
    }
    val channelNumberDigits = remember(orderedChannelIds, channelNumbers) {
        ChannelNavigation.entryMaxDigits(orderedChannelIds, channelNumbers)
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
    val programDetails = remember { ProgramDetailsState() }
    var infoRailChannelId by remember { mutableStateOf<ChannelId?>(null) }
    val recentChannelIds by lastPlayedChannelStore.recentChannelIds.collectAsStateWithLifecycle(emptyList())
    // The control last focused in the controls, which closing Info returns to.
    var lastChromeAction by remember { mutableStateOf<String?>(null) }
    var infoReturnAction by remember { mutableStateOf<String?>(null) }
    var infoPendingAction by remember { mutableStateOf<ProgrammeAction?>(null) }
    var infoMutation by remember { mutableStateOf<DvrMutationAction?>(null) }
    var infoMutationTarget by remember { mutableStateOf<ProgrammeRecordingTarget?>(null) }
    var infoMutationObservation by remember { mutableStateOf<SessionObservation?>(null) }
    val dvrMutationActions = remember(session) { DvrMutationActions(session.dvrRepository) }
    val rootFocus = remember { FocusRequester() }

    var currentChannelId by remember { mutableStateOf(channelId) }
    var currentChannelName by remember { mutableStateOf(channelName) }
    val confirmedPlayingChannelId = playingLiveChannelId.takeIf {
        it == currentChannelId && playbackState.presented
    }
    val player = remember { videoPlayerViewModel.getPlayerInstance() }
    val playWhenReady by rememberPlaybackIntent(player)
    val livePauseState by playbackRuntime.livePause.collectAsStateWithLifecycle()
    val livePauseNotice by playbackRuntime.livePauseNotice.collectAsStateWithLifecycle()
    val livePauseAvailability = liveTransportAvailability(livePauseState, playingLiveChannelId == currentChannelId)
    val channelIndicator = channelPlaybackIndicator(
        currentChannelId,
        playingLiveChannelId?.let { at.bernhardberger.tvhplayer.playback.AppPlaybackTarget.Live(it) },
        playbackState,
        playWhenReady,
    )
    val timelineState = rememberLiveTimelinePresentationState(player)
    var sampledTimeshiftState by remember { mutableStateOf(AppTimeshiftState()) }
    var liveRequestToken by remember { mutableLongStateOf(0L) }
    // The command token a live start began with, until it pauses: its known timing does not end it.
    var liveStartToken by remember { mutableStateOf<Long?>(0L) }
    // Requests reset presentation synchronously, before their first frame. This effect only samples;
    // it must not reinstate live start after a Pause already accepted for this request.
    LaunchedEffect(videoPlayerViewModel, liveRequestToken) {
        val requestToken = liveRequestToken
        val requestedTarget = at.bernhardberger.tvhplayer.playback.AppPlaybackTarget.Live(currentChannelId)
        while (true) {
            timelineState.sampleTimeshiftPresentation {
                // Admission may leave the old channel installed. Its paused state and coordinates
                // do not belong to this request, including across a suspended sample.
                if (requestToken != liveRequestToken || playbackRuntime.activeTarget.value != requestedTarget) AppTimeshiftState()
                else videoPlayerViewModel.sampleTimeshiftPresentation().takeIf {
                    requestToken == liveRequestToken && playbackRuntime.activeTarget.value == requestedTarget
                } ?: AppTimeshiftState()
            }?.let {
                if (requestToken == liveRequestToken) {
                    sampledTimeshiftState = it.copy(
                        displayLiveEdgeMs = timelineState.displayLiveEdgeMs,
                        historyStartTimeline = timelineState.historyStartTimeline,
                    )
                    if (it.paused) liveStartToken = null
                }
            }
            delay(250L)
        }
    }
    // Plays the live edge since its start: no pause or timeshift command yet.
    val undisturbedLiveStart = liveStartToken == timeshiftCommandToken
    val livePaused = !livePlayWhenReady(livePauseState, playWhenReady)
    // The bar's schedule and the info bar's programme both follow it (nowDescribesPlayback).
    val liveStart = undisturbedLiveStart && !livePaused
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
    var initialPlaybackSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    var initialPlaybackEpoch by remember { mutableStateOf<Long?>(null) }
    var initialPlaybackIntent by remember { mutableStateOf<Long?>(null) }
    var initialPlaybackToken by remember { mutableLongStateOf(-1L) }
    var initialStartupTarget by remember { mutableStateOf<at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget?>(null) }
    var admittedStartupTarget by remember { mutableStateOf(startupTarget) }
    var initialPlaybackAdopted by remember { mutableStateOf(false) }
    var startupOutcomeDelivered by remember { mutableStateOf(false) }
    var adoptedPlaying by remember { mutableStateOf(false) }
    // The viewing intent of the selection the next live start plays (entry, then each tune).
    var liveIntent by remember { mutableStateOf(screenEntry) }
    // The request token a key's own tune owns (started at once, or settling after a CH+/-
    // burst); the entry/resume effect below starts every other request.
    var directLiveToken by remember { mutableLongStateOf(NO_DIRECT_LIVE_TOKEN) }
    // The selection that key's start plays, and its running start.
    var directLiveSelection by remember { mutableStateOf<LivePlaybackSelection?>(null) }
    // The one outstanding live start (a key's, or the effect's that a key supersedes).
    val directStart = remember { mutableStateOf<Job?>(null) }
    val zapPacer = remember { ChannelZapPacer() }
    val pendingZap = remember { mutableStateOf<Job?>(null) }
    var requestedChannelFailed by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val timeshiftUnavailableText = stringResource(R.string.timeshift_unavailable)
    val timeshiftReconnectLiveText = stringResource(R.string.timeshift_reconnect_live)
    val timeshiftExpiredText = stringResource(R.string.timeshift_target_expired)
    val timeshiftReplacedText = stringResource(R.string.timeshift_target_replaced)
    val timeshiftUncertainText = stringResource(R.string.timeshift_seek_uncertain)
    val pauseUnavailableChannelText = stringResource(R.string.pause_unavailable_channel)
    val pauseTimeshiftOffText = stringResource(R.string.pause_timeshift_off)
    LaunchedEffect(timelineState, effectiveTimeshiftState.timeline) {
        timelineState.updateTimeline(effectiveTimeshiftState.timeline)
    }
    val visibleSeekPreview = timelineState.previewForTimeline(effectiveTimeshiftState.timeline)
    val nowSec = timelineState.nowEpochSec
    var aspectRatio by remember { mutableStateOf(settings.aspectRatio) }

    fun dispatchTimeshiftCommand(
        rollbackPlayWhenReady: Boolean? = null,
        command: suspend () -> TimeshiftCommandResult?,
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
                interruptionOwned = videoPlayerViewModel.hasAudioInterruption,
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

    fun showPauseUnavailable(reason: String) {
        timelineState.applyFeedback(timelineState.beginFeedbackOperation(), reason)
    }

    fun showPauseUnavailable() = showPauseUnavailable(
        if (livePauseAvailability == LivePauseAvailability.OFF) pauseTimeshiftOffText
        else pauseUnavailableChannelText,
    )

    LaunchedEffect(livePauseNotice) {
        val notice = livePauseNotice ?: return@LaunchedEffect
        // A pause pressed while timeshift was starting was dropped: no grant for this channel.
        showPauseUnavailable(pauseUnavailableChannelText)
        layerState.chrome.peekBanner()
        playbackRuntime.consumeLivePauseNotice(notice)
    }

    fun dispatchPlaybackAction(action: MediaPlaybackAction) {
        dispatchTimeshiftPlaybackAction(
            action = action,
            // A pending channel change toggles the viewer's intent for it, not the installed player.
            playWhenReady = livePlayWhenReady(playbackRuntime.livePause.value, player.playWhenReady),
        ) { resume, rollback ->
            dispatchTimeshiftCommand(rollbackPlayWhenReady = rollback) {
                if (resume) videoPlayerViewModel.resumeTimeshift() else videoPlayerViewModel.pauseTimeshiftPlayback()
            }
        }
    }

    LaunchedEffect(settings.aspectRatio) {
        aspectRatio = settings.aspectRatio
    }

    // Only Stats for nerds needs full diagnostics; badges use the selected tracks.
    val diagnosticsWanted = layerState.statsVisible
    DisposableEffect(diagnosticsWanted) {
        videoPlayerViewModel.setDiagnosticsEnabled(diagnosticsWanted)
        onDispose {
            if (diagnosticsWanted) videoPlayerViewModel.setDiagnosticsEnabled(false)
        }
    }

    var lastPlayedChannelId by remember { mutableStateOf<ChannelId?>(null) }
    // Counts ON_START: each start runs the entry/resume effect below again, even when a
    // stopped window never composed the stop in between (its frame clock is paused).
    var screenStarts by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    screenActive = true
                    screenStarts += 1
                }
                Lifecycle.Event.ON_STOP -> {
                    screenActive = false
                    // Leaving drops a half-typed number: it never tunes on return.
                    channelNumberInput = ""
                    // Leaving cancels the settling and the running start now, before any
                    // recomposition: an admitted start never installs behind a stopped screen.
                    // Resume restarts the current request through the effect below.
                    pendingZap.value?.cancel()
                    pendingZap.value = null
                    directStart.value?.cancel()
                    directLiveToken = NO_DIRECT_LIVE_TOKEN
                    lastPlayedChannelId = null
                    // Its selection too, even when the start never reached the runtime; the
                    // restart registers it again.
                    liveIntent?.let(playbackRuntime::abandonLiveSelection)
                    // A Banner never outlives the stop, nor does its timer.
                    layerState.chrome.hideBanner()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        // A closed screen cancels its settling and start: nothing starts its selection now.
        onDispose { liveIntent?.let(playbackRuntime::abandonLiveSelection) }
    }

    suspend fun retireCancelledStartup(selection: LivePlaybackSelection, intent: Long, epoch: Long? = null) {
        // Cleanup must finish after this route/effect is removed; the runtime serializes and fences it.
        withContext(NonCancellable) {
            val installedEpoch = epoch ?: playbackRuntime.installedLiveTargetEpoch(selection, intent)
            if (installedEpoch != null) playbackRuntime.stopAfterLoss(selection, installedEpoch, intent)
        }
    }

    LaunchedEffect(startupTarget) {
        val target = startupTarget ?: return@LaunchedEffect
        try {
            awaitCancellation()
        } finally {
            val selection = initialPlaybackSelection
            val intent = initialPlaybackIntent
            if (initialStartupTarget == target && !initialPlaybackAdopted && !startupOutcomeDelivered &&
                selection != null && intent != null
            ) retireCancelledStartup(selection, intent, initialPlaybackEpoch)
        }
    }

    /** Starts [requestToken]'s selection; a newer request supersedes its outcome. */
    suspend fun runLiveStart(
        playbackSelection: LivePlaybackSelection,
        requestToken: Long,
        requestIntent: Long?,
    ) {
        val startupOwner = startupTarget
        if (startupOwner != null) {
            initialPlaybackSelection = playbackSelection
            initialPlaybackIntent = requestIntent
            initialPlaybackEpoch = null
            initialPlaybackToken = requestToken
            initialStartupTarget = startupOwner
            initialPlaybackAdopted = false
            startupOutcomeDelivered = false
        }
        try {
            startInitialLivePlayback(
                startPlayback = {
                    profileTrace("P49:zap:tune-start:token:$requestToken") { }
                    videoPlayerViewModel.playChannel(playbackSelection, requestIntent)
                },
                isCurrent = { requestToken == liveRequestToken },
                onRejected = {
                    requestedChannelFailed = true
                    lastPlayedChannelId = null
                    videoPlayerViewModel.stopAfterLoss()
                },
                onResolved = { result ->
                    initialPlaybackResolved = true
                    if (result?.isStarted == true) {
                        lastPlayedChannelId = currentChannelId
                        requestedChannelFailed = false
                        initialPlaybackSelection = playbackSelection
                        initialPlaybackIntent = requestIntent
                        initialPlaybackEpoch = requestIntent?.let { playbackRuntime.installedLiveTargetEpoch(playbackSelection, it) }
                        initialPlaybackToken = requestToken
                        initialStartupTarget = startupOwner
                        initialPlaybackAdopted = false
                        adoptedPlaying = false
                        if (startupOwner != null && (initialPlaybackEpoch == null ||
                                playbackRuntime.liveTargetPresentation(playbackSelection, requestIntent) == null)
                        ) requestedChannelFailed = true
                    }
                    requestedLiveSelection = null
                },
                withdrawn = { requestIntent != null && playbackRuntime.isPlaybackIntentStopped(requestIntent) },
            )
        } finally {
            // A commit can precede cancellation before onResolved obtains its receipt.
            if (startupOwner != null && !kotlinx.coroutines.currentCoroutineContext().isActive && requestIntent != null) {
                retireCancelledStartup(playbackSelection, requestIntent)
            }
        }
    }

    LaunchedEffect(
        screenActive,
        screenStarts,
        currentChannelId,
        authorizedLiveSelection,
        requestedLiveSelection,
        liveRequestToken,
        directLiveToken,
        startupTarget,
        // A session arriving without the channel decides an entry that waited for it.
        currentSession,
    ) {
        if (screenEntry == null) return@LaunchedEffect
        // Leaving withdraws an entry still waiting: a session arriving during the exit starts nothing.
        if (playerClose.requested) return@LaunchedEffect
        // ON_STOP cancelled the outstanding start; the next ON_START restarts it here.
        if (!screenActive) return@LaunchedEffect
        if (startupTarget != null && admittedStartupTarget != startupTarget) {
            // A new launch can reuse this exact route; its adoption owns fresh viewing intent.
            liveIntent = playbackRuntime.notePlaybackIntent()
            admittedStartupTarget = startupTarget
        }

        if (lastPlayedChannelId == currentChannelId &&
            (startupTarget == null || initialStartupTarget == startupTarget)
        ) return@LaunchedEffect
        if (directLiveToken == liveRequestToken) {
            if (pendingZap.value != null || directLiveSelection == authorizedLiveSelection) return@LaunchedEffect
            // The session changed under the key's start: this effect restarts it.
            directStart.value?.cancel()
            directLiveToken = NO_DIRECT_LIVE_TOKEN
            return@LaunchedEffect
        }
        val adoptionPresentation = if (startupTarget != null) {
            authorizedLiveSelection?.let(playbackRuntime::liveTargetPresentation)
        } else null
        if (
            if (startupTarget != null) adoptionPresentation != null else
                playingLiveChannelId == currentChannelId &&
                    playbackState !is AppPlaybackState.Idle && playbackState !is AppPlaybackState.Failed
        ) {
            lastPlayedChannelId = currentChannelId
            requestedLiveSelection = null
            initialPlaybackResolved = true
            // A warm entry adopted the playing channel: it serves this screen's intent.
            liveIntent?.let(playbackRuntime::notePlaybackIntentServed)
            initialPlaybackSelection = authorizedLiveSelection
            initialPlaybackIntent = liveIntent
            initialPlaybackEpoch = adoptionPresentation?.epoch
            initialPlaybackToken = liveRequestToken
            initialStartupTarget = startupTarget
            initialPlaybackAdopted = true
            startupOutcomeDelivered = false
            adoptedPlaying = adoptionPresentation?.let { it.playing && (it.visible || it.audioOnly) } == true
            return@LaunchedEffect
        }

        if (playbackState is AppPlaybackState.Failed && requestedLiveSelection == null) {
            return@LaunchedEffect
        }
        if (initialPlaybackResolved && requestedLiveSelection == null) return@LaunchedEffect
        val playbackSelection = authorizedLiveSelection ?: run {
            // A current session without the channel: it is gone after sync, or never existed.
            if (currentSession != null) requestedChannelFailed = true
            return@LaunchedEffect
        }
        // This start supersedes a key's running one and is superseded by the next key's.
        val superseded = directStart.value
        directStart.value = coroutineContext.job
        superseded?.cancelAndJoin()
        runLiveStart(playbackSelection, liveRequestToken, liveIntent)
    }

    LaunchedEffect(confirmedPlayingChannelId) {
        if (confirmedPlayingChannelId != null) {
            lastPlayedChannelStore.setChannelId(currentChannelId)
        }
    }

    fun openInfo(returnTo: String? = null) {
        programDetails.reset()
        infoRailChannelId = null
        infoPendingAction = null
        infoMutation = null
        infoReturnAction = returnTo ?: lastChromeAction.takeIf { layerState.chrome.controlsVisible }
        restoreInfoFocus = false
        layerState.openInfo()
    }

    fun dismissRecordingDialog() {
        infoPendingAction = null
        infoMutation = null
        layerState.dismissRecordingConfirmation()
        programDetails.focusContent()
    }

    fun closeInfo() {
        infoPendingAction = null
        infoMutation = null
        layerState.dismissRecordingConfirmation()
        layerState.closeInfo(returnToRail = infoRailChannelId != null)
        infoRailChannelId?.let {
            selectedId = it
            infoRailChannelId = null
            return
        }
        restoreInfoFocus = infoReturnAction == null
    }

    /**
     * An options key opened the options: commit a pending seek, drop a number entry,
     * settle the recording state of a replaced Info panel and focus the page as the
     * gear does.
     */
    fun optionsOpenedFromKey(infoWasOpen: Boolean) {
        if (timelineState.seekPending) timelineState.commitPendingSeek()
        channelNumberInput = ""
        if (infoWasOpen) { infoPendingAction = null; infoMutation = null }
        restoreOptionsFocus = false
    }

    // Confirmed watch history, newest first, restricted to the current channel scope.
    val recentChannels = remember(recentChannelIds, channels, currentChannelId) {
        recentChannelIds.filter { it != currentChannelId }
            .mapNotNull { id -> channels.firstOrNull { it.id == id } }.take(RecentChannelCount)
    }

    fun openChannelDrawer() {
        // The rail reopens on its remembered card; preview that card from the first frame. A card
        // gone from its row reopens the row on its first card, as the rail itself does.
        val row = channelBrowser.row.takeIf { it == PlayerChannelRow.ON_NOW || recentChannels.isNotEmpty() }
            ?: PlayerChannelRow.ON_NOW
        val rowChannels = if (row == PlayerChannelRow.RECENT) recentChannels else channels
        selectedId = channelBrowser.collection(row).focusedId?.takeIf { id -> rowChannels.any { it.id == id } }
            ?: recentChannels.firstOrNull()?.id?.takeIf { row == PlayerChannelRow.RECENT } ?: browsingFocusChannelId(
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
            expiredText = timeshiftExpiredText,
            replacedText = timeshiftReplacedText,
            uncertainText = timeshiftUncertainText,
            seekContent = videoPlayerViewModel::seekTimeshift,
        )
    }

    /**
     * Runs a key's start now, before this key's recomposition or controls reveal. It
     * supersedes the running start: that one is cancelled, and this start waits for its
     * cancellation to complete, so [directStart] alone owns every outstanding start.
     */
    fun launchDirectStart(playbackSelection: LivePlaybackSelection, requestToken: Long, requestIntent: Long?) {
        directLiveSelection = playbackSelection
        val superseded = directStart.value
        superseded?.cancel()
        directStart.value = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            superseded?.join()
            runLiveStart(playbackSelection, requestToken, requestIntent)
        }
    }

    /**
     * A settled CH+/- burst tunes its last channel with the state current when it settles.
     * A request no longer current, a screen that stopped meanwhile or a selection the
     * session no longer authorizes goes back to the entry/resume effect above.
     */
    fun startSettledZap(requestToken: Long) {
        pendingZap.value = null
        if (directLiveToken != requestToken) return
        val playbackSelection = authorizedLiveSelection
        if (requestToken != liveRequestToken || !screenActive || playbackSelection == null) {
            directLiveToken = NO_DIRECT_LIVE_TOKEN
            // Unauthorized now: nothing starts it until the effect restarts it (and registers it).
            if (requestToken == liveRequestToken && playbackSelection == null) {
                liveIntent?.let(playbackRuntime::abandonLiveSelection)
            }
            return
        }
        launchDirectStart(playbackSelection, requestToken, liveIntent)
    }
    val latestStartSettledZap by rememberUpdatedState<(Long) -> Unit> { startSettledZap(it) }

    /**
     * Tunes [channel] from the key that chose it: at once, or [settleMs] after a CH+/- repeat
     * so a burst opens no tuner per press. The newest request supersedes every earlier one.
     */
    fun tuneChannel(channel: Channel, settleMs: Long = 0L): Boolean {
        profileTrace("P49:zap:tune-channel:settle:$settleMs:time:${SystemClock.uptimeMillis()}") { }
        val channelId = channel.id
        channelNumberInput = ""
        selection.setSelected(channelId)
        selectedId = channelId

        val pickAction = channelPickAction(confirmedPlayingChannelId, channelId)
        if (pickAction == ChannelPickAction.CLOSE_DRAWER) {
            layerState.dismissChannelDrawer()
            return true
        }

        val playbackSelection = currentLivePlaybackSelection(observation, channelId)
            ?: return true
        timeshiftCommandToken += 1L
        timelineState.invalidateForSourceChange()
        liveRequestToken += 1L
        sampledTimeshiftState = AppTimeshiftState()
        liveStartToken = timeshiftCommandToken
        // Accepted now, played under this one intent (at once or after the burst settles):
        // a Stop from before loses, a Stop from after withdraws the delayed start.
        liveIntent = playbackRuntime.notePlaybackIntent()
        val requestIntent = liveIntent
        // A Pause from now until this channel starts is held for it, not sent to the one playing.
        requestIntent?.let(playbackRuntime::noteLiveSelection)
        requestedChannelFailed = false
        lastPlayedChannelId = null
        requestedLiveSelection = playbackSelection
        currentChannelId = channelId
        currentChannelName = channel.name.orEmpty()

        val requestToken = liveRequestToken
        // A closing or stopped screen leaves the request to the entry/resume effect.
        val direct = screenEntry != null && screenActive
        directLiveToken = if (direct) requestToken else NO_DIRECT_LIVE_TOKEN
        pendingZap.value = scope.scheduleChannelZap(
            pending = pendingZap.value,
            settleMs = settleMs.takeIf { direct },
        ) { settled ->
            if (settled) {
                latestStartSettledZap(requestToken)
            } else {
                launchDirectStart(playbackSelection, requestToken, requestIntent)
            }
        }
        timelineState.clearFeedback()

        layerState.onChannelTuneRequested()
        return true
    }

    fun tuneAdjacentChannel(direction: Int, keyTimeMs: Long, repeat: Boolean): Boolean {
        val adjacentId = ChannelNavigation.adjacentId(
            orderedIds = orderedChannelIds,
            currentId = currentChannelId,
            direction = direction,
        ) ?: return false

        val channel = channels.firstOrNull { it.id == adjacentId } ?: return false
        // A series' first press tunes at once; its later presses and every repeat settle, so a
        // series tunes its first and its last channel, not one tuner per key press.
        return tuneChannel(channel, settleMs = zapPacer.settleDelayMs(keyTimeMs, repeat))
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

    // The digit timer commits with the list, numbers and session current when it fires,
    // not the ones of the composition that started it.
    val channelNumberEntryReadiness = ChannelNavigation.entryReadiness(
        orderedIds = orderedChannelIds,
        channelNumbers = channelNumbers,
        enteredNumber = channelNumberInput,
        playable = { currentLivePlaybackSelection(observation, it) != null },
    )
    // Set while an entry that named no channel is still shown, after its commit.
    var channelNumberUnknown by remember { mutableStateOf(false) }
    val channelNumberTarget = remember(channelNumberInput, channels, channelNumberDigits, channelNumberUnknown) {
        val channel = ChannelNavigation.idForNumber(orderedChannelIds, channelNumbers, channelNumberInput)
            ?.let { id -> channels.firstOrNull { it.id == id } }
        when {
            channel != null -> ChannelNumberTarget.Channel(channel.name.orEmpty(), channel.icon)
            channelNumberUnknown -> ChannelNumberTarget.None
            orderedChannelIds.isEmpty() || ChannelNavigation.canExtendToNumber(
                orderedChannelIds, channelNumbers, channelNumberInput, channelNumberDigits,
            ) -> ChannelNumberTarget.Pending
            else -> ChannelNumberTarget.None
        }
    }
    val latestChannelNumberEntryReadiness by rememberUpdatedState(channelNumberEntryReadiness)
    val latestChannelNumberDigits by rememberUpdatedState(channelNumberDigits)
    val latestTuneEnteredChannel by rememberUpdatedState<() -> Unit> { tuneEnteredChannel() }
    // Uptime of the last digit or CH key not yet followed by a recomposition (0 when none).
    val zapKeyUptime = remember { longArrayOf(0L) }
    SideEffect {
        val keyUptime = zapKeyUptime[0]
        if (keyUptime != 0L) {
            zapKeyUptime[0] = 0L
            profileTrace("P49:zap:first-recomposition:age:${SystemClock.uptimeMillis() - keyUptime}") { }
        }
    }
    LaunchedEffect(channelNumberInput) {
        channelNumberUnknown = false
        if (channelNumberInput.isEmpty()) return@LaunchedEffect
        val entered = channelNumberInput
        val enteredAt = SystemClock.uptimeMillis()
        commitChannelNumberEntry(
            entryDelayMs = if (ChannelNavigation.isCompleteEntry(entered, latestChannelNumberDigits)) {
                COMPLETE_CHANNEL_NUMBER_TIMEOUT_MS
            } else {
                CHANNEL_NUMBER_TIMEOUT_MS
            },
            readiness = snapshotFlow { latestChannelNumberEntryReadiness },
        ) { atTimer, settled ->
            profileTrace(
                "P49:zap:digit-commit:timer:$atTimer:commit:$settled:waited:${SystemClock.uptimeMillis() - enteredAt}",
            ) { }
            if (!screenActive) {
                // Stopped before this effect was cancelled: leaving dropped the entry.
                channelNumberInput = ""
            } else if (settled == ChannelNumberEntryReadiness.NOT_READY) {
                // Still not playable at the bound: drop the entry, keep the selection.
                profileTrace("P49:zap:digit-dropped:not-ready") { }
                channelNumberInput = ""
            } else if (settled == ChannelNumberEntryReadiness.UNKNOWN) {
                // The entry names no channel: say so long enough to read, then drop it. A further
                // digit or Back replaces or clears it meanwhile.
                channelNumberUnknown = true
                delay(NO_CHANNEL_HOLD_MS)
                channelNumberInput = ""
            } else {
                latestTuneEnteredChannel()
            }
        }
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
        programmeWindow(effectiveTimeshiftState) { observation.eventAt(currentChannelId, it) }
    } else null
    val displayedWindow = if (at.bernhardberger.tvhplayer.BuildConfig.PROGRAMME_WINDOW_B) {
        visibleSeekPreview?.let { preview ->
            programmeWindow(effectiveTimeshiftState, preview.target, preview.mappingTimeline) {
                observation.eventAt(currentChannelId, it)
            }
        } ?: committedWindow.takeIf { visibleSeekPreview == null }
    } else null
    // A slot window (no programme at the position) is an axis only: it has no Next line.
    val infoNextEvent = displayedNextEvent(visibleSeekPreview != null, displayedWindow,
        effectiveTimeshiftState, nextEvent, liveStart, nowEvent) { observation.nextEvent(currentChannelId, it) }
    val currentChannelNumber = remember(channels, currentChannelId) {
        ChannelNavigation.numberForId(
            orderedChannelIds,
            channelNumbers,
            currentChannelId,
        )
    }
    val infoEvent = displayedProgrammeEvent(visibleSeekPreview != null, displayedWindow,
        committedWindow, effectiveTimeshiftState, nowEvent, liveStart)
    val detailsChannelId = infoRailChannelId ?: currentChannelId
    val detailsChannel = channels.firstOrNull { it.id == detailsChannelId }
    val detailsBaseEvent = if (infoRailChannelId == null) infoEvent else channelsVm.nowEvent(detailsChannelId, nowSec)
    val actionableInfoEvent = currentProgrammeEvent(observation, programDetails.opened ?: detailsBaseEvent)
    val currentRecording = remember(observation, actionableInfoEvent?.id) {
        actionableInfoEvent?.let { observation.dvrEntryForEvent(it) }
    }
    val currentRecordingTarget = currentSession?.let { capability ->
        actionableInfoEvent?.programmeRecordingTarget(capability)
    }
    val latestRecordingTarget by rememberUpdatedState(currentRecordingTarget)
    LaunchedEffect(programDetails.selectionVersion, currentRecordingTarget) {
        infoPendingAction = null
        infoMutation = null
        layerState.dismissRecordingConfirmation()
    }
    val infoActions = actionableInfoEvent?.let {
        programmeActions(it, nowSec, currentRecording, canModifyRecordings = canModifyRecordings)
    }.orEmpty()
    val recordActionEligible = ProgrammeAction.RECORD in infoActions
    val currentSubscriptionFailure = subscriptionFailure.takeIf { playingLiveChannelId == currentChannelId }
        ?: (playbackState as? AppPlaybackState.Failed)?.subscriptionIssue
    val currentLiveInterrupted = liveInterrupted(
        serverStopped = activeLivePlayback?.serverStopped == true,
        playingLiveChannelId = playingLiveChannelId,
        shownChannelId = currentChannelId,
    )
    val currentPlaybackFailed = requestedChannelFailed || playbackState is AppPlaybackState.Failed
    val statusPresentation = playbackStatusPresentation(
        connectionAvailable = connState is ConnectionState.Connected,
        playbackStarting = playbackState is AppPlaybackState.Starting,
        playbackRecovering = playbackState is AppPlaybackState.Recovering,
        playbackPlaying = playbackState.presented,
        playbackFailed = currentPlaybackFailed || currentSubscriptionFailure != null,
        liveInterrupted = currentLiveInterrupted,
    )
    val interruptionMessageShown = liveInterruptionMessageShown(
        liveInterrupted = currentLiveInterrupted,
        subscriptionIssuePresent = currentSubscriptionFailure != null,
        playbackFailed = currentPlaybackFailed,
    )
    val recoveryVisible = screenActive &&
        (serverWait.presentation != null || statusPresentation == PlaybackStatusPresentation.FULL_RECOVERY)
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
            layerState.onChannelUnavailable()
        }
    }
    fun currentPlayerForegroundContext() =
        layerState.foregroundContext(
            numberEntryVisible = channelNumberInput.isNotEmpty(),
            recoveryVisible = recoveryVisible,
            terminalErrorVisible = false,
            seekPreviewPhase = timelineState.seekPreviewPhase(layerState.chrome.controlsVisible),
        )
    val foregroundContext = currentPlayerForegroundContext()
    val confirmationVisible = foregroundContext.confirmationVisible
    val infoVisible = foregroundContext.infoVisible
    val showDrawer = foregroundContext.channelDrawerVisible
    val seekPreviewPhase = foregroundContext.seekPreviewPhase
    val foregroundLayer = playerForegroundLayer(foregroundContext)
    val autoHideEligible = playerControlsAutoHideEligible(
        PlayerAutoHideContext(
            controlsVisible = layerState.chrome.controlsVisible,
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
        layerState.chrome.updateAutoHideEligibility(autoHideEligible)
    }
    val chromeState = playerStateCell(paused = livePaused)
    val glanceTracks = trackGlance(rememberPlayerTracks(player))
    val initialPresentation = if (startupTarget == null) null else initialPlaybackSelection?.let {
        playbackRuntime.liveTargetPresentation(it, liveIntent)
    }
    val startupOutcome = at.bernhardberger.tvhplayer.core.mainStartupPlaybackOutcome(
        expectedEpoch = initialPlaybackEpoch.takeIf {
            initialPlaybackToken == liveRequestToken && initialStartupTarget == startupTarget
        },
        presentation = initialPresentation,
        recovery = requestedChannelFailed || currentSubscriptionFailure != null ||
            (initialPlaybackSelection != null && currentSession !== initialPlaybackSelection?.currentSession) ||
            currentLiveInterrupted || recoveryVisible || channelUnavailable ||
            playbackState is AppPlaybackState.Recovering || playbackState is AppPlaybackState.Failed,
        adoptedPlaying = adoptedPlaying,
    )
    val latestStartupOutcome by rememberUpdatedState(onStartupOutcome)
    LaunchedEffect(startupTarget, startupOutcome) {
        val target = startupTarget ?: return@LaunchedEffect
        if (target.matchesPlayer(currentChannelId, channelName) && startupOutcome != null) {
            // Only the current owner can hand recovery to this player; a stale callback cannot.
            startupOutcomeDelivered = latestStartupOutcome(target, startupOutcome)
        }
    }
    // The Banner stays while tuning and hides after the first frame of this tune; only a
    // service whose tracks are positively audio-only lets its playing state stand in.
    val bannerFramePresented = at.bernhardberger.tvhplayer.core.bannerFramePresented(
        tuneConfirmed = confirmedPlayingChannelId != null,
        videoFrameVisible = videoPresentation.visible,
        playing = playbackState is AppPlaybackState.Playing,
        audioOnly = glanceTracks.audioOnly,
    )
    val busyBlocked = channelUnavailable || statusPresentation == PlaybackStatusPresentation.FULL_RECOVERY
    val busyTarget = AppPlaybackTarget.Live(currentChannelId)
    val busyKey = busyTarget to (currentSession to liveRequestToken)
    var tunePresented by remember(busyKey) { mutableStateOf(false) }
    LaunchedEffect(busyKey, bannerFramePresented) {
        if (bannerFramePresented) tunePresented = true
    }
    val tuningEligible = tuningStatusEligible(
        playbackState, !livePaused, activeTarget, busyTarget,
        screenActive && currentSession != null, busyBlocked, tunePresented || bannerFramePresented,
    )
    val tuningVisible by rememberBusyVisible(busyKey, tuningEligible, PlayerBusyStatus.TUNING)
    val bufferingEligible = bufferingStatusEligible(
        playbackState, !livePaused, activeTarget, busyTarget,
        screenActive && currentSession != null && activeLivePlayback != null,
        foregroundBlocked = busyBlocked || tuningEligible,
    )
    val bufferingVisible by rememberBusyVisible(busyKey, bufferingEligible, PlayerBusyStatus.BUFFERING)
    val busyStatus = when {
        tuningVisible -> PlayerBusyStatus.TUNING
        bufferingVisible -> PlayerBusyStatus.BUFFERING
        else -> null
    }
    val compactTuning = tuningVisible && statusPresentation == PlaybackStatusPresentation.COMPACT_TUNING
    SideEffect {
        layerState.chrome.onBannerFramePresented(bannerFramePresented)
        layerState.chrome.holdBanner(livePaused)
    }
    val airingEventId = nowEvent?.id?.value
    var watchedProgramme by remember { mutableStateOf<Pair<ChannelId, Long?>?>(null) }
    LaunchedEffect(currentChannelId, airingEventId) {
        val previous = watchedProgramme
        watchedProgramme = currentChannelId to airingEventId
        if (previous?.first != currentChannelId || confirmedPlayingChannelId == null || !screenActive) {
            return@LaunchedEffect
        }
        val atLiveEdge = !effectiveTimeshiftState.available ||
            (effectiveTimeshiftState.timingKnown && timeshiftPositionPresentation(effectiveTimeshiftState).atLiveEdge)
        if (at.bernhardberger.tvhplayer.core.programmeChangeBannerDue(
                previousEventId = previous.second,
                currentEventId = airingEventId,
                chromeHidden = foregroundLayer == PlayerForegroundLayer.NONE,
                atLiveEdge = atLiveEdge,
                seekPreview = visibleSeekPreview != null,
            )
        ) layerState.onProgrammeChanged()
    }

    fun confirmInfoRecording() {
        val target = infoMutationTarget ?: return
        val openingObservation = infoMutationObservation
        val request = currentDvrMutation(infoMutation, openingObservation, session.observation.value)
        val noticeContext = notices.context()
        val selectionVersion = programDetails.selectionVersion
        infoPendingAction = null
        layerState.dismissRecordingConfirmation()
        programDetails.focusContent()
        if (request == null) { infoMutation = null; return }
        // Keep the guard set for the whole input dispatch, including synchronous repository results.
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val preferred = if (request is DvrMutationAction.CreateProgramme)
                    uiSettingsStore.settings.first().recordingProfileId else null
                val current = session.observation.value
                val currentRequest = currentDvrMutation(request, openingObservation, current) ?: return@launch
                val configured = if (currentRequest is DvrMutationAction.CreateProgramme)
                    currentRequest.copy(configId = at.bernhardberger.tvhplayer.core.chooseDvrConfig(
                        current.currentDvrConfigurations(), preferred)) else currentRequest
                val feedback = dvrMutationActions.execute(configured)
                notices.postDvrFailure(configured, feedback, noticeContext)
            } finally {
                if (detailsRecordingResultIsCurrent(request, infoMutation, target, latestRecordingTarget,
                        layerState.infoOpen, selectionVersion == programDetails.selectionVersion) &&
                    session.observation.value.currentSession === target.currentSession) {
                    infoMutation = null
                }
            }
        }
    }

    fun activateInfoRecording(action: ProgrammeAction = ProgrammeAction.RECORD) {
        if (infoMutation != null || action !in infoActions) return
        val target = currentRecordingTarget ?: return
        val mutation = when (action) {
            ProgrammeAction.RECORD -> DvrMutationAction.CreateProgramme(target, null)
            ProgrammeAction.STOP_RECORDING -> currentRecording?.let { DvrMutationAction.Stop(target.currentSession, it.id) }
            ProgrammeAction.CANCEL_RECORDING -> currentRecording?.let { DvrMutationAction.Cancel(target.currentSession, it.id) }
            else -> null
        } ?: return
        infoMutation = mutation
        infoMutationTarget = target
        infoMutationObservation = observation
        if (action != ProgrammeAction.RECORD) {
            infoPendingAction = action
            layerState.showRecordingConfirmation()
        } else confirmInfoRecording()
    }

    LaunchedEffect(infoPendingAction, observation) {
        if (infoPendingAction != null && (infoPendingAction !in infoActions ||
                currentDvrMutation(infoMutation, infoMutationObservation, observation) == null)) dismissRecordingDialog()
    }

    fun dispatchRecoveryRetry() {
        when (recoveryUiModel.retryCommand) {
            PlaybackRetryCommand.RECONNECT -> onReconnect()
            PlaybackRetryCommand.RETRY_LIVE -> videoPlayerViewModel.retryLiveNow(viewerRetry = true)
            PlaybackRetryCommand.RESUME_RECORDING,
            PlaybackRetryCommand.NONE -> Unit
        }
    }

    LaunchedEffect(connState, screenActive) {
        // Waiting for the first session with nothing playing is no loss; an active target still is.
        if (!screenActive || serverWait.presentation != null && playbackRuntime.activeTarget.value == null ||
            screenEntry == null) return@LaunchedEffect

        when (connState) {
            is ConnectionState.Connected -> {
                if (connectionLost) {
                    connectionLost = false
                    layerState.showControls()

                    videoPlayerViewModel.retryLiveNow(viewerRetry = false)
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
                    videoPlayerViewModel.stopAfterLoss()
                    lastPlayedChannelId = null
                }
            }
        }
    }

    // Playback/recovery effects above stay mounted. No chrome exists under startup until its first
    // picture is delivered; the passive Banner then shows while the startup screen fades, as on an
    // ordinary entry. Focus and Back wait for the reveal to end; startup consumes its keys.
    if (!contentAllowed &&
        !(startupOutcomeDelivered && startupTarget != null && initialStartupTarget == startupTarget)) return
    if (contentAllowed) PlayerRootFocusEffect(foregroundLayer, rootFocus)

    val handlePlaybackBack: () -> Unit = handle@{
        // Trial: Back first undoes a step inside the program details.
        if (playerForegroundLayer(currentPlayerForegroundContext()) == PlayerForegroundLayer.INFO &&
            programDetails.back()
        ) return@handle
        when (
            playerBackAction(
                seekPreviewPhase = timelineState.seekPreviewPhase(layerState.chrome.controlsVisible),
                surface = PlayerSurface.LIVE,
                foregroundLayer = playerForegroundLayer(currentPlayerForegroundContext()),
                // The Banner as drawn: a failed tune replaces it with the centre message.
                bannerVisible = layerState.chrome.bannerVisible && !channelUnavailable,
            )
        ) {
            PlayerBackAction.DISMISS_CONFIRMATION -> dismissRecordingDialog()
            PlayerBackAction.CLOSE_INFO ->
                closeInfo()
            PlayerBackAction.RESTORE_AND_CLOSE_QUICK_LIST -> {
                layerState.quickList.restoreStart()
                layerState.closeQuickList()
            }
            PlayerBackAction.RETURN_TO_OPTIONS_ROOT ->
                layerState.showOptionsPage(PlaybackOptionsPage.ROOT)
            PlayerBackAction.CLOSE_OPTIONS -> {
                layerState.closeOptions()
                restoreOptionsFocus = true
            }
            PlayerBackAction.CLEAR_NUMBER_ENTRY -> channelNumberInput = ""
            PlayerBackAction.CLOSE_CHANNEL_DRAWER -> layerState.dismissChannelDrawer()
            PlayerBackAction.CLOSE_PLAYER -> playerClose.close()
            PlayerBackAction.HIDE_BANNER -> layerState.chrome.hideBanner()
            PlayerBackAction.CANCEL_PENDING_SEEK -> timelineState.cancelPendingSeek()
            PlayerBackAction.DISMISS_SEEK_FEEDBACK ->
                timelineState.dismissDispatchedFeedback()
            PlayerBackAction.HIDE_CONTROLS -> layerState.chrome.hideControls()
            PlayerBackAction.HIDE_STATS -> layerState.updateStatsVisibility(false)
        }
    }
    if (contentAllowed) PlayerBackHandler(handlePlaybackBack)

    // Until the displayed channel is confirmed playing, tracks and diagnostics may still
    // describe the previous channel, so no badge is shown.
    val badgesConfirmed = confirmedPlayingChannelId != null
    val chromeBadges: @Composable () -> List<GlanceBadge> = {
        if (badgesConfirmed) playerGlanceBadges(diagnostics, glanceTracks) else emptyList()
    }
    val unavailableTitle = stringResource(R.string.player_info_unavailable_title)
    val controlsEvent = infoEvent.takeUnless { channelUnavailable }
    // During a quick step the Next line follows the step's target programme, so the block keeps its height.
    val controlsNext = infoNextEvent.takeUnless { channelUnavailable }
    val chromeContent = PlayerChromeContent(
        clock = formatClock(nowSec),
        state = chromeState,
        info = liveInfoBarData(
            resources = androidx.compose.ui.platform.LocalContext.current.resources,
            channelNumber = currentChannelNumber,
            channelName = currentChannelName,
            event = controlsEvent,
            next = controlsNext,
            nextScheduled = controlsNext?.let { observation.dvrEntryForEvent(it) }?.state ==
                at.bernhardberger.tvheadend.sdk.core.DvrEntryState.SCHEDULED,
            nowSec = nowSec,
            unavailableTitle = unavailableTitle,
        ),
        recordingNow = currentChannelId in recordingChannelIds,
        badges = chromeBadges(),
        picon = currentChannel?.icon,
        // The info bar's artwork tile.
        artwork = controlsEvent?.image,
        channelId = currentChannelId,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (serverWait.consumesKey(event, layerState)) return@onPreviewKeyEvent true
                val keyCode = event.nativeKeyEvent.keyCode
                if (layerState.infoOpen) {
                    if (event.key == Key.Back) {
                        // Rebuilding details can detach the KeyUp target. A fresh Back down is
                        // still a new press, not a repeat of that unfinished revealing cycle.
                        if (event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN && event.nativeKeyEvent.repeatCount == 0) {
                            layerState.beginOpeningKeyCycle(keyCode)
                            handlePlaybackBack()
                        }
                        if (event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_UP) layerState.endOpeningKeyCycle(keyCode)
                        return@onPreviewKeyEvent true
                    }
                    if (playbackSuppressesRevealingKey(layerState.revealingKeyCode, keyCode)) {
                        if (event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_UP) layerState.endOpeningKeyCycle(keyCode)
                        return@onPreviewKeyEvent true
                    }
                    // Details owns navigation; no playback or options key
                    // (including Left-as-Back in the other panels) reaches the player underneath.
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> false
                        else -> true
                    }
                }
                if (handlePlayerStopKeyWithoutTarget(
                        event = event,
                        hasActiveTarget = playbackRuntime.activeTarget.value != null,
                        beginKeyCycle = layerState::beginOpeningKeyCycle,
                        stopAndClose = stopAndClose,
                    )
                ) {
                    return@onPreviewKeyEvent true
                }
                val keyContext = PlayerKeyContext(
                    surface = PlayerSurface.LIVE,
                    controlsVisible = layerState.chrome.controlsVisible,
                    seekbarFocused = false,
                    timeshiftAvailable = effectiveTimeshiftState.available,
                    optionsOpen = layerState.optionsPage != null,
                    statsOpen = layerState.statsVisible,
                    infoOpen = layerState.infoOpen,
                    drawerOpen = showDrawer,
                    confirmationOpen = foregroundLayer == PlayerForegroundLayer.CONFIRMATION,
                    livePause = livePauseAvailability,
                )
                layerState.handleOverlayKey(
                    event = event,
                    keyContext = keyContext,
                    foregroundLayer = foregroundLayer,
                    quickListAvailable = playingLiveChannelId == currentChannelId && !channelUnavailable &&
                        playbackRuntime.isQuickListTargetCurrent(videoPresentation.epoch),
                    onBack = handlePlaybackBack,
                    onOptionsOpened = ::optionsOpenedFromKey,
                )?.let { return@onPreviewKeyEvent it }

                val mediaAction = mediaPlaybackAction(
                    keyCode = event.nativeKeyEvent.keyCode,
                    playKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    pauseKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    toggleKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    repeatCount = event.nativeKeyEvent.repeatCount,
                )
                if (mediaAction != MediaPlaybackAction.NONE) {
                    when (liveMediaKeyAction(effectiveTimeshiftState.available, livePauseAvailability)) {
                        LiveMediaKeyAction.DISPATCH -> {
                            layerState.beginOpeningKeyCycle(keyCode)
                            dispatchPlaybackAction(mediaAction)
                            layerState.showControls()
                            return@onPreviewKeyEvent true
                        }
                        LiveMediaKeyAction.REVEAL_WITH_REASON -> {
                            layerState.beginOpeningKeyCycle(keyCode)
                            showPauseUnavailable()
                            layerState.showControls()
                            return@onPreviewKeyEvent true
                        }
                        LiveMediaKeyAction.PASS_THROUGH -> Unit
                    }
                }

                ChannelNavigation.digitForKeyCode(event.nativeKeyEvent.keyCode)?.let { digit ->
                    zapKeyUptime[0] = profileZapKey(event.nativeKeyEvent)
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        channelNumberInput = ChannelNavigation.appendDigit(
                            channelNumberInput, digit, channelNumberDigits,
                        )
                    }
                    return@onPreviewKeyEvent true
                }

                ChannelNavigation.directionForKeyCode(event.nativeKeyEvent.keyCode)?.let { direction ->
                    zapKeyUptime[0] = profileZapKey(event.nativeKeyEvent)
                    channelNumberInput = ""
                    when (playbackChannelKeyAction(
                        railBrowsesChannelKeys = foregroundLayer == PlayerForegroundLayer.CHANNEL_DRAWER,
                    )) {
                        ChannelKeyAction.TUNE ->
                            return@onPreviewKeyEvent tuneAdjacentChannel(direction, event.nativeKeyEvent.eventTime,
                                repeat = event.nativeKeyEvent.repeatCount > 0)
                        ChannelKeyAction.BROWSE_LIST -> Unit
                    }
                }

                if (channelNumberInput.isNotEmpty()) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> {
                            layerState.beginOpeningKeyCycle(keyCode)
                            // Not playable yet: the entry stays pending for the digit timer.
                            channelNumberEntryReadiness == ChannelNumberEntryReadiness.NOT_READY ||
                                tuneEnteredChannel()
                        }
                        else -> false
                    }
                }

                if (showDrawer) {
                    return@onPreviewKeyEvent false
                }

                val keyAction = playerKeyAction(keyContext, keyCode = keyCode)
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
                        if (timelineState.seekPending) timelineState.commitPendingSeek()
                        // Either direction peeks controls without activating the newly focused action.
                        restoreInfoFocus = false
                        layerState.showControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE -> {
                        dispatchPlaybackAction(MediaPlaybackAction.TOGGLE)
                        // Pausing shows the Banner, not the controls, so Left/Right keep stepping.
                        layerState.chrome.peekBanner()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.PEEK_BANNER -> {
                        if (livePauseUnavailable(livePauseAvailability)) showPauseUnavailable()
                        layerState.chrome.peekBanner()
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
                    // Options keys are decided before the overlays above can claim them.
                    PlayerKeyAction.OPEN_OPTIONS -> Unit
                    PlayerKeyAction.SEEK_BACK -> {
                        queueTimeshiftSeek(-seekStepMs(event.nativeKeyEvent.repeatCount))
                        // A quick step on the hidden player shows its preview in the Banner.
                        layerState.chrome.peekBanner()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.SEEK_FORWARD -> {
                        queueTimeshiftSeek(seekStepMs(event.nativeKeyEvent.repeatCount))
                        layerState.chrome.peekBanner()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.HIDE_CONTROLS -> {
                        layerState.chrome.hideControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.CLOSE_PLAYER -> {
                        playerClose.close()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.PASS_THROUGH -> Unit
                }
                false
            }
            .focusRequester(rootFocus)
            .livePlayerRoot(stringResource(R.string.player_live_tv_surface), contentAllowed)
    ) { ProvideArtworkSession(currentSession, observation) {
        // A quick step's preview plays inside the Banner; the pending or dispatched seek is its own layer.
        val bannerSeekPreview = visibleSeekPreview?.takeIf {
            foregroundLayer == PlayerForegroundLayer.PENDING_SEEK_PREVIEW ||
                foregroundLayer == PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW
        }
        val channelRailOpen = foregroundLayer == PlayerForegroundLayer.CHANNEL_DRAWER
        // Trial: Down from the action row opens the channel rows below on the playing channel, so
        // closing returns to the action it came from.
        val openRailFromActions: () -> Unit = {
            layerState.beginOpeningKeyCycle(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
            if (foregroundLayer != PlayerForegroundLayer.CHANNEL_DRAWER) openChannelDrawer()
        }
        PlayerPage(
            railExpanded = layerState.channelRailPresented,
            nowPlaying = {
                NowPlayingStrip(currentChannelNumber?.toString(), currentChannelName, currentChannel?.icon,
                    channelsVm.nowEvent(currentChannelId, nowSec), nowSec,
                    playbackIndicator = channelIndicator)
            },
            details = (detailsChannel to detailsBaseEvent).takeIf {
                layerState.infoOpen && (foregroundLayer == PlayerForegroundLayer.INFO ||
                    foregroundLayer == PlayerForegroundLayer.CONFIRMATION)
            },
            // Held OK on a rail card: the programme opens as a dialog over the rail.
            dialog = infoRailChannelId != null,
            railHeader = if (programDetails.scheduleEntry && layerState.infoOpen) ({
                val channel = channels.firstOrNull { it.id == (infoRailChannelId ?: selectedId) }
                ProgrammeScheduleHeader(channelTitleText(channel?.visibleChannelNumber, channel?.name.orEmpty()))
            }) else null,
            player = { keepControls ->
        PlayerChrome(
            mode = playerChromeMode(
                controls = foregroundLayer == PlayerForegroundLayer.CONTROLS || channelRailOpen || keepControls,
                // A pending or dispatched step holds the Banner up after its own timer ran out.
                banner = !channelUnavailable && (bannerSeekPreview != null ||
                    (layerState.chrome.bannerVisible && foregroundLayer == PlayerForegroundLayer.NONE)),
                stepPreview = bannerSeekPreview != null,
            ),
            content = chromeContent,
            timeline = PlayerChromeTimeline.Live(
                timeshift = visibleSeekPreview?.let {
                    projectedTimeshiftState(effectiveTimeshiftState, it.decision.targetMs)
                } ?: effectiveTimeshiftState,
                nowSec = nowSec,
                programme = nowEvent.takeUnless { channelUnavailable },
                committedTimeshift = effectiveTimeshiftState,
                timeshiftExpected = effectiveTimeshiftState.available ||
                    livePauseAvailability == at.bernhardberger.tvhplayer.playback.LivePauseAvailability.STARTING ||
                    livePauseAvailability == at.bernhardberger.tvhplayer.playback.LivePauseAvailability.READY,
                committedWindow = committedWindow,
                programmeWindow = displayedWindow,
                previewing = visibleSeekPreview != null,
                step = bannerSeekPreview?.decision,
                stepDeltaMs = visibleSeekPreview?.decision?.deltaMs,
                liveStart = liveStart,
                liveAvailable = !channelUnavailable,
                feedback = timelineState.feedback,
                feedbackIsError = timelineState.feedbackIsError,
                motionKey = currentChannelId to liveRequestToken,
                tuning = compactTuning,
            ),
            actions = PlayerChromeActions(
                active = layerState.chrome.controlsVisible && layerState.optionsPage == null,
                paused = !livePlayWhenReady(livePauseState, playWhenReady),
                livePause = livePauseAvailability,
                restoreFocus = when {
                    infoReturnAction != null && !layerState.infoOpen -> infoReturnAction
                    restoreInfoFocus -> PlayerIdentityCardTag
                    restoreOptionsFocus -> "player-settings"
                    else -> layerState.restoreChannelAction
                },
            ),
            entry = layerState.chrome.controlsEntry,
            panelOpen = layerState.optionsPage != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            onTogglePause = {
                dispatchPlaybackAction(MediaPlaybackAction.TOGGLE)
            },
            onPauseUnavailable = ::showPauseUnavailable,
            onSeek = { deltaMs ->
                queueTimeshiftSeek(deltaMs)
            },
            onCommitSeek = timelineState::commitPendingSeek,
            onStop = stopAndClose,
            onInfo = {
                openInfo()
            },
            onOptions = {
                restoreOptionsFocus = false
                layerState.openOptions()
            },
            onInteraction = layerState.chrome::onUserInteraction,
            onActionFocused = { lastChromeAction = it; layerState.onActionFocused(it) },
            onFocusRestored = {
                infoReturnAction = null
                restoreInfoFocus = false
                restoreOptionsFocus = false
                layerState.onChannelActionRestored()
            },
            onDownFromActions = openRailFromActions,
            downHint = stringResource(R.string.nav_channels),
            onChannelCardFocusChanged = layerState.chrome::onChannelCardFocusChanged,
            decorationCoversControls = channelRailOpen,
            controlsDecoration = { _, controls ->
                QuickZapPresentation(
                    // Keep the departing rail expanded throughout its vertical step.
                    expanded = layerState.channelRailPresented,
                    // The rows' saved logos are those of the observation their scope was resolved from.
                    channelContent = { ProvideArtworkSession(currentSession, channelScope.observation) {
                        PlayerChannelBrowser(
                            state = channelBrowser,
                            channels = channels,
                            recentChannels = recentChannels,
                            playingChannelId = currentChannelId,
                            recordingChannelIds = recordingChannelIds,
                            nowSec = nowSec,
                            nowEvent = { id -> observation.eventAt(id, kotlin.time.Instant.fromEpochSeconds(nowSec)) },
                            onFocusChannel = { selectedId = it },
                            onPickChannel = { tuneChannel(it) },
                            onClose = { keyCode ->
                                if (keyCode != null) layerState.beginOpeningKeyCycle(keyCode)
                                layerState.dismissChannelDrawer()
                            },
                            onOpenDetails = { channel, _, keyCode ->
                                keyCode?.let(layerState::beginOpeningKeyCycle)
                                infoRailChannelId = channel.id
                                programDetails.reset()
                                infoPendingAction = null
                                infoMutation = null
                                infoReturnAction = null
                                restoreInfoFocus = false
                                layerState.openInfo()
                            },
                            active = foregroundLayer == PlayerForegroundLayer.CHANNEL_DRAWER,
                            channelsTitle = channelsTitle,
                        )
                    } },
                    trayBleeds = recentChannels.isNotEmpty(),
                    header = { PlayerChannelBrowserHint(channelBrowser, recentChannels.isNotEmpty(), channelsTitle) },
                    preview = {
                        val previewChannel = channels.firstOrNull { it.id == selectedId }
                        QuickZapTrayPreview(previewChannel,
                            previewChannel?.let { observation.eventAt(it.id, kotlin.time.Instant.fromEpochSeconds(nowSec)) },
                            previewChannel?.let { observation.nextEvent(it.id, kotlin.time.Instant.fromEpochSeconds(nowSec)) },
                            nowSec,
                            Modifier.padding(horizontal = at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin),
                            scrolling = channelBrowser.scrolling)
                    },
                    controls = controls,
                )
            },
        )
        // Full recovery and the unavailable message replace even an exiting ring.
        if (!busyBlocked) PlayerBusyIndicator(busyStatus, Modifier.align(Alignment.Center), cover = videoPresentation)
        // With the chrome hidden and no Banner, only the paused chip remains.
        PlayerHiddenStatusChip(
            state = hiddenChipState(
                chromeState, hidden = foregroundLayer == PlayerForegroundLayer.NONE && !layerState.chrome.bannerVisible,
                available = !channelUnavailable,
            ),
            end = liveBarEnd(liveBehindMs(effectiveTimeshiftState, compactTuning, liveStart), end = null),
            modifier = Modifier.align(Alignment.BottomStart).padding(playerHiddenChipPadding()),
        )

            },
        ) { (shownChannel, shownEvent) ->
            val shownChannelId = shownChannel?.id ?: shownEvent?.channelId ?: currentChannelId
            val detailsIdentity = channelTitleText(shownChannel?.visibleChannelNumber, shownChannel?.name.orEmpty())
            val watch: (() -> Unit)? = shownChannel?.takeIf { it.id != currentChannelId }?.let { channel ->
                {
                    infoRailChannelId = null
                    infoPendingAction = null
                    infoMutation = null
                    infoReturnAction = null
                    layerState.closeInfo()
                    tuneChannel(channel)
                }
            }
            LiveProgrammeInfoOverlay(
                event = shownEvent,
                channelIdentity = detailsIdentity,
                channelName = shownChannel?.name.orEmpty(),
                recordingScheduled = currentRecording != null,
                canRecord = recordActionEligible,
                recordingState = LiveInfoRecordingState.Idle,
                confirmationVisible = false,
                restoreRecordFocus = false,
                onRecord = { activateInfoRecording() },
                onRecordingActivate = { activateInfoRecording() },
                onRecordingDismiss = ::dismissRecordingDialog,
                onClose = ::closeInfo,
                onWatch = watch,
                onUp = {
                    if (infoRailChannelId == null) {
                        layerState.beginOpeningKeyCycle(AndroidKeyEvent.KEYCODE_DPAD_UP)
                        closeInfo()
                    }
                },
                details = { recordFocus, firstFocus ->
                    val shown = shownEvent ?: return@LiveProgrammeInfoOverlay
                    val schedule = remember(observation, shownChannelId, shown.id) {
                        channelSchedule(shown, { after -> observation.nextEvent(shownChannelId, after) })
                    }
                    ProgramDetails(
                        state = programDetails,
                        current = shown,
                        schedule = schedule,
                        nowSec = nowSec,
                        channelIdentity = detailsIdentity,
                        tile = { event, tileModifier ->
                            ProgrammeHero(
                                image = event.image,
                                channelId = shownChannelId,
                                channelNumber = shownChannel?.visibleChannelNumber?.toString().orEmpty(),
                                picon = shownChannel?.icon,
                                modifier = tileModifier,
                                recordingBadge = observation.dvrEntryForEvent(event)?.state == DvrEntryState.RECORDING,
                            )
                        },
                        recordingFor = { event -> observation.dvrEntryForEvent(event) },
                        canModifyRecordings = canModifyRecordings,
                        recordFocus = recordFocus,
                        firstFocus = firstFocus,
                        onAction = { action -> if (action == ProgrammeAction.WATCH) watch?.invoke() else activateInfoRecording(action) },
                        busy = infoMutation != null && infoPendingAction == null,
                        showWatch = watch != null,
                        onClose = {
                            layerState.beginOpeningKeyCycle(AndroidKeyEvent.KEYCODE_DPAD_UP)
                            closeInfo()
                        },
                    )
                },
            )
            infoPendingAction?.let { action ->
                ConfirmProgrammeActionDialog(action, actionableInfoEvent?.title.orEmpty(),
                    entries = infoMutationTarget?.eventId?.let { infoMutationObservation?.dvrEntriesForEvent(it) },
                    configurations = observation.currentDvrConfigurations(),
                    channelName = shownChannel?.name.orEmpty(),
                    dateAndTime = at.bernhardberger.tvhplayer.ui.common.formatRecordingSchedule(actionableInfoEvent?.start?.epochSeconds),
                    onDismiss = ::dismissRecordingDialog, onConfirm = ::confirmInfoRecording,
                    onPreviewKeyEvent = { event ->
                        val code = event.nativeKeyEvent.keyCode
                        when {
                            playbackSuppressesRevealingKey(layerState.revealingKeyCode, code) -> {
                                if (event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_UP) layerState.endOpeningKeyCycle(code)
                                true
                            }
                            // The confirmation owns Back through its card fade.
                            event.key == Key.Back -> false
                            event.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter) -> false
                            else -> true
                        }
                    })
            }
        }

        PlaybackStatsVisibility(
            visible = foregroundLayer == PlayerForegroundLayer.STATS,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 36.dp, end = 48.dp),
        ) {
            PlaybackStatsOverlay(
                diagnostics = diagnostics,
                aspectRatio = aspectRatio,
                timeshiftState = effectiveTimeshiftState,
                frameRate = playbackRuntime.frameRate,
            )
        }

        PlayerPanelVisibility(
            value = layerState.optionsPage?.let { it to layerState.optionsQuickList },
            // A quick list applies its focused row when it leaves; that must not wait.
            closesAtOnce = { (_, quick) -> quick },
        ) { (page, _) ->
            val audioAutomatic by playbackRuntime.audioAutomatic.collectAsStateWithLifecycle()
            PlaybackOptionsSheet(
                page = page,
                player = player,
                audioAutomatic = audioAutomatic,
                onAutomaticAudio = { playbackRuntime.useAutomaticAudio() },
                tracksResolving =
                    playbackState is AppPlaybackState.Starting ||
                        playbackState is AppPlaybackState.Recovering,
                aspectRatio = aspectRatio,
                statsVisible = layerState.statsVisible,
                onPageChange = layerState::showOptionsPage,
                quickList = layerState.quickList.takeIf { layerState.optionsQuickList },
                onQuickListClose = layerState::closeQuickList,
                quickListTargetEpoch = videoPresentation.epoch,
                quickListAvailable = !channelUnavailable && playingLiveChannelId == currentChannelId,
                isQuickListTargetCurrent = { epoch ->
                    playbackRuntime.isQuickListTargetCurrent(epoch) &&
                        videoPlayerViewModel.playingLiveChannelId.value == currentChannelId
                },
                onQuickAudioSelection = { epoch, override -> playbackRuntime.selectQuickListAudio(epoch, override) },
                onAspectRatioChange = { mode ->
                    aspectRatio = mode
                    scope.launch { settingsStore.setAspectRatio(mode) }
                },
                onStatsVisibleChange = layerState::updateStatsVisibility,
            )
        }

        ChannelNumberOverlay(
            number = channelNumberInput.takeIf { foregroundLayer == PlayerForegroundLayer.NUMBER_ENTRY }.orEmpty(),
            target = channelNumberTarget,
            maxDigits = channelNumberDigits,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 56.dp, top = 48.dp),
        )

        val unavailableShown = channelUnavailable && foregroundLayer in setOf(PlayerForegroundLayer.CONTROLS, PlayerForegroundLayer.NONE, PlayerForegroundLayer.CHANNEL_DRAWER)
        val failedState = playbackState as? AppPlaybackState.Failed
        AnimatedVisibility(
            visible = unavailableShown,
            enter = fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)),
            exit = fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate)),
            modifier = Modifier.align(Alignment.Center).semanticsWhileShown(unavailableShown),
            label = "player-channel-unavailable",
        ) {
            // The exit keeps the message it showed while the failure state clears.
            val (message, failureDetail) = rememberLastShown(
                if (unavailableShown) {
                    stringResource(
                        currentSubscriptionFailure?.messageResource()
                            ?: if (interruptionMessageShown) R.string.player_playback_interrupted else R.string.player_playback_failed,
                    ) to
                        listOfNotNull(
                            failedState?.recoveryReason?.toString(),
                            failedState?.playerErrorCode,
                            failedState?.playerErrorClassification,
                            failedState?.targetResult?.toString(),
                        ).joinToString(" · ")
                } else {
                    null
                },
            ) ?: return@AnimatedVisibility
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = TvOverlayBoxAlpha), MaterialTheme.shapes.large)
                    .padding(24.dp)
                    .testTag("player-channel-unavailable"),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (failureDetail.isNotEmpty()) {
                    Text(
                        text = failureDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        LivePlayerServerWait(serverWait, currentChannel, currentChannelName, rootFocus)
        TvRecoveryOverlay(
            visible = foregroundLayer == PlayerForegroundLayer.RECOVERY && serverWait.presentation == null,
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
                playerClose::close
            },
            secondaryActionLabel = if (recoveryHasRetry) {
                stringResource(R.string.close)
            } else {
                null
            },
            onSecondaryAction = if (!recoveryHasRetry) {
                null
            } else {
                playerClose::close
            },
        )
    } }
}

private fun SessionObservation.dvrEntries(): List<DvrEntry> =
    dvrSnapshotForDisplay?.entries.orEmpty().takeIf {
        dvrSnapshotAuthority == RetainedMetadataAuthority.CURRENT
    }.orEmpty()

/** Trial: how many recent channels the row below the top identity offers. */
private const val RecentChannelCount = 3
