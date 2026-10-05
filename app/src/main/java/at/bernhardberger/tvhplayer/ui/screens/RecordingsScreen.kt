package at.bernhardberger.tvhplayer.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import at.bernhardberger.tvhplayer.ui.components.BrowseTabContent
import at.bernhardberger.tvhplayer.ui.components.BrowsePreparationPending
import at.bernhardberger.tvhplayer.ui.components.PreparedBrowseData
import at.bernhardberger.tvhplayer.ui.components.rememberPreparedBrowseData
import at.bernhardberger.tvhplayer.ui.components.rememberBrowseContentMotion
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
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingDetailsPanel
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingModeTabs
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingProblems
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingSchedule
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingsEmptyState
import at.bernhardberger.tvhplayer.ui.screens.recordings.listItems
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingItemKey
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
    state: RecordingsScreenState? = null,
    dvrMutationActions: DvrMutationActions,
    currentObservation: () -> SessionObservation = { observation },
    notices: NoticeCenter = koinInject(),
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
    var detailsObservation by remember { mutableStateOf<SessionObservation?>(null) }
    var detailsInitialAction by remember {
        mutableStateOf<RecordingDetailsAction?>(null)
    }
    var pendingAction by remember { mutableStateOf<PendingRecordingAction?>(null) }
    var pendingMutation by remember { mutableStateOf<DvrMutationAction?>(null) }
    var confirmationKey by remember { mutableStateOf<Key?>(null) }
    var pendingDetailsReturn by remember { mutableStateOf(false) }

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

    val modeMotion = rememberBrowseContentMotion(mode) { screenState.mode.value }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { if (detailsEntry != null) hideFromAccessibility() }
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
        BrowseTabContent(
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
                            contentFocusOwned = false
                            detailsInitialAction = null
                            detailsEntry = it
                            detailsObservation = observation
                        }
                    },
                    imageLoader = imageLoader,
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
                                contentFocusOwned = false
                                detailsInitialAction = null
                                detailsEntry = it
                                detailsObservation = observation
                            },
                            imageLoader = imageLoader,
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
                                contentFocusOwned = false
                                detailsInitialAction = null
                                detailsEntry = it
                                detailsObservation = observation
                            },
                            imageLoader = imageLoader,
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
            pendingAction = null
            pendingMutation = null
            detailsEntry = null
            detailsObservation = null
            detailsInitialAction = null
            pendingDetailsReturn = true
        }
    }
    if (opened != null && pendingAction == null) {
        RecordingDetailsPanel(
            contentPadding = contentPadding,
            entry = opened,
            canModifyRecordings = selectedCapability != null,
            playbackEligible = selectedCapability != null,
            initialAction = when (detailsInitialAction) {
                RecordingDetailsAction.CANCEL,
                RecordingDetailsAction.STOP -> when (opened.state) {
                    DvrEntryState.SCHEDULED -> RecordingDetailsAction.CANCEL
                    DvrEntryState.RECORDING -> RecordingDetailsAction.STOP
                    else -> null
                }
                else -> detailsInitialAction
            },
            backEnabled = backEnabled,
            onPlay = { intent ->
                selectedCapability?.let { capability ->
                    detailsEntry = null
                    detailsObservation = null
                    detailsInitialAction = null
                    requestContentFocus = true
                    onPlayRecording(
                        RecordingPlaybackSelection(capability, opened.id),
                        intent,
                    )
                }
            },
            onStop = {
                detailsInitialAction = RecordingDetailsAction.STOP
                pendingMutation = selectedCapability?.let { capability ->
                    DvrMutationAction.Stop(capability, opened.id)
                }
                pendingAction = PendingRecordingAction.STOP
            },
            onCancel = {
                detailsInitialAction = RecordingDetailsAction.CANCEL
                pendingMutation = selectedCapability?.let { capability ->
                    DvrMutationAction.Cancel(capability, opened.id)
                }
                pendingAction = PendingRecordingAction.CANCEL
            },
            onDelete = {
                detailsInitialAction = RecordingDetailsAction.DELETE
                pendingMutation = selectedCapability?.let { capability ->
                    DvrMutationAction.Delete(capability, opened.id)
                }
                pendingAction = PendingRecordingAction.DELETE
            },
            onClose = {
                val restoredBeforeDismissal = runCatching {
                    contentFocus.requestFocus()
                }.getOrDefault(false)
                pendingDetailsReturn = !restoredBeforeDismissal
                detailsEntry = null
                detailsObservation = null
                detailsInitialAction = null
            },
        )
    }

    val action = pendingAction
    val target = opened
    val mutationStateCurrent = pendingMutation?.recordingStateIsCurrent(observation) == true
    LaunchedEffect(action, selectedCapability, mutationStateCurrent) {
        if (action != null && (selectedCapability == null || !mutationStateCurrent)) {
            pendingAction = null
            pendingMutation = null
            // The dismissed dialog must not restore focus to an action whose authority expired.
            if (selectedCapability == null) detailsInitialAction = RecordingDetailsAction.CLOSE
        }
    }
    if (action != null && target != null && selectedCapability != null && mutationStateCurrent) {
        RecordingConfirmationDialog(
            action = action,
            title = target.title.orEmpty(),
            backEnabled = backEnabled,
            onDismiss = {
                pendingAction = null
                pendingMutation = null
            },
            onConfirm = {
                pendingAction = null
                val mutation = pendingMutation
                pendingMutation = null
                val mutationObservation = detailsObservation
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
