package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.common.*
import at.bernhardberger.tvhplayer.ui.components.*
import at.bernhardberger.tvhplayer.ui.player.LocalPlayerPanelMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame
import at.bernhardberger.tvhplayer.ui.player.animateShown
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.rememberCloseBehindPlayer
import at.bernhardberger.tvhplayer.ui.rememberPlayerEntryPending
import at.bernhardberger.tvhplayer.ui.LocalPlayerEntryHandoff
import org.koin.compose.koinInject

/** Shared programme navigation and guarded DVR actions for Guide and Channels. */
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
@Composable
internal fun ProgrammeDetailsHost(
    openingEvent: EpgEvent,
    openingObservation: SessionObservation,
    observation: SessionObservation,
    currentObservation: () -> SessionObservation,
    nowSecProvider: () -> Long,
    showContextMenu: Boolean,
    onShowDetails: () -> Unit,
    mutations: DvrMutationActions,
    notices: NoticeCenter,
    mutationScope: CoroutineScope,
    mutationInFlight: MutableState<DvrMutationAction?>,
    onPlay: (ChannelId, String) -> Unit,
    onPlayRecording: ((RecordingPlaybackSelection) -> Unit)?,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit = { _, _ -> },
    onClose: () -> Unit,
    returnFocusRequester: FocusRequester? = null,
    settingsStore: UiSettingsStore = koinInject(),
    retainPlayerSnapshot: Boolean = false,
) {
    val closeBehindPlayer = rememberCloseBehindPlayer()
    val entryPending = rememberPlayerEntryPending()
    val close = {
        onClose()
        // Owners retain their deferred scroll/missing-item recovery. For a mounted origin,
        // transfer focus in this close dispatch, before overlay disposal admits default entry.
        runCatching { returnFocusRequester?.requestFocus() }
        Unit
    }
    val departing = LocalPlayerEntryHandoff.current?.departing == true
    val presentation = Pair(observation, rememberDetailsEpochSeconds(nowSecProvider))
    var lastPresentation by remember { mutableStateOf(presentation) }
    // Playback can publish its target before the shell starts fading. Keep the page pressed,
    // not a relabelled/reflowed version of it, until that existing handoff removes the overlay.
    // Its data and clock are held here; what plays is held by HoldWatching around browse.
    SideEffect { if (!departing) lastPresentation = presentation }
    val (displayObservation, nowSec) = if (departing) lastPresentation else presentation
    var selected by remember { mutableStateOf(openingEvent) }
    var stack by remember { mutableStateOf<List<Triple<EpgEvent, EventId, BrowseContextAction?>>>(emptyList()) }
    var returnCard by remember { mutableStateOf<EventId?>(null) }
    var returnAction by remember { mutableStateOf<BrowseContextAction?>(null) }
    var pending by remember { mutableStateOf<DvrMutationAction?>(null) }
    var pendingAction by remember { mutableStateOf<ProgrammeAction?>(null) }
    var pendingEntries by remember { mutableStateOf<DvrEventEntries?>(null) }
    var inFlight by mutationInFlight
    val confirmationVisibility = remember(pendingAction) {
        MutableTransitionState(false).apply { targetState = pendingAction != null }
    }
    var confirmationKey by remember { mutableStateOf<Key?>(null) }
    var menuAiring by remember { mutableStateOf<EpgEvent?>(null) }
    var menuConsumed by remember { mutableStateOf(false) }
    var mutationEvent by remember { mutableStateOf<EpgEvent?>(null) }
    // Opened before Ready, the session that arrives later becomes its own, so recording actions appear.
    val sessionObservation = remember(openingObservation, displayObservation.currentSession) {
        if (openingObservation.currentSession != null) openingObservation else displayObservation
    }
    val capability = sessionObservation.currentSession?.takeIf { it === displayObservation.currentSession }
    // Opened before Ready it stays: Watch needs only the channel. A session it opened with is gone.
    val sessionLost = openingObservation.currentSession != null && capability == null
    val live = displayObservation.event(selected.id)
    var lastKnown by remember(selected.id) { mutableStateOf(selected) }
    SideEffect { if (live != null) lastKnown = live }
    val event = live ?: lastKnown
    val channel = event.channelId?.let(displayObservation::channel)
    val recording = displayObservation.dvrEntryForEvent(event)
    val canModify = capability != null && displayObservation.dvrState is DvrRepositoryState.Current &&
        (displayObservation.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    val currentMutation = currentDvrMutation(pending, sessionObservation, observation)
    // Playing is the programme at the viewer's position on the watched channel, as its marks show it.
    // Behind live, the programme on air there also returns to the viewer, and live is offered beside it.
    val watchingChannel = LocalWatchingChannel.current
    val goLive = LocalWatchingGoLive.current
    @Composable
    fun isBehindLive(event: EpgEvent) = capability != null && goLive != null && event.channelId != null &&
        event.channelId == watchingChannel && watchingBehindLive(event.channelId)
    @Composable
    fun isPlaying(event: EpgEvent) = capability != null && watchingIndicator(event, nowSec) != ChannelPlaybackIndicator.NONE ||
        isBehindLive(event) && event.isRunning(nowSec)
    val actions = programmeDetailsActions(event, nowSec, recording,
        recording?.let(displayObservation::recordingPlaybackAdmission), canModify,
        channel != null, liveProgramme = live != null, playing = isPlaying(event), behindLive = isBehindLive(event))
        .filter { it != BrowseContextAction.PLAY || onPlayRecording != null && capability != null }
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    val closing = !visibility.targetState
    val closeAnimated = { visibility.targetState = false }
    BackHandler(enabled = closing) { }
    LaunchedEffect(visibility.isIdle, visibility.currentState, closing) {
        if (closing && visibility.isIdle && !visibility.currentState) close()
    }
    val keyHandler: (KeyEvent) -> Boolean = { key ->
        if (key.key == confirmationKey && pendingAction == null) {
            if (key.type == KeyEventType.KeyUp) confirmationKey = null
            true
        } else {
            if (pendingAction != null && key.type == KeyEventType.KeyDown) confirmationKey = key.key
            if (key.key == confirmationKey && key.type == KeyEventType.KeyUp) confirmationKey = null
            false
        }
    }
    LaunchedEffect(capability, live, channel, currentMutation) {
        if (if (retainPlayerSnapshot) channel == null else
            sessionLost || (openingObservation.event(selected.id) != null && live == null)) {
            close()
            return@LaunchedEffect
        }
        if (pendingAction != null && currentMutation == null) {
            returnAction = actions.firstOrNull { it in listOf(BrowseContextAction.RECORD,
                BrowseContextAction.STOP, BrowseContextAction.CANCEL) } ?: actions.firstOrNull()
            pendingAction = null
            pending = null
            if (menuConsumed) close()
        }
    }
    val pushPage: (EpgEvent) -> Unit = { next ->
        stack = stack + Triple(selected, next.id, returnAction)
        selected = next
        returnCard = null
        returnAction = null
    }
    fun executeDirect(mutation: DvrMutationAction) {
        if (inFlight != null) return
        inFlight = mutation
        // Queue after this input dispatch so even an immediate result cannot admit a second click.
        mutationScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val preferred = if (mutation is DvrMutationAction.CreateProgramme)
                    settingsStore.settings.first().recordingProfileId else null
                val current = currentObservation()
                val request = currentDvrMutation(mutation, sessionObservation, current) ?: return@launch
                if (request is DvrMutationAction.CreateProgramme &&
                    current.dvrEntryForEvent(request.target.eventId)?.state in
                    listOf(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) return@launch
                val configured = if (request is DvrMutationAction.CreateProgramme)
                    request.copy(configId = chooseDvrConfig(current.currentDvrConfigurations(), preferred)) else request
                val context = notices.context()
                val result = mutations.execute(configured)
                // Command failures survive navigation; NoticeCenter owns their context lifetime.
                notices.postDvrFailure(configured, result, context)
            } finally {
                inFlight = null
            }
        }
    }
    val act: (EpgEvent, BrowseContextAction) -> Unit = action@{ programme, action ->
        if (action == BrowseContextAction.WATCH_LIVE) {
            // The jump outlives this page; the player's own command queue orders it.
            goLive?.let { mutationScope.launch { it() } }
        }
        if (action == BrowseContextAction.CONTINUE_WATCHING || action == BrowseContextAction.WATCH_LIVE) {
            if (retainPlayerSnapshot) closeAnimated() else programme.channelId?.let(currentObservation()::channel)?.let {
                closeBehindPlayer(close)
                onPlay(it.id, it.name.orEmpty())
            }
            return@action
        }
        if (inFlight != null && action in listOf(BrowseContextAction.RECORD,
                BrowseContextAction.STOP, BrowseContextAction.CANCEL)) return@action
        val current = currentObservation()
        if (action == BrowseContextAction.WATCH || action == BrowseContextAction.SHOW_GUIDE) {
            // Live playback shares the player's wait for the session, and the guide is browsing:
            // neither needs more than the channel. Recording actions keep the session guard below.
            val event = current.event(programme.id) ?: programme
            event.channelId?.let(current::channel)?.let {
                if (action == BrowseContextAction.WATCH) {
                    if (retainPlayerSnapshot) close() else closeBehindPlayer(close)
                    onPlay(it.id, it.name.orEmpty())
                } else {
                    close()
                    onShowGuide(it.id, event)
                }
            }
            return@action
        }
        if (action == BrowseContextAction.DETAILS) {
            // Details only display: they open before Ready too.
            val airingMenu = menuAiring != null
            if (!airingMenu && !showContextMenu) returnAction = action
            menuAiring = null
            if (airingMenu) pushPage(current.event(programme.id) ?: programme) else onShowDetails()
            return@action
        }
        if (capability == null || capability !== current.currentSession) { close(); return@action }
        val actionEvent = current.event(programme.id) ?: programme
        val actionRecording = current.dvrEntryForEvent(actionEvent)
        val actionCanModify = current.dvrState is DvrRepositoryState.Current &&
            (current.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
        if (action == BrowseContextAction.PLAY && actionRecording != null) {
            // Every page and menu stays up until the player has replaced browse.
            closeBehindPlayer(close)
            onPlayRecording?.invoke(RecordingPlaybackSelection(capability, actionRecording.id))
            return@action
        }
        val airingMenu = menuAiring != null
        if (!airingMenu && !showContextMenu) returnAction = action
        if (!airingMenu && showContextMenu && action in listOf(
                BrowseContextAction.RECORD, BrowseContextAction.STOP, BrowseContextAction.CANCEL)) menuConsumed = true
        menuAiring = null
        mutationEvent = actionEvent
        when (action) {
            BrowseContextAction.RECORD -> if (current.event(programme.id) != null && actionCanModify &&
                actionRecording?.state !in listOf(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) {
                executeDirect(DvrMutationAction.CreateProgramme(actionEvent.programmeRecordingTarget(capability), null))
            }
            BrowseContextAction.STOP -> if (actionRecording != null && actionCanModify) {
                val candidate = DvrMutationAction.Stop(capability, actionRecording.id)
                if (currentDvrMutation(candidate, sessionObservation, current) != null) {
                    pendingEntries = current.dvrEntriesForEvent(actionEvent)
                    pending = candidate
                    pendingAction = ProgrammeAction.STOP_RECORDING
                }
            }
            BrowseContextAction.CANCEL -> if (actionRecording != null && actionCanModify) {
                val candidate = DvrMutationAction.Cancel(capability, actionRecording.id)
                if (currentDvrMutation(candidate, sessionObservation, current) != null) {
                    pendingEntries = current.dvrEntriesForEvent(actionEvent)
                    pending = candidate
                    pendingAction = ProgrammeAction.CANCEL_RECORDING
                }
            }
            else -> Unit
        }
        if (menuConsumed && pendingAction == null) close()
    }
    AnimatedVisibility(visibleState = visibility, modifier = Modifier.browseDetailsViewport(),
        enter = EnterTransition.None, exit = ExitTransition.None) {
    val detailAlpha = animateShown(
        tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard),
        tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard), "browse-details-layer")
    PlayerMotionFrame(leaving = closing, modifier = Modifier.onPreviewKeyEvent { closing || departing || entryPending() }) {
    AnimatedContent(targetState = showContextMenu, transitionSpec = {
        // Details owns its fade so the shell uses exactly the same progress.
        (if (targetState) fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)) else EnterTransition.None)
            .togetherWith(if (initialState) fadeOut(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)) else ExitTransition.None)
            .using(null)
    }, label = "browse-menu-details") { menu ->
    PlayerMotionFrame(leaving = closing || menu != showContextMenu) {
    if (menu) {
        AnimatedVisibility(visible = !closing && !menuConsumed,
            enter = EnterTransition.None, exit = ExitTransition.None) {
        CompositionLocalProvider(LocalPlayerPanelMotion provides this) {
        PlayerMotionFrame(leaving = closing || pendingAction != null) {
        ProgrammeContextMenu(
            event, channel, recording, nowSec, canModify, capability, displayObservation,
            canPlayRecording = onPlayRecording != null,
            modifier = Modifier.onPreviewKeyEvent(keyHandler),
            onAction = act, onClose = closeAnimated,
            backEnabled = !closing && menu == showContextMenu && pendingAction == null,
        )
        }
        }
        }
    } else {
        val menuAlpha = animateShown(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard),
            tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard), "browse-details-from-menu")
        val pageAlpha = remember(detailAlpha, menuAlpha) { derivedStateOf { detailAlpha.value * menuAlpha.value } }
        AnimatedContent(selected, transitionSpec = {
            (fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)) togetherWith
                fadeOut(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard))).using(null)
        }, label = "programme-details-stack") { page ->
            val pageLive = displayObservation.event(page.id)
            val pageEvent = pageLive ?: if (page.id == selected.id) event else page
            val pageChannel = pageEvent.channelId?.let(displayObservation::channel)
            val pageRecording = displayObservation.dvrEntryForEvent(pageEvent)
            // Once per programme, EPG publication and details minute, not per grid tick.
            val snapshot = displayObservation.epgSnapshotForDisplay
            val airings = remember(pageEvent, snapshot, displayObservation.channelState, nowSec) {
                snapshot?.let { otherAirings(pageEvent, it.events, nowSec,
                    channelNumber = { id -> displayObservation.channel(id)?.number }) }.orEmpty()
            }
            val pageActions = programmeDetailsActions(pageEvent, nowSec, pageRecording,
                pageRecording?.let(displayObservation::recordingPlaybackAdmission), canModify,
                pageChannel != null, liveProgramme = pageLive != null, playing = isPlaying(pageEvent),
                behindLive = isBehindLive(pageEvent))
                .filter { it != BrowseContextAction.PLAY || onPlayRecording != null && capability != null }
            val covered = menuAiring != null || pendingAction != null
            // The page shows its pictures with the session it was opened with.
            PlayerMotionFrame(leaving = page.id != selected.id || covered) { ProvideArtworkSession(capability, displayObservation) {
            ProgrammeDetailsPage(pageEvent, pageRecording, pageChannel, nowSec,
                pageActions,
                { action -> act(pageEvent, action) },
                onClose = {
                    val previous = stack.lastOrNull()
                    if (previous == null) closeAnimated() else {
                        stack = stack.dropLast(1)
                        selected = previous.first
                        returnCard = previous.second
                        returnAction = previous.third
                    }
                },
                 modifier = Modifier.onPreviewKeyEvent(keyHandler),
                 visibilityAlpha = pageAlpha,
                initialAction = returnAction,
                backEnabled = !closing && page.id == selected.id && menu == showContextMenu && !covered,
                onActionFocused = { returnAction = it },
                related = airings,
                channelOf = displayObservation::channel,
                returnToCard = returnCard,
                onReturnToCardConsumed = { returnCard = null },
                onRelated = pushPage,
                onRelatedContextMenu = { airing ->
                    returnCard = airing.id
                    menuAiring = airing
                },
            )
            } }
        }
    }
    }
    }
    menuAiring?.let { airing ->
        val menuEvent = displayObservation.event(airing.id) ?: airing
        ProgrammeContextMenu(menuEvent, menuEvent.channelId?.let(displayObservation::channel),
            displayObservation.dvrEntryForEvent(menuEvent),
            nowSec = nowSec, canModify = canModify, currentSession = capability, observation = displayObservation,
            canPlayRecording = onPlayRecording != null, onAction = act, onClose = { menuAiring = null }, backEnabled = !closing,
            modifier = Modifier.onPreviewKeyEvent(keyHandler))
    }
    pendingAction?.takeIf { currentMutation != null }?.let { action ->
        val programme = mutationEvent ?: event
        ConfirmProgrammeActionDialog(action, programme.title.orEmpty(),
            entries = pendingEntries,
            configurations = observation.currentDvrConfigurations(),
            visibility = confirmationVisibility,
            onPreviewKeyEvent = keyHandler,
            channelName = programme.channelId?.let(observation::channel)?.name.orEmpty(),
            dateAndTime = formatRecordingSchedule(programme.start.epochSeconds),
            onDismiss = {
                pendingAction = null
                pending = null
                if (menuConsumed) close()
            },
            onConfirm = {
                val mutation = currentDvrMutation(pending, sessionObservation, currentObservation())
                pendingAction = null
                pending = null
                if (mutation != null) executeDirect(mutation)
                if (menuConsumed) close()
            },
        )
    }
    }
    }
}
