package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
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
import at.bernhardberger.tvhplayer.ui.screens.recordings.ArchiveList
import at.bernhardberger.tvhplayer.ui.screens.recordings.ArchiveListItem
import at.bernhardberger.tvhplayer.ui.screens.recordings.FolderMetadataPane
import at.bernhardberger.tvhplayer.ui.screens.recordings.PendingRecordingAction
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingBrowserSurface
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingConfirmationDialog
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingDetailsAction
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingDetailsPanel
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingMetadataPane
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

private enum class RecordingDetailsReturnTarget {
    CONTENT,
    FOLDER_PREVIEW,
}

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
    val archivePath = mutableStateOf<List<String>>(emptyList())
}

private data class RecordingsTabBody(
    val mode: DvrLibraryMode,
    val path: List<String>,
    val location: String,
    val empty: Boolean,
    val connection: ConnectionUiState,
    val observation: SessionObservation,
    val channelsById: Map<ChannelId, Channel>,
    val archiveItems: List<ArchiveListItem>,
    val archiveSelection: ArchiveListItem?,
    val recordingSelection: DvrEntry?,
    val schedule: List<DvrScheduleSection>,
    val problems: Map<DvrProblemBucket, List<DvrEntry>>,
    val selectedKey: String?,
    val scrollIndex: Int,
    val scrollOffset: Int,
    val recoveryGeneration: Int,
    val previewId: DvrEntryId?,
    val previewFocused: Boolean,
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
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    val currentSession = observation.currentSession
    val latestSession by rememberUpdatedState(currentSession)
    val preparationAuthority = currentSession ?: observation.dvrSnapshotForDisplay
    var retainedPreparation by remember(preparationAuthority) {
        mutableStateOf<PreparedBrowseData<DvrSnapshot?, RecordingLibraryPresentation>?>(null)
    }
    val channels = observation.channelCatalogForDisplay?.channels.orEmpty()
    val channelsById = remember(channels) { channels.associateBy { it.id } }
    val scope = rememberCoroutineScope()
    val screenState = state ?: remember { RecordingsScreenState() }
    val contentFocus = remember { FocusRequester() }
    val folderTransitionFocus = remember { FocusRequester() }
    var pendingFolderPath by remember { mutableStateOf<List<String>?>(null) }
    val modeFocus = remember { FocusRequester() }
    val folderPreviewFocus = remember { FocusRequester() }
    val selectedKeys = screenState.selectedKeys
    val archiveScrollPositions = screenState.archiveScrollPositions
    var mode by screenState.mode
    var archivePath by screenState.archivePath
    var requestContentFocus by remember { mutableStateOf(true) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var contentFocusOwned by remember { mutableStateOf(false) }
    var relocatingKey by remember { mutableStateOf<Key?>(null) }
    var focusRecoveryGeneration by remember { mutableIntStateOf(0) }
    var folderPreviewFocused by remember { mutableStateOf(false) }
    var folderPreviewRecordingId by remember { mutableStateOf<DvrEntryId?>(null) }
    var detailsOpenedFromFolderPreview by remember { mutableStateOf(false) }
    var detailsEntry by remember { mutableStateOf<DvrEntry?>(null) }
    var detailsGeneration by remember { mutableIntStateOf(0) }
    var detailsObservation by remember { mutableStateOf<SessionObservation?>(null) }
    var detailsInitialAction by remember {
        mutableStateOf<RecordingDetailsAction?>(null)
    }
    var pendingAction by remember { mutableStateOf<PendingRecordingAction?>(null) }
    var pendingMutation by remember { mutableStateOf<DvrMutationAction?>(null) }
    var actionResult by remember { mutableStateOf<DvrMutationFeedback?>(null) }
    var pendingDetailsReturn by remember {
        mutableStateOf<RecordingDetailsReturnTarget?>(null)
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
    Box(Modifier.fillMaxSize()) content@{
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
        ((selectedKeys[location] != null && selectedKeys[location] !in observedKeys) ||
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
    val selectedArchiveItem = archiveItems.firstOrNull { it.key == selectedKeys[location] }
    val selectedRecording = when (mode) {
        DvrLibraryMode.ARCHIVE ->
            (selectedArchiveItem as? ArchiveListItem.Recording)?.entry
        DvrLibraryMode.SCHEDULE -> library.schedule.firstOrNull {
            "recording:${recordingItemKey(it.id)}" == selectedKeys[location]
        }
        DvrLibraryMode.PROBLEMS -> library.problems.firstOrNull {
            "recording:${recordingItemKey(it.id)}" == selectedKeys[location]
        }
    }

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
        archivePath = survivingArchivePath
    }
    LaunchedEffect(pendingFolderPath, mode, initialFocusEnabled, observedArchive) {
        val destination = pendingFolderPath ?: return@LaunchedEffect
        if (mode != DvrLibraryMode.ARCHIVE || !initialFocusEnabled ||
            observedEntries.isEmpty() || observedArchive.folderAt(destination) == null
        ) {
            pendingFolderPath = null
            return@LaunchedEffect
        }
        // Attach the temporary list-local target before disposing the focused folder row.
        withFrameNanos { }
        if (mode != DvrLibraryMode.ARCHIVE || pendingFolderPath != destination ||
            drawerState?.currentValue == androidx.tv.material3.DrawerValue.Open
        ) {
            pendingFolderPath = null
            return@LaunchedEffect
        }
        if (runCatching { folderTransitionFocus.requestFocus() }.getOrDefault(false)) {
            archivePath = destination
            requestContentFocus = true
        } else {
            pendingFolderPath = null
        }
    }

    LaunchedEffect(
        location,
        itemKeys,
        selectedKeys[location],
        requestContentFocus,
        initialFocusEnabled,
        retryAvailable,
    ) {
        if (itemKeys.isEmpty() && !retryAvailable) {
            if (!initialFocusEnabled || (!requestContentFocus && !contentFocusOwned)) return@LaunchedEffect
            if (drawerState?.currentValue == androidx.tv.material3.DrawerValue.Open) return@LaunchedEffect
            if (modeFocus.requestFocus()) {
                requestContentFocus = false
                pendingFolderPath = null
                contentFocusOwned = false
            }
            return@LaunchedEffect
        }
        if (itemKeys.isNotEmpty() && selectedKeys[location] !in itemKeys) {
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
                pendingFolderPath = null
                return@LaunchedEffect
            }
        }
    }
    LaunchedEffect(location) {
        folderPreviewFocused = false
        folderPreviewRecordingId = null
    }
    LaunchedEffect(
        detailsEntry,
        pendingAction,
        pendingDetailsReturn,
        location,
        focusRecoveryGeneration,
    ) {
        val target = pendingDetailsReturn ?: return@LaunchedEffect
        if (detailsEntry != null || pendingAction != null) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            val restored = runCatching {
                when (target) {
                    RecordingDetailsReturnTarget.CONTENT -> {
                        if (itemKeys.isEmpty() && !retryAvailable) modeFocus.requestFocus()
                        else contentFocus.requestFocus()
                    }
                    RecordingDetailsReturnTarget.FOLDER_PREVIEW -> {
                        folderPreviewFocused = true
                        folderPreviewFocus.requestFocus()
                    }
                }
            }.getOrDefault(false)
            if (restored) {
                pendingDetailsReturn = null
                requestContentFocus = false
                return@LaunchedEffect
            }
        }
    }

    BackHandler(
        enabled = backEnabled && detailsEntry == null && mode == DvrLibraryMode.ARCHIVE &&
            (folderPreviewFocused || archivePath.isNotEmpty()),
    ) {
        if (folderPreviewFocused) {
            folderPreviewFocused = false
            runCatching { contentFocus.requestFocus() }
        } else {
            pendingFolderPath = archivePath.dropLast(1)
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
                    contentHasFocus &&
                    selectedKeys[location] == itemKeys.firstOrNull()
                ) {
                    relocatingKey = event.key
                    if (modeFocus.requestFocus()) {
                        pendingFolderPath = null
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
                .padding(start = startPadding, end = endPadding)
                .testTag("recordings-header"),
        )
        Spacer(Modifier.height(TvSpacing8))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startPadding)
                .testTag("recordings-mode-tabs-row"),
        ) {
            RecordingModeTabs(
                selected = mode,
                selectedFocus = modeFocus,
                onFocused = {
                    modeMotion.select(it, DvrLibraryMode.entries)
                    if (mode != it) {
                        pendingFolderPath = null
                        contentFocusOwned = false
                        requestContentFocus = false
                    }
                    mode = it
                },
                onClick = {
                    modeMotion.select(it, DvrLibraryMode.entries)
                    if (mode != it) pendingFolderPath = null
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
                    mode, archivePath, location, entries.isEmpty(), connectionUiState,
                    observation, channelsById, archiveItems, selectedArchiveItem,
                    selectedRecording, scheduleGroups, problemGroups, selectedKeys[location],
                    archiveScrollPositions[location] ?: 0,
                    screenState.archiveScrollOffsets[location] ?: 0,
                    focusRecoveryGeneration, folderPreviewRecordingId, folderPreviewFocused,
                )
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { frame, owner ->
        val mode = frame.mode
        val archivePath = frame.path
        val location = frame.location
        val observation = frame.observation
        val currentSession = observation.currentSession
        val channelsById = frame.channelsById
        val archiveItems = frame.archiveItems
        val selectedArchiveItem = frame.archiveSelection
        val selectedRecording = frame.recordingSelection
        val scheduleGroups = frame.schedule
        val problemGroups = frame.problems
        if (mode == DvrLibraryMode.ARCHIVE && archivePath.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildString {
                    append(stringResource(R.string.recordings_archive))
                    archivePath.forEach { append(" / ").append(it) }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = startPadding, end = endPadding),
            )
        }
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
                DvrLibraryMode.ARCHIVE -> Row(
                    modifier = Modifier
                        .padding(start = startPadding, end = endPadding)
                        .fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RecordingBrowserSurface(
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                            .then(if (owner.isCurrent) Modifier.focusRequester(folderTransitionFocus) else Modifier)
                            .focusProperties { canFocus = owner.isCurrent && pendingFolderPath != null }
                            .onFocusChanged {
                                if (!owner.isCurrent) return@onFocusChanged
                                contentHasFocus = it.hasFocus
                                if (it.hasFocus) contentFocusOwned = true
                            }
                            .focusable(),
                    ) {
                        key(location, frame.recoveryGeneration) {
                            val generation = frame.recoveryGeneration
                            ArchiveList(
                                items = archiveItems,
                                selectedKey = frame.selectedKey,
                                selectedFocus = contentFocus,
                                initialScrollIndex = frame.scrollIndex,
                                initialScrollOffset = frame.scrollOffset,
                                onScrollChanged = { index, offset ->
                                    if (owner.isCurrent && generation == focusRecoveryGeneration) {
                                        archiveScrollPositions[location] = index
                                        screenState.archiveScrollOffsets[location] = offset
                                    }
                                },
                                onFocused = {
                                    if (!owner.isCurrent) return@ArchiveList
                                    selectedKeys[location] = it
                                    folderPreviewFocused = false
                                },
                                onMoveToPreview = {
                                    if (!owner.isCurrent) return@ArchiveList
                                    runCatching { folderPreviewFocus.requestFocus() }
                                },
                                onOpenFolder = { folder ->
                                    if (!owner.isCurrent) return@ArchiveList
                                    val destination = "archive:${folder.path.joinToString("/")}"
                                    val destinationKeys = folder.listItems().map { it.key }
                                    if (selectedKeys[destination] !in destinationKeys) {
                                        selectedKeys[destination] = destinationKeys.firstOrNull().orEmpty()
                                    }
                                    pendingFolderPath = folder.path
                                },
                                onOpenRecording = {
                                    if (!owner.isCurrent) return@ArchiveList
                                    contentFocusOwned = false
                                    detailsOpenedFromFolderPreview = false
                                    detailsInitialAction = null
                                    detailsEntry = it
                                    detailsGeneration++
                                    detailsObservation = observation
                                    actionResult = null
                                },
                                imageLoader = imageLoader,
                                currentSession = currentSession,
                                piconForEntry = { entry ->
                                    entry.channelId?.let(channelsById::get)?.icon
                                },
                            )
                        }
                    }
                    RecordingBrowserSurface(
                        modifier = Modifier.weight(0.54f).fillMaxHeight(),
                    ) {
                        when (val item = selectedArchiveItem) {
                            is ArchiveListItem.Folder -> FolderMetadataPane(
                                folder = item.folder,
                                imageLoader = imageLoader,
                                currentSession = currentSession,
                                piconForEntry = { entry ->
                                    entry.channelId?.let(channelsById::get)?.icon
                                },
                                previewFocus = folderPreviewFocus,
                                selectedPreviewId = frame.previewId,
                                restoreFocus = owner.isCurrent && frame.previewFocused,
                                onPreviewFocusChanged = {
                                    if (!owner.isCurrent) return@FolderMetadataPane
                                    folderPreviewFocused = it
                                    if (it) contentFocusOwned = false
                                },
                                onPreviewRecordingFocused = { if (owner.isCurrent) folderPreviewRecordingId = it },
                                onMoveToFolder = {
                                    if (!owner.isCurrent) return@FolderMetadataPane
                                    folderPreviewFocused = false
                                    runCatching { contentFocus.requestFocus() }
                                },
                                onOpenRecording = {
                                    if (!owner.isCurrent) return@FolderMetadataPane
                                    contentFocusOwned = false
                                    detailsOpenedFromFolderPreview = true
                                    detailsInitialAction = null
                                    detailsEntry = it
                                    detailsGeneration++
                                    detailsObservation = observation
                                    actionResult = null
                                },
                            )
                            else -> RecordingMetadataPane(
                                entry = selectedRecording,
                                piconPath = selectedRecording?.let {
                                    it.channelId?.let(channelsById::get)?.icon
                                },
                                imageLoader = imageLoader,
                                currentSession = currentSession,
                            )
                        }
                    }
                }
                DvrLibraryMode.SCHEDULE -> RecordingBrowserSurface(
                    modifier = Modifier
                        .padding(start = startPadding, end = endPadding)
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
                                detailsOpenedFromFolderPreview = false
                                detailsInitialAction = null
                                detailsEntry = it
                                detailsGeneration++
                                detailsObservation = observation
                                actionResult = null
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
                DvrLibraryMode.PROBLEMS -> RecordingBrowserSurface(
                    modifier = Modifier
                        .padding(start = startPadding, end = endPadding)
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
                                detailsOpenedFromFolderPreview = false
                                detailsInitialAction = null
                                detailsEntry = it
                                detailsGeneration++
                                detailsObservation = observation
                                actionResult = null
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
            actionResult = null
            pendingDetailsReturn = RecordingDetailsReturnTarget.CONTENT
        }
    }
    if (opened != null && pendingAction == null) {
        RecordingDetailsPanel(
            contentPadding = contentPadding,
            entry = opened,
            actionResult = actionResult,
            canModifyRecordings = selectedCapability != null,
            playbackEligible = selectedCapability != null,
            initialAction = detailsInitialAction,
            backEnabled = backEnabled,
            onPlay = { intent ->
                selectedCapability?.let { capability ->
                    detailsEntry = null
                    detailsObservation = null
                    detailsInitialAction = null
                    actionResult = null
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
                val target = if (detailsOpenedFromFolderPreview) {
                    RecordingDetailsReturnTarget.FOLDER_PREVIEW
                } else {
                    RecordingDetailsReturnTarget.CONTENT
                }
                val restoredBeforeDismissal = runCatching {
                    when (target) {
                        RecordingDetailsReturnTarget.CONTENT -> contentFocus.requestFocus()
                        RecordingDetailsReturnTarget.FOLDER_PREVIEW -> {
                            folderPreviewFocused = true
                            folderPreviewFocus.requestFocus()
                        }
                    }
                }.getOrDefault(false)
                pendingDetailsReturn = target.takeUnless { restoredBeforeDismissal }
                detailsEntry = null
                detailsObservation = null
                detailsInitialAction = null
                actionResult = null
            },
        )
    }

    val action = pendingAction
    val target = opened
    LaunchedEffect(action, selectedCapability) {
        if (action != null && selectedCapability == null) {
            pendingAction = null
            pendingMutation = null
            actionResult = DvrMutationFeedback.CONNECTION_UNAVAILABLE
        }
    }
    if (action != null && target != null && selectedCapability != null) {
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
                val mutationEntry = detailsEntry
                val mutationObservation = detailsObservation
                val mutationGeneration = detailsGeneration
                scope.launch {
                    val result = dvrMutationActions.execute(mutation)
                    if (
                        detailsGeneration == mutationGeneration &&
                        detailsEntry === mutationEntry && detailsObservation === mutationObservation &&
                        latestSession != null && mutationObservation?.currentSession === latestSession
                    ) {
                        actionResult = result
                    }
                }
            },
        )
    }
    }
}
