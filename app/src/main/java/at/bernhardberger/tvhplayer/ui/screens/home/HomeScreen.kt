package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.focus.FocusRequester
import at.bernhardberger.tvhplayer.ui.components.AppErrorAlert
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.ui.screens.ChannelContextMenu
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.rememberCloseBehindPlayer
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import org.koin.compose.koinInject
import kotlin.time.Instant

/**
 * Home's landing page. The page itself, including the scroll-away header, lives in
 * [HomeContent]. All rows use the same retained metadata and selected scope as Channels and Guide.
 */
@Composable
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
internal fun HomeScreen(
    contentPadding: PaddingValues,
    initialFocusEnabled: Boolean,
    channelsVm: ChannelsViewModel,
    playingChannelId: ChannelId?,
    playingRecordingId: DvrEntryId?,
    onPlay: (ChannelId, String) -> Unit,
    onPlayRecording: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit,
    onReturnToRecording: (DvrEntryId, () -> Unit) -> Boolean,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit,
    onGenre: (ProgrammeCategory) -> Unit,
    onRecordings: (DvrLibraryMode) -> Unit,
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    connectionFailure: ConnectionUiState? = connectionUiState,
    onRetry: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    lastPlayedStore: LastPlayedChannelStore = koinInject(),
) {
    val scope by channelsVm.scope.collectAsStateWithLifecycle()
    val observation by channelsVm.observation.collectAsStateWithLifecycle()
    val recents by lastPlayedStore.recentChannelIds.collectAsStateWithLifecycle(emptyList())
    val nowSec = rememberCurrentEpochSeconds()()
    val playing = playingRecordingId?.let(HomePlaying::Recording)
        ?: playingChannelId?.let(HomePlaying::LiveChannel)
    val input = remember(scope, observation, recents, playing, nowSec) {
        val channels = scope.scope.visibleChannels
        HomeInput(
            channels = channels,
            currentEvents = channels.mapNotNull { channel ->
                observation.eventAt(channel.id, Instant.fromEpochSeconds(nowSec))?.let { channel.id to it }
            }.toMap(),
            upcomingEvents = guideDisplayEvents(observation.epgSnapshotForDisplay),
            recentChannelIds = recents,
            dvrEntries = observation.dvrSnapshotForDisplay?.entries.orEmpty(),
            admission = observation::recordingPlaybackAdmission,
            playing = playing,
            nowSec = nowSec,
        )
    }
    HomeScreenContent(input, observation, contentPadding, initialFocusEnabled, onPlay,
        onPlayRecording, onReturnToRecording, onShowGuide, onGenre, onRecordings, modifier,
        connectionUiState, connectionFailure, onRetry, onOpenConnectionSettings)
}

@Composable
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
internal fun HomeScreenContent(
    input: HomeInput,
    observation: SessionObservation,
    contentPadding: PaddingValues,
    initialFocusEnabled: Boolean,
    onPlay: (ChannelId, String) -> Unit,
    onPlayRecording: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit,
    onReturnToRecording: (DvrEntryId, () -> Unit) -> Boolean,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit,
    onGenre: (ProgrammeCategory) -> Unit,
    onRecordings: (DvrLibraryMode) -> Unit,
    modifier: Modifier = Modifier,
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    connectionFailure: ConnectionUiState? = connectionUiState,
    onRetry: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = {},
) {
    val nowSec = input.nowSec
    val playingRecordingId = (input.playing as? HomePlaying.Recording)?.entryId
    val sections = remember(input) { homeSections(input) }
    var focused by remember { mutableStateOf<HomeFocusKey?>(null) }
    var channelMenu by remember { mutableStateOf<ChannelId?>(null) }
    var channelDetails by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<DvrEntry?>(null) }
    var recordingMenu by remember { mutableStateOf(false) }
    val mutationScope = rememberCoroutineScope()
    val mutationInFlight = remember { mutableStateOf<DvrMutationAction?>(null) }
    val closeBehindPlayer = rememberCloseBehindPlayer()
    fun openRecording(entry: DvrEntry, menu: Boolean) {
        if (!menu && entry.id == playingRecordingId &&
            onReturnToRecording(entry.id) { closeBehindPlayer { recording = null } }) return
        val capability = observation.currentSession
        val action = recordingOpenAction(observation.recordingPlaybackAdmission(entry))
        if (!menu && !recordingIsRunning(entry) && capability != null &&
            (action == RecordingOpenAction.START_OVER || action == RecordingOpenAction.RESUME)) {
            onPlayRecording(RecordingPlaybackSelection(capability, entry.id),
                if (action == RecordingOpenAction.RESUME) RecordingPlaybackStart.RESUME else RecordingPlaybackStart.START_OVER)
        } else { recordingMenu = menu; recording = entry }
    }
    val activate: (HomeFocusKey, Boolean) -> Unit = { key, menu ->
        when (val section = sections.firstOrNull { it.kind == key.section }) {
            is HomeSection.CurrentActivity -> section.items.getOrNull(key.position)?.let { item ->
                when (val current = item.playing) {
                    is HomePlaying.LiveChannel -> if (!menu) observation.channel(current.channelId)
                        ?.let { onPlay(it.id, it.name.orEmpty()) }
                    is HomePlaying.Recording -> if (!menu) onReturnToRecording(current.entryId) {}
                    null -> item.recording?.let { openRecording(it, menu) }
                }
            }
            is HomeSection.RecentChannels -> section.items.getOrNull(key.position)?.channel?.let {
                if (menu) { channelDetails = false; channelMenu = it.id } else onPlay(it.id, it.name.orEmpty())
            }
            is HomeSection.OnNow -> section.items.getOrNull(key.position)?.channel?.let {
                if (menu) { channelDetails = false; channelMenu = it.id } else onPlay(it.id, it.name.orEmpty())
            }
            is HomeSection.Genres -> if (!menu) section.items.getOrNull(key.position)?.let { onGenre(it.category) }
            is HomeSection.ContinueWatching -> if (key.item == "all-recordings") {
                if (!menu) onRecordings(DvrLibraryMode.ARCHIVE)
            } else section.items.getOrNull(key.position)?.let { openRecording(it.entry, menu) }
            is HomeSection.RecentlyRecorded -> if (key.item == "all-recordings") {
                if (!menu) onRecordings(DvrLibraryMode.ARCHIVE)
            } else section.items.getOrNull(key.position)?.let { openRecording(it.entry, menu) }
            is HomeSection.Upcoming -> if (!menu) onRecordings(DvrLibraryMode.SCHEDULE)
            null -> Unit
        }
    }
    val error = if (sections.isEmpty()) connectionFailure?.browseAppError()
        ?: connectionUiState.browseAppError(noChannels = true).takeIf { connectionUiState == ConnectionUiState.Ready }
        else null
    val errorFocus = remember { FocusRequester() }
    LaunchedEffect(error, initialFocusEnabled) {
        if (error != null && initialFocusEnabled) { withFrameNanos { }; errorFocus.requestFocus() }
    }
    if (error != null) AppErrorAlert(error = error, actionFocus = errorFocus,
        leadingExit = LocalBrowseNavigationFocus.current ?: FocusRequester.Default,
        modifier = modifier.fillMaxSize(), onAction = {
            when (it) {
                AppErrorAction.RETRY -> onRetry()
                AppErrorAction.CONNECTION_SETTINGS -> onOpenConnectionSettings()
                AppErrorAction.CLOSE -> Unit
            }
        }) else HomeContent(
        sections = sections,
        contentPadding = contentPadding,
        initialFocus = focused?.let { previous ->
            if (previous.item == "all-recordings" && sections.any { it.kind == previous.section }) previous
            else homeRestoreFocus(sections, previous)
        } ?: homeInitialFocus(sections),
        onFocused = { focused = it },
        modifier = modifier,
        focusEnabled = initialFocusEnabled && channelMenu == null && recording == null,
        observation = observation,
        input = input,
        onActivate = { activate(it, false) },
        onContextMenu = { activate(it, true) },
    )
    channelMenu?.let { id ->
        ChannelContextMenu(id, observation, { nowSec }, mutationScope, mutationInFlight,
            onPlay = onPlay, onShowGuide = onShowGuide,
            onClose = { channelMenu = null }, showDetails = channelDetails,
            onShowDetails = { channelDetails = true },
            onPlayRecording = { onPlayRecording(it, RecordingPlaybackStart.START_OVER) })
    }
    recording?.let { entry ->
        HomeRecordingActions(entry, observation, recordingMenu, nowSec, onPlayRecording,
            onPlay, onShowGuide, onReturnToRecording, onClose = { recording = null })
    }
}
