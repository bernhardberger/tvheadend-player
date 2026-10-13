package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.common.*
import at.bernhardberger.tvhplayer.ui.components.*
import at.bernhardberger.tvhplayer.ui.player.channelSchedule
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import at.bernhardberger.tvhplayer.ui.screens.guide.DvrConfigDialog
import coil3.ImageLoader
import kotlinx.coroutines.launch

/** Shared programme navigation and guarded DVR actions for Guide and Channels. */
@Composable
internal fun ProgrammeDetailsHost(
    openingEvent: EpgEvent,
    openingObservation: SessionObservation,
    observation: SessionObservation,
    currentObservation: () -> SessionObservation,
    nowSec: Long,
    fromGuide: Boolean,
    showContextMenu: Boolean,
    onShowDetails: () -> Unit,
    imageLoader: ImageLoader,
    mutations: DvrMutationActions,
    notices: NoticeCenter,
    onPlay: (LivePlaybackSelection, String) -> Unit,
    onPlayRecording: ((RecordingPlaybackSelection) -> Unit)?,
    onShowGuide: (ChannelId) -> Unit = {},
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf(openingEvent) }
    var stack by remember { mutableStateOf<List<Pair<EpgEvent, EventId>>>(emptyList()) }
    var returnCard by remember { mutableStateOf<EventId?>(null) }
    var returnAction by remember { mutableStateOf<BrowseContextAction?>(null) }
    var pending by remember { mutableStateOf<DvrMutationAction?>(null) }
    var pendingAction by remember { mutableStateOf<ProgrammeAction?>(null) }
    var target by remember { mutableStateOf<ProgrammeRecordingTarget?>(null) }
    var configs by remember { mutableStateOf<List<DvrConfiguration>?>(null) }
    var confirmationKey by remember { mutableStateOf<Key?>(null) }
    val capability = openingObservation.currentSession?.takeIf { it === observation.currentSession }
    val live = observation.event(selected.id)
    val event = live ?: selected
    val channel = event.channelId?.let(observation::channel)
    val recording = observation.dvrEntryForProgramme(event)
    val canModify = capability != null && observation.dvrState is DvrRepositoryState.Current &&
        (observation.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    val currentMutation = currentDvrMutation(pending, openingObservation, observation)
    val actions = programmeDetailsActions(event, nowSec, recording, canModify,
        channel != null && capability != null, fromGuide, liveProgramme = live != null)
        .filter { it != BrowseContextAction.PLAY || onPlayRecording != null }
    val scope = rememberCoroutineScope()
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
    LaunchedEffect(capability, live, currentMutation, configs, target) {
        if (capability == null || (openingObservation.event(selected.id) != null && live == null)) onClose()
        if (pendingAction != null && currentMutation == null) {
            returnAction = actions.firstOrNull { it in listOf(BrowseContextAction.RECORD,
                BrowseContextAction.STOP, BrowseContextAction.CANCEL) } ?: actions.firstOrNull()
            pendingAction = null
            pending = null
        }
        if (configs != null && currentGuideRecordingTarget(target, observation) == null) { configs = null; target = null }
    }
    val act: (BrowseContextAction) -> Unit = action@{ action ->
        val current = currentObservation()
        if (capability == null || capability !== current.currentSession) { onClose(); return@action }
        returnAction = action
        when (action) {
            BrowseContextAction.DETAILS -> onShowDetails()
            BrowseContextAction.WATCH -> channel?.let {
                onClose()
                onPlay(LivePlaybackSelection(capability, it.id), it.name.orEmpty())
            }
            BrowseContextAction.PLAY -> recording?.let {
                onClose()
                onPlayRecording?.invoke(RecordingPlaybackSelection(capability, it.id))
            }
            BrowseContextAction.SHOW_GUIDE -> channel?.let { onClose(); onShowGuide(it.id) }
            BrowseContextAction.RECORD -> if (live != null && canModify) {
                target = event.programmeRecordingTarget(capability)
                when (val choice = chooseDvrConfig(current.currentDvrConfigurations())) {
                    is DvrConfigChoice.Automatic -> {
                        pending = DvrMutationAction.CreateProgramme(requireNotNull(target), choice.configId)
                        pendingAction = ProgrammeAction.RECORD
                    }
                    is DvrConfigChoice.RequiresSelection -> configs = choice.configs
                }
            }
            BrowseContextAction.STOP -> if (recording != null && canModify) {
                pending = DvrMutationAction.Stop(capability, recording.id)
                pendingAction = ProgrammeAction.STOP_RECORDING
            }
            BrowseContextAction.CANCEL -> if (recording != null && canModify) {
                pending = DvrMutationAction.Cancel(capability, recording.id)
                pendingAction = ProgrammeAction.CANCEL_RECORDING
            }
            else -> Unit
        }
    }
    if (showContextMenu && pendingAction == null && configs == null) {
        BrowseContextMenu(
            title = if (fromGuide) event.title.orEmpty() else channel?.name.orEmpty(),
            metadata = if (fromGuide) channel?.name.orEmpty() else stringResource(R.string.context_channel_now,
                channel?.number?.toString().orEmpty(), event.title.orEmpty()),
            timeRange = "${formatHm(event.start.epochSeconds)}–${formatHm(event.stop.epochSeconds)}",
            actions = if (fromGuide) guideContextActions(event, nowSec, recording, canModify)
                else channelContextActions(event, nowSec, recording, canModify),
            initialAction = returnAction, watchChannel = fromGuide, programDetailsLabel = !fromGuide,
            modifier = Modifier.onPreviewKeyEvent(keyHandler),
            onAction = act, onClose = onClose,
            logo = { PiconBox(imageLoader, channel?.icon, Modifier.size(56.dp, 40.dp), capability) },
        )
    } else if (!showContextMenu && pendingAction == null && configs == null) {
        key(event.id, returnCard) {
            ProgrammeDetailsPage(event, recording, channel, nowSec,
                actions,
                imageLoader, capability, act,
                onClose = {
                    val previous = stack.lastOrNull()
                    if (previous == null) onClose() else {
                        stack = stack.dropLast(1)
                        selected = previous.first
                        returnCard = previous.second
                    }
                },
                modifier = Modifier.onPreviewKeyEvent(keyHandler),
                initialAction = returnAction,
                backEnabled = pendingAction == null && configs == null,
                related = channel?.let { ch ->
                    channelSchedule(observation.nextEvent(ch.id, event.start), { observation.nextEvent(ch.id, it) }, 8)
                }.orEmpty(),
                returnToCard = returnCard,
                onRelated = { next ->
                    stack = stack + (event to next.id)
                    selected = next
                    returnCard = null
                    returnAction = null
                },
            )
        }
    }
    configs?.let { choices ->
        DvrConfigDialog(choices, onDismiss = { configs = null }, onSelect = { config ->
            configs = null
            pending = currentGuideRecordingTarget(target, currentObservation())?.let {
                DvrMutationAction.CreateProgramme(it, config.id)
            }
            pendingAction = ProgrammeAction.RECORD.takeIf { pending != null }
        })
    }
    pendingAction?.takeIf { currentMutation != null }?.let { action ->
        ConfirmProgrammeActionDialog(action, event.title.orEmpty(),
            onPreviewKeyEvent = keyHandler,
            channelName = channel?.name.orEmpty(),
            dateAndTime = formatRecordingSchedule(event.start.epochSeconds, event.stop.epochSeconds.takeIf { action == ProgrammeAction.RECORD }),
            onDismiss = { pendingAction = null; pending = null },
            onConfirm = {
                val mutation = currentDvrMutation(pending, openingObservation, currentObservation())
                pendingAction = null
                pending = null
                target = null
                if (mutation != null) scope.launch {
                    val current = currentDvrMutation(mutation, openingObservation, currentObservation()) ?: return@launch
                    val context = notices.context()
                    notices.postDvrFailure(current, mutations.execute(current), context)
                }
            },
        )
    }
}
