package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.notices.*
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.components.*
import at.bernhardberger.tvhplayer.ui.rememberCloseBehindPlayer
import at.bernhardberger.tvhplayer.ui.rememberPlayerEntryPending
import at.bernhardberger.tvhplayer.ui.screens.*
import at.bernhardberger.tvhplayer.ui.common.programmeEpisodeLine
import at.bernhardberger.tvhplayer.ui.screens.recordings.PendingRecordingAction
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingConfirmationDialog
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Home uses the same recording policy, choice, details and mutation confirmation surfaces as DVR. */
@OptIn(ExperimentalTvheadendApi::class)
@Composable
internal fun HomeRecordingActions(
    entry: DvrEntry,
    observation: SessionObservation,
    contextMenu: Boolean,
    nowSec: Long,
    onPlay: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit,
    onWatchChannel: (ChannelId, String) -> Unit,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit,
    onReturnToRecording: (DvrEntryId, () -> Unit) -> Boolean,
    onClose: () -> Unit,
) {
    val opening = remember { observation }
    val frameOwner = LocalTabOwner.current
    val owner = remember { frameOwner }
    val session: TvheadendSession = koinInject()
    val notices: NoticeCenter = koinInject()
    val scope = rememberCoroutineScope()
    val mutations = remember(session) { DvrMutationActions(session.dvrRepository) }
    val capability = opening.currentSession?.takeIf { it === observation.currentSession }
    val opened = if (capability != null && observation.dvrState is DvrRepositoryState.Current)
        observation.dvrEntry(entry.id)?.takeUnless(::recordingWasRemoved) else entry
    LaunchedEffect(capability, opened) {
        if (opening.currentSession != null && capability == null || opened == null) onClose()
    }
    if (opened == null) return
    val admission = capability?.let { observation.recordingPlaybackAdmission(opened) }
    val channel = opened.channelId?.let(observation::channel)
    val closeBehindPlayer = rememberCloseBehindPlayer()
    val pending = rememberPlayerEntryPending()
    val watchLive: (() -> Unit)? = if (recordingIsRunning(opened) && channel != null && capability != null) ({
        if (owner?.isCurrent != false && session.observation.value.currentSession === capability) {
            closeBehindPlayer(onClose); onWatchChannel(channel.id, channel.name.orEmpty())
        }
    }) else null
    val play: (RecordingPlaybackStart) -> Unit = { start ->
        val current = session.observation.value
        if (owner?.isCurrent != false && capability != null && current.currentSession === capability &&
            current.dvrEntry(opened.id)?.let(current::recordingPlaybackAdmission)?.let {
                recordingOpenAction(it) != RecordingOpenAction.DETAILS
            } == true) {
            closeBehindPlayer(onClose)
            onPlay(RecordingPlaybackSelection(capability, opened.id), start)
        }
    }
    var menu by remember { mutableStateOf(contextMenu) }
    var details by remember { mutableStateOf(false) }
    var initialAction by remember { mutableStateOf<BrowseContextAction?>(null) }
    var pendingMutation by remember { mutableStateOf<DvrMutationAction?>(null) }
    var mutationBusy by remember { mutableStateOf(false) }
    val pendingCurrent = pendingMutation?.recordingStateIsCurrent(observation) == true
    LaunchedEffect(capability, pendingCurrent) {
        if (pendingMutation != null && (capability == null || !pendingCurrent)) { pendingMutation = null; onClose() }
    }
    if (details) {
        RecordingDetailsHost(opened, opening, observation, { session.observation.value }, nowSec,
            mutations, notices, scope, onPlayRecording = { selection, start ->
                if (owner?.isCurrent != false) { closeBehindPlayer(onClose); onPlay(selection, start) }
            }, onClose = onClose,
            onWatchChannel = { id, name ->
                if (owner?.isCurrent != false) { closeBehindPlayer(onClose); onWatchChannel(id, name) }
            },
            onShowGuide = onShowGuide,
            onContinueWatching = {
                if (owner?.isCurrent != false) onReturnToRecording(opened.id) { closeBehindPlayer(onClose) }
            },
            initialAction = initialAction)
    } else if (menu) {
        val canModify = capability != null && (observation.sessionState as? SessionState.Ready)
            ?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
        BrowseContextMenu(
            title = opened.title.orEmpty(),
            channel = channel?.let { channelTitleText(it.number, it.name.orEmpty()) } ?: opened.channelName.orEmpty(),
            episode = programmeEpisodeLine(opened.episode, opened.subtitle, androidx.compose.ui.platform.LocalContext.current.resources),
            time = at.bernhardberger.tvhplayer.ui.screens.recordings.recordingTimeRange(opened.start?.epochSeconds,
                opened.stop?.epochSeconds, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
            summary = opened.summary?.takeIf(String::isNotBlank) ?: opened.description.orEmpty(),
            recordingState = opened.state,
            resumePositionMs = offeredResumePosition(admission)?.inWholeMilliseconds,
            playbackIndicator = watchingIndicator(opened.id),
            actions = recordingContextActions(opened, admission, canModify, canWatchChannel = watchLive != null,
                playing = capability != null && at.bernhardberger.tvhplayer.ui.warmRecordingId(
                    LocalWatchingTarget.current, LocalRecordingPlaybackSelection.current, observation.currentSession) == opened.id),
            modifier = Modifier.onPreviewKeyEvent { pending() || mutationBusy },
            onClose = onClose,
            backEnabled = pendingMutation == null && !mutationBusy,
            onAction = handle@{ action ->
                if (owner?.isCurrent == false) return@handle
                when (action) {
                    BrowseContextAction.CONTINUE_WATCHING -> onReturnToRecording(opened.id) { closeBehindPlayer(onClose) }
                    BrowseContextAction.WATCH, BrowseContextAction.RESUME -> play(RecordingPlaybackStart.RESUME)
                    BrowseContextAction.PLAY, BrowseContextAction.START_OVER -> play(RecordingPlaybackStart.START_OVER)
                    BrowseContextAction.WATCH_LIVE -> watchLive?.invoke()
                    BrowseContextAction.STOP -> capability?.let { pendingMutation = DvrMutationAction.Stop(it, opened.id) }
                    BrowseContextAction.CANCEL -> capability?.let { pendingMutation = DvrMutationAction.Cancel(it, opened.id) }
                    BrowseContextAction.DELETE, BrowseContextAction.REMOVE_ENTRY -> capability?.let {
                        pendingMutation = DvrMutationAction.Delete(it, opened.id)
                    }
                    BrowseContextAction.MARK_WATCHED, BrowseContextAction.MARK_UNWATCHED -> {
                        mutationBusy = true
                        scope.launch {
                            val current = session.observation.value
                            val latest = current.dvrEntry(opened.id)
                            if (owner?.isCurrent == false || capability == null || current.currentSession !== capability || latest == null ||
                                recordingWatchStateAction(latest, current.recordingPlaybackAdmission(latest),
                                    (current.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED) != action
                            ) { mutationBusy = false; return@launch }
                            val context = notices.context()
                            when (val result = mutations.updateWatchState(capability, opened.id, action == BrowseContextAction.MARK_WATCHED)) {
                                DvrProgressResult.Accepted -> Unit
                                is DvrProgressResult.Failed -> notices.post(
                                    Notice.DvrActionFailed(DvrMutationKind.UPDATE, result.toDvrMutationFeedback()), context)
                            }
                            mutationBusy = false
                            onClose()
                        }
                    }
                    else -> { menu = false; details = true; initialAction = action.takeUnless { it == BrowseContextAction.DETAILS } }
                }
            },
            artwork = { ProvideArtworkSession(capability, observation) {
                ProgrammeArtwork(opened.image ?: opened.fanartImage, channel?.icon, null, Modifier.fillMaxSize())
            } },
        )
    } else {
        val active = watchLive != null
        val choice = active || recordingOpenAction(admission) == RecordingOpenAction.RESUME_CHOICE
        LaunchedEffect(choice) { if (!choice) details = true }
        if (choice) RecordingPlaybackChoice(opened, admission, observation, channel, nowSec,
            onPlay = play, onClose = onClose,
            onWatchLive = watchLive)
    }
    pendingMutation?.takeIf { capability != null && pendingCurrent }?.let { mutation ->
        val action = when (mutation) {
            is DvrMutationAction.Stop -> PendingRecordingAction.STOP
            is DvrMutationAction.Cancel -> PendingRecordingAction.CANCEL
            else -> PendingRecordingAction.DELETE
        }
        val visibility = remember(mutation) { MutableTransitionState(false).apply { targetState = true } }
        RecordingConfirmationDialog(visibility = visibility, action = action, title = opened.title.orEmpty(),
            removeEntry = deleteRemovesEntryOnly(opened),
            backEnabled = !mutationBusy,
            channelName = channel?.name ?: opened.channelName.orEmpty(),
            dateAndTime = at.bernhardberger.tvhplayer.ui.common.formatRecordingSchedule(opened.start?.epochSeconds,
                dateOnly = action == PendingRecordingAction.DELETE,
                locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
            onDismiss = { if (!mutationBusy) { pendingMutation = null; onClose() } },
            onConfirm = {
                if (!mutationBusy && owner?.isCurrent != false) {
                    mutationBusy = true
                    scope.launch {
                        val current = session.observation.value
                        if (owner?.isCurrent == false || current.currentSession !== capability || !mutation.recordingStateIsCurrent(current)) {
                            mutationBusy = false; pendingMutation = null; onClose(); return@launch
                        }
                        val context = notices.context()
                        notices.postDvrFailure(mutation, mutations.execute(mutation), context)
                        mutationBusy = false
                        pendingMutation = null
                        onClose()
                    }
                }
            })
    }
}
