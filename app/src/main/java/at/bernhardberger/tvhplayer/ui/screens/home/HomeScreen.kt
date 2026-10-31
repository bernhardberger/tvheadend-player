package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.key.onPreviewKeyEvent
import at.bernhardberger.tvhplayer.ui.components.TabOwner
import at.bernhardberger.tvhplayer.ui.components.LocalTabOwner
import at.bernhardberger.tvhplayer.ui.components.AppErrorAlert
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
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
    val owner = remember(scope.scope, observation.currentSession, scope.observation?.currentSession) {
        val frameScope = scope.scope
        val frameSession = observation.currentSession
        val scopeSession = scope.observation?.currentSession
        TabOwner {
            channelsVm.scope.value.scope == frameScope &&
                channelsVm.observation.value.currentSession === frameSession &&
                scopeSession === frameSession
        }
    }
    DisposableEffect(owner) { onDispose { owner.release() } }
    fun current(channelId: ChannelId? = null) = owner.isCurrent &&
        (channelId == null || channelsVm.scope.value.scope.visibleChannels.any { it.id == channelId })
    val recents by lastPlayedStore.recentChannelIds.collectAsStateWithLifecycle(emptyList())
    val nowSec = rememberCurrentEpochSeconds()()
    val playing = playingRecordingId?.let(HomePlaying::Recording)
        ?: playingChannelId?.let(HomePlaying::LiveChannel)
    val alignedInput = remember(scope, observation, recents, playing, nowSec) {
        homeInput(scope, observation, recents, playing, nowSec)
    }
    // Retain the input together with both observations: programme pictures and saved channel
    // logos must keep the authority of the same aligned frame while the two flows catch up.
    var retainedFrame by remember { mutableStateOf<Triple<HomeInput, SessionObservation, SessionObservation?>?>(null) }
    val frame = alignedInput?.let { Triple(it, observation, scope.observation) } ?: retainedFrame
    SideEffect { if (alignedInput != null) retainedFrame = frame }
    if (frame == null) return
    CompositionLocalProvider(LocalTabOwner provides owner) {
        ProvideArtworkSession(frame.second.currentSession, frame.third) {
        HomeScreenContent(frame.first, frame.second, contentPadding, initialFocusEnabled,
            onPlay = { id, name -> if (current(id)) onPlay(id, name) },
            onPlayRecording = { selection, start ->
                if (owner.isCurrent) onPlayRecording(selection, start)
            },
            onReturnToRecording = { id, before -> owner.isCurrent && onReturnToRecording(id, before) },
            onShowGuide = { id, event -> if (current(id)) onShowGuide(id, event) },
            onGenre = { if (current()) onGenre(it) }, onRecordings = { if (current()) onRecordings(it) },
            modifier = modifier, connectionUiState = connectionUiState, connectionFailure = connectionFailure,
            onRetry = onRetry, onOpenConnectionSettings = onOpenConnectionSettings)
        }
    }
}

@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
internal fun homeInput(
    scope: ChannelScopeState,
    observation: SessionObservation,
    recents: List<ChannelId>,
    playing: HomePlaying?,
    nowSec: Long,
): HomeInput? {
    if (scope.observation?.currentSession !== observation.currentSession) return null
    val channels = scope.scope.visibleChannels
    return HomeInput(
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
    val owner = LocalTabOwner.current
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
        if (owner?.isCurrent == false) return
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
    val activate: (HomeFocusKey, Boolean) -> Unit = activate@{ key, menu ->
        if (owner?.isCurrent == false) return@activate
        when (val section = sections.firstOrNull { it.kind == key.section }) {
            is HomeSection.CurrentActivity -> section.items.getOrNull(key.position)?.let { item ->
                when (val current = item.playing) {
                    is HomePlaying.LiveChannel -> if (!menu) observation.channel(current.channelId)
                        ?.let { onPlay(it.id, it.name.orEmpty()) }
                    is HomePlaying.Recording -> if (menu || !onReturnToRecording(current.entryId) {}) {
                        input.dvrEntries.firstOrNull { it.id == current.entryId }?.let { openRecording(it, menu) }
                    }
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
        val openingOwner = remember(id) { owner }
        Box(Modifier.onPreviewKeyEvent { openingOwner?.isCurrent == false }) {
            ChannelContextMenu(id, observation, { nowSec }, mutationScope, mutationInFlight,
                onPlay = onPlay, onShowGuide = onShowGuide,
                onClose = { channelMenu = null }, showDetails = channelDetails,
                onShowDetails = { channelDetails = true },
                onPlayRecording = { onPlayRecording(it, RecordingPlaybackStart.START_OVER) })
        }
        LaunchedEffect(owner) { if (openingOwner?.isCurrent == false) channelMenu = null }
    }
    recording?.let { entry ->
        val openingOwner = remember(entry.id) { owner }
        Box(Modifier.onPreviewKeyEvent { openingOwner?.isCurrent == false }) {
            HomeRecordingActions(entry, observation, recordingMenu, nowSec, onPlayRecording,
                onPlay, onShowGuide, onReturnToRecording, onClose = { recording = null })
        }
        LaunchedEffect(owner) { if (openingOwner?.isCurrent == false) recording = null }
    }
}
