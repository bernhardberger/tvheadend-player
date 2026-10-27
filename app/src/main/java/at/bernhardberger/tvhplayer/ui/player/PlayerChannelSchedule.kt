package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.toConnectionUiState
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.components.*
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import kotlin.time.Instant

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
    connectionUiState: ConnectionUiState? = null,
    session: TvheadendSession,
) {
    val historyStart = scheduleHistoryStart(timeshift)
    val snapshot = observation.epgSnapshotForDisplay
    val channelEvents = remember(snapshot, channelId) {
        channelScheduleEvents(snapshot?.events.orEmpty(), channelId, Long.MIN_VALUE, null)
    }
    val events = remember(channelEvents, nowSec, historyStart) {
        channelEvents.filter { it.stop.epochSeconds > (historyStart ?: nowSec) }
    }
    val connection = connectionUiState ?: observation.sessionState.toConnectionUiState()
    val viewerSec = liveViewerEpochSec(channelId, nowSec).takeIf { channelWatchingIndicator(channelId).isPlayingItem }
    // The startup guide holds only hours ahead; ask for the Guide's reach for this channel.
    val throughSec = remember(channelId) { scheduleCoverageThroughSec(nowSec, ZoneId.systemDefault()) }
    // The request still running, if any; a replaced request's late cleanup leaves its successor's alone.
    var pendingRequest by remember(channelId) { mutableStateOf<Any?>(null) }
    val currentSession = observation.currentSession
    LaunchedEffect(channelId, currentSession, throughSec) {
        val capability = currentSession ?: return@LaunchedEffect
        val request = Any()
        pendingRequest = request
        try {
            withTimeoutOrNull(SCHEDULE_COVERAGE_TIMEOUT_MS) {
                withContext(Dispatchers.Default) {
                    session.epgRepository.acquireCoverage(capability, channelId, Instant.fromEpochSeconds(throughSec))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Repository failures settle the request; connection state owns user feedback.
        } finally {
            if (pendingRequest === request) pendingRequest = null
        }
    }
    val moreLoading = scheduleCoveragePending(connection, observation.epgState, channelId, throughSec, pendingRequest != null)
    var focused by remember(channelId) { mutableStateOf(scheduleInitialEvent(events, viewerSec, nowSec)) }
    // Survives the details page replacing the list, so Back after details still reaches the tabs.
    var openingEvent by remember(channelId) { mutableStateOf<EventId?>(null) }
    var contextMenu by remember { mutableStateOf(false) }
    val details = layers.scheduleDetails
    if (details != null) LivePlayerProgrammeDetails(details, channel, observation, currentObservation, nowSec,
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
    else ProvideArtworkSession(observation.currentSession, observation) {
        PlaybackOptionsOverlayFrame(panelTag = "channel-schedule-panel", panelWidth = 480.dp, showScrim = false) {
            ChannelSchedule(channel, events, nowSec, viewerSec,
                 loading = scheduleLoading(connection, observation.epgState) || moreLoading,
                 offline = scheduleOffline(connection, observation.epgState),
                recording = { observation.dvrEntryForEvent(it)?.state },
                onOpen = { contextMenu = false; layers.showScheduleDetails(it) },
                onContextMenu = { contextMenu = true; layers.showScheduleDetails(it) },
                onClose = { layers.closeSchedule() }, onRetry = onRetry,
                initialEvent = focused ?: scheduleInitialEvent(events, viewerSec, nowSec), onFocused = { focused = it },
                viewerBehindMs = liveBehindMs(timeshift),
                onBackKeyDown = { layers.beginOpeningKeyCycle(android.view.KeyEvent.KEYCODE_BACK) },
                openingEvent = openingEvent, onOpeningEvent = { openingEvent = it })
        }
    }
}

@Composable
internal fun rememberScheduleAvailable(observation: SessionObservation, channelId: ChannelId, nowSec: Long): Boolean {
    val snapshot = observation.epgSnapshotForDisplay
    val end = remember(snapshot, channelId) {
        snapshot?.events?.asSequence()?.filter { it.channelId == channelId && it.stop > it.start }
            ?.maxOfOrNull { it.stop.epochSeconds }
    }
    return end != null && end > nowSec
}

/** Seven days for one channel is a larger query than a Guide page; after this the end footer shows. */
private const val SCHEDULE_COVERAGE_TIMEOUT_MS = 30_000L
