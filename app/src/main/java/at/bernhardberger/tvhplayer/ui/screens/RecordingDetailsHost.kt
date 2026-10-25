package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.notices.*
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.common.formatRecordingSchedule
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsPage
import at.bernhardberger.tvhplayer.ui.components.LocalActivePlaybackTarget
import at.bernhardberger.tvhplayer.ui.components.LocalRecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.warmRecordingId
import at.bernhardberger.tvhplayer.ui.LocalPlayerEntryHandoff
import at.bernhardberger.tvhplayer.ui.screens.recordings.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Recording details and their session-bound actions, shared by browse and the player. */
@OptIn(ExperimentalTvheadendApi::class)
@Composable
internal fun RecordingDetailsHost(
    recording: DvrEntry,
    openingObservation: SessionObservation,
    observation: SessionObservation,
    currentObservation: () -> SessionObservation,
    nowSec: Long,
    mutations: DvrMutationActions,
    notices: NoticeCenter,
    mutationScope: CoroutineScope,
    onPlayRecording: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit,
    onClose: () -> Unit,
    onWatchChannel: ((ChannelId, String) -> Unit)? = null,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit = { _, _ -> },
    onContinueWatching: (() -> Unit)? = null,
    initialAction: BrowseContextAction? = null,
    backEnabled: Boolean = true,
    visibilityAlpha: State<Float>? = null,
) {
    val departing = LocalPlayerEntryHandoff.current?.departing == true
    val presentation = Triple(observation, nowSec, warmRecordingId(LocalActivePlaybackTarget.current,
        LocalRecordingPlaybackSelection.current, observation.currentSession))
    var lastPresentation by remember { mutableStateOf(presentation) }
    // Keep progress, metadata and actions from the activation while this shell leaves for playback.
    SideEffect { if (!departing) lastPresentation = presentation }
    val (displayObservation, displayNowSec, playingId) = if (departing) lastPresentation else presentation
    val capability = openingObservation.currentSession?.takeIf { displayObservation.currentSession === it }
    val opened = if (capability != null && displayObservation.dvrState is DvrRepositoryState.Current)
        displayObservation.dvrEntry(recording.id) else recording
    LaunchedEffect(opened == null) { if (opened == null) onClose() }
    if (opened == null) return
    val admission = capability?.let {
        (if (displayObservation.dvrState is DvrRepositoryState.Current) displayObservation else openingObservation)
            .recordingPlaybackAdmission(opened)
    }
    val channel = opened.channelId?.let(displayObservation::channel)
    val playing = capability != null && playingId == opened.id
    val canModify = capability != null && (displayObservation.sessionState as? SessionState.Ready)
        ?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    var pending by remember { mutableStateOf<DvrMutationAction?>(null) }
    var focusedAction by remember { mutableStateOf(initialAction) }
    var confirmationKey by remember { mutableStateOf<Key?>(null) }
    val latestObservation by rememberUpdatedState(currentObservation)
    val pendingCurrent = pending?.recordingStateIsCurrent(observation) == true
    LaunchedEffect(capability, pendingCurrent) {
        if (pending != null && (capability == null || !pendingCurrent)) {
            pending = null
            if (capability == null) focusedAction = null
        }
    }
    Box(Modifier.onPreviewKeyEvent { event ->
        // A refresh can remove the confirmation between down and up.
        if (departing) true else if (event.key == confirmationKey && pending == null) {
            if (event.type == KeyEventType.KeyUp) confirmationKey = null
            true
        } else {
            if (pending != null && event.type == KeyEventType.KeyDown) confirmationKey = event.key
            if (event.key == confirmationKey && event.type == KeyEventType.KeyUp) confirmationKey = null
            false
        }
    }) {
        ProvideArtworkSession(capability, displayObservation) {
            ProgrammeDetailsPage(
                event = null, recording = opened, channel = channel, nowSec = displayNowSec,
                recordingEvent = opened.eventId?.takeIf { capability != null }?.let(displayObservation::event),
                recordingConfigurations = displayObservation.currentDvrConfigurations().takeIf { capability != null }.orEmpty(),
                recordingAdmission = admission,
                actions = recordingDetailsActions(opened, admission, canModify,
                    channel != null, playing = playing, nowSec = displayNowSec),
                initialAction = focusedAction,
                backEnabled = backEnabled && pending == null,
                visibilityAlpha = visibilityAlpha,
                onClose = onClose,
                onAction = { action ->
                    val current = currentObservation()
                    val session = capability?.takeIf { current.currentSession === it }
                    when (action) {
                        BrowseContextAction.CONTINUE_WATCHING -> onContinueWatching?.invoke()
                        BrowseContextAction.SHOW_GUIDE -> opened.channelId?.let(current::channel)?.let {
                            onClose()
                            onShowGuide(it.id, opened.eventId?.let(current::event))
                        }
                        BrowseContextAction.WATCH, BrowseContextAction.RESUME,
                        BrowseContextAction.PLAY, BrowseContextAction.START_OVER -> {
                            if (action == BrowseContextAction.WATCH && opened.state == DvrEntryState.SCHEDULED) {
                                opened.channelId?.let(current::channel)?.let { onWatchChannel?.invoke(it.id, it.name.orEmpty()) }
                            } else session?.let {
                                onPlayRecording(RecordingPlaybackSelection(it, opened.id),
                                    if (action == BrowseContextAction.RESUME || action == BrowseContextAction.WATCH)
                                        RecordingPlaybackStart.RESUME else RecordingPlaybackStart.START_OVER)
                            }
                        }
                        BrowseContextAction.STOP, BrowseContextAction.CANCEL, BrowseContextAction.DELETE -> {
                            focusedAction = action
                            pending = session?.let {
                                when (action) {
                                    BrowseContextAction.STOP -> DvrMutationAction.Stop(it, opened.id)
                                    BrowseContextAction.CANCEL -> DvrMutationAction.Cancel(it, opened.id)
                                    else -> DvrMutationAction.Delete(it, opened.id)
                                }
                            }
                        }
                        BrowseContextAction.MARK_WATCHED, BrowseContextAction.MARK_UNWATCHED -> session?.let { owner ->
                            mutationScope.launch {
                                val latest = latestObservation()
                                if (latest.currentSession !== owner) return@launch
                                val entry = latest.dvrEntry(opened.id) ?: return@launch
                                val writable = (latest.sessionState as? SessionState.Ready)
                                    ?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
                                if (recordingWatchStateAction(entry, latest.recordingPlaybackAdmission(entry), writable) != action) return@launch
                                val context = notices.context()
                                when (val result = mutations.updateWatchState(owner, entry.id, action == BrowseContextAction.MARK_WATCHED)) {
                                    DvrProgressResult.Accepted -> Unit
                                    is DvrProgressResult.Failed -> notices.post(
                                        Notice.DvrActionFailed(DvrMutationKind.UPDATE, result.toDvrMutationFeedback()), context)
                                }
                            }
                        }
                        else -> Unit
                    }
                },
            )
        }
        val mutation = pending
        if (mutation != null && capability != null && pendingCurrent) {
            val action = when (mutation) {
                is DvrMutationAction.Stop -> PendingRecordingAction.STOP
                is DvrMutationAction.Cancel -> PendingRecordingAction.CANCEL
                else -> PendingRecordingAction.DELETE
            }
            val visibility = remember(mutation) { MutableTransitionState(false).apply { targetState = true } }
            RecordingConfirmationDialog(
                visibility = visibility, action = action, title = opened.title.orEmpty(),
                channelName = channel?.name ?: opened.channelName.orEmpty(),
                dateAndTime = formatRecordingSchedule(opened.start?.epochSeconds,
                    dateOnly = action == PendingRecordingAction.DELETE, locale = LocalConfiguration.current.locales[0]),
                backEnabled = backEnabled,
                onDismiss = { pending = null },
                onConfirm = {
                    pending = null
                    mutationScope.launch {
                        val latest = latestObservation()
                        if (latest.currentSession !== capability || !mutation.recordingStateIsCurrent(latest)) return@launch
                        val context = notices.context()
                        notices.postDvrFailure(mutation, mutations.execute(mutation), context)
                    }
                },
            )
        }
    }
}
