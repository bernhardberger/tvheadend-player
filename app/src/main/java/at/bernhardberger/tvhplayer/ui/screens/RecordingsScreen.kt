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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.core.DvrProblemBucket
import at.bernhardberger.tvhplayer.core.buildDvrArchive
import at.bernhardberger.tvhplayer.core.groupDvrProblems
import at.bernhardberger.tvhplayer.core.groupDvrSchedule
import at.bernhardberger.tvhplayer.core.partitionDvrLibrary
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader
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
import org.koin.compose.koinInject

private enum class RecordingDetailsReturnTarget {
    CONTENT,
    FOLDER_PREVIEW,
}

class RecordingsScreenState {
    val selectedKeys = mutableStateMapOf<String, String>()
    val archiveScrollPositions = mutableStateMapOf<String, Int>()
    val mode = mutableStateOf(DvrLibraryMode.ARCHIVE)
    val archivePath = mutableStateOf<List<String>>(emptyList())
}

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
    val entries = observation.dvrEntries()
    val channels = observation.channels()
    val channelsById = remember(channels) { channels.associateBy { it.id } }
    val library = remember(entries) { partitionDvrLibrary(entries) }
    val archive = remember(library.archive) { buildDvrArchive(library.archive) }
    val scope = rememberCoroutineScope()
    val screenState = state ?: remember { RecordingsScreenState() }
    val contentFocus = remember { FocusRequester() }
    val folderPreviewFocus = remember { FocusRequester() }
    val selectedKeys = screenState.selectedKeys
    val archiveScrollPositions = screenState.archiveScrollPositions
    var mode by screenState.mode
    var archivePath by screenState.archivePath
    var requestContentFocus by remember { mutableStateOf(true) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var contentFocusOwned by remember { mutableStateOf(false) }
    var focusRecoveryGeneration by remember { mutableIntStateOf(0) }
    var folderPreviewFocused by remember { mutableStateOf(false) }
    var folderPreviewRecordingId by remember { mutableStateOf<DvrEntryId?>(null) }
    var detailsOpenedFromFolderPreview by remember { mutableStateOf(false) }
    var detailsEntry by remember { mutableStateOf<DvrEntry?>(null) }
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

    val archiveFolder = archive.folderAt(archivePath) ?: archive
    val archiveItems = remember(archiveFolder) { archiveFolder.listItems() }
    val scheduleGroups = remember(library.schedule) {
        groupDvrSchedule(library.schedule, System.currentTimeMillis() / 1000L)
    }
    val problemGroups = remember(library.problems) { groupDvrProblems(library.problems) }
    val location = when (mode) {
        DvrLibraryMode.ARCHIVE -> "archive:${archivePath.joinToString("/")}"
        DvrLibraryMode.SCHEDULE -> "schedule"
        DvrLibraryMode.PROBLEMS -> "problems"
    }
    val itemKeys = when (mode) {
        DvrLibraryMode.ARCHIVE -> archiveItems.map { it.key }
        DvrLibraryMode.SCHEDULE -> scheduleGroups.flatMap { it.entries }
            .map { "recording:${recordingItemKey(it.id)}" }
        DvrLibraryMode.PROBLEMS -> DvrProblemBucket.entries
            .flatMap { problemGroups[it].orEmpty() }
            .map { "recording:${recordingItemKey(it.id)}" }
    }
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

    LaunchedEffect(
        location,
        itemKeys,
        selectedKeys[location],
        requestContentFocus,
        initialFocusEnabled,
    ) {
        if (itemKeys.isEmpty()) {
            requestContentFocus = false
            return@LaunchedEffect
        }
        if (selectedKeys[location] !in itemKeys) {
            selectedKeys[location] = itemKeys.first()
            archiveScrollPositions[location] = 0
            focusRecoveryGeneration++
            if (contentFocusOwned) requestContentFocus = true
            return@LaunchedEffect
        }
        if (!initialFocusEnabled) return@LaunchedEffect
        if (!requestContentFocus) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            val focused = runCatching { contentFocus.requestFocus() }.getOrDefault(false)
            if (focused) {
                requestContentFocus = false
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
                    RecordingDetailsReturnTarget.CONTENT -> contentFocus.requestFocus()
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
        enabled = backEnabled && detailsEntry == null &&
            (folderPreviewFocused || archivePath.isNotEmpty()),
    ) {
        if (folderPreviewFocused) {
            folderPreviewFocused = false
            runCatching { contentFocus.requestFocus() }
        } else {
            archivePath = archivePath.dropLast(1)
            requestContentFocus = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { if (detailsEntry != null) hideFromAccessibility() }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionUp &&
                    contentHasFocus
                ) {
                    contentFocusOwned = false
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
                onFocused = {
                    if (mode != it) {
                        contentFocusOwned = false
                        requestContentFocus = false
                    }
                    mode = it
                },
                onClick = {
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

        if (entries.isEmpty()) {
            RecordingsEmptyState(
                connectionUiState = connectionUiState,
                onRetry = onRetry,
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
                            .onFocusChanged {
                                contentHasFocus = it.hasFocus
                                if (it.hasFocus) contentFocusOwned = true
                            },
                    ) {
                        key(location, focusRecoveryGeneration) {
                            val generation = focusRecoveryGeneration
                            ArchiveList(
                                items = archiveItems,
                                selectedKey = selectedKeys[location],
                                selectedFocus = contentFocus,
                                initialScrollIndex = archiveScrollPositions[location] ?: 0,
                                onScrollChanged = {
                                    if (generation == focusRecoveryGeneration) {
                                        archiveScrollPositions[location] = it
                                    }
                                },
                                onFocused = {
                                    selectedKeys[location] = it
                                    folderPreviewFocused = false
                                },
                                onMoveToPreview = {
                                    runCatching { folderPreviewFocus.requestFocus() }
                                },
                                onOpenFolder = { folder ->
                                    val destination = "archive:${folder.path.joinToString("/")}"
                                    val destinationKeys = folder.listItems().map { it.key }
                                    if (selectedKeys[destination] !in destinationKeys) {
                                        selectedKeys[destination] = destinationKeys.firstOrNull().orEmpty()
                                    }
                                    archivePath = folder.path
                                    requestContentFocus = true
                                },
                                onOpenRecording = {
                                    contentFocusOwned = false
                                    detailsOpenedFromFolderPreview = false
                                    detailsInitialAction = null
                                    detailsEntry = it
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
                                selectedPreviewId = folderPreviewRecordingId,
                                restoreFocus = folderPreviewFocused,
                                onPreviewFocusChanged = {
                                    folderPreviewFocused = it
                                    if (it) contentFocusOwned = false
                                },
                                onPreviewRecordingFocused = { folderPreviewRecordingId = it },
                                onMoveToFolder = {
                                    folderPreviewFocused = false
                                    runCatching { contentFocus.requestFocus() }
                                },
                                onOpenRecording = {
                                    contentFocusOwned = false
                                    detailsOpenedFromFolderPreview = true
                                    detailsInitialAction = null
                                    detailsEntry = it
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
                            contentHasFocus = it.hasFocus
                            if (it.hasFocus) contentFocusOwned = true
                        },
                ) {
                    key(location, focusRecoveryGeneration) {
                        val generation = focusRecoveryGeneration
                        RecordingSchedule(
                            groups = scheduleGroups,
                            selectedKey = selectedKeys[location],
                            selectedFocus = contentFocus,
                            onFocused = { selectedKeys[location] = it },
                            onOpen = {
                                contentFocusOwned = false
                                detailsOpenedFromFolderPreview = false
                                detailsInitialAction = null
                                detailsEntry = it
                                detailsObservation = observation
                                actionResult = null
                            },
                            imageLoader = imageLoader,
                            currentSession = currentSession,
                            piconForEntry = { entry ->
                                entry.channelId?.let(channelsById::get)?.icon
                            },
                            initialScrollIndex = archiveScrollPositions[location] ?: 0,
                            onScrollChanged = {
                                if (generation == focusRecoveryGeneration) {
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
                            contentHasFocus = it.hasFocus
                            if (it.hasFocus) contentFocusOwned = true
                        },
                ) {
                    key(location, focusRecoveryGeneration) {
                        val generation = focusRecoveryGeneration
                        RecordingProblems(
                            groups = problemGroups,
                            selectedKey = selectedKeys[location],
                            selectedFocus = contentFocus,
                            onFocused = { selectedKeys[location] = it },
                            onOpen = {
                                contentFocusOwned = false
                                detailsOpenedFromFolderPreview = false
                                detailsInitialAction = null
                                detailsEntry = it
                                detailsObservation = observation
                                actionResult = null
                            },
                            imageLoader = imageLoader,
                            currentSession = currentSession,
                            piconForEntry = { entry ->
                                entry.channelId?.let(channelsById::get)?.icon
                            },
                            initialScrollIndex = archiveScrollPositions[location] ?: 0,
                            onScrollChanged = {
                                if (generation == focusRecoveryGeneration) {
                                    archiveScrollPositions[location] = it
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    val opened = detailsEntry
    if (opened != null && pendingAction == null) {
        val selectedObservation = detailsObservation ?: observation
        val authoritative = selectedObservation.dvrEntry(opened.id) ?: opened
        val selectedCapability = selectedObservation.currentSession
            ?.takeIf { observation.currentSession === it }
        RecordingDetailsPanel(
            contentPadding = contentPadding,
            entry = authoritative,
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
                        RecordingPlaybackSelection(capability, authoritative.id),
                        intent,
                    )
                }
            },
            onCancel = {
                detailsInitialAction = RecordingDetailsAction.CANCEL
                pendingMutation = selectedCapability?.let { capability ->
                    DvrMutationAction.Cancel(capability, authoritative.id)
                }
                pendingAction = PendingRecordingAction.CANCEL
            },
            onDelete = {
                detailsInitialAction = RecordingDetailsAction.DELETE
                pendingMutation = selectedCapability?.let { capability ->
                    DvrMutationAction.Delete(capability, authoritative.id)
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
    val target = detailsEntry
    if (action != null && target != null) {
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
                scope.launch {
                    actionResult = dvrMutationActions.execute(mutation)
                }
            },
        )
    }
}

private fun SessionObservation.dvrEntries(): List<DvrEntry> = when (val state = dvrState) {
    is DvrRepositoryState.Current -> state.snapshot.entries
    is DvrRepositoryState.Stale -> state.snapshot.entries
    is DvrRepositoryState.Synchronizing -> state.staleSnapshot?.entries.orEmpty()
    DvrRepositoryState.Empty -> emptyList()
}

private fun SessionObservation.channels(): List<Channel> = when (val state = channelState) {
    is ChannelRepositoryState.Current -> state.catalog.channels
    is ChannelRepositoryState.Stale -> state.catalog.channels
    is ChannelRepositoryState.Synchronizing -> state.staleCatalog?.channels.orEmpty()
    ChannelRepositoryState.Empty -> emptyList()
}
