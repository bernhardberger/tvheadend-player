package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.DvrConfigChoice
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.channelContextActions
import at.bernhardberger.tvhplayer.core.chooseDvrConfig
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.components.BrowseContextMenu
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import at.bernhardberger.tvhplayer.ui.screens.guide.DvrConfigDialog
import coil3.ImageLoader
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun ChannelContextMenu(
    channelId: ChannelId,
    observation: SessionObservation,
    nowSec: Long,
    imageLoader: ImageLoader,
    onPlay: (LivePlaybackSelection, String) -> Unit,
    onShowGuide: (ChannelId) -> Unit,
    onClose: () -> Unit,
) {
    val opening = remember { observation }
    val channel = opening.channel(channelId) ?: return
    val event = remember { opening.eventAt(channelId, kotlin.time.Instant.fromEpochSeconds(nowSec)) }
    val capability = opening.currentSession?.takeIf { it === observation.currentSession }
    val recording = event?.let(observation::dvrEntryForProgramme)
    val canModify = capability != null && observation.dvrState is DvrRepositoryState.Current &&
        (observation.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    val session: TvheadendSession = koinInject()
    val notices: NoticeCenter = koinInject()
    val mutations = remember(session) { DvrMutationActions(session.dvrRepository) }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<DvrMutationAction?>(null) }
    var pendingAction by remember { mutableStateOf<ProgrammeAction?>(null) }
    var returnAction by remember { mutableStateOf<BrowseContextAction?>(null) }
    var configs by remember { mutableStateOf<DvrConfigChoice.RequiresSelection?>(null) }
    LaunchedEffect(capability) { if (capability == null) onClose() }
    if (pendingAction == null && configs == null) BrowseContextMenu(
        title = channel.name.orEmpty(),
        metadata = stringResource(R.string.context_channel_now, channel.number?.toString().orEmpty(),
            event?.title ?: stringResource(R.string.no_epg)),
        timeRange = event?.let { "${formatHm(it.start.epochSeconds)}–${formatHm(it.stop.epochSeconds)}" }.orEmpty(),
        actions = channelContextActions(event, nowSec, recording, canModify),
        onClose = onClose,
        initialAction = returnAction,
        onAction = { action ->
            returnAction = action
            when (action) {
                BrowseContextAction.WATCH -> capability?.let {
                    onClose()
                    onPlay(LivePlaybackSelection(it, channelId), channel.name.orEmpty())
                }
                BrowseContextAction.SHOW_GUIDE -> { onClose(); onShowGuide(channelId) }
                BrowseContextAction.RECORD -> if (event != null && capability != null) {
                    when (val choice = chooseDvrConfig(observation.currentDvrConfigurations())) {
                        is DvrConfigChoice.Automatic -> {
                            pending = DvrMutationAction.CreateProgramme(event.programmeRecordingTarget(capability), choice.configId)
                            pendingAction = ProgrammeAction.RECORD
                        }
                        is DvrConfigChoice.RequiresSelection -> configs = choice
                    }
                }
                BrowseContextAction.STOP -> if (recording != null && capability != null) {
                    pending = DvrMutationAction.Stop(capability, recording.id)
                    pendingAction = ProgrammeAction.STOP_RECORDING
                }
                BrowseContextAction.CANCEL -> if (recording != null && capability != null) {
                    pending = DvrMutationAction.Cancel(capability, recording.id)
                    pendingAction = ProgrammeAction.CANCEL_RECORDING
                }
                else -> Unit
            }
        },
        logo = { PiconBox(imageLoader = imageLoader, currentSession = capability, piconPath = channel.icon,
            modifier = Modifier.width(56.dp).height(40.dp)) },
    )
    configs?.let { choice ->
        DvrConfigDialog(configs = choice.configs, onDismiss = onClose, onSelect = { config ->
            configs = null
            if (event != null && capability != null) {
                pending = DvrMutationAction.CreateProgramme(event.programmeRecordingTarget(capability), config.id)
                pendingAction = ProgrammeAction.RECORD
            }
        })
    }
    pendingAction?.let { action ->
        ConfirmProgrammeActionDialog(action = action, programmeTitle = event?.title.orEmpty(),
            channelName = channel.name.orEmpty(),
            dateAndTime = at.bernhardberger.tvhplayer.ui.common.formatRecordingSchedule(event?.start?.epochSeconds,
                event?.stop?.epochSeconds?.takeIf { action == ProgrammeAction.RECORD }),
            onDismiss = { pendingAction = null; pending = null }, onConfirm = {
                val mutation = currentDvrMutation(pending, opening, session.observation.value)
                pendingAction = null
                pending = null
                if (mutation != null) scope.launch {
                    val current = currentDvrMutation(mutation, opening, session.observation.value) ?: return@launch
                    val context = notices.context()
                    notices.postDvrFailure(current, mutations.execute(current), context)
                }
            })
    }
}
