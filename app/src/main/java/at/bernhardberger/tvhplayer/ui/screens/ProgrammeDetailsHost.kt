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
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.common.*
import at.bernhardberger.tvhplayer.ui.components.*
import at.bernhardberger.tvhplayer.ui.player.LocalPlayerPanelMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame
import at.bernhardberger.tvhplayer.ui.player.animateShown
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import coil3.ImageLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
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
    fromGuide: Boolean,
    showContextMenu: Boolean,
    onShowDetails: () -> Unit,
    imageLoader: ImageLoader,
    mutations: DvrMutationActions,
    notices: NoticeCenter,
    mutationScope: CoroutineScope,
    mutationInFlight: MutableState<DvrMutationAction?>,
    onPlay: (LivePlaybackSelection, String) -> Unit,
    onPlayRecording: ((RecordingPlaybackSelection) -> Unit)?,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit = { _, _ -> },
    onClose: () -> Unit,
    returnFocusRequester: FocusRequester? = null,
    settingsStore: UiSettingsStore = koinInject(),
) {
    val close = {
        onClose()
        // Owners retain their deferred scroll/missing-item recovery. For a mounted origin,
        // transfer focus in this close dispatch, before overlay disposal admits default entry.
        runCatching { returnFocusRequester?.requestFocus() }
        Unit
    }
    val nowSec = rememberDetailsEpochSeconds(nowSecProvider)
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
    val capability = openingObservation.currentSession?.takeIf { it === observation.currentSession }
    val live = observation.event(selected.id)
    val event = live ?: selected
    val channel = event.channelId?.let(observation::channel)
    val recording = observation.dvrEntryForEvent(event)
    val canModify = capability != null && observation.dvrState is DvrRepositoryState.Current &&
        (observation.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    val currentMutation = currentDvrMutation(pending, openingObservation, observation)
    val actions = programmeDetailsActions(event, nowSec, recording, canModify,
        channel != null && capability != null, fromGuide, liveProgramme = live != null, stacked = stack.isNotEmpty())
        .filter { it != BrowseContextAction.PLAY || onPlayRecording != null }
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
    LaunchedEffect(capability, live, currentMutation) {
        if (capability == null || (openingObservation.event(selected.id) != null && live == null)) {
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
                val request = currentDvrMutation(mutation, openingObservation, current) ?: return@launch
                if (request is DvrMutationAction.CreateProgramme &&
                    current.dvrEntryForEvent(request.target.eventId)?.state in
                    listOf(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING)) return@launch
                val configured = if (request is DvrMutationAction.CreateProgramme)
                    request.copy(configId = chooseDvrConfig(current.currentDvrConfigurations(), preferred)) else request
                val context = notices.context()
                notices.postDvrFailure(configured, mutations.execute(configured), context)
            } finally {
                inFlight = null
            }
        }
    }
    val act: (EpgEvent, BrowseContextAction) -> Unit = action@{ programme, action ->
        if (inFlight != null && action in listOf(BrowseContextAction.RECORD,
                BrowseContextAction.STOP, BrowseContextAction.CANCEL)) return@action
        val current = currentObservation()
        if (capability == null || capability !== current.currentSession) { close(); return@action }
        val actionEvent = current.event(programme.id) ?: programme
        val actionChannel = actionEvent.channelId?.let(current::channel)
        val actionRecording = current.dvrEntryForEvent(actionEvent)
        val actionCanModify = current.dvrState is DvrRepositoryState.Current &&
            (current.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
        val airingMenu = menuAiring != null
        if (!airingMenu && !showContextMenu) returnAction = action
        if (!airingMenu && showContextMenu && action in listOf(
                BrowseContextAction.RECORD, BrowseContextAction.STOP, BrowseContextAction.CANCEL)) menuConsumed = true
        menuAiring = null
        mutationEvent = actionEvent
        when (action) {
            BrowseContextAction.DETAILS -> if (airingMenu) pushPage(actionEvent) else onShowDetails()
            BrowseContextAction.WATCH -> actionChannel?.let {
                close()
                onPlay(LivePlaybackSelection(capability, it.id), it.name.orEmpty())
            }
            BrowseContextAction.PLAY -> actionRecording?.let {
                close()
                onPlayRecording?.invoke(RecordingPlaybackSelection(capability, it.id))
            }
            BrowseContextAction.SHOW_GUIDE -> actionChannel?.let { close(); onShowGuide(it.id, actionEvent) }
            BrowseContextAction.RECORD -> if (current.event(programme.id) != null && actionCanModify &&
                actionRecording?.state !in listOf(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING)) {
                executeDirect(DvrMutationAction.CreateProgramme(actionEvent.programmeRecordingTarget(capability), null))
            }
            BrowseContextAction.STOP -> if (actionRecording != null && actionCanModify) {
                val candidate = DvrMutationAction.Stop(capability, actionRecording.id)
                if (currentDvrMutation(candidate, openingObservation, current) != null) {
                    pendingEntries = current.dvrEntriesForEvent(actionEvent)
                    pending = candidate
                    pendingAction = ProgrammeAction.STOP_RECORDING
                }
            }
            BrowseContextAction.CANCEL -> if (actionRecording != null && actionCanModify) {
                val candidate = DvrMutationAction.Cancel(capability, actionRecording.id)
                if (currentDvrMutation(candidate, openingObservation, current) != null) {
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
    PlayerMotionFrame(leaving = closing, modifier = Modifier.onPreviewKeyEvent { closing }) {
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
            event, channel, recording, fromGuide, stack.isNotEmpty(), nowSec, canModify, capability, imageLoader,
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
            val pageLive = observation.event(page.id)
            val pageEvent = pageLive ?: page
            val pageChannel = pageEvent.channelId?.let(observation::channel)
            val pageRecording = observation.dvrEntryForEvent(pageEvent)
            // Once per programme, EPG publication and details minute, not per grid tick.
            val snapshot = observation.epgSnapshotForDisplay
            val airings = remember(pageEvent, snapshot, observation.channelState, nowSec) {
                snapshot?.let { otherAirings(pageEvent, it.events, nowSec,
                    channelNumber = { id -> observation.channel(id)?.number }) }.orEmpty()
            }
            val pageActions = programmeDetailsActions(pageEvent, nowSec, pageRecording, canModify,
                pageChannel != null && capability != null, fromGuide, liveProgramme = pageLive != null,
                stacked = page.id != openingEvent.id || stack.isNotEmpty())
                .filter { it != BrowseContextAction.PLAY || onPlayRecording != null }
            val covered = menuAiring != null || pendingAction != null
            PlayerMotionFrame(leaving = page.id != selected.id || covered) {
            ProgrammeDetailsPage(pageEvent, pageRecording, pageChannel, nowSec,
                pageActions,
                imageLoader, capability, { action -> act(pageEvent, action) },
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
                channelOf = observation::channel,
                returnToCard = returnCard,
                onReturnToCardConsumed = { returnCard = null },
                onRelated = pushPage,
                onRelatedContextMenu = { airing ->
                    returnCard = airing.id
                    menuAiring = airing
                },
            )
            }
        }
    }
    }
    }
    menuAiring?.let { airing ->
        val menuEvent = observation.event(airing.id) ?: airing
        ProgrammeContextMenu(menuEvent, menuEvent.channelId?.let(observation::channel),
            observation.dvrEntryForEvent(menuEvent), fromGuide = true, stacked = true,
            nowSec = nowSec, canModify = canModify, currentSession = capability, imageLoader = imageLoader,
            onAction = act, onClose = { menuAiring = null }, backEnabled = !closing,
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
                val mutation = currentDvrMutation(pending, openingObservation, currentObservation())
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
