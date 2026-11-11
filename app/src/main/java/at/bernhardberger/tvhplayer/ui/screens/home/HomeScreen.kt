package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.ui.screens.recordChannelProgramme
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
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.screens.ChannelsGroup
import at.bernhardberger.tvhplayer.ui.screens.channelsGroups
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Home's landing page. The page itself, including the scroll-away header, lives in
 * [HomeContent]. All rows use the same retained metadata and selected scope as Channels and Guide.
 */
@Composable
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
internal fun HomeScreen(
    lastUpdateTime: Long?,
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
    onRecordings: (DvrLibraryMode, DvrEntryId?) -> Unit,
    modifier: Modifier = Modifier,
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    connectionFailure: ConnectionUiState? = connectionUiState,
    onRetry: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = {},
    onBrowseChannels: () -> Unit = {},
    focusMemory: MutableState<HomeFocusKey?> = remember { mutableStateOf(null) },
    lastPlayedStore: LastPlayedChannelStore = koinInject(),
    onContentDrawn: () -> Unit = {},
) {
    val scope by channelsVm.scope.collectAsStateWithLifecycle()
    val observation by channelsVm.observation.collectAsStateWithLifecycle()
    val settingsStore: UiSettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsStateWithLifecycle<UiSettings?>(null)
    val saveScope = rememberCoroutineScope()
    val recordSession: TvheadendSession = koinInject()
    val recordNotices: NoticeCenter = koinInject()
    val recordMutations = remember(recordSession) { DvrMutationActions(recordSession.dvrRepository) }
    val recordInFlight = remember { mutableStateOf<DvrMutationAction?>(null) }
    var hintNow by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        hintNow = System.currentTimeMillis()
        onPauseOrDispose { }
    }
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
    val recentRecordings by lastPlayedStore.recentRecordingIds.collectAsStateWithLifecycle(emptyList())
    val nowSec = rememberCurrentEpochSeconds()()
    val playing = playingRecordingId?.let(HomePlaying::Recording)
        ?: playingChannelId?.let(HomePlaying::LiveChannel)
    val alignedInput = remember(scope, observation, recents, recentRecordings, playing, nowSec) {
        homeInput(scope, observation, recents, playing, nowSec)?.copy(recentRecordingIds = recentRecordings)
    }
    // Retain the input together with both observations: programme pictures and saved channel
    // logos must keep the authority of the same aligned frame while the two flows catch up.
    var retainedFrame by remember { mutableStateOf<Triple<HomeInput, SessionObservation, SessionObservation?>?>(null) }
    val frame = alignedInput?.let { Triple(it, observation, scope.observation) } ?: retainedFrame
    SideEffect { if (alignedInput != null) retainedFrame = frame }
    val loadedSettings = settings ?: return
    if (frame == null || !scope.settingsLoaded) return
    var laidOut by remember { mutableStateOf(false) }
    val latestOnContentDrawn by rememberUpdatedState(onContentDrawn)
    LaunchedEffect(laidOut) {
        if (laidOut) {
            // Layout runs in the frame being drawn. Release startup on the following frame,
            // never from composition while Home's first layout is still pending.
            withFrameNanos { }
            latestOnContentDrawn()
        }
    }
    CompositionLocalProvider(LocalTabOwner provides owner) {
        ProvideArtworkSession(frame.second.currentSession, frame.third) {
        HomeScreenContent(frame.first.copy(hiddenSections = loadedSettings.hiddenHomeSections), frame.second, contentPadding, initialFocusEnabled,
            onRecordKey = { channelId -> if (current(channelId)) recordMutations.recordChannelProgramme(channelId,
                { recordSession.observation.value }, { System.currentTimeMillis() / 1000L }, settingsStore,
                recordNotices, saveScope, recordInFlight) else null },
            updateHintShown = at.bernhardberger.tvhplayer.settings.whatsNewHintShown(
                loadedSettings.whatsNewSeenVersion, at.bernhardberger.tvhplayer.BuildConfig.VERSION_CODE, lastUpdateTime, hintNow),
            onWhatsNewOpened = { saveScope.launch { settingsStore.markWhatsNewSeen(at.bernhardberger.tvhplayer.BuildConfig.VERSION_CODE) } },
            focusMemory = focusMemory,
            onPlay = { id, name -> if (current(id)) onPlay(id, name) },
            onPlayRecording = { selection, start ->
                if (owner.isCurrent) onPlayRecording(selection, start)
            },
            onReturnToRecording = { id, before -> owner.isCurrent && onReturnToRecording(id, before) },
            onShowGuide = { id, event -> if (current(id)) onShowGuide(id, event) },
            // A recording's channel may sit outside the selected tag; Recordings acts on it as well.
            onWatchRecordingChannel = { id, name -> if (owner.isCurrent) onPlay(id, name) },
            onShowRecordingGuide = { id, event -> if (owner.isCurrent) onShowGuide(id, event) },
            onGenre = { if (current()) onGenre(it) }, onRecordings = { mode, entryId -> if (current()) onRecordings(mode, entryId) },
            modifier = modifier.onGloballyPositioned { laidOut = true }, connectionUiState = connectionUiState, connectionFailure = connectionFailure,
            onRetry = onRetry, onOpenConnectionSettings = onOpenConnectionSettings,
            onBrowseChannels = { if (current()) onBrowseChannels() },
            groups = channelsGroups(scope.scope), activeTagId = scope.scope.activeTagId,
            onSelectTag = { if (owner.isCurrent) channelsVm.selectTag(it) },
            onSectionVisibleChange = { section, visible -> if (owner.isCurrent) saveScope.launch { settingsStore.setHomeSectionVisible(section, visible) } },
            catalogCurrent = scope.channelCatalogCurrent && scope.settingsLoaded,
            hasChannelsOutsideScope = scope.scope.allChannels.isNotEmpty())
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
        dvrEntries = observation.dvrSnapshotForDisplay?.entries.orEmpty().filterNot(::recordingWasRemoved),
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
    onRecordings: (DvrLibraryMode, DvrEntryId?) -> Unit,
    modifier: Modifier = Modifier,
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    connectionFailure: ConnectionUiState? = connectionUiState,
    onRetry: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = {},
    onBrowseChannels: () -> Unit = {},
    onWatchRecordingChannel: (ChannelId, String) -> Unit = onPlay,
    onShowRecordingGuide: (ChannelId, EpgEvent?) -> Unit = onShowGuide,
    groups: List<ChannelsGroup> = emptyList(),
    activeTagId: ChannelTagId? = null,
    onSelectTag: (ChannelTagId?) -> Unit = {},
    onSectionVisibleChange: (HomeSectionKind, Boolean) -> Unit = { _, _ -> },
    catalogCurrent: Boolean = observation.channelState.channelCatalogAuthority == RetainedMetadataAuthority.CURRENT,
    hasChannelsOutsideScope: Boolean = observation.channelState.channelCatalogForDisplay?.channels?.isNotEmpty() == true,
    focusMemory: MutableState<HomeFocusKey?> = remember { mutableStateOf(null) },
    updateHintShown: Boolean = false,
    onWhatsNewOpened: () -> Unit = {},
    onRecordKey: (ChannelId) -> BrowseContextAction?,
) {
    val nowSec = input.nowSec
    val owner = LocalTabOwner.current
    val playingRecordingId = (input.playing as? HomePlaying.Recording)?.entryId
    val sections = remember(input) { homeSections(input) }
    var focused by focusMemory
    // Startup priority applies until a card lands; later metadata must not replace that card.
    var followInitialFocus by remember { mutableStateOf(focused == null) }
    var hadFocusOwnership by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(false) }
    val tagsFocus = remember { FocusRequester() }
    var restoreHeader by remember { mutableStateOf(false) }
    var restoreTagHeader by remember { mutableStateOf(false) }
    val viewFocus = remember { FocusRequester() }
    var channelMenu by remember { mutableStateOf<ChannelId?>(null) }
    var channelDetails by remember { mutableStateOf(false) }
    var recordInitialAction by remember { mutableStateOf<BrowseContextAction?>(null) }
    var cardFocused by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<DvrEntry?>(null) }
    var recordingMenu by remember { mutableStateOf(false) }
    val mutationScope = rememberCoroutineScope()
    val mutationInFlight = remember { mutableStateOf<DvrMutationAction?>(null) }
    fun recordFocusedProgramme(channelId: ChannelId) {
        when (val action = onRecordKey(channelId)) {
            BrowseContextAction.STOP, BrowseContextAction.CANCEL -> {
                recordInitialAction = action
                channelDetails = true
                channelMenu = channelId
            }
            else -> Unit
        }
    }
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
            is HomeSection.RecentChannels -> if (key.item == "all-channels") {
                if (!menu) onBrowseChannels()
            } else section.items.getOrNull(key.position)?.channel?.let {
                if (menu) { channelDetails = false; channelMenu = it.id } else onPlay(it.id, it.name.orEmpty())
            }
            is HomeSection.OnNow -> section.items.getOrNull(key.position)?.channel?.let {
                if (menu) { channelDetails = false; channelMenu = it.id } else onPlay(it.id, it.name.orEmpty())
            }
            is HomeSection.Genres -> if (!menu) section.items.getOrNull(key.position)?.let { onGenre(it.category) }
            is HomeSection.ContinueWatching -> if (key.item == "all-recordings") {
                if (!menu) onRecordings(DvrLibraryMode.ARCHIVE, null)
            } else section.items.getOrNull(key.position)?.let { openRecording(it.entry, menu) }
            is HomeSection.RecentlyRecorded -> if (key.item == "all-recordings") {
                if (!menu) onRecordings(DvrLibraryMode.ARCHIVE, null)
            } else section.items.getOrNull(key.position)?.let { openRecording(it.entry, menu) }
            is HomeSection.Upcoming -> if (!menu) {
                if (key.item == "all-scheduled") onRecordings(DvrLibraryMode.SCHEDULE, null)
                else section.items.getOrNull(key.position)?.entry?.let { onRecordings(DvrLibraryMode.SCHEDULE, it.id) }
            }
            null -> Unit
        }
    }
    val error = if (sections.isEmpty()) connectionFailure?.browseAppError()
        ?: connectionUiState.browseAppError(noChannels = catalogCurrent && input.channels.isEmpty() && !hasChannelsOutsideScope)
            .takeIf { connectionUiState == ConnectionUiState.Ready }
        else null
    // The empty slot outlives the first rows until a card takes focus: swapping the alert for the
    // empty state in that moment would drop its focused action onto the navigation rail.
    var lastEmptyError by remember { mutableStateOf(error) }
    SideEffect { if (sections.isEmpty()) lastEmptyError = error }
    val shownError = if (sections.isEmpty()) error else lastEmptyError
    // Known channels with no rows yet only mean an empty Home once the guide and recordings have
    // synchronized. Saved data arrives moments after the catalog on a cold start.
    val waiting = sections.isEmpty() && error == null && when {
        input.channels.isEmpty() -> !catalogCurrent
        input.hiddenSections.size == HomeSectionKind.entries.size -> false
        else -> !catalogCurrent || observation.epgSnapshotAuthority != RetainedMetadataAuthority.CURRENT ||
            observation.dvrSnapshotAuthority != RetainedMetadataAuthority.CURRENT
    }
    var loadingDelayed by remember(waiting) { mutableStateOf(false) }
    LaunchedEffect(waiting) {
        if (waiting) { delay(10_000); loadingDelayed = true }
    }
    // Rows that never arrive still end in the real empty Home, with its way into Channels.
    val loading = waiting && (input.channels.isEmpty() || !loadingDelayed)
    val openOptions = { if (owner?.isCurrent != false) { restoreTagHeader = false; restoreHeader = true; options = true } }
    val errorFocus = remember { FocusRequester() }
    val contentFocusEnabled = initialFocusEnabled && channelMenu == null && recording == null && !options && !tags
    LaunchedEffect(contentFocusEnabled) {
        if (contentFocusEnabled) hadFocusOwnership = true
        else if (hadFocusOwnership) followInitialFocus = false
    }
    HomeContent(
        updateHintShown = updateHintShown,
        onWhatsNewOpened = onWhatsNewOpened,
        sections = sections,
        contentPadding = contentPadding,
        initialFocus = if (followInitialFocus) homeInitialFocus(sections) else focused?.let { previous ->
            if (previous.item in HomeEndCardKeys && sections.any { it.kind == previous.section }) previous
            else homeRestoreFocus(sections, previous)
        } ?: homeInitialFocus(sections),
        onFocused = { focused = it; cardFocused = true; followInitialFocus = false; restoreHeader = false },
        modifier = modifier.onPreviewKeyEvent { key ->
            // Drawer entry may deliver only the release to Home; it is still viewer input.
            followInitialFocus = false
            if (key.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_MEDIA_RECORD) false
            else {
                if (key.type == KeyEventType.KeyDown && key.nativeKeyEvent.repeatCount == 0 &&
                    contentFocusEnabled && cardFocused && owner?.isCurrent != false) {
                    val focus = focused
                    val section = sections.firstOrNull { it.kind == focus?.section }
                    val channelId = when (section) {
                        is HomeSection.CurrentActivity -> (section.items.getOrNull(focus?.position ?: -1)?.playing as? HomePlaying.LiveChannel)?.channelId
                        is HomeSection.RecentChannels -> if (focus?.item == "all-channels") null else section.items.getOrNull(focus?.position ?: -1)?.channel?.id
                        is HomeSection.OnNow -> section.items.getOrNull(focus?.position ?: -1)?.channel?.id
                        else -> null
                    }
                    if (channelId != null) recordFocusedProgramme(channelId)
                }
                true
            }
        },
        followInitialFocus = followInitialFocus && sections.isNotEmpty(),
        focusEnabled = contentFocusEnabled,
        tagsRequester = tagsFocus,
        tag = activeTagId?.let { id -> groups.firstOrNull { it.tagId == id }?.name },
        onTags = { if (owner?.isCurrent != false) { restoreTagHeader = true; restoreHeader = true; tags = true } },
        restoreTags = restoreTagHeader,
        viewRequester = viewFocus, restoreHeader = restoreHeader,
        onHeaderFocused = { cardFocused = false; restoreHeader = false },
        emptyFocus = errorFocus.takeUnless { loading && !loadingDelayed },
        onViewOptions = openOptions,
        emptyContent = {
            if (shownError != null) AppErrorAlert(error = shownError, actionFocus = errorFocus,
                leadingExit = LocalBrowseNavigationFocus.current ?: FocusRequester.Default,
                upExit = viewFocus, modifier = Modifier.fillMaxSize(), onAction = {
                    when (it) {
                        AppErrorAction.RETRY -> onRetry()
                        AppErrorAction.CONNECTION_SETTINGS -> onOpenConnectionSettings()
                        AppErrorAction.CLOSE -> Unit
                    }
                }) else HomeEmptyState(input, loading, hasChannelsOutsideScope,
                    loadingDelayed, contentPadding, errorFocus, viewFocus, onOpenConnectionSettings,
                    onBrowseChannels, openOptions)
        },
        observation = observation,
        input = input,
        onActivate = { activate(it, false) },
        onContextMenu = { activate(it, true) },
    )
    if (options) {
        val openingOwner = remember { owner }
        Box(Modifier.onPreviewKeyEvent { openingOwner?.isCurrent == false }) {
            HomeViewOptionsPanel(input.hiddenSections,
                { section, visible -> if (openingOwner?.isCurrent != false) onSectionVisibleChange(section, visible) },
                onClose = { runCatching { viewFocus.requestFocus() }; options = false })
        }
        LaunchedEffect(owner) { if (openingOwner?.isCurrent == false) options = false }
    }
    if (tags) {
        val openingOwner = remember { owner }
        Box(Modifier.onPreviewKeyEvent { openingOwner?.isCurrent == false }) {
            at.bernhardberger.tvhplayer.ui.screens.ChannelsTagsPanel(groups, activeTagId,
                onSelect = {
                    if (openingOwner?.isCurrent != false) onSelectTag(it)
                    runCatching { tagsFocus.requestFocus() }
                    tags = false
                },
                onClose = { runCatching { tagsFocus.requestFocus() }; tags = false })
        }
        LaunchedEffect(owner) { if (openingOwner?.isCurrent == false) tags = false }
    }
    channelMenu?.let { id ->
        val openingOwner = remember(id) { owner }
        Box(Modifier.onPreviewKeyEvent { openingOwner?.isCurrent == false }) {
            ChannelContextMenu(id, observation, { nowSec }, mutationScope, mutationInFlight,
                onPlay = onPlay, onShowGuide = onShowGuide,
                onClose = { channelMenu = null; recordInitialAction = null }, showDetails = channelDetails,
                initialAction = recordInitialAction,
                onShowDetails = { channelDetails = true },
                onPlayRecording = { onPlayRecording(it, RecordingPlaybackStart.START_OVER) })
        }
        LaunchedEffect(owner) { if (openingOwner?.isCurrent == false) channelMenu = null }
    }
    recording?.let { entry ->
        val openingOwner = remember(entry.id) { owner }
        Box(Modifier.onPreviewKeyEvent { openingOwner?.isCurrent == false }) {
            HomeRecordingActions(entry, observation, recordingMenu, nowSec, onPlayRecording,
                onWatchRecordingChannel, onShowRecordingGuide, onReturnToRecording, onClose = { recording = null })
        }
        LaunchedEffect(owner) { if (openingOwner?.isCurrent == false) recording = null }
    }
}
