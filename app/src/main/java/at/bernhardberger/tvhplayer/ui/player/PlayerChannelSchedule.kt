package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.components.*
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import kotlinx.coroutines.CoroutineScope

/** Player-only sheet and details navigation; the column itself has no player dependency. */
@Composable
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
internal fun PlayerChannelSchedule(
    layers: LivePlayerLayerState, channelId: ChannelId, channel: Channel?, observation: SessionObservation,
    currentObservation: () -> SessionObservation, nowSec: Long, timeshift: AppTimeshiftState,
    mutations: DvrMutationActions, notices: NoticeCenter, scope: CoroutineScope,
    settings: UiSettingsStore, onRetry: () -> Unit,
    onPlay: (ChannelId, String) -> Unit,
    onPlayRecording: ((RecordingPlaybackSelection) -> Unit)?,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit,
) {
    val historyStart = scheduleHistoryStart(timeshift)
    val snapshot = observation.epgSnapshotForDisplay
    val events = remember(snapshot, channelId, nowSec, historyStart) {
        channelScheduleEvents(snapshot?.events.orEmpty(), channelId, nowSec, historyStart)
    }
    val viewerSec = liveViewerEpochSec(channelId, nowSec).takeIf { channelWatchingIndicator(channelId).isPlayingItem }
    var focused by remember { mutableStateOf(scheduleInitialEvent(events, viewerSec, nowSec)) }
    var contextMenu by remember { mutableStateOf(false) }
    val details = layers.scheduleDetails
    if (details != null) {
        LivePlayerProgrammeDetails(details, channel, observation, currentObservation, nowSec,
            mutations, notices, scope, settings,
            onPlay = { id, name -> layers.closeSchedule(false); onPlay(id, name) },
            onPlayRecording = onPlayRecording?.let { play -> { selection -> layers.closeSchedule(false); play(selection) } },
            onShowGuide = { id, event -> layers.closeSchedule(false); onShowGuide(id, event) },
            onClose = { layers.showScheduleDetails(null) },
            onContinueWatching = { layers.closeSchedule(false) },
            showContextMenu = contextMenu,
            onShowDetails = { contextMenu = false },
            onBackKeyDown = { layers.beginOpeningKeyCycle(android.view.KeyEvent.KEYCODE_BACK) },
            framedArt = details.id != scheduleInitialEvent(events, viewerSec, nowSec)?.id)
    } else ProvideArtworkSession(observation.currentSession, observation) {
        PlaybackOptionsOverlayFrame(paneTitle = stringResource(R.string.channel_schedule_pane,
            channel?.name ?: stringResource(R.string.epg_search_unknown_channel)),
            panelTag = "channel-schedule-panel", panelWidth = 480.dp, showScrim = false) {
            ChannelSchedule(channel, events, nowSec, viewerSec,
                loading = observation.epgState is EpgRepositoryState.Synchronizing || observation.epgState is EpgRepositoryState.Empty,
                offline = observation.epgState is EpgRepositoryState.Stale,
                recording = { observation.dvrEntryForEvent(it)?.state },
                onOpen = { contextMenu = false; layers.showScheduleDetails(it) },
                onContextMenu = { contextMenu = true; layers.showScheduleDetails(it) },
                onClose = { layers.closeSchedule() }, onRetry = onRetry,
                initialEvent = focused ?: scheduleInitialEvent(events, viewerSec, nowSec), onFocused = { focused = it })
        }
    }
}
