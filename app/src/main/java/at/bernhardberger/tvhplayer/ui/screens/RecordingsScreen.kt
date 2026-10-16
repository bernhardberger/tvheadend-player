package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import at.bernhardberger.tvhplayer.ui.components.browseDetailsViewport
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.animateShown

import androidx.compose.ui.graphics.graphicsLayer

import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.recordingContextActions
import at.bernhardberger.tvhplayer.ui.components.BrowseContextMenu
import at.bernhardberger.tvhplayer.ui.components.ProgrammeArtwork
import at.bernhardberger.tvhplayer.ui.common.programmeEpisodeLine
import androidx.compose.foundation.layout.width

import at.bernhardberger.tvhplayer.notices.NoticeCenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ConnectionRecoveryAction
import at.bernhardberger.tvhplayer.core.primaryRecoveryAction
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.core.DvrLibraryPartition
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.DvrProblemBucket
import at.bernhardberger.tvhplayer.core.DvrScheduleSection
import at.bernhardberger.tvhplayer.core.buildDvrArchive
import at.bernhardberger.tvhplayer.core.groupDvrProblems
import at.bernhardberger.tvhplayer.core.groupDvrSchedule
import at.bernhardberger.tvhplayer.core.partitionDvrLibrary
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader
import at.bernhardberger.tvhplayer.ui.components.TabContent
import at.bernhardberger.tvhplayer.ui.components.BrowsePreparationPending
import at.bernhardberger.tvhplayer.ui.components.PreparedBrowseData
import at.bernhardberger.tvhplayer.ui.components.rememberPreparedBrowseData
import at.bernhardberger.tvhplayer.ui.components.rememberTabContentMotion
import at.bernhardberger.tvhplayer.ui.screens.recordings.ArchiveDepthContent
import at.bernhardberger.tvhplayer.ui.screens.recordings.archiveLevelPath
import at.bernhardberger.tvhplayer.ui.screens.recordings.reconcileArchiveStack
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthStack
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvBrowseTrailingInset
import at.bernhardberger.tvhplayer.ui.screens.recordings.PendingRecordingAction
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingConfirmationDialog
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingDetailsAction
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsPage
import at.bernhardberger.tvhplayer.core.recordingDetailsActions
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingModeTabs
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingProblems
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingSchedule
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingsEmptyState
import at.bernhardberger.tvhplayer.ui.screens.recordings.listItems
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingItemKey
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingTimeRange
import coil3.ImageLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.compose.koinInject

private data class RecordingLibraryPresentation(
    val library: DvrLibraryPartition,
    val archive: DvrArchiveFolder,
    val schedule: List<DvrScheduleSection>,
    val problems: Map<DvrProblemBucket, List<DvrEntry>>,
)

class RecordingsScreenState {
    val selectedKeys = mutableStateMapOf<String, String>()
    val archiveScrollPositions = mutableStateMapOf<String, Int>()
    val archiveScrollOffsets = mutableMapOf<String, Int>()
    val mode = mutableStateOf(DvrLibraryMode.ARCHIVE)
    val archiveNavigation = DepthNavigationState(DepthStack(listOf(DepthFrame("archive:"))))
    val archivePath: List<String> get() = archiveLevelPath(archiveNavigation.stack.active.levelId)
}

private data class RecordingsTabBody(
    val mode: DvrLibraryMode,
    val location: String,
    val empty: Boolean,
    val connection: ConnectionUiState,
    val observation: SessionObservation,
    val channelsById: Map<ChannelId, Channel>,
    val archive: DvrArchiveFolder,
    val schedule: List<DvrScheduleSection>,
    val problems: Map<DvrProblemBucket, List<DvrEntry>>,
    val selectedKey: String?,
    val scrollIndex: Int,
    val scrollOffset: Int,
    val recoveryGeneration: Int,
)

@Composable
fun RecordingsScreen(
    contentPadding: PaddingValues = PaddingValues(),
    initialFocusEnabled: Boolean = true,
    backEnabled: Boolean = true,
    session: TvheadendSession = koinInject(),
    imageLoader: ImageLoader = koinInject(),
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    onRetry: () -> Unit = {},
    onPlayRecording: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit = { _, _ -> },
    onWatchChannel: ((at.bernhardberger.tvhplayer.playback.LivePlaybackSelection, String) -> Unit)? = null,
    state: RecordingsScreenState? = null,
) {
    val observation by session.observation.collectAsStateWithLifecycle()
    val dvrMutationActions = remember(session.dvrRepository) {
        DvrMutationActions(session.dvrRepository)
    }
    RecordingsScreenContent(
        observation = observation,
        contentPadding = contentPadding,
        initialFocusEnabled = initialFocusEnabled,
        backEnabled = backEnabled,
        imageLoader = imageLoader,
        connectionUiState = connectionUiState,
        onRetry = onRetry,
        onPlayRecording = onPlayRecording,
        onWatchChannel = onWatchChannel,
        state = state,
        dvrMutationActions = dvrMutationActions,
        currentObservation = { session.observation.value },
    )
}

@Composable
internal fun RecordingsScreenContent(
    observation: SessionObservation,
    contentPadding: PaddingValues = PaddingValues(),
    initialFocusEnabled: Boolean = true,
    backEnabled: Boolean = true,
    imageLoader: ImageLoader = koinInject(),
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    onRetry: () -> Unit = {},
    onPlayRecording: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit = { _, _ -> },
    onWatchChannel: ((at.bernhardberger.tvhplayer.playback.LivePlaybackSelection, String) -> Unit)? = null,
    state: RecordingsScreenState? = null,
    dvrMutationActions: DvrMutationActions,
    currentObservation: () -> SessionObservation = { observation },
    notices: NoticeCenter = koinInject(),
    nowSecProvider: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    val currentSession = observation.currentSession
    val latestObservationProvider by rememberUpdatedState(currentObservation)
    val preparationAuthority = currentSession ?: observation.dvrSnapshotForDisplay
    var retainedPreparation by remember(preparationAuthority) {
        mutableStateOf<PreparedBrowseData<DvrSnapshot?, RecordingLibraryPresentation>?>(null)
    }
    val channels = observation.channelCatalogForDisplay?.channels.orEmpty()
    val channelsById = remember(channels) { channels.associateBy { it.id } }
    val scope = rememberCoroutineScope()
    val screenState = state ?: remember { RecordingsScreenState() }
    val contentFocus = remember { FocusRequester() }
    val modeFocus = remember { FocusRequester() }
    val selectedKeys = screenState.selectedKeys
    val archiveScrollPositions = screenState.archiveScrollPositions
    var mode by screenState.mode
    val archivePath = screenState.archivePath
    val archiveNavigation = screenState.archiveNavigation
    val drawerFocus = LocalBrowseNavigationFocus.current
    var requestContentFocus by remember { mutableStateOf(true) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var contentFocusOwned by remember { mutableStateOf(false) }
    var relocatingKey by remember { mutableStateOf<Key?>(null) }
    var focusRecoveryGeneration by remember { mutableIntStateOf(0) }
    var detailsEntry by remember { mutableStateOf<DvrEntry?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showResumeChoice by remember { mutableStateOf(false) }
    var menuConsumed by remember(detailsEntry?.id) { mutableStateOf(false) }
    var detailsObservation by remember { mutableStateOf<SessionObservation?>(null) }
    var detailsInitialAction by remember {
        mutableStateOf<RecordingDetailsAction?>(null)
    }
    var pendingAction by remember { mutableStateOf<PendingRecordingAction?>(null) }
    val confirmationVisibility = remember(pendingAction) {
        MutableTransitionState(false).apply { targetState = pendingAction != null }
    }
    var pendingMutation by remember { mutableStateOf<DvrMutationAction?>(null) }
    var confirmationKey by remember { mutableStateOf<Key?>(null) }
    var pendingDetailsReturn by remember { mutableStateOf(false) }

    fun openRecording(entry: DvrEntry) {
        val capability = observation.currentSession
        val action = at.bernhardberger.tvhplayer.core.recordingOpenAction(entry, capability != null)
        showContextMenu = false
        if (capability != null && (action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.START_OVER ||
                action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.RESUME)) {
            onPlayRecording(RecordingPlaybackSelection(capability, entry.id),
                if (action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.RESUME) RecordingPlaybackStart.RESUME
                else RecordingPlaybackStart.START_OVER)
            return
        }
        showResumeChoice = action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.RESUME_CHOICE
        contentFocusOwned = false
        detailsInitialAction = null
        detailsEntry = entry
        detailsObservation = observation
    }

    val location = when (mode) {
        DvrLibraryMode.ARCHIVE -> "archive:${archivePath.joinToString("/")}"
        DvrLibraryMode.SCHEDULE -> "schedule"
        DvrLibraryMode.PROBLEMS -> "problems"
    }
    val prepared = rememberPreparedBrowseData(preparationAuthority, observation.dvrSnapshotForDisplay) { snapshot ->
        val library = partitionDvrLibrary(snapshot?.entries.orEmpty())
        currentCoroutineContext().ensureActive()
        val archive = buildDvrArchive(library.archive)
        currentCoroutineContext().ensureActive()
        RecordingLibraryPresentation(
            library = library,
            archive = archive,
            schedule = groupDvrSchedule(library.schedule, System.currentTimeMillis() / 1000L),
            problems = groupDvrProblems(library.problems),
        )
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        // An observation can dismiss the confirmation between down and up.
        // Finish that key cycle here, not on the refreshed details' action.
        if (event.key == confirmationKey && pendingAction == null) {
            if (event.type == KeyEventType.KeyUp) confirmationKey = null
            true
        } else {
            if (pendingAction != null && event.type == KeyEventType.KeyDown) confirmationKey = event.key
            if (event.key == confirmationKey && event.type == KeyEventType.KeyUp) confirmationKey = null
            false
        }
    }) content@{
    BrowsePreparationPending(contentPadding, initialFocusEnabled, ready = prepared != null,
        onReadyFocus = { requestContentFocus = true; true })
    if (prepared == null) return@content
    val observedEntries = prepared.input?.entries.orEmpty()
    val retained = retainedPreparation ?: prepared
    val retainedEntries = retained.input?.entries.orEmpty()
    val observedLibrary = prepared.value.library
    val observedArchive = prepared.value.archive
    val survivingArchivePath = remember(observedArchive, archivePath) {
        var path = archivePath
        while (path.isNotEmpty() && observedArchive.folderAt(path) == null) path = path.dropLast(1)
        path
    }
    val observedKeys = when (mode) {
        DvrLibraryMode.ARCHIVE -> requireNotNull(observedArchive.folderAt(survivingArchivePath)).listItems().map { it.key }
        DvrLibraryMode.SCHEDULE -> observedLibrary.schedule.map { "recording:${recordingItemKey(it.id)}" }
        DvrLibraryMode.PROBLEMS -> observedLibrary.problems.map { "recording:${recordingItemKey(it.id)}" }
    }
    val removalHandoff = contentFocusOwned && retainedEntries != observedEntries &&
        ((if (mode == DvrLibraryMode.ARCHIVE) archiveNavigation.stack.active.focusedItemId
            else selectedKeys[location])?.let { it !in observedKeys } == true ||
            (mode == DvrLibraryMode.ARCHIVE && survivingArchivePath != archivePath))
    val entries = if (removalHandoff) retainedEntries else observedEntries
    val presentation = if (removalHandoff) retained.value else prepared.value
    val library = presentation.library
    val archive = presentation.archive
    val archiveFolder = archive.folderAt(archivePath) ?: archive
    val archiveItems = remember(archiveFolder) { archiveFolder.listItems() }
    val scheduleGroups = presentation.schedule
    val problemGroups = presentation.problems
    val itemKeys = when (mode) {
        DvrLibraryMode.ARCHIVE -> archiveItems.map { it.key }
        DvrLibraryMode.SCHEDULE -> scheduleGroups.flatMap { it.entries }
            .map { "recording:${recordingItemKey(it.id)}" }
        DvrLibraryMode.PROBLEMS -> DvrProblemBucket.entries
            .flatMap { problemGroups[it].orEmpty() }
            .map { "recording:${recordingItemKey(it.id)}" }
    }
    val retryAvailable = entries.isEmpty() &&
        connectionUiState.primaryRecoveryAction() == ConnectionRecoveryAction.RETRY

    val drawerState = at.bernhardberger.tvhplayer.ui.components.LocalBrowseDrawerState.current
    LaunchedEffect(observedEntries) {
        // Transfer locally before removing the actual focused node. Keeping only the
        // old key would recreate the node and leave the same focus vacancy.
        if (removalHandoff && initialFocusEnabled &&
            drawerState?.currentValue != androidx.tv.material3.DrawerValue.Open
        ) {
            if (modeFocus.requestFocus()) {
                contentFocusOwned = false
                requestContentFocus = observedKeys.isNotEmpty()
            }
        }
        retainedPreparation = prepared
        archiveNavigation.update(reconcileArchiveStack(archiveNavigation.stack, observedArchive))
    }

    LaunchedEffect(
        location,
        itemKeys,
        selectedKeys[location],
        requestContentFocus,
        initialFocusEnabled,
        retryAvailable,
        detailsEntry,
        pendingAction,
    ) {
        if (detailsEntry != null || pendingAction != null) return@LaunchedEffect
        if (itemKeys.isEmpty() && !retryAvailable) {
            if (!initialFocusEnabled || (!requestContentFocus && !contentFocusOwned)) return@LaunchedEffect
            if (drawerState?.currentValue == androidx.tv.material3.DrawerValue.Open) return@LaunchedEffect
            if (modeFocus.requestFocus()) {
                requestContentFocus = false
                contentFocusOwned = false
            }
            return@LaunchedEffect
        }
        if (mode != DvrLibraryMode.ARCHIVE && itemKeys.isNotEmpty() && selectedKeys[location] !in itemKeys) {
            selectedKeys[location] = itemKeys.first()
            archiveScrollPositions[location] = 0
            screenState.archiveScrollOffsets[location] = 0
            focusRecoveryGeneration++
            if (contentFocusOwned) requestContentFocus = true
            return@LaunchedEffect
        }
        if (!initialFocusEnabled) return@LaunchedEffect
        if (!requestContentFocus) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            if (drawerState?.currentValue == androidx.tv.material3.DrawerValue.Open) return@LaunchedEffect
            val focused = runCatching { contentFocus.requestFocus() }.getOrDefault(false)
            if (focused) {
                requestContentFocus = false
                return@LaunchedEffect
            }
        }
    }
    LaunchedEffect(
        detailsEntry,
        pendingAction,
        pendingDetailsReturn,
        location,
        focusRecoveryGeneration,
    ) {
        if (!pendingDetailsReturn) return@LaunchedEffect
        if (detailsEntry != null || pendingAction != null) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            val restored = runCatching {
                if (itemKeys.isEmpty() && !retryAvailable) modeFocus.requestFocus()
                else contentFocus.requestFocus()
            }.getOrDefault(false)
            if (restored) {
                pendingDetailsReturn = false
                requestContentFocus = false
                return@LaunchedEffect
            }
        }
    }

    val modeMotion = rememberTabContentMotion(mode) { screenState.mode.value }
    val detailsLayering = at.bernhardberger.tvhplayer.ui.components.LocalBrowseContentLayering.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { if (detailsEntry != null) hideFromAccessibility() }
            .graphicsLayer { alpha = 1f - (detailsLayering?.detailsAlpha() ?: if (detailsEntry != null && !showContextMenu && !showResumeChoice) 1f else 0f) }
            .onPreviewKeyEvent { event ->
                if (event.key == relocatingKey) {
                    if (event.type == KeyEventType.KeyUp) relocatingKey = null
                    return@onPreviewKeyEvent true
                }
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionUp &&
                    mode != DvrLibraryMode.ARCHIVE &&
                    contentHasFocus &&
                    selectedKeys[location] == itemKeys.firstOrNull()
                ) {
                    relocatingKey = event.key
                    if (modeFocus.requestFocus()) {
                        contentFocusOwned = false
                        requestContentFocus = false
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        TopLevelBrowseHeader(
            title = stringResource(R.string.recordings_title),
            modifier = Modifier
                .padding(start = startPadding + TvBrowseLeadingInset, end = maxOf(endPadding, TvBrowseTrailingInset))
                .testTag("recordings-header"),
        )
        Spacer(Modifier.height(TvSpacing8))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startPadding + TvBrowseLeadingInset)
                .testTag("recordings-mode-tabs-row"),
        ) {
            RecordingModeTabs(
                selected = mode,
                selectedFocus = modeFocus,
                onFocused = {
                    modeMotion.select(it, DvrLibraryMode.entries)
                    if (mode != it) {
                        contentFocusOwned = false
                        requestContentFocus = false
                    }
                    mode = it
                },
                onClick = {
                    modeMotion.select(it, DvrLibraryMode.entries)
                    mode = it
                    requestContentFocus = true
                },
                onMoveToContent = {
                    requestContentFocus = true
                },
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start)
                    .testTag("recordings-mode-tabs"),
            )
        }
        TabContent(
            motion = modeMotion,
            selectedKey = mode,
            state = {
                RecordingsTabBody(
                    mode, location, entries.isEmpty(), connectionUiState,
                    observation, channelsById, archive,
                    scheduleGroups, problemGroups, selectedKeys[location],
                    archiveScrollPositions[location] ?: 0,
                    screenState.archiveScrollOffsets[location] ?: 0,
                    focusRecoveryGeneration,
                )
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { frame, owner ->
        val mode = frame.mode
        val location = frame.location
        val observation = frame.observation
        val currentSession = observation.currentSession
        val channelsById = frame.channelsById
        val scheduleGroups = frame.schedule
        val problemGroups = frame.problems
        Spacer(Modifier.height(TvSpacing16))

        if (frame.empty) {
            RecordingsEmptyState(
                connectionUiState = frame.connection,
                onRetry = { if (owner.isCurrent) onRetry() },
                retryFocus = if (owner.isCurrent) contentFocus else remember { FocusRequester() },
                upFocus = modeFocus,
                modifier = Modifier.padding(start = startPadding, end = endPadding),
            )
        } else {
            when (mode) {
                DvrLibraryMode.ARCHIVE -> ArchiveDepthContent(
                    root = frame.archive,
                    navigation = archiveNavigation,
                    contentPadding = PaddingValues(start = startPadding + TvBrowseLeadingInset),
                    isCurrent = owner.isCurrent,
                    initialFocusEnabled = owner.isCurrent && initialFocusEnabled &&
                        detailsEntry == null && (contentFocusOwned || requestContentFocus),
                    backEnabled = backEnabled && detailsEntry == null && contentFocusOwned,
                    contentFocus = contentFocus,
                    onModeFocus = {
                        if (owner.isCurrent && modeFocus.requestFocus()) {
                            contentFocusOwned = false
                            requestContentFocus = false
                        }
                    },
                    onDrawerFocus = drawerFocus?.let { focus -> {
                        if (owner.isCurrent && focus.requestFocus()) {
                            contentFocusOwned = false
                            requestContentFocus = false
                        }
                    } },
                    onOpenRecording = {
                        if (owner.isCurrent) {
                            openRecording(it)
                        }
                    },
                    imageLoader = imageLoader,
                    onOpenContextMenu = {
                        if (owner.isCurrent) {
                            contentFocusOwned = false
                            detailsInitialAction = null
                            detailsEntry = it
                            detailsObservation = observation
                            showContextMenu = true
                        }
                    },
                    currentSession = currentSession,
                    piconForEntry = { it.channelId?.let(channelsById::get)?.icon },
                    modifier = Modifier.fillMaxSize().onFocusChanged {
                        if (owner.isCurrent) {
                            contentHasFocus = it.hasFocus
                            if (it.hasFocus) contentFocusOwned = true
                        }
                    },
                )
                DvrLibraryMode.SCHEDULE -> Box(
                    modifier = Modifier
                        // Full-width native focus growth needs extra trailing safe-area reserve.
                        .padding(start = startPadding + TvBrowseLeadingInset - 24.dp, end = maxOf(endPadding, TvBrowseTrailingInset) + 12.dp - 24.dp)
                        .fillMaxSize()
                        .onFocusChanged {
                            if (!owner.isCurrent) return@onFocusChanged
                            contentHasFocus = it.hasFocus
                            if (it.hasFocus) contentFocusOwned = true
                        },
                ) {
                    key(location, frame.recoveryGeneration) {
                        val generation = frame.recoveryGeneration
                        RecordingSchedule(
                            groups = scheduleGroups,
                            selectedKey = frame.selectedKey,
                            selectedFocus = contentFocus,
                            onFocused = { if (owner.isCurrent) selectedKeys[location] = it },
                            onOpen = {
                                if (!owner.isCurrent) return@RecordingSchedule
                                openRecording(it)
                            },
                            imageLoader = imageLoader,
                            onOpenContextMenu = {
                                if (owner.isCurrent) {
                                    contentFocusOwned = false
                                    detailsInitialAction = null
                                    detailsEntry = it
                                    detailsObservation = observation
                                    showContextMenu = true
                                }
                            },
                            currentSession = currentSession,
                            piconForEntry = { entry ->
                                entry.channelId?.let(channelsById::get)?.icon
                            },
                            initialScrollIndex = frame.scrollIndex,
                            onScrollChanged = {
                                if (owner.isCurrent && generation == focusRecoveryGeneration) {
                                    archiveScrollPositions[location] = it
                                }
                            },
                        )
                    }
                }
                DvrLibraryMode.PROBLEMS -> Box(
                    modifier = Modifier
                        .padding(start = startPadding + TvBrowseLeadingInset - 24.dp, end = maxOf(endPadding, TvBrowseTrailingInset) + 12.dp - 24.dp)
                        .fillMaxSize()
                        .onFocusChanged {
                            if (!owner.isCurrent) return@onFocusChanged
                            contentHasFocus = it.hasFocus
                            if (it.hasFocus) contentFocusOwned = true
                        },
                ) {
                    key(location, frame.recoveryGeneration) {
                        val generation = frame.recoveryGeneration
                        RecordingProblems(
                            groups = problemGroups,
                            selectedKey = frame.selectedKey,
                            selectedFocus = contentFocus,
                            onFocused = { if (owner.isCurrent) selectedKeys[location] = it },
                            onOpen = {
                                if (!owner.isCurrent) return@RecordingProblems
                                openRecording(it)
                            },
                            imageLoader = imageLoader,
                            onOpenContextMenu = {
                                if (owner.isCurrent) {
                                    contentFocusOwned = false
                                    detailsInitialAction = null
                                    detailsEntry = it
                                    detailsObservation = observation
                                    showContextMenu = true
                                }
                            },
                            currentSession = currentSession,
                            piconForEntry = { entry ->
                                entry.channelId?.let(channelsById::get)?.icon
                            },
                            initialScrollIndex = frame.scrollIndex,
                            onScrollChanged = {
                                if (owner.isCurrent && generation == focusRecoveryGeneration) {
                                    archiveScrollPositions[location] = it
                                }
                            },
                        )
                    }
                }
            }
        }
        }
    }

    // Refresh metadata only within the opening session. Never substitute a colliding ID
    // from a later session, including while a captured confirmation is pending.
    val selectedCapability = detailsObservation?.currentSession
        ?.takeIf { observation.currentSession === it }
    val opened = detailsEntry?.let { entry ->
        if (selectedCapability != null && observation.dvrState is DvrRepositoryState.Current) {
            observation.dvrEntry(entry.id)
        } else entry
    }
    LaunchedEffect(detailsEntry, opened) {
        if (detailsEntry != null && opened == null) {
            showResumeChoice = false
            pendingAction = null
            pendingMutation = null
            detailsEntry = null
            detailsObservation = null
            detailsInitialAction = null
            pendingDetailsReturn = true
        }
    }
    if (opened != null) {
        val nowSec = at.bernhardberger.tvhplayer.ui.common.rememberDetailsEpochSeconds(nowSecProvider)
        if (showResumeChoice) {
            val resources = androidx.compose.ui.platform.LocalContext.current.resources
            val channel = opened.channelId?.let(channelsById::get)
            val closeResume = {
                showResumeChoice = false
                detailsEntry = null
                detailsObservation = null
                pendingDetailsReturn = true
            }
            val play: (RecordingPlaybackStart) -> Unit = { intent ->
                selectedCapability?.let { capability ->
                    closeResume()
                    onPlayRecording(RecordingPlaybackSelection(capability, opened.id), intent)
                }
            }
            LaunchedEffect(selectedCapability, opened.state) {
                if (selectedCapability == null || opened.state != DvrEntryState.COMPLETED) closeResume()
            }
            val resumeFocus = remember { FocusRequester() }
            val startOverFocus = remember { FocusRequester() }
            var startOverFocused by remember(opened.id) { mutableStateOf(false) }
            val durationMs = at.bernhardberger.tvhplayer.core.recordingDetailsFacts(opened)
                .firstOrNull { it.kind == at.bernhardberger.tvhplayer.core.DetailsFactKind.LENGTH }?.seconds?.times(1000L)
            val positionMs = opened.playPosition?.inWholeMilliseconds ?: 0L
            val resumeEnd = at.bernhardberger.tvhplayer.core.recordingEndsAtSec(nowSec, positionMs, durationMs)
                ?.let { stringResource(R.string.recording_ends_at, at.bernhardberger.tvhplayer.ui.common.formatClock(it)) }.orEmpty()
            val startOverEnd = at.bernhardberger.tvhplayer.core.recordingEndsAtSec(nowSec, 0L, durationMs)
                ?.let { stringResource(R.string.recording_ends_at, at.bernhardberger.tvhplayer.ui.common.formatClock(it)) }.orEmpty()
            val spokenResume = stringResource(R.string.recording_resume_from,
                at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes(positionMs / 60_000L, resources, spoken = true))
            at.bernhardberger.tvhplayer.ui.components.CentredDialog(
                title = opened.title.orEmpty(),
                secondaryText = programmeEpisodeLine(opened.episode, opened.subtitle, resources),
                text = if (startOverFocused) startOverEnd else resumeEnd,
                eyebrow = {
                    at.bernhardberger.tvhplayer.ui.components.ProgrammeEyebrow(channel, listOfNotNull(
                    (channel?.let { at.bernhardberger.tvhplayer.ui.components.channelTitleText(it.number, it.name.orEmpty()) }
                        ?: opened.channelName)?.takeIf(String::isNotBlank),
                    opened.start?.epochSeconds?.formatDateTime(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
                    ).joinToString(" · "), imageLoader, observation.currentSession,
                        Modifier.fillMaxWidth(), Modifier.testTag("recording-resume-picon"))
                },
                onDismiss = closeResume,
                backEnabled = backEnabled,
            ) {
                LaunchedEffect(Unit) { withFrameNanos { }; resumeFocus.requestFocus() }
                Button(onClick = { play(RecordingPlaybackStart.RESUME) },
                    modifier = Modifier.height(40.dp).focusRequester(resumeFocus)
                        .onFocusChanged { if (it.isFocused) startOverFocused = false }
                        .semantics { contentDescription = spokenResume; if (resumeEnd.isNotBlank()) stateDescription = resumeEnd }
                        .focusProperties { left = FocusRequester.Cancel; right = startOverFocus }
                        .testTag("recording-resume")) {
                    Icon(painterResource(R.drawable.ic_play_arrow), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recording_resume_from,
                        at.bernhardberger.tvhplayer.core.formatPlaybackDuration(opened.playPosition?.inWholeMilliseconds ?: 0L)),
                        maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = { play(RecordingPlaybackStart.START_OVER) },
                    modifier = Modifier.height(40.dp).focusRequester(startOverFocus)
                        .onFocusChanged { if (it.isFocused) startOverFocused = true }
                        .semantics { if (startOverEnd.isNotBlank()) stateDescription = startOverEnd }
                        .focusProperties { left = resumeFocus; right = FocusRequester.Cancel }
                        .testTag("recording-start-over")) {
                    Icon(painterResource(R.drawable.ic_replay), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.context_start_over), maxLines = 1, softWrap = false)
                }
            }
        } else {
        val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
        val closing = !visibility.targetState
        BackHandler(enabled = backEnabled && closing) { }
        LaunchedEffect(visibility.isIdle, visibility.currentState, closing) {
            if (closing && visibility.isIdle && !visibility.currentState) {
                val restoredBeforeDismissal = runCatching { contentFocus.requestFocus() }.getOrDefault(false)
                pendingDetailsReturn = !restoredBeforeDismissal
                detailsEntry = null
                detailsObservation = null
                detailsInitialAction = null
            }
        }
        AnimatedVisibility(visibleState = visibility, modifier = Modifier.browseDetailsViewport(),
            enter = EnterTransition.None, exit = ExitTransition.None) {
        val detailAlpha = animateShown(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard),
            tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard), "recording-details-layer")
        at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame(leaving = closing,
            modifier = Modifier.onPreviewKeyEvent { closing }) {
        // Match ProgrammeDetailsHost: keep both pages drawn while details owns its alpha.
        AnimatedContent(targetState = showContextMenu, transitionSpec = {
            (if (targetState) fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)) else EnterTransition.None)
                .togetherWith(if (initialState) fadeOut(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)) else ExitTransition.None)
                .using(null)
        }, label = "recording-menu-details") { menu ->
        at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame(leaving = closing || menu != showContextMenu) {
        if (menu) {
        AnimatedVisibility(visible = !closing && !menuConsumed,
            enter = EnterTransition.None, exit = ExitTransition.None) {
        androidx.compose.runtime.CompositionLocalProvider(
            at.bernhardberger.tvhplayer.ui.player.LocalPlayerPanelMotion provides this) {
        at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame(leaving = pendingAction != null) {
        BrowseContextMenu(
            backEnabled = backEnabled && !closing && menu == showContextMenu && pendingAction == null,
            title = opened.title.orEmpty(),
            channel = opened.channelId?.let(channelsById::get)?.let {
                at.bernhardberger.tvhplayer.ui.components.channelTitleText(it.number, it.name.orEmpty())
            } ?: opened.channelName.orEmpty(),
            episode = programmeEpisodeLine(opened.episode, opened.subtitle, androidx.compose.ui.platform.LocalContext.current.resources),
            time = recordingTimeRange(opened.start?.epochSeconds, opened.stop?.epochSeconds,
                androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
            summary = opened.summary?.takeIf(String::isNotBlank) ?: opened.description.orEmpty(),
            actions = recordingContextActions(opened, canModifyRecordings = selectedCapability != null &&
                (observation.sessionState as? at.bernhardberger.tvheadend.sdk.core.SessionState.Ready)
                    ?.capabilities?.dvrWrite == at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED,
                playbackEligible = selectedCapability != null),
            onClose = { visibility.targetState = false },
            onAction = { action ->
                menuConsumed = action != BrowseContextAction.DETAILS
                showContextMenu = action in listOf(BrowseContextAction.STOP, BrowseContextAction.CANCEL, BrowseContextAction.DELETE)
                when (action) {
                    BrowseContextAction.WATCH, BrowseContextAction.PLAY,
                    BrowseContextAction.RESUME, BrowseContextAction.START_OVER -> selectedCapability?.let {
                        detailsEntry = null
                        detailsObservation = null
                        detailsInitialAction = null
                        requestContentFocus = true
                        onPlayRecording(RecordingPlaybackSelection(it, opened.id),
                            if (action == BrowseContextAction.RESUME || action == BrowseContextAction.WATCH)
                                RecordingPlaybackStart.RESUME else RecordingPlaybackStart.START_OVER)
                    }
                    BrowseContextAction.STOP -> {
                        detailsInitialAction = RecordingDetailsAction.STOP
                        pendingMutation = selectedCapability?.let { DvrMutationAction.Stop(it, opened.id) }
                        pendingAction = PendingRecordingAction.STOP
                    }
                    BrowseContextAction.CANCEL -> {
                        detailsInitialAction = RecordingDetailsAction.CANCEL
                        pendingMutation = selectedCapability?.let { DvrMutationAction.Cancel(it, opened.id) }
                        pendingAction = PendingRecordingAction.CANCEL
                    }
                    BrowseContextAction.DELETE -> {
                        detailsInitialAction = RecordingDetailsAction.DELETE
                        pendingMutation = selectedCapability?.let { DvrMutationAction.Delete(it, opened.id) }
                        pendingAction = PendingRecordingAction.DELETE
                    }
                    else -> Unit
                }
            },
            artwork = {
                val artwork = (opened.image ?: opened.fanartImage)?.takeIf { selectedCapability != null }
                ProgrammeArtwork(artwork, opened.channelId?.let(channelsById::get)?.icon, null, imageLoader,
                    selectedCapability, Modifier.fillMaxSize().testTag(if (artwork != null)
                        "browse-context-artwork" else "browse-context-picon"),
                    plateModifier = Modifier.testTag("browse-context-plate"))
            },
        )
        }
        }
        }
        } else {
        val menuAlpha = animateShown(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard),
            tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard), "recording-details-from-menu")
        val alpha = remember(detailAlpha, menuAlpha) { derivedStateOf { detailAlpha.value * menuAlpha.value } }
        ProgrammeDetailsPage(
            event = null,
            recording = opened,
            nowSec = nowSec,
            recordingEvent = opened.eventId?.takeIf { selectedCapability != null }?.let(observation::event),
            recordingConfigurations = observation.currentDvrConfigurations().takeIf { selectedCapability != null }.orEmpty(),
            imageLoader = imageLoader,
            currentSession = selectedCapability,
            channel = opened.channelId?.let(channelsById::get),
            actions = recordingDetailsActions(opened, selectedCapability != null,
                canModifyRecordings = selectedCapability != null &&
                (observation.sessionState as? at.bernhardberger.tvheadend.sdk.core.SessionState.Ready)
                    ?.capabilities?.dvrWrite == at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED,
                canWatchChannel = selectedCapability != null && opened.channelId?.let(channelsById::get) != null && onWatchChannel != null),
            initialAction = when (detailsInitialAction) {
                RecordingDetailsAction.CANCEL,
                RecordingDetailsAction.STOP -> when (opened.state) {
                    DvrEntryState.SCHEDULED -> BrowseContextAction.CANCEL
                    DvrEntryState.RECORDING -> BrowseContextAction.STOP
                    else -> null
                }
                RecordingDetailsAction.RESUME -> BrowseContextAction.RESUME
                RecordingDetailsAction.BEGINNING -> BrowseContextAction.START_OVER
                RecordingDetailsAction.PLAY -> BrowseContextAction.PLAY
                RecordingDetailsAction.DELETE -> BrowseContextAction.DELETE
                null -> null
            },
            backEnabled = backEnabled && !closing && menu == showContextMenu && pendingAction == null,
            visibilityAlpha = alpha,
            onAction = { action ->
                val current = currentObservation()
                val capability = selectedCapability?.takeIf { current.currentSession === it }
                when (action) {
                BrowseContextAction.WATCH, BrowseContextAction.RESUME, BrowseContextAction.PLAY,
                BrowseContextAction.START_OVER -> capability?.let {
                    detailsEntry = null
                    detailsObservation = null
                    detailsInitialAction = null
                    requestContentFocus = true
                    if (action == BrowseContextAction.WATCH && opened.state == DvrEntryState.SCHEDULED) {
                        opened.channelId?.let(current::channel)?.let { ch ->
                            onWatchChannel?.invoke(at.bernhardberger.tvhplayer.playback.LivePlaybackSelection(it, ch.id), ch.name.orEmpty())
                        }
                    } else onPlayRecording(RecordingPlaybackSelection(it, opened.id),
                        if (action == BrowseContextAction.RESUME || action == BrowseContextAction.WATCH)
                            RecordingPlaybackStart.RESUME else RecordingPlaybackStart.START_OVER)
                }
                BrowseContextAction.STOP -> {
                detailsInitialAction = RecordingDetailsAction.STOP
                pendingMutation = capability?.let { DvrMutationAction.Stop(it, opened.id) }
                pendingAction = PendingRecordingAction.STOP
                }
                BrowseContextAction.CANCEL -> {
                detailsInitialAction = RecordingDetailsAction.CANCEL
                pendingMutation = capability?.let { DvrMutationAction.Cancel(it, opened.id) }
                pendingAction = PendingRecordingAction.CANCEL
                }
                BrowseContextAction.DELETE -> {
                detailsInitialAction = RecordingDetailsAction.DELETE
                pendingMutation = capability?.let { DvrMutationAction.Delete(it, opened.id) }
                pendingAction = PendingRecordingAction.DELETE
                }
                else -> Unit
                }
            },
            onClose = { visibility.targetState = false },
        )
        }
        }
        }
        }
        }
        }
    }

    val action = pendingAction
    val target = opened
    val closeConsumedMenu = {
        if (showContextMenu && menuConsumed) {
            // Match details dismissal: restore the mounted card before removing the dialog.
            pendingDetailsReturn = !runCatching { contentFocus.requestFocus() }.getOrDefault(false)
            showContextMenu = false
            detailsEntry = null
            detailsObservation = null
            detailsInitialAction = null
        }
    }
    val mutationStateCurrent = pendingMutation?.recordingStateIsCurrent(observation) == true
    LaunchedEffect(action, selectedCapability, mutationStateCurrent) {
        if (action != null && (selectedCapability == null || !mutationStateCurrent)) {
            pendingAction = null
            pendingMutation = null
            // The dismissed dialog must not restore focus to an action whose authority expired.
            if (selectedCapability == null) detailsInitialAction = null
            closeConsumedMenu()
        }
    }
    if (action != null && target != null && selectedCapability != null && mutationStateCurrent) {
        RecordingConfirmationDialog(
            visibility = confirmationVisibility,
            action = action,
            title = target.title.orEmpty(),
            channelName = target.channelId?.let(channelsById::get)?.name ?: target.channelName.orEmpty(),
            dateAndTime = at.bernhardberger.tvhplayer.ui.common.formatRecordingSchedule(target.start?.epochSeconds,
                dateOnly = action == PendingRecordingAction.DELETE,
                locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
            backEnabled = backEnabled,
            onDismiss = {
                pendingAction = null
                pendingMutation = null
                closeConsumedMenu()
            },
            onConfirm = {
                pendingAction = null
                val mutation = pendingMutation
                pendingMutation = null
                val mutationObservation = detailsObservation
                closeConsumedMenu()
                scope.launch {
                    val latestObservation = latestObservationProvider()
                    if (mutation == null ||
                        mutationObservation?.currentSession !== latestObservation.currentSession ||
                        !mutation.recordingStateIsCurrent(latestObservation)
                    ) return@launch
                    val noticeContext = notices.context()
                    val result = dvrMutationActions.execute(mutation)
                    notices.postDvrFailure(mutation, result, noticeContext)
                }
            },
        )
    }
    }
}
