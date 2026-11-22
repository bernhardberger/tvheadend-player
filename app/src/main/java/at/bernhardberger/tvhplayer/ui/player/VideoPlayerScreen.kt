package at.bernhardberger.tvhplayer.ui.player
import at.bernhardberger.tvhplayer.ui.screens.postRecordUnavailable

import at.bernhardberger.tvhplayer.ui.screens.currentDvrConfigurations

import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy

import at.bernhardberger.tvhplayer.ui.components.channelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.rememberPlaybackIntent

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.core.GlanceBadgeKind
import at.bernhardberger.tvhplayer.core.recordingIsRunning
import at.bernhardberger.tvhplayer.core.programmeArtwork
import at.bernhardberger.tvhplayer.core.deviceFaultDetail
import at.bernhardberger.tvhplayer.core.livePauseUnavailable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.MainStartupPlaybackOutcome
import at.bernhardberger.tvhplayer.core.mainStartupPlaybackOutcome
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ChannelNumberEntryReadiness
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.core.activeRecordingChannelIds
import at.bernhardberger.tvhplayer.core.runningRecordingForEvent
import at.bernhardberger.tvhplayer.core.ChannelKeyAction
import at.bernhardberger.tvhplayer.core.ChannelPickAction
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.MediaPlaybackAction
import at.bernhardberger.tvhplayer.core.PlaybackStatusPresentation
import at.bernhardberger.tvhplayer.core.PlaybackRetryCommand
import at.bernhardberger.tvhplayer.core.PlayerBackAction
import at.bernhardberger.tvhplayer.core.PlayerBackKeyCycle
import at.bernhardberger.tvhplayer.core.playerBackHoldAllowed
import at.bernhardberger.tvhplayer.core.PlayerAutoHideContext
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.channelPickAction
import at.bernhardberger.tvhplayer.core.mediaPlaybackAction
import at.bernhardberger.tvhplayer.core.liveInterrupted
import at.bernhardberger.tvhplayer.core.liveChannelCondition
import at.bernhardberger.tvhplayer.core.liveRetryRequestFailure
import at.bernhardberger.tvhplayer.core.livePlayerAlert
import at.bernhardberger.tvhplayer.core.playbackStatusPresentation
import at.bernhardberger.tvhplayer.core.playbackChannelKeyAction
import at.bernhardberger.tvhplayer.core.playerControlsAutoHideEligible
import at.bernhardberger.tvhplayer.core.playerBackAction
import at.bernhardberger.tvhplayer.core.playerChromeLayer
import at.bernhardberger.tvhplayer.core.playerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.LiveMediaKeyAction
import at.bernhardberger.tvhplayer.core.liveMediaKeyAction
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.playback.LivePauseState
import at.bernhardberger.tvhplayer.core.livePlayWhenReady
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.playerKeyAction
import at.bernhardberger.tvhplayer.core.playerKeyActionStartsOpeningCycle
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.programmeDetailsActions
import at.bernhardberger.tvhplayer.core.chooseDvrConfig
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import at.bernhardberger.tvhplayer.core.livePauseAccepted
import at.bernhardberger.tvhplayer.core.playbackSeekStepMs
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandDisposition
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
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
import at.bernhardberger.tvhplayer.core.recordingWasRemoved
import at.bernhardberger.tvhplayer.core.liveBarEnd
import at.bernhardberger.tvhplayer.core.playerStateCell
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.PauseUnavailableReason
import at.bernhardberger.tvhplayer.ui.screens.postDvrFailure
import at.bernhardberger.tvhplayer.ui.screens.nextRecordingForEvent
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import at.bernhardberger.tvhplayer.ui.screens.currentDvrMutation
import at.bernhardberger.tvhplayer.ui.screens.launchMutation
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.screens.settings.activeProfile
import at.bernhardberger.tvhplayer.ui.screens.settings.accountName
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

// An outgoing player can remain composed during the root's route transition.
internal val LocalSessionChannelStepAllowed = androidx.compose.runtime.compositionLocalOf { true }

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
    targetAspect: TargetAspect,
    channelsVm: ChannelsViewModel,
    session: TvheadendSession = koinInject(),
    channelId: ChannelId,
    channelName: String,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
    playbackRuntime: at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime = koinInject(),
    contentAllowed: Boolean = true,
    startupTarget: at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget? = null,
    freshStart: Boolean = false,
    startupChannelStartAtMillis: Long? = null,
    onStartupOutcome: (at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget, at.bernhardberger.tvhplayer.core.MainStartupPlaybackOutcome) -> Boolean = { _, _ -> false },
    notices: NoticeCenter = koinInject(),
    uiSettingsStore: at.bernhardberger.tvhplayer.settings.UiSettingsStore = koinInject(),
    onOpenConnectionSettings: () -> Unit = {},
    onShowGuide: (ChannelId, EpgEventEntry?) -> Unit = { _, _ -> },
    onPlayRecording: ((at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection) -> Unit)? = null,
    connectionUiState: at.bernhardberger.tvhplayer.core.ConnectionUiState? = null,
    guideEntryRequestId: Long? = null,
    consumeGuideEntry: () -> Boolean = { false },
    statsStore: at.bernhardberger.tvhplayer.di.PlayerStatsStore = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val statsLevel by statsStore.level.collectAsStateWithLifecycle()
    val layerState = rememberLivePlayerLayerState()
    val livePlayerOwner = remember { Any() }
    val playerClose = rememberPlayerClose {
        playbackRuntime.onLivePlayerClosed(livePlayerOwner)
        onClose()
    }
    // First, before any playback effect: a user stop since the last intent closes the screen.
    val screenEntry = rememberPlayerEntry({
        playbackRuntime.enterPlayerScreen(livePlayerOwner, appInitiated = startupTarget != null)
    }, playerClose)
    DisposableEffect(playbackRuntime) {
        onDispose { playbackRuntime.onLivePlayerClosed(livePlayerOwner) }
    }
    val stopAndClose: () -> Unit = {
        scope.launch {
            stopPlaybackAndClose(
                stopPlayback = videoPlayerViewModel::stop,
                closePlayer = playerClose::close,
            )
        }
    }
    CloseOnSessionStop(playbackRuntime.sessionStops, playerClose)

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
    val channelScope by channelsVm.scope.collectAsStateWithLifecycle()
    // One source: the rows come from the scope whose observation their saved logos use.
    val channels = channelScope.scope.visibleChannels
    @Composable
    fun channelsTitle() = channelScope.scope.let { scope -> scope.tags.firstOrNull { it.id == scope.activeTagId } }
        ?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.all_channels)
    val observation by videoPlayerViewModel.observation.collectAsStateWithLifecycle()
    val currentSession = observation.currentSession
    val channelBrowser = remember(currentSession) { PlayerChannelBrowserState() }
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
    var timeshiftCommandToken by remember { mutableLongStateOf(0L) }
    var restoreToLiveAfterReconnect by remember { mutableStateOf(false) }
    val remoteKeys = remember { LivePlayerRemoteKeyState() }
    val dvrMutationActions = remember(session) { DvrMutationActions(session.dvrRepository) }
    val rootFocus = remember { FocusRequester() }

    var currentChannelId by remember { mutableStateOf(channelId) }
    val programmeActions = remember { mutableStateOf<DvrMutationAction?>(null) }.let { inFlight ->
        remember(currentChannelId) { LiveProgrammeActionState(inFlight) }
    }
    var currentChannelName by remember { mutableStateOf(channelName) }
    val confirmedPlayingChannelId = playingLiveChannelId.takeIf {
        it == currentChannelId && playbackState.presented
    }
    val player = remember { videoPlayerViewModel.getPlayerInstance() }
    val playWhenReady by rememberPlaybackIntent(player)
    val livePauseState by playbackRuntime.livePause.collectAsStateWithLifecycle()
    val livePauseAvailability = liveTransportAvailability(livePauseState, playingLiveChannelId == currentChannelId)
    fun channelIndicator() = channelPlaybackIndicator(
        currentChannelId,
        playingLiveChannelId?.let { at.bernhardberger.tvhplayer.playback.AppPlaybackTarget.Live(it) },
        playbackState,
        playWhenReady && !livePauseState.pausedSeekRefresh,
    )
    val timelineState = rememberLiveTimelinePresentationState(player)
    var sampledTimeshiftState by remember { mutableStateOf(AppTimeshiftState()) }
    var liveRequestToken by remember { mutableLongStateOf(0L) }
    val liveStartUndisturbed by playbackRuntime.liveStartUndisturbed.collectAsStateWithLifecycle()
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
                }
            }
            delay(250L)
        }
    }
    val livePaused = !livePlayWhenReady(livePauseState, playWhenReady)
    // The bar's schedule and the info bar's programme both follow it (nowDescribesPlayback).
    val liveStart = liveStartUndisturbed && !livePaused
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
    val initialPlayback = remember { InitialLivePlaybackState() }
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
    LaunchedEffect(timelineState, effectiveTimeshiftState.timeline) {
        timelineState.updateTimeline(effectiveTimeshiftState.timeline)
    }
    val visibleSeekPreview = timelineState.previewForTimeline(effectiveTimeshiftState.timeline)
    val nowSec = timelineState.nowEpochSec

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

    fun showPauseUnavailable(reason: PauseUnavailableReason =
        if (livePauseAvailability == LivePauseAvailability.OFF) PauseUnavailableReason.SETTINGS_OFF
        else PauseUnavailableReason.CHANNEL_UNAVAILABLE,
    ) {
        notices.post(Notice.PauseUnavailable(reason), notices.context())
    }

    @Composable
    fun LivePauseNoticeEffect() {
    val livePauseNotice by playbackRuntime.livePauseNotice.collectAsStateWithLifecycle()
    LaunchedEffect(livePauseNotice) {
        val notice = livePauseNotice ?: return@LaunchedEffect
        // A pause pressed while timeshift was starting was dropped: no grant for this channel.
        showPauseUnavailable(PauseUnavailableReason.CHANNEL_UNAVAILABLE)
        layerState.core.chrome.peekBanner()
        playbackRuntime.consumeLivePauseNotice(notice)
    }
    }
    LivePauseNoticeEffect()

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
                    if (timelineState.scrubSpeed != 0) timelineState.cancelPendingSeek()
                    screenActive = false
                    // Leaving drops a half-typed number: it never tunes on return.
                    remoteKeys.channelNumberInput = ""
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
                    layerState.core.chrome.hideBanner()
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
            val selection = initialPlayback.selection
            val intent = initialPlayback.intent
            if (initialStartupTarget == target && !initialPlaybackAdopted && !startupOutcomeDelivered &&
                selection != null && intent != null
            ) retireCancelledStartup(selection, intent, initialPlayback.epoch)
        }
    }

    /** Starts [requestToken]'s selection; a newer request supersedes its outcome. */
    val runLiveStart: suspend (LivePlaybackSelection, Long, Long?) -> Unit = { playbackSelection, requestToken, requestIntent ->
        val startupOwner = startupTarget
        initialPlayback.failedSelection = null
        if (startupOwner != null) {
            initialPlayback.selection = playbackSelection
            initialPlayback.intent = requestIntent
            initialPlayback.epoch = null
            initialPlayback.token = requestToken
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
                    initialPlayback.resolved = true
                    initialPlayback.failedSelection = playbackSelection.takeUnless { result?.isStarted == true }
                    if (result?.isStarted == true) {
                        lastPlayedChannelId = currentChannelId
                        requestedChannelFailed = false
                        initialPlayback.selection = playbackSelection
                        initialPlayback.intent = requestIntent
                        initialPlayback.epoch = requestIntent?.let {
                            playbackRuntime.installedLiveTargetEpoch(playbackSelection, it)
                        }
                        initialPlayback.token = requestToken
                        initialStartupTarget = startupOwner
                        initialPlaybackAdopted = false
                        adoptedPlaying = false
                        if (startupOwner != null && (initialPlayback.epoch == null ||
                                playbackRuntime.liveTargetPresentation(playbackSelection, requestIntent) == null)
                        ) requestedChannelFailed = true
                    }
                    requestedLiveSelection = null
                },
                withdrawn = { requestIntent != null && playbackRuntime.isPlaybackIntentStopped(requestIntent) },
            )
        } finally {
            // A commit can precede cancellation before onResolved obtains its receipt.
            if (startupOwner != null && requestIntent != null) {
                if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                    retireCancelledStartup(playbackSelection, requestIntent)
                }
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
            startupOutcomeDelivered = false
        }

        // A warm surface can still own the ended session's target. Reuse its fenced recovery
        // before the entry guards adopt it; it is a no-op for this session's current target.
        if (startupTarget == null) {
            val result = playbackRuntime.retuneAfterReconnect()
            if (result != null) {
                initialPlayback.failedSelection = authorizedLiveSelection.takeUnless { result.isStarted }
                requestedChannelFailed = !result.isStarted
                return@LaunchedEffect
            }
        }
        val restartingChannelStart = if (initialPlayback.failedSelection == authorizedLiveSelection) false else
            playbackRuntime.videoPresentation.value.channelStartAtMillis != null &&
                playbackRuntime.activeTarget.value == null && playbackRuntime.state.value !is AppPlaybackState.Starting
        if (!restartingChannelStart && lastPlayedChannelId == currentChannelId) {
            if (startupTarget == null || initialStartupTarget == startupTarget) return@LaunchedEffect
        }
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
                    playbackRuntime.state.value !is AppPlaybackState.Idle &&
                    playbackRuntime.state.value !is AppPlaybackState.Failed
        ) {
            lastPlayedChannelId = currentChannelId
            requestedLiveSelection = null
            initialPlayback.resolved = true
            // A warm entry adopted the playing channel: it serves this screen's intent.
            liveIntent?.let(playbackRuntime::notePlaybackIntentServed)
            initialPlayback.selection = authorizedLiveSelection
            initialPlayback.intent = liveIntent
            initialPlayback.epoch = adoptionPresentation?.epoch
            initialPlayback.token = liveRequestToken
            initialStartupTarget = startupTarget
            initialPlaybackAdopted = true
            if (startupTarget != null) startupOutcomeDelivered = false
            adoptedPlaying = adoptionPresentation?.let { it.playing && (it.visible || it.audioOnly) } == true
            // A reconnect the browse owner did not reach before this entry left the ended stream.
            if (startupTarget == null) withContext(NonCancellable) { playbackRuntime.retuneAfterReconnect() }
            return@LaunchedEffect
        }

        if (!restartingChannelStart && playbackState is AppPlaybackState.Failed && requestedLiveSelection == null) {
            return@LaunchedEffect
        }
        if (!restartingChannelStart && initialPlayback.resolved && requestedLiveSelection == null) return@LaunchedEffect
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

    PersistConfirmedChannel(confirmedPlayingChannelId, channelScope, observation, lastPlayedChannelStore)

    fun openInfo(fromKey: Boolean = false, returnAction: String = "player-info") {
        programmeActions.infoPinnedEventId = null
        remoteKeys.infoInitialAction = null
        remoteKeys.infoRailChannelId = null
        remoteKeys.infoReturnOrigin = returnAction
        remoteKeys.infoReturnAction = null
        layerState.core.restoreInfoFocus = false
        layerState.openInfo(fromKey)
    }

    fun prepareSchedule() {
        if (timelineState.seekPending) timelineState.commitPendingSeek()
        remoteKeys.channelNumberInput = ""
        // A zap can close the schedule past a covered Info; closeInfo() sets the target again on a real return.
        remoteKeys.infoReturnAction = null
    }

    fun dismissRecordingDialog() {
        programmeActions.confirmation = null
        layerState.dismissRecordingConfirmation()
        layerState.core.restoreControl = "player-record"
    }

    fun closeInfo() {
        programmeActions.infoPinnedEventId = null
        layerState.dismissRecordingConfirmation()
        val returnToRail = remoteKeys.infoRailChannelId != null
        layerState.closeInfo(returnToRail = returnToRail)
        remoteKeys.infoRailChannelId = null
        remoteKeys.infoReturnAction =
            if (returnToRail || !layerState.core.chrome.controlsVisible) null else remoteKeys.infoReturnOrigin
    }

    /**
     * An options key opened the options: commit a pending seek and drop a number entry.
     */
    fun optionsOpenedFromKey() {
        if (timelineState.seekPending) timelineState.commitPendingSeek()
        remoteKeys.channelNumberInput = ""
    }

    // Confirmed watch history, newest first, restricted to the current channel scope.
    @Composable
    fun rememberRecentChannels(): List<Channel> {
    val recentChannelIds by lastPlayedChannelStore.recentChannelIds.collectAsStateWithLifecycle(emptyList())
    return remember(recentChannelIds, channels, currentChannelId) {
        recentChannelIds.filter { it != currentChannelId }
            .mapNotNull { id -> channels.firstOrNull { it.id == id } }.take(RecentChannelCount)
    }
    }
    val recentChannels = rememberRecentChannels()

    fun openChannelDrawer(fromKey: Boolean = false) {
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
        layerState.openChannelDrawer(fromKey)
    }

    @Composable
    fun timeshiftSeekAction(automatic: Boolean = false): (Long) -> Unit {
        val expiredText = stringResource(R.string.timeshift_target_expired)
        val replacedText = stringResource(R.string.timeshift_target_replaced)
        val uncertainText = stringResource(R.string.timeshift_seek_uncertain)
        return { deltaMs ->
            if (!automatic) timelineState.stopScrub()
            timelineState.queueRelativeSeek(
                state = effectiveTimeshiftState,
                requestedDeltaMs = deltaMs,
                unavailableText = timeshiftUnavailableText,
                expiredText = expiredText,
                replacedText = replacedText,
                uncertainText = uncertainText,
                seekContent = videoPlayerViewModel::seekTimeshift,
            )
        }
    }
    val queueTimeshiftSeek = timeshiftSeekAction()
    val queueScrubSeek = timeshiftSeekAction(automatic = true)
    fun commitScrub() {
        timelineState.commitPendingSeek { dispatchPlaybackAction(MediaPlaybackAction.PLAY) }
    }
    @Composable
    fun LiveScrubEffect() {
        val latestQueueScrubSeek by rememberUpdatedState(queueScrubSeek)
        val latestTimeshift by rememberUpdatedState(effectiveTimeshiftState)
        LaunchedEffect(timelineState, timelineState.scrubSpeed) {
            val speed = timelineState.scrubSpeed
            if (speed == 0) return@LaunchedEffect
            var previous = withFrameNanos { it }
            var remainder = 0L
            while (timelineState.scrubSpeed == speed) {
                val frame = withFrameNanos { it }
                if (timelineState.scrubSpeed != speed) return@LaunchedEffect
                val scaled = (frame - previous) * speed + remainder
                val delta = scaled / 1_000_000L
                remainder = scaled % 1_000_000L
                previous = frame
                val state = latestTimeshift
                if (!state.available) {
                    timelineState.cancelPendingSeek()
                    return@LaunchedEffect
                }
                val target = timelineState.preview?.decision?.targetMs ?: state.positionMs
                val start = (state.bufferStartMs + 1_000L).coerceAtMost(state.liveEdgeMs)
                val next = (target + delta).coerceIn(start, state.liveEdgeMs)
                latestQueueScrubSeek(next - target)
                val reachedStart = speed < 0 && next <= start
                val reachedLiveEdge = speed > 0 && next >= state.liveEdgeMs
                if (reachedStart || reachedLiveEdge) {
                    commitScrub()
                    return@LaunchedEffect
                }
            }
        }
    }
    LiveScrubEffect()

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
    val tuneChannel = fun(channel: Channel, settleMs: Long): Boolean {
        profileTrace("P49:zap:tune-channel:settle:$settleMs:time:${SystemClock.uptimeMillis()}") { }
        val channelId = channel.id
        remoteKeys.channelNumberInput = ""
        selection.setSelected(channelId)
        selectedId = channelId

        val pickAction = channelPickAction(confirmedPlayingChannelId, channelId)
        if (pickAction == ChannelPickAction.CLOSE_DRAWER) {
            layerState.dismissChannelDrawer()
            return true
        }

        val playbackSelection = currentLivePlaybackSelection(observation, channelId)
            ?: return true
        if (programmeActions.confirmation != null) dismissRecordingDialog()
        timeshiftCommandToken += 1L
        timelineState.invalidateForSourceChange()
        liveRequestToken += 1L
        sampledTimeshiftState = AppTimeshiftState()
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
        return tuneChannel(channel, zapPacer.settleDelayMs(keyTimeMs, repeat))
    }

    val sessionChannelStepEnabled = LocalSessionChannelStepAllowed.current && screenActive &&
        !playerClose.requested && activeTarget is AppPlaybackTarget.Live
    val latestSessionChannelStep by rememberUpdatedState<(Int) -> Unit> { direction ->
        if (sessionChannelStepEnabled) {
            remoteKeys.channelNumberInput = ""
            tuneAdjacentChannel(direction, SystemClock.uptimeMillis(), repeat = false)
        }
    }
    DisposableEffect(playbackRuntime, sessionChannelStepEnabled) {
        val unregister = if (sessionChannelStepEnabled) {
            playbackRuntime.registerSessionChannelStep { latestSessionChannelStep(it) }
        } else null
        onDispose { unregister?.invoke() }
    }

    fun tuneEnteredChannel(): Boolean {
        if (remoteKeys.channelNumberInput.isEmpty()) return false

        val channelId = ChannelNavigation.idForNumber(
            orderedIds = orderedChannelIds,
            channelNumbers = channelNumbers,
            enteredNumber = remoteKeys.channelNumberInput,
        )
        val channel = channels.firstOrNull { it.id == channelId }
        remoteKeys.channelNumberCommitted = channel != null
        remoteKeys.channelNumberInput = ""

        return channel?.let { tuneChannel(it, 0L) } ?: true
    }

    // The digit timer commits with the list, numbers and session current when it fires,
    // not the ones of the composition that started it.
    @Composable
    fun channelNumberTarget(): ChannelNumberTarget {
        val channelNumberInput = remoteKeys.channelNumberInput
        val channelNumberUnknown = remoteKeys.channelNumberUnknown
        return remember(channelNumberInput, channels, channelNumberDigits, channelNumberUnknown) {
            val channel = ChannelNavigation.idForNumber(orderedChannelIds, channelNumbers, channelNumberInput)
                ?.let { id -> channels.firstOrNull { it.id == id } }
            when {
                channel != null -> ChannelNumberTarget.Channel(channel.visibleChannelNumber?.toString().orEmpty(),
                    channel.name.orEmpty(), channel.icon)
                channelNumberUnknown -> ChannelNumberTarget.None
                orderedChannelIds.isEmpty() || ChannelNavigation.canExtendToNumber(
                    orderedChannelIds, channelNumbers, channelNumberInput, channelNumberDigits,
                ) -> ChannelNumberTarget.Pending
                else -> ChannelNumberTarget.None
            }
        }
    }
    val channelNumberEntryReadiness = ChannelNavigation.entryReadiness(
        orderedIds = orderedChannelIds,
        channelNumbers = channelNumbers,
        enteredNumber = remoteKeys.channelNumberInput,
        playable = { currentLivePlaybackSelection(observation, it) != null },
    )
    @Composable
    fun ChannelNumberEntryEffect() {
    val latestChannelNumberEntryReadiness by rememberUpdatedState(channelNumberEntryReadiness)
    val latestChannelNumberDigits by rememberUpdatedState(channelNumberDigits)
    val latestTuneEnteredChannel by rememberUpdatedState<() -> Unit> { tuneEnteredChannel() }
    SideEffect {
        val keyUptime = remoteKeys.zapKeyUptime
        if (keyUptime != 0L) {
            remoteKeys.zapKeyUptime = 0L
            profileTrace("P49:zap:first-recomposition:age:${SystemClock.uptimeMillis() - keyUptime}") { }
        }
    }
    LaunchedEffect(remoteKeys.channelNumberInput) {
        remoteKeys.channelNumberUnknown = false
        val entered = remoteKeys.channelNumberInput
        if (entered.isEmpty()) return@LaunchedEffect
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
                remoteKeys.channelNumberInput = ""
            } else if (settled == ChannelNumberEntryReadiness.NOT_READY) {
                // Still not playable at the bound: drop the entry, keep the selection.
                profileTrace("P49:zap:digit-dropped:not-ready") { }
                remoteKeys.channelNumberInput = ""
            } else if (settled == ChannelNumberEntryReadiness.UNKNOWN) {
                // The entry names no channel: say so long enough to read, then drop it. A further
                // digit or Back replaces or clears it meanwhile.
                remoteKeys.channelNumberUnknown = true
                delay(NO_CHANNEL_HOLD_MS)
                remoteKeys.channelNumberInput = ""
            } else {
                latestTuneEnteredChannel()
            }
        }
    }

    }
    ChannelNumberEntryEffect()

    val nowEvent = remember(observation, currentChannelId, nowSec) {
        observation.eventAt(
            currentChannelId,
            kotlin.time.Instant.fromEpochSeconds(nowSec),
        )
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
    val infoEvent = displayedProgrammeEvent(visibleSeekPreview != null, displayedWindow,
        committedWindow, effectiveTimeshiftState, nowEvent, liveStart)

    fun recordActions(
        programme: EpgEventEntry,
        recording: DvrEntry?,
        observation: SessionObservation,
    ): List<BrowseContextAction> {
        val canModify = observation.dvrState is DvrRepositoryState.Current &&
            (observation.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
        return programmeDetailsActions(programme, nowSec, recording,
            recording?.let(observation::recordingPlaybackAdmission), canModify,
            programme.channelId?.let(observation::channel) != null,
            liveProgramme = observation.event(programme.id) != null)
    }

    fun recordingConfirmationIsCurrent(confirmation: LiveRecordConfirmation, current: SessionObservation): Boolean {
        if (confirmation.channelId != currentChannelId ||
            currentDvrMutation(confirmation.mutation, confirmation.opening, current) == null) return false
        val target = when (val mutation = confirmation.mutation) {
            is DvrMutationAction.Stop -> mutation.recordingId to BrowseContextAction.STOP
            is DvrMutationAction.Cancel -> mutation.recordingId to BrowseContextAction.CANCEL
            else -> null
        }
        return target?.let { (recordingId, action) ->
            val programme = current.event(confirmation.programme.id) ?: confirmation.programme
            val recording = current.dvrEntryForEvent(programme)?.takeUnless(::recordingWasRemoved)
            recording?.id == recordingId && action in recordActions(programme, recording, current)
        } ?: false
    }

    fun recordProgramme(programme: EpgEventEntry?, fromControl: Boolean = false) {
        if (programmeActions.inFlight.value != null || programmeActions.confirmation != null) return
        val opening = observation
        val capability = opening.currentSession
        if (programme == null) {
            notices.postRecordUnavailable(programme, opening, nowSec)
            return
        }
        val recording = opening.dvrEntryForEvent(programme)?.takeUnless(::recordingWasRemoved)
        val actions = recordActions(programme, recording, opening)
        if (fromControl && recording != null && recording.state in LiveRecordStates) {
            val mutation = when {
                capability == null -> null
                BrowseContextAction.STOP in actions -> DvrMutationAction.Stop(capability, recording.id)
                BrowseContextAction.CANCEL in actions -> DvrMutationAction.Cancel(capability, recording.id)
                else -> null
            }
            if (mutation == null) {
                // Rule-created schedules, for example, offer Skip in Details instead of Cancel.
                openInfo(returnAction = "player-record")
                programmeActions.infoPinnedEventId = programme.id.takeIf { opening.event(it) != null }
            } else {
                programmeActions.confirmation = LiveRecordConfirmation(programme, mutation, opening, currentChannelId)
                layerState.showRecordingConfirmation()
            }
        } else if (capability == null || BrowseContextAction.RECORD !in actions) {
            notices.postRecordUnavailable(programme, opening, nowSec)
        } else {
            val mutation = DvrMutationAction.CreateProgramme(programme.programmeRecordingTarget(capability), null)
            programmeActions.inFlight.value = mutation
            scope.launch {
                try {
                    val preferred = uiSettingsStore.settings.first().recordingProfileId
                    val current = session.observation.value
                    val request = currentDvrMutation(mutation, opening, current) as? DvrMutationAction.CreateProgramme
                        ?: return@launch
                    if (current.dvrEntryForEvent(programme)?.state in LiveRecordStates) return@launch
                    val configured = request.copy(
                        configId = chooseDvrConfig(current.currentDvrConfigurations(), preferred))
                    val context = notices.context()
                    notices.postDvrFailure(configured, dvrMutationActions.execute(configured), context)
                } finally {
                    programmeActions.inFlight.value = null
                }
            }
        }
    }
    @Composable
    fun currentChannelNumber() = remember(channels, currentChannelId) {
        ChannelNavigation.numberForId(
            orderedChannelIds,
            channelNumbers,
            currentChannelId,
        )
    }
    val currentSubscriptionFailure = subscriptionFailure.takeIf { playingLiveChannelId == currentChannelId }
        ?: (playbackState as? AppPlaybackState.Failed)?.subscriptionIssue
    val currentLiveInterrupted = liveInterrupted(
        serverStopped = activeLivePlayback?.serverStopped == true,
        playingLiveChannelId = playingLiveChannelId,
        shownChannelId = currentChannelId,
    )
    val currentPlaybackFailed = requestedChannelFailed || playbackState is AppPlaybackState.Failed
    val channelCondition = liveChannelCondition(currentSubscriptionFailure, currentPlaybackFailed, currentLiveInterrupted,
        deviceFault = (playbackState as? AppPlaybackState.Failed)?.deviceFault == true)
    val statusPresentation = playbackStatusPresentation(
        connectionAvailable = connState is ConnectionState.Connected,
        playbackStarting = playbackState is AppPlaybackState.Starting,
        playbackRecovering = playbackState is AppPlaybackState.Recovering,
        playbackPlaying = playbackState.presented,
        channelCondition = channelCondition,
    )
    val channelUnavailable = statusPresentation == PlaybackStatusPresentation.CHANNEL_UNAVAILABLE
    val channelStartOrigin = remember(videoPresentation.channelStartAtMillis) {
        videoPresentation.channelStartAtMillis?.let { startupChannelStartAtMillis ?: it }
    }
    val serverWait = rememberLivePlayerServerWait(
        observation, connectionUiState, playerClose, onReconnect, onOpenConnectionSettings,
        channelStartOrigin,
        playerRecovery = currentSession != null && (channelCondition != null || channelUnavailable ||
            statusPresentation == PlaybackStatusPresentation.FULL_RECOVERY || connState is ConnectionState.Error),
    )
    val genuineRecovery = serverWait.presentation is MainStartupPresentation.Actionable ||
        statusPresentation == PlaybackStatusPresentation.FULL_RECOVERY
    val recoveryVisible = screenActive && genuineRecovery
    // The box's states plus a connected live TV alert (208): either way the channel's source is gone.
    val channelFailed = channelCondition != null && connState is ConnectionState.Connected &&
        playbackState !is AppPlaybackState.Recovering
    LaunchedEffect(channelFailed) {
        if (channelFailed) {
            timelineState.invalidateForSourceChange()
            layerState.onChannelUnavailable()
        }
    }
    fun currentPlayerForegroundContext() =
        layerState.foregroundContext(
            numberEntryVisible = remoteKeys.channelNumberInput.isNotEmpty(),
            recoveryVisible = recoveryVisible,
            terminalErrorVisible = false,
            seekPreviewPhase = timelineState.seekPreviewPhase(layerState.core.chrome.controlsVisible),
        )
    val foregroundContext = currentPlayerForegroundContext()
    val confirmationVisible = foregroundContext.confirmationVisible
    val validRecordConfirmation = programmeActions.confirmation
        ?.takeIf { recordingConfirmationIsCurrent(it, observation) }
    LaunchedEffect(confirmationVisible, validRecordConfirmation) {
        if (confirmationVisible && validRecordConfirmation == null) dismissRecordingDialog()
        else if (!confirmationVisible) programmeActions.confirmation = null
    }
    val infoVisible = foregroundContext.infoVisible
    val seekPreviewPhase = foregroundContext.seekPreviewPhase
    val foregroundLayer = playerForegroundLayer(foregroundContext)
    LaunchedEffect(guideEntryRequestId) {
        if (guideEntryRequestId != null && consumeGuideEntry()) {
            layerState.toggleSchedule(foregroundLayer, prepare = ::prepareSchedule)
        }
    }
    val numberEntryUp = foregroundLayer == PlayerForegroundLayer.NUMBER_ENTRY
    LaunchedEffect(numberEntryUp, layerState.core.chrome.controlsVisible) {
        if (!layerState.core.chrome.controlsVisible) remoteKeys.numberEntryReturnAction = null
        else if (numberEntryUp) remoteKeys.numberEntryReturnAction = remoteKeys.lastChromeControl
    }
    @Composable
    fun AutoHideEligibility() {
    val autoHideEligible = playerControlsAutoHideEligible(
        PlayerAutoHideContext(
            controlsVisible = layerState.core.chrome.controlsVisible,
            playbackProgressing = timelineState.playbackProgressing &&
                !effectiveTimeshiftState.paused,
            playbackStable = connState is ConnectionState.Connected &&
                playbackState is AppPlaybackState.Playing &&
                statusPresentation == PlaybackStatusPresentation.NONE,
            seekPending = timelineState.seekPending,
            modalVisible = layerState.core.optionsPage != null ||
                layerState.core.infoOpen ||
                foregroundContext.confirmationVisible ||
                foregroundContext.channelDrawerVisible ||
                remoteKeys.channelNumberInput.isNotEmpty(),
            recoveryVisible = recoveryVisible,
            actionableErrorVisible = connState is ConnectionState.Error ||
                playbackState is AppPlaybackState.Failed,
        )
    )
    SideEffect {
        layerState.core.chrome.updateAutoHideEligibility(autoHideEligible)
    }
    }
    AutoHideEligibility()
    @Composable
    fun StartupOutcome() {
    val initialPresentation = if (startupTarget == null) null else initialPlayback.selection?.let {
        playbackRuntime.liveTargetPresentation(it, liveIntent)
    }
    val startupOutcome = mainStartupPlaybackOutcome(
        expectedEpoch = initialPlayback.epoch.takeIf {
            initialPlayback.token == liveRequestToken && initialStartupTarget == startupTarget
        },
        presentation = initialPresentation,
        recovery = requestedChannelFailed || currentSubscriptionFailure != null ||
            (initialPlayback.selection != null && currentSession !== initialPlayback.selection?.currentSession) ||
            currentLiveInterrupted || genuineRecovery || channelUnavailable ||
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
    }
    StartupOutcome()
    val busyBlocked = channelUnavailable || statusPresentation == PlaybackStatusPresentation.FULL_RECOVERY
    val trackGlance = rememberTrackGlance(player, rememberPlayerTracks(player), confirmedPlayingChannelId != null)
    @Composable
    fun rememberBusyStatus(): PlayerBusyStatus? {
    // The Banner stays while tuning and hides after the first frame of this tune; only a
    // service whose tracks are positively audio-only lets its playing state stand in.
    val bannerFramePresented = at.bernhardberger.tvhplayer.core.bannerFramePresented(
        tuneConfirmed = confirmedPlayingChannelId != null,
        videoFrameVisible = videoPresentation.visible,
        playing = playbackState is AppPlaybackState.Playing,
        audioOnly = trackGlance.audioOnly,
    )
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
    SideEffect {
        layerState.core.chrome.onBannerFramePresented(bannerFramePresented)
        layerState.core.chrome.holdBanner(livePaused)
    }
    return busyStatus
    }
    val busyStatus = rememberBusyStatus()
    @Composable
    fun ProgrammeChangeBanner() {
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
    }
    ProgrammeChangeBanner()

    /**
     * Retunes the last live channel. While the session has the channel on screen, its failed
     * request ends here, so the retune is not covered by that failure's message; a channel the
     * session lacks keeps it. A retune that does not start gives the request its failure back
     * while that request is still the current one.
     */
    fun retryLive(
        viewerRetry: Boolean,
        start: ((PlaybackTargetResult?) -> Unit) -> Unit = { onResult ->
            videoPlayerViewModel.retryLiveNow(viewerRetry, onResult)
        },
    ) {
        val retunesRequest = authorizedLiveSelection != null
        val cleared = requestedChannelFailed && retunesRequest
        if (cleared) requestedChannelFailed = false
        val request = liveRequestToken
        start { result ->
            liveRetryRequestFailure(retunesRequest, cleared, result, request == liveRequestToken)
                ?.let { requestedChannelFailed = it }
        }
    }

    fun dispatchRecoveryRetry(command: PlaybackRetryCommand) {
        when (command) {
            PlaybackRetryCommand.RECONNECT -> onReconnect()
            PlaybackRetryCommand.RETRY_LIVE -> retryLive(viewerRetry = true)
            PlaybackRetryCommand.RESUME_RECORDING,
            PlaybackRetryCommand.NONE -> Unit
        }
    }

    LaunchedEffect(connState, screenActive, videoPresentation.channelStartAtMillis, serverWait.waitingForFirstSession) {
        // Entry/resume recovery belongs to its start, not the mid-picture loss path.
        if (videoPresentation.channelStartAtMillis != null) {
            connectionLost = false
            return@LaunchedEffect
        }
        if (serverWait.waitingForFirstSession && playbackRuntime.activeTarget.value == null) return@LaunchedEffect
        if (!screenActive ||
            screenEntry == null) return@LaunchedEffect

        when (connState) {
            is ConnectionState.Connected -> {
                if (connectionLost) {
                    connectionLost = false
                    layerState.showControls()

                    retryLive(viewerRetry = false)
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
                    val previousLastPlayedChannelId = lastPlayedChannelId
                    val previousRestoreToLive = restoreToLiveAfterReconnect
                    connectionLost = true
                    restoreToLiveAfterReconnect =
                        effectiveTimeshiftState.available && effectiveTimeshiftState.timingKnown &&
                            !timeshiftPositionPresentation(
                                effectiveTimeshiftState
                            ).atLiveEdge
                    lastPlayedChannelId = null
                    // Publish loss before suspending, so a Connected re-key still recovers it.
                    if (videoPlayerViewModel.stopAfterLoss(unlessChannelStarting = true) == null) {
                        connectionLost = false
                        restoreToLiveAfterReconnect = previousRestoreToLive
                        lastPlayedChannelId = previousLastPlayedChannelId
                        return@LaunchedEffect
                    }
                    layerState.showControls()
                }
            }
        }
    }

    // Playback/recovery effects above stay mounted. No chrome exists under startup until its first
    // picture is delivered; the passive Banner then shows while the startup screen fades, as on an
    // ordinary entry. Focus and Back wait for the reveal to end; startup consumes its keys.
    if (contentAllowed) PlayerRootFocusEffect(foregroundLayer, rootFocus)

    val handlePlaybackBack: () -> Unit = handle@{
        if (serverWait.presentation is MainStartupPresentation.Passive) {
            playerClose.close()
            return@handle
        }
        val action = playerBackAction(
            surface = PlayerSurface.LIVE,
            context = currentPlayerForegroundContext(),
            // The Banner as drawn: a failed tune replaces it with the centre message.
            bannerVisible = layerState.core.chrome.bannerVisible && !channelUnavailable,
        )
        if (!layerState.core.applyBackAction(action)) when (action) {
            PlayerBackAction.DISMISS_CONFIRMATION -> dismissRecordingDialog()
            PlayerBackAction.CLOSE_INFO ->
                closeInfo()
            PlayerBackAction.CLOSE_SCHEDULE -> layerState.closeSchedule()
            PlayerBackAction.CLEAR_NUMBER_ENTRY -> remoteKeys.channelNumberInput = ""
            PlayerBackAction.CLOSE_CHANNEL_DRAWER -> layerState.dismissChannelDrawer()
            PlayerBackAction.CLOSE_PLAYER -> playerClose.close()
            PlayerBackAction.CANCEL_PENDING_SEEK -> timelineState.cancelPendingSeek()
            PlayerBackAction.DISMISS_SEEK_FEEDBACK ->
                timelineState.dismissDispatchedFeedback()
            else -> Unit
        }
    }
    if (contentAllowed) PlayerBackHandler(handlePlaybackBack)

    val backCycle = remember { PlayerBackKeyCycle() }
    val activityKeyCycleOwner = at.bernhardberger.tvhplayer.ui.startup.LocalMainStartupKeyCycleOwner.current
    val playerModals = remember { mutableStateListOf<PlayerModalBackOwner>() }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    @Composable
    fun playerRootModifier(): Modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (serverWait.presentation is MainStartupPresentation.Passive &&
                    at.bernhardberger.tvhplayer.ui.startup.startupSystemKey(event.nativeKeyEvent.keyCode)
                ) return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN) layerState.core.onKeyDown()
                if (backCycle.intercept(event,
                        holdAllowed = contentAllowed &&
                            playerBackHoldAllowed(foregroundLayer) && playerModals.isEmpty(),
                        onBack = {
                            val modalBack = playerModals.isNotEmpty() ||
                                layerState.core.infoOpen || layerState.scheduleOpen
                            if (!contentAllowed || modalBack) {
                                backDispatcher?.onBackPressed()
                            }
                            else handlePlaybackBack()
                        }, stopAndClose = stopAndClose,
                        activityKeyCycleOwner = activityKeyCycleOwner,
                        backOwner = playerModals.lastOrNull() ?: (foregroundLayer to when (foregroundLayer) {
                            PlayerForegroundLayer.INFO -> remoteKeys.infoRailChannelId ?: currentChannelId
                            PlayerForegroundLayer.SCHEDULE -> layerState.scheduleDetails?.id ?: currentChannelId
                            PlayerForegroundLayer.OPTIONS_DETAIL, PlayerForegroundLayer.OPTIONS_QUICK_LIST -> layerState.core.optionsPage
                            else -> null
                        }))) return@onPreviewKeyEvent true
                if (serverWait.consumesKey(event, layerState)) return@onPreviewKeyEvent true
                val keyCode = event.nativeKeyEvent.keyCode
                layerState.handleScheduleKey(event, foregroundLayer, ::prepareSchedule)?.let { return@onPreviewKeyEvent it }
                if (layerState.core.infoOpen || foregroundContext.confirmationVisible) {
                    if (layerState.core.consumesRevealingKey(event)) return@onPreviewKeyEvent true
                    // Details owns navigation; no playback or options key
                    // (including Left-as-Back in the other panels) reaches the player underneath.
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back -> false
                        else -> true
                    }
                }
                if (handlePlayerStopKeyWithoutTarget(
                        event = event,
                        hasActiveTarget = playbackRuntime.activeTarget.value != null,
                        beginKeyCycle = layerState.core::beginOpeningKeyCycle,
                        stopAndClose = stopAndClose,
                    )
                ) {
                    return@onPreviewKeyEvent true
                }
                val keyContext = PlayerKeyContext(
                    surface = PlayerSurface.LIVE,
                    controlsVisible = layerState.core.chrome.controlsVisible,
                    seekbarFocused = layerState.core.chrome.controlsVisible &&
                        remoteKeys.lastChromeControl == "player-seekbar",
                    timeshiftAvailable = effectiveTimeshiftState.available,
                    optionsOpen = layerState.core.optionsPage != null,
                    infoOpen = layerState.core.infoOpen,
                    drawerOpen = foregroundContext.channelDrawerVisible,
                    confirmationOpen = foregroundLayer == PlayerForegroundLayer.CONFIRMATION,
                    livePause = livePauseAvailability,
                    scrubbing = timelineState.scrubSpeed != 0,
                    programmeRecording = infoEvent?.let(observation::dvrEntryForEvent)?.state in LiveRecordStates,
                )
                val dedicatedAction = playerKeyAction(keyContext, keyCode)
                if (keyCode == AndroidKeyEvent.KEYCODE_MEDIA_RECORD && dedicatedAction == PlayerKeyAction.CONSUME) {
                    return@onPreviewKeyEvent true
                }
                if (dedicatedAction in listOf(PlayerKeyAction.SCRUB_BACK, PlayerKeyAction.SCRUB_FORWARD) ||
                    (dedicatedAction == PlayerKeyAction.PEEK_BANNER && keyCode in listOf(
                        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD, AndroidKeyEvent.KEYCODE_MEDIA_REWIND))) {
                    val direction = if (keyCode == AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD) 1 else -1
                    if (dedicatedAction == PlayerKeyAction.PEEK_BANNER) {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                            if (livePauseUnavailable(livePauseAvailability)) showPauseUnavailable()
                            layerState.core.chrome.peekBanner()
                        }
                    } else if (event.type == KeyEventType.KeyDown) {
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            remoteKeys.scrubKeyCode = keyCode
                            remoteKeys.scrubKeyHeld = false
                        } else if (remoteKeys.scrubKeyCode == keyCode) {
                            remoteKeys.scrubKeyHeld = true
                            queueTimeshiftSeek(playbackSeekStepMs(direction, event.nativeKeyEvent.repeatCount))
                            layerState.core.chrome.peekBanner()
                        }
                    } else if (event.type == KeyEventType.KeyUp && remoteKeys.scrubKeyCode == keyCode) {
                        remoteKeys.scrubKeyCode = null
                        if (!remoteKeys.scrubKeyHeld && !event.nativeKeyEvent.isCanceled) {
                            timelineState.startScrub(direction)
                            queueScrubSeek(direction.toLong())
                            layerState.core.chrome.peekBanner()
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                layerState.core.handleOverlayKey(
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
                    if (timelineState.scrubSpeed != 0) {
                        layerState.core.beginOpeningKeyCycle(keyCode)
                        commitScrub()
                        return@onPreviewKeyEvent true
                    }
                    if (!layerState.core.chrome.controlsVisible) {
                        layerState.core.beginOpeningKeyCycle(keyCode)
                        if (livePauseAccepted(effectiveTimeshiftState.available, livePauseAvailability)) {
                            dispatchPlaybackAction(MediaPlaybackAction.TOGGLE)
                        } else if (livePauseUnavailable(livePauseAvailability)) showPauseUnavailable()
                        layerState.core.chrome.peekBanner()
                        return@onPreviewKeyEvent true
                    }
                    when (liveMediaKeyAction(effectiveTimeshiftState.available, livePauseAvailability)) {
                        LiveMediaKeyAction.DISPATCH -> {
                            layerState.core.beginOpeningKeyCycle(keyCode)
                            dispatchPlaybackAction(mediaAction)
                            layerState.showControls()
                            return@onPreviewKeyEvent true
                        }
                        LiveMediaKeyAction.REVEAL_WITH_REASON -> {
                            layerState.core.beginOpeningKeyCycle(keyCode)
                            showPauseUnavailable()
                            layerState.showControls()
                            return@onPreviewKeyEvent true
                        }
                        LiveMediaKeyAction.PASS_THROUGH -> Unit
                    }
                }

                ChannelNavigation.digitForKeyCode(event.nativeKeyEvent.keyCode)?.let { digit ->
                    remoteKeys.zapKeyUptime = profileZapKey(event.nativeKeyEvent)
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        remoteKeys.channelNumberCommitted = false
                        remoteKeys.channelNumberInput = ChannelNavigation.appendDigit(
                            remoteKeys.channelNumberInput, digit, channelNumberDigits,
                        )
                    }
                    return@onPreviewKeyEvent true
                }

                ChannelNavigation.directionForKeyCode(event.nativeKeyEvent.keyCode)?.let { direction ->
                    remoteKeys.zapKeyUptime = profileZapKey(event.nativeKeyEvent)
                    remoteKeys.channelNumberInput = ""
                    when (playbackChannelKeyAction(
                        railBrowsesChannelKeys = foregroundLayer == PlayerForegroundLayer.CHANNEL_DRAWER,
                    )) {
                        ChannelKeyAction.TUNE ->
                            return@onPreviewKeyEvent tuneAdjacentChannel(direction, event.nativeKeyEvent.eventTime,
                                repeat = event.nativeKeyEvent.repeatCount > 0)
                        ChannelKeyAction.BROWSE_LIST -> Unit
                    }
                }

                if (remoteKeys.channelNumberInput.isNotEmpty()) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> {
                            layerState.core.beginOpeningKeyCycle(keyCode)
                            // Not playable yet: the entry stays pending for the digit timer.
                            channelNumberEntryReadiness == ChannelNumberEntryReadiness.NOT_READY ||
                                tuneEnteredChannel()
                        }
                        else -> false
                    }
                }

                if (foregroundContext.channelDrawerVisible) {
                    return@onPreviewKeyEvent false
                }

                val keyAction = playerKeyAction(keyContext, keyCode = keyCode)
                if (playerKeyActionStartsOpeningCycle(keyAction)) {
                    layerState.core.beginOpeningKeyCycle(keyCode)
                }
                when (keyAction) {
                    PlayerKeyAction.COMMIT_SCRUB -> {
                        layerState.core.beginOpeningKeyCycle(keyCode)
                        commitScrub()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.SCHEDULE_PROGRAMME -> {
                        val programme = infoEvent
                        if (timelineState.scrubSpeed != 0) commitScrub()
                        layerState.core.beginOpeningKeyCycle(keyCode)
                        recordProgramme(programme)
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.OPEN_RECORDING_INFO -> {
                        val programme = infoEvent
                        val wasScrubbing = timelineState.scrubSpeed != 0
                        val initialAction = if (programme?.let(observation::dvrEntryForEvent)?.state == DvrEntryState.SCHEDULED)
                            BrowseContextAction.CANCEL else BrowseContextAction.STOP
                        if (wasScrubbing) commitScrub()
                        openInfo(fromKey = true)
                        programmeActions.infoPinnedEventId = programme?.id.takeIf { wasScrubbing }
                        remoteKeys.infoInitialAction = initialAction
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.SCRUB_BACK,
                    PlayerKeyAction.SCRUB_FORWARD,
                    PlayerKeyAction.CONSUME -> return@onPreviewKeyEvent true
                    PlayerKeyAction.DISMISS_OVERLAY_ONLY -> {
                        when {
                            layerState.core.infoOpen -> {
                                closeInfo()
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent true
                        }
                    }
                    PlayerKeyAction.REVEAL_CONTROLS -> {
                        if (timelineState.seekPending) timelineState.commitPendingSeek()
                        // Either direction peeks controls without activating the newly focused action.
                        layerState.core.restoreInfoFocus = false
                        layerState.showControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE -> {
                        dispatchPlaybackAction(MediaPlaybackAction.TOGGLE)
                        // Pausing shows the Banner, not the controls, so Left/Right keep stepping.
                        layerState.core.chrome.peekBanner()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.PEEK_BANNER -> {
                        if (livePauseUnavailable(livePauseAvailability)) showPauseUnavailable()
                        layerState.core.chrome.peekBanner()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.OPEN_CHANNELS -> {
                        openChannelDrawer(fromKey = true)
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.OPEN_INFO -> {
                        openInfo(fromKey = true)
                        return@onPreviewKeyEvent true
                    }
                    // Options keys are decided before the overlays above can claim them.
                    PlayerKeyAction.OPEN_OPTIONS -> Unit
                    PlayerKeyAction.SEEK_BACK -> {
                        queueTimeshiftSeek(playbackSeekStepMs(-1, event.nativeKeyEvent.repeatCount))
                        // A quick step on the hidden player shows its preview in the Banner.
                        layerState.core.chrome.peekBanner()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.SEEK_FORWARD -> {
                        queueTimeshiftSeek(playbackSeekStepMs(1, event.nativeKeyEvent.repeatCount))
                        layerState.core.chrome.peekBanner()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.HIDE_CONTROLS -> {
                        layerState.core.chrome.hideControls()
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
    if (!contentAllowed &&
        !(startupOutcomeDelivered && startupTarget != null && initialStartupTarget == startupTarget)) {
        return
    }
    @Composable
    fun PlayerContent() {
    val rootModifier = playerRootModifier()
    val onPickChannel: (Channel) -> Boolean = { tuneChannel(it, 0L) }
    val onTogglePause = { dispatchPlaybackAction(MediaPlaybackAction.TOGGLE) }
    val onSeek: (Long) -> Unit = { queueTimeshiftSeek(it) }
    val onRecoveryRetry: (PlaybackRetryCommand) -> Unit = { dispatchRecoveryRetry(it) }
    val chromeLayer = playerChromeLayer(foregroundContext)
    // The digits shown in the top identity's place; empty while another layer covers the entry.
    val numberEntry = remoteKeys.channelNumberInput
        .takeIf { foregroundLayer == PlayerForegroundLayer.NUMBER_ENTRY }.orEmpty()
    val compactTuning = busyStatus == PlayerBusyStatus.TUNING && statusPresentation == PlaybackStatusPresentation.COMPACT_TUNING
    val dvrEntries = observation.dvrEntries()
    val recordingChannelIds = remember(dvrEntries, nowSec) { activeRecordingChannelIds(dvrEntries, nowSec) }
    val chromeState = playerStateCell(paused = livePaused)
    val currentChannel = remember(observation, currentChannelId) {
        observation.channel(currentChannelId)
    }
    @Composable
    fun chromeContent(): PlayerChromeContent {
    val nextEvent = remember(observation, currentChannelId, nowSec) {
        observation.nextEvent(
            currentChannelId,
            kotlin.time.Instant.fromEpochSeconds(nowSec),
        )
    }
    // A slot window (no programme at the position) is an axis only: it has no Next line.
    val infoNextEvent = displayedNextEvent(visibleSeekPreview != null, displayedWindow,
        effectiveTimeshiftState, nextEvent, liveStart, nowEvent) { observation.nextEvent(currentChannelId, it) }
    val unavailableTitle = stringResource(R.string.no_epg)
    val controlsEvent = infoEvent.takeUnless { channelUnavailable }
    // During a quick step the Next line follows the step's target programme, so the block keeps its height.
    val controlsNext = infoNextEvent.takeUnless { channelUnavailable }
    return PlayerChromeContent(
        clockSec = wallClockMinute(nowSec),
        state = chromeState,
        info = liveInfoBarData(
            resources = androidx.compose.ui.platform.LocalContext.current.resources,
            channelNumber = currentChannelNumber(),
            channelName = currentChannelName,
            event = controlsEvent,
            next = controlsNext,
            nowSec = nowSec,
            unavailableTitle = unavailableTitle,
            nowRecording = observation.runningRecordingForEvent(controlsEvent),
            nextRecording = observation.nextRecordingForEvent(controlsNext),
        ),
        channelRecording = currentChannelId in recordingChannelIds,
        badges = trackGlance.badges,
        picon = currentChannel?.icon,
        // The info bar's artwork tile.
        artwork = controlsEvent?.let(::programmeArtwork),
        channelId = currentChannelId,
    )
    }
    Box(modifier = rootModifier) { ProvideArtworkSession(currentSession, observation) {
        // A quick step's preview plays inside the Banner; the pending or dispatched seek is its own layer.
        val bannerSeekPreview = visibleSeekPreview?.takeIf {
            foregroundLayer == PlayerForegroundLayer.PENDING_SEEK_PREVIEW ||
                foregroundLayer == PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW
        }
        // A pending or dispatched step holds the Banner up after its own timer ran out.
        val bannerShown = bannerSeekPreview != null ||
            (layerState.core.chrome.bannerVisible && chromeLayer == PlayerForegroundLayer.NONE)
        val channelRailOpen = foregroundLayer == PlayerForegroundLayer.CHANNEL_DRAWER
        // The channel drawer opens with the controls; the page's own Info never holds them here (no details).
        val controlsLayerShown = chromeLayer == PlayerForegroundLayer.CONTROLS || channelRailOpen
        val chromePanelOpen = layerState.core.optionsPage != null || layerState.core.infoOpen ||
            layerState.scheduleOpen || foregroundContext.confirmationVisible
        // The lower chrome's mode, for the chrome itself and for the Stats card under it.
        fun lowerChromeMode(keepControls: Boolean) = playerChromeMode(
            controls = controlsLayerShown || keepControls,
            banner = !channelUnavailable && bannerShown,
            stepPreview = bannerSeekPreview != null,
        )
        // Passive diagnostics sit below all chrome and foreground panels; while the channel is unavailable the
        // channel message carries their text instead, whatever layer is open. The channel drawer's browse surface
        // takes the card's place. While the lower chrome is drawn the debug level draws the stream path card only.
        LivePlayerDiagnostics(
            viewModel = videoPlayerViewModel,
            // PlayerPage keeps the controls only while its details compose; this page has none (details = null).
            level = presentedStatsLevel(statsLevel,
                lowerChromeMode(keepControls = false) != PlayerChromeMode.HIDDEN && !chromePanelOpen),
            visible = statsLevel.enabled && foregroundLayer != PlayerForegroundLayer.RECOVERY && !channelUnavailable &&
                !channelRailOpen,
            accessible = foregroundLayer == PlayerForegroundLayer.NONE,
            targetKey = currentChannelId,
            aspectRatio = targetAspect.mode,
            timeshiftState = effectiveTimeshiftState,
            playbackRuntime = playbackRuntime,
            raster = trackGlance.badges.firstOrNull { it.kind == GlanceBadgeKind.RASTER }?.value,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin, top = 100.dp),
        )
        // Trial: Down from the action row opens the channel rows below on the playing channel, so
        // closing returns to the action it came from.
        val openRailFromActions: () -> Unit = {
            layerState.core.beginOpeningKeyCycle(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
            if (foregroundLayer != PlayerForegroundLayer.CHANNEL_DRAWER) openChannelDrawer()
        }
        // The page's strip and the detached rail outlive the controls' exit.
        val quickZap = rememberQuickZapRail(expanded = layerState.channelRailPresented, detached = true)
        @Composable
        fun PlayerControls(keepControls: Boolean) {
        val previewId = visibleSeekPreview?.id
        val onStepAnnounced = remember(timelineState, previewId) {
            { previewId?.let(timelineState::stepAnnounced); Unit }
        }
        // The same availability as the options sheet's quick lists the buttons open.
        val trackButtons = rememberPlayerTrackButtons(
            player, available = !channelFailed && playingLiveChannelId == currentChannelId)
        val controlsShown = controlsLayerShown || keepControls
        PlayerChrome(
            mode = lowerChromeMode(keepControls),
            content = chromeContent(),
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
                stepSettled = visibleSeekPreview?.dispatched == true,
                onStepAnnounced = onStepAnnounced,
                stepId = previewId,
                scrubSpeed = timelineState.scrubSpeed,
                liveStart = liveStart,
                liveAvailable = !channelUnavailable,
                feedback = timelineState.feedback,
                feedbackIsError = timelineState.feedbackIsError,
                motionKey = currentChannelId to liveRequestToken,
                tuning = compactTuning,
            ),
            actions = PlayerChromeActions(
                // Under number entry the controls stay up, but the root takes the keys.
                active = layerState.core.chrome.controlsVisible && layerState.core.optionsPage == null && !layerState.core.infoOpen &&
                    foregroundLayer != PlayerForegroundLayer.NUMBER_ENTRY && !foregroundContext.confirmationVisible,
                paused = !livePlayWhenReady(livePauseState, playWhenReady),
                livePause = livePauseAvailability,
                restoreFocus = when {
                    remoteKeys.infoReturnAction != null && !layerState.core.infoOpen -> remoteKeys.infoReturnAction
                    layerState.core.restoreInfoFocus -> "player-info"
                    layerState.core.restoreOptionsFocus -> "player-settings"
                    remoteKeys.numberEntryReturnAction != null -> remoteKeys.numberEntryReturnAction
                    else -> layerState.core.restoreControl
                },
                tracks = trackButtons,
            ),
            entry = layerState.core.chrome.controlsEntry,
            panelOpen = chromePanelOpen,
            numberEntryShown = numberEntry.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            onTogglePause = onTogglePause,
            onPauseUnavailable = { showPauseUnavailable() },
            onSeek = onSeek,
            onCommitSeek = timelineState::commitPendingSeek,
            onStop = stopAndClose,
            onRecord = { recordProgramme(infoEvent, fromControl = true) },
            recordState = infoEvent?.let(observation::dvrEntryForEvent)?.takeUnless(::recordingWasRemoved)
                ?.state?.takeIf { it in LiveRecordStates },
            onInfo = {
                openInfo()
            },
            onSchedule = {
                prepareSchedule()
                layerState.openSchedule()
            },
            scheduleAvailable = rememberScheduleAvailable(observation, currentChannelId, nowSec),
            onOptions = layerState.core::openOptions,
            onAudioList = layerState.core::showAudioPicker,
            onInteraction = layerState.core.chrome::onUserInteraction,
            onActionFocused = { layerState.onActionFocused(it) },
            onControlFocused = { remoteKeys.lastChromeControl = it },
            onFocusRestored = {
                remoteKeys.infoReturnAction = null
                remoteKeys.numberEntryReturnAction = null
                layerState.core.onFocusRestored()
            },
            onDownFromActions = openRailFromActions,
            downHint = stringResource(R.string.nav_channels),
            decorationCoversControls = channelRailOpen,
            controlsDecoration = { actionsAlpha, controls ->
                // Below the controls, the rail's cards hint at what Down opens.
                QuickZapControls(quickZap, peek = actionsAlpha.takeIf { channels.isNotEmpty() }, controls = controls)
            },
        )
        QuickZapRail(
            quickZap,
            // The rows' saved logos are those of the observation their scope was resolved from.
            channelContent = { ProvideArtworkSession(currentSession, channelScope.observation) {
                // Read in the rail, so the controls' show and hide do not recompose the whole player.
                val railNowSec = quickZap.clock(nowSec)
                PlayerChannelBrowser(
                    state = channelBrowser,
                    channels = channels,
                    recentChannels = recentChannels,
                    playingChannelId = currentChannelId,
                    playbackIndicator = channelIndicator(),
                    recordingChannelIds = recordingChannelIds,
                    nowSec = railNowSec,
                    nowEvent = { id -> observation.eventAt(id, kotlin.time.Instant.fromEpochSeconds(railNowSec)) },
                    runningRecordingForEvent = observation::runningRecordingForEvent,
                    onFocusChannel = { selectedId = it },
                    onPickChannel = { onPickChannel(it) },
                    onClose = { keyCode ->
                        if (keyCode != null) layerState.core.beginOpeningKeyCycle(keyCode)
                        layerState.dismissChannelDrawer()
                    },
                    onOpenDetails = { channel, _, keyCode ->
                        keyCode?.let(layerState.core::beginOpeningKeyCycle)
                        remoteKeys.infoRailChannelId = channel.id
                        remoteKeys.infoReturnAction = null
                        layerState.core.restoreInfoFocus = false
                        layerState.openInfo()
                    },
                    active = foregroundLayer == PlayerForegroundLayer.CHANNEL_DRAWER,
                    channelsTitle = channelsTitle(),
                )
            } },
            trayBleeds = recentChannels.isNotEmpty(),
            header = { PlayerChannelBrowserHint(channelBrowser, recentChannels.isNotEmpty(), channelsTitle()) },
            preview = {
                val previewChannel = channels.firstOrNull { it.id == selectedId }
                val previewNext = previewChannel?.let {
                    observation.nextEvent(it.id, kotlin.time.Instant.fromEpochSeconds(nowSec))
                }
                val previewEvent = previewChannel?.let {
                    observation.eventAt(it.id, kotlin.time.Instant.fromEpochSeconds(nowSec))
                }
                QuickZapTrayPreview(previewChannel,
                    previewEvent,
                    previewNext,
                    nowSec,
                    Modifier.padding(horizontal = at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin),
                    scrolling = channelBrowser.scrolling,
                    nextRecording = observation.nextRecordingForEvent(previewNext),
                    nowRecording = observation.runningRecordingForEvent(previewEvent))
            },
            peeks = channels.isNotEmpty(),
            visible = controlsShown && !chromePanelOpen,
        )
        // Full recovery and the unavailable message replace even an exiting ring.
        if (!busyBlocked) PlayerBusyIndicator(busyStatus, Modifier.align(Alignment.Center), cover = videoPresentation)
        // With the chrome hidden and no Banner, only the paused chip remains.
        PlayerHiddenStatusChip(
            state = hiddenChipState(
                chromeState, hidden = foregroundLayer == PlayerForegroundLayer.NONE && !layerState.core.chrome.bannerVisible,
                available = !channelUnavailable,
            ),
            end = liveBarEnd(liveBehindMs(effectiveTimeshiftState, compactTuning, liveStart), end = null),
            modifier = Modifier.align(Alignment.BottomStart).padding(playerHiddenChipPadding()),
        )

        }
        PlayerPage(
            rail = quickZap,
            chromeIdentityVisible = lowerChromeMode(keepControls = false) != PlayerChromeMode.HIDDEN && !chromePanelOpen,
            numberEntryShown = numberEntry.isNotEmpty(),
            nowPlaying = {
                NowPlayingStrip(currentChannelId, currentChannelNumber()?.toString(), currentChannelName, currentChannel?.icon,
                    channelsVm.nowEvent(currentChannelId, nowSec), nowSec,
                    playbackIndicator = channelIndicator(),
                    programmeAt = { at -> channelsVm.nowEvent(currentChannelId, at) })
            },
            details = null as Unit?,
            player = { keepControls -> PlayerControls(keepControls) },
        ) { }
        @Composable
        fun PlayerPanels() {
        val aspectRatio = targetAspect.mode
        val detailsChannelId = remoteKeys.infoRailChannelId ?: currentChannelId
        val detailsChannel = channels.firstOrNull { it.id == detailsChannelId }
        val detailsBaseEvent = if (remoteKeys.infoRailChannelId == null) {
            programmeActions.infoPinnedEventId?.let(observation::event)
                ?: infoEvent.takeIf { programmeActions.infoPinnedEventId == null }
        } else channelsVm.nowEvent(detailsChannelId, nowSec)
        if (layerState.scheduleOpen) PlayerChannelSchedule(
            layerState, currentChannelId, currentChannel, observation, { session.observation.value }, nowSec, effectiveTimeshiftState,
            dvrMutationActions, notices, scope, uiSettingsStore, onReconnect,
            onPlay = { id, _ -> channels.firstOrNull { it.id == id }?.let(onPickChannel) },
            onPlayRecording = onPlayRecording, onShowGuide = onShowGuide,
            connectionUiState = connectionUiState, session = session,
        )
        if (layerState.core.infoOpen && foregroundLayer == PlayerForegroundLayer.INFO) {
            LivePlayerProgrammeDetails(
                event = detailsBaseEvent, channel = detailsChannel, observation = observation,
                currentObservation = { session.observation.value }, nowSec = nowSec,
                mutations = dvrMutationActions, notices = notices, mutationScope = scope,
                initialAction = remoteKeys.infoInitialAction,
                settingsStore = uiSettingsStore,
                onPlay = { id, _ -> channels.firstOrNull { it.id == id }?.let(onPickChannel) },
                onShowGuide = onShowGuide, onClose = ::closeInfo,
                onPlayRecording = onPlayRecording,
            )
        }
        if (foregroundContext.confirmationVisible && validRecordConfirmation != null) {
            val (programme, mutation, opening) = validRecordConfirmation
            ConfirmProgrammeActionDialog(
                action = if (mutation is DvrMutationAction.Stop) {
                    ProgrammeAction.STOP_RECORDING
                } else ProgrammeAction.CANCEL_RECORDING,
                event = programme,
                channel = programme.channelId?.let(observation::channel),
                nowSec = nowSec,
                currentSession = currentSession,
                observation = observation,
                entries = opening.dvrEntriesForEvent(programme),
                configurations = observation.currentDvrConfigurations(),
                signedInUsername = koinInject<AppProfileOwner>().profiles.collectAsStateWithLifecycle()
                    .value.activeProfile()?.accountName(),
                onDismiss = ::dismissRecordingDialog,
                onConfirm = confirm@{
                    if (programmeActions.confirmation !== validRecordConfirmation) return@confirm
                    val stillCurrent = recordingConfirmationIsCurrent(
                        validRecordConfirmation, session.observation.value,
                    )
                    dismissRecordingDialog()
                    if (stillCurrent) dvrMutationActions.launchMutation(
                        mutation, opening, { session.observation.value }, { nowSec },
                        { uiSettingsStore.settings.first().recordingProfileId },
                        notices, scope, programmeActions.inFlight,
                    )
                },
            )
        }

        PlayerPanelVisibility(
            value = layerState.core.optionsPage?.let { it to layerState.core.optionsQuickList },
            // A quick list applies its focused row when it leaves; that must not wait.
            closesAtOnce = { (_, quick) -> quick },
        ) { (page, _) ->
            val liveOptions = rememberLivePlaybackOptions(
                currentChannelId, observation.currentSession != null, playbackRuntime,
            ) { id ->
                retryLive(viewerRetry = true) { onResult ->
                    playbackRuntime.selectChannelStreamProfile(currentChannelId, id, onResult)
                }
            }
            val audioAutomatic by playbackRuntime.audioAutomatic.collectAsStateWithLifecycle()
            PlaybackOptionsSheet(
                page = page,
                player = player,
                audioAutomatic = audioAutomatic,
                onAutomaticAudio = { playbackRuntime.useAutomaticAudio() },
                subtitlesDefault = liveOptions.subtitlesDefault,
                onDefaultSubtitles = { playbackRuntime.useDefaultSubtitles() },
                onSubtitleSelection = { override, off, epoch -> playbackRuntime.selectSubtitles(override, off, epoch) },
                subtitleLanguage = liveOptions.subtitleLanguage,
                streamProfile = liveOptions.streamProfile,
                tracksResolving =
                    playbackState is AppPlaybackState.Starting ||
                        playbackState is AppPlaybackState.Recovering,
                aspectRatio = aspectRatio,
                statsLevel = statsLevel,
                onPageChange = layerState.core::showOptionsPage,
                quickList = layerState.core.quickList.takeIf { layerState.core.optionsQuickList },
                onQuickListClose = layerState.core::closeQuickList,
                quickListTargetEpoch = videoPresentation.epoch,
                quickListAvailable = !channelFailed && playingLiveChannelId == currentChannelId,
                isQuickListTargetCurrent = { epoch ->
                    playbackRuntime.isQuickListTargetCurrent(epoch) &&
                        videoPlayerViewModel.playingLiveChannelId.value == currentChannelId
                },
                onAudioSelection = { override, epoch -> playbackRuntime.selectAudio(override, epoch) },
                onAspectRatioChange = targetAspect.select,
                onStatsLevelChange = statsStore::setLevel,
            )
        }

        }
        PlayerPanels()

        PlayerTopIdentitySlot {
            ChannelNumberOverlay(
                number = numberEntry,
                target = channelNumberTarget(),
                maxDigits = channelNumberDigits,
                settles = remoteKeys.channelNumberCommitted,
            )
        }

        val unavailableShown = channelUnavailable && foregroundLayer in setOf(PlayerForegroundLayer.CONTROLS, PlayerForegroundLayer.NONE, PlayerForegroundLayer.CHANNEL_DRAWER)
        PlayerChannelMessage(
            visible = unavailableShown,
            condition = channelCondition,
            modifier = Modifier.align(Alignment.Center),
            deviceFaultDetail = playbackState.deviceFaultDetail(),
            statsFailure = (playbackState as? AppPlaybackState.Failed)?.takeIf { statsLevel.enabled },
            statsRows = { rememberLiveStatsRows(videoPlayerViewModel, statsLevel, currentChannelId) },
        )
        LivePlayerServerWait(
            serverWait, currentChannel, currentChannelName, rootFocus, announceStatus = startupTarget == null,
            freshStart = freshStart,
        )
        LivePlayerAlertOverlay(
            visible = foregroundLayer == PlayerForegroundLayer.RECOVERY && serverWait.presentation == null,
            alert = livePlayerAlert(connState, playbackState, channelCondition),
            onRetry = onRecoveryRetry,
            onConnectionSettings = serverWait::openConnectionSettings,
            onClose = playerClose::close,
            statsFailure = (playbackState as? AppPlaybackState.Failed)?.takeIf { statsLevel.enabled },
        )
    } }
    }
    CompositionLocalProvider(LocalPlayerModalRegistry provides playerModals) {
        PlayerContent()
    }
}

private val LiveRecordStates = setOf(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)

private class LiveProgrammeActionState(val inFlight: MutableState<DvrMutationAction?>) {
    var infoPinnedEventId by mutableStateOf<EventId?>(null)
    var confirmation by mutableStateOf<LiveRecordConfirmation?>(null)
}

private data class LiveRecordConfirmation(
    val programme: EpgEventEntry,
    val mutation: DvrMutationAction,
    val opening: SessionObservation,
    val channelId: ChannelId,
)

private class LivePlayerRemoteKeyState {
    var channelNumberInput by mutableStateOf("")
    // Whether the entry last cleared was committed to its channel, so it settles into the identity.
    var channelNumberCommitted by mutableStateOf(false)
    // Set while an entry that named no channel is still shown, after its commit.
    var channelNumberUnknown by mutableStateOf(false)
    // Uptime of the last digit or CH key not yet followed by a recomposition (0 when none).
    var zapKeyUptime = 0L
    var infoRailChannelId by mutableStateOf<ChannelId?>(null)
    var infoReturnOrigin = "player-info"
    var infoReturnAction by mutableStateOf<String?>(null)
    // The control focused before number entry took over the controls.
    var numberEntryReturnAction by mutableStateOf<String?>(null)
    var infoInitialAction by mutableStateOf<BrowseContextAction?>(null)
    var lastChromeControl by mutableStateOf<String?>(null)
    var scrubKeyHeld by mutableStateOf(false)
    var scrubKeyCode by mutableStateOf<Int?>(null)
}

private class InitialLivePlaybackState {
    var failedSelection by mutableStateOf<LivePlaybackSelection?>(null)
    var resolved by mutableStateOf(false)
    var selection by mutableStateOf<LivePlaybackSelection?>(null)
    var epoch by mutableStateOf<Long?>(null)
    var intent by mutableStateOf<Long?>(null)
    var token by mutableLongStateOf(-1L)
}

@Composable
private fun PersistConfirmedChannel(
    channelId: ChannelId?,
    scope: at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState,
    observation: SessionObservation,
    store: LastPlayedChannelStore,
) {
    val target = at.bernhardberger.tvhplayer.stores.recentChannelToPersist(
        scope.profileId, channelId,
        observation.currentSession != null && scope.observation?.currentSession === observation.currentSession,
    )
    LaunchedEffect(target) {
        target?.let { (profileId, confirmedChannel) -> store.setChannelId(profileId, confirmedChannel) }
    }
}

private fun SessionObservation.dvrEntries(): List<DvrEntry> =
    dvrSnapshotForDisplay?.entries.orEmpty().filterNot(::recordingWasRemoved).takeIf {
        dvrSnapshotAuthority == RetainedMetadataAuthority.CURRENT
    }.orEmpty()

/** Trial: how many recent channels the row below the top identity offers. */
private const val RecentChannelCount = 8
