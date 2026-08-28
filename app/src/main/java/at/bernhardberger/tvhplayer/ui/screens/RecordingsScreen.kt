package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.core.DvrProblemBucket
import at.bernhardberger.tvhplayer.core.DvrScheduleSection
import at.bernhardberger.tvhplayer.core.DvrScheduleSectionKind
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.core.buildDvrArchive
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.groupDvrSchedule
import at.bernhardberger.tvhplayer.core.groupDvrProblems
import at.bernhardberger.tvhplayer.core.partitionDvrLibrary
import at.bernhardberger.tvhplayer.core.recordingFocusTargetKey
import at.bernhardberger.tvhplayer.core.recordingListPageTargetIndex
import at.bernhardberger.tvhplayer.core.recordingListMetadata
import at.bernhardberger.tvhplayer.core.resolvePiconModel
import at.bernhardberger.tvhplayer.core.summarizeDvrFolder
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader
import at.bernhardberger.tvhplayer.ui.components.TvListRow
import coil3.ImageLoader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class PendingRecordingAction {
    CANCEL,
    DELETE,
}

private enum class RecordingDetailsAction {
    RESUME,
    BEGINNING,
    PLAY,
    CANCEL,
    DELETE,
    CLOSE,
}

private enum class RecordingDetailsReturnTarget {
    CONTENT,
    FOLDER_PREVIEW,
}

private sealed interface ArchiveListItem {
    val key: String

    data class Folder(val folder: DvrArchiveFolder) : ArchiveListItem {
        override val key = "folder:${folder.path.joinToString("/")}"
    }

    data class Recording(val entry: DvrEntry) : ArchiveListItem {
        override val key = "recording:${entry.id}"
    }
}

private fun DvrArchiveFolder.listItems(): List<ArchiveListItem> =
    folders.map(ArchiveListItem::Folder) + recordings.map(ArchiveListItem::Recording)

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
        onCancelRecording = session.dvrRepository::cancelEntry,
        onDeleteRecording = session.dvrRepository::deleteEntry,
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
    onCancelRecording: suspend (
        CurrentSessionObservation,
        DvrEntryId,
    ) -> DvrMutationResult<Unit> = { _, _ -> DvrMutationResult.NotReady },
    onDeleteRecording: suspend (
        CurrentSessionObservation,
        DvrEntryId,
    ) -> DvrMutationResult<Unit> = { _, _ -> DvrMutationResult.NotReady },
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
    var pendingCurrentSession by remember { mutableStateOf<CurrentSessionObservation?>(null) }
    var pendingRecordingId by remember { mutableStateOf<DvrEntryId?>(null) }
    var actionResult by remember { mutableStateOf<DvrMutationResult<*>?>(null) }
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
            .map { "recording:${it.id}" }
        DvrLibraryMode.PROBLEMS -> DvrProblemBucket.entries
            .flatMap { problemGroups[it].orEmpty() }
            .map { "recording:${it.id}" }
    }
    val selectedArchiveItem = archiveItems.firstOrNull { it.key == selectedKeys[location] }
    val selectedRecording = when (mode) {
        DvrLibraryMode.ARCHIVE ->
            (selectedArchiveItem as? ArchiveListItem.Recording)?.entry
        DvrLibraryMode.SCHEDULE -> library.schedule.firstOrNull {
            "recording:${it.id}" == selectedKeys[location]
        }
        DvrLibraryMode.PROBLEMS -> library.problems.firstOrNull {
            "recording:${it.id}" == selectedKeys[location]
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
            if (contentHasFocus) requestContentFocus = true
            return@LaunchedEffect
        }
        if (!initialFocusEnabled) return@LaunchedEffect
        if (!requestContentFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { contentFocus.requestFocus() }
        requestContentFocus = false
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
            .onKeyEvent { event ->
                if (event.key != Key.Back || event.type != KeyEventType.KeyUp) {
                    return@onKeyEvent false
                }
                when {
                    folderPreviewFocused && detailsEntry == null -> {
                        folderPreviewFocused = false
                        runCatching { contentFocus.requestFocus() }
                        true
                    }
                    archivePath.isNotEmpty() && detailsEntry == null -> {
                        archivePath = archivePath.dropLast(1)
                        requestContentFocus = true
                        true
                    }
                    else -> false
                }
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
                    mode = it
                    requestContentFocus = false
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
                            .onFocusChanged { contentHasFocus = it.hasFocus },
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
                                onPreviewFocusChanged = { folderPreviewFocused = it },
                                onPreviewRecordingFocused = { folderPreviewRecordingId = it },
                                onMoveToFolder = {
                                    folderPreviewFocused = false
                                    runCatching { contentFocus.requestFocus() }
                                },
                                onOpenRecording = {
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
                        .onFocusChanged { contentHasFocus = it.hasFocus },
                ) {
                    key(location, focusRecoveryGeneration) {
                        val generation = focusRecoveryGeneration
                        RecordingSchedule(
                            groups = scheduleGroups,
                            selectedKey = selectedKeys[location],
                            selectedFocus = contentFocus,
                            onFocused = { selectedKeys[location] = it },
                            onOpen = {
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
                        .onFocusChanged { contentHasFocus = it.hasFocus },
                ) {
                    key(location, focusRecoveryGeneration) {
                        val generation = focusRecoveryGeneration
                        RecordingProblems(
                            groups = problemGroups,
                            selectedKey = selectedKeys[location],
                            selectedFocus = contentFocus,
                            onFocused = { selectedKeys[location] = it },
                            onOpen = {
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
                pendingCurrentSession = selectedCapability
                pendingRecordingId = authoritative.id
                pendingAction = PendingRecordingAction.CANCEL
            },
            onDelete = {
                detailsInitialAction = RecordingDetailsAction.DELETE
                pendingCurrentSession = selectedCapability
                pendingRecordingId = authoritative.id
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
            onDismiss = { pendingAction = null },
            onConfirm = {
                pendingAction = null
                scope.launch {
                    val capability = pendingCurrentSession
                    val recordingId = pendingRecordingId
                    actionResult = if (capability == null || recordingId == null) {
                        DvrMutationResult.NotReady
                    } else when (action) {
                        PendingRecordingAction.CANCEL ->
                            onCancelRecording(capability, recordingId)
                        PendingRecordingAction.DELETE ->
                            onDeleteRecording(capability, recordingId)
                    }
                    pendingCurrentSession = null
                    pendingRecordingId = null
                }
            },
        )
    }
}

@Composable
private fun RecordingModeTabs(
    selected: DvrLibraryMode,
    modifier: Modifier = Modifier,
    onFocused: (DvrLibraryMode) -> Unit,
    onClick: (DvrLibraryMode) -> Unit,
    onMoveToContent: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = modifier
            .focusRestorer(selectedFocus)
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    onMoveToContent().let { true }
            },
    ) {
        val scheme = MaterialTheme.colorScheme
        val tabColors = TabDefaults.pillIndicatorTabColors(
            // Raise unselected contrast so Archive/Schedule/Problems all read as enabled.
            contentColor = scheme.onSurface.copy(alpha = TvTextSecondaryAlpha),
            inactiveContentColor = scheme.onSurface.copy(alpha = TvTextSecondaryAlpha),
            selectedContentColor = scheme.onSurface,
            focusedContentColor = scheme.inverseOnSurface,
            focusedSelectedContentColor = scheme.inverseOnSurface,
            disabledContentColor = scheme.onSurface.copy(alpha = TvTextDisabledAlpha),
            disabledInactiveContentColor = scheme.onSurface.copy(alpha = TvTextDisabledAlpha),
            disabledSelectedContentColor = scheme.onSurface.copy(alpha = TvTextDisabledAlpha),
        )
        DvrLibraryMode.entries.forEach { mode ->
            Tab(
                selected = selected == mode,
                onFocus = { onFocused(mode) },
                onClick = { onClick(mode) },
                colors = tabColors,
                modifier = if (selected == mode) {
                    Modifier.focusRequester(selectedFocus)
                } else {
                    Modifier
                },
            ) {
                Text(
                    text = stringResource(
                        when (mode) {
                            DvrLibraryMode.ARCHIVE -> R.string.recordings_archive
                            DvrLibraryMode.SCHEDULE -> R.string.recordings_schedule
                            DvrLibraryMode.PROBLEMS -> R.string.recordings_problems
                        }
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        horizontal = TvSpacing16,
                        vertical = TvSpacing8,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RecordingBrowserSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        content()
    }
}

@Composable
private fun ArchiveList(
    items: List<ArchiveListItem>,
    selectedKey: String?,
    selectedFocus: FocusRequester,
    initialScrollIndex: Int,
    onScrollChanged: (Int) -> Unit,
    onFocused: (String) -> Unit,
    onMoveToPreview: () -> Unit,
    onOpenFolder: (DvrArchiveFolder) -> Unit,
    onOpenRecording: (DvrEntry) -> Unit,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    piconForEntry: (DvrEntry) -> String?,
) {
    if (items.isEmpty()) {
        ModeEmptyState(R.string.recordings_archive_empty)
        return
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
    val scope = rememberCoroutineScope()
    val focusTargetKey = recordingFocusTargetKey(items.map { it.key }, selectedKey)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect(onScrollChanged)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .focusRestorer(selectedFocus)
            .testTag("recordings-archive-list")
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = ChannelNavigation.pageDirectionForKeyCode(
                    event.nativeKeyEvent.keyCode
                ) ?: return@onPreviewKeyEvent false
                val current = items.indexOfFirst { it.key == selectedKey }
                val target = recordingListPageTargetIndex(
                    itemCount = items.size,
                    currentIndex = current,
                    visibleItemCount = listState.layoutInfo.visibleItemsInfo.size,
                    direction = direction,
                ) ?: return@onPreviewKeyEvent true
                onFocused(items[target].key)
                scope.launch {
                    listState.animateScrollToItem(target)
                    delay(60)
                    runCatching { selectedFocus.requestFocus() }
                }
                true
            },
    ) {
        items(items, key = { it.key }) { item ->
            val selected = item.key == selectedKey
            val focusTarget = item.key == focusTargetKey
            when (item) {
                is ArchiveListItem.Folder -> FolderListRow(
                    folder = item.folder,
                    selected = selected,
                    focusTarget = focusTarget,
                    selectedFocus = selectedFocus,
                    onFocused = { onFocused(item.key) },
                    onMoveToPreview = onMoveToPreview,
                    onClick = { onOpenFolder(item.folder) },
                )
                is ArchiveListItem.Recording -> RecordingListRow(
                    entry = item.entry,
                    piconPath = piconForEntry(item.entry),
                    imageLoader = imageLoader,
                    currentSession = currentSession,
                    selected = selected,
                    focusTarget = focusTarget,
                    selectedFocus = selectedFocus,
                    onFocused = { onFocused(item.key) },
                    onClick = { onOpenRecording(item.entry) },
                )
            }
        }
    }
}

@Composable
private fun FolderListRow(
    folder: DvrArchiveFolder,
    selected: Boolean,
    focusTarget: Boolean,
    selectedFocus: FocusRequester,
    onFocused: () -> Unit,
    onMoveToPreview: () -> Unit,
    onClick: () -> Unit,
) {
    val summary = remember(folder) { summarizeDvrFolder(folder) }
    TvListRow(
        selected = selected,
        onClick = onClick,
        headlineContent = {
            Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                buildString {
                    append(
                        pluralStringResource(
                            R.plurals.recordings_folder_recording_count,
                            summary.recordingCount,
                            summary.recordingCount,
                        )
                    )
                    if (summary.totalSizeBytes > 0) {
                        append(" • ").append(formatFileSize(summary.totalSizeBytes))
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.Unspecified,
            )
        },
        leadingContent = {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recordings-folder-${folder.path.joinToString("/")}")
            .then(if (focusTarget) Modifier.focusRequester(selectedFocus) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onMoveToPreview()
                    true
                } else {
                    false
                }
            },
    )
}

@Composable
private fun FolderMetadataPane(
    folder: DvrArchiveFolder,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    piconForEntry: (DvrEntry) -> String?,
    previewFocus: FocusRequester,
    selectedPreviewId: DvrEntryId?,
    restoreFocus: Boolean,
    onPreviewFocusChanged: (Boolean) -> Unit,
    onPreviewRecordingFocused: (DvrEntryId) -> Unit,
    onMoveToFolder: () -> Unit,
    onOpenRecording: (DvrEntry) -> Unit,
) {
    val summary = remember(folder) { summarizeDvrFolder(folder) }
    val focusTargetId = selectedPreviewId
        ?.takeIf { selectedId -> summary.recentRecordings.any { it.id == selectedId } }
        ?: summary.recentRecordings.firstOrNull()?.id
    LaunchedEffect(focusTargetId) {
        if (focusTargetId != null && focusTargetId != selectedPreviewId) {
            if (restoreFocus) {
                withFrameNanos { }
                previewFocus.requestFocus()
            }
            onPreviewRecordingFocused(focusTargetId)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (summary.recentRecordings.isNotEmpty()) {
            Text(
                text = stringResource(R.string.recordings_folder_recent_in, folder.name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer(previewFocus)
                    .onFocusChanged { onPreviewFocusChanged(it.hasFocus) }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            onMoveToFolder()
                            true
                        } else {
                            false
                        }
                    },
            ) {
                itemsIndexed(summary.recentRecordings, key = { _, entry -> entry.id }) {
                        _, entry ->
                    FolderRecentRecordingRow(
                        entry = entry,
                        imageLoader = imageLoader,
                        currentSession = currentSession,
                        piconPath = piconForEntry(entry),
                        selected = focusTargetId == entry.id,
                        modifier = Modifier
                            .testTag("folder-preview-recording-${entry.id}")
                            .then(
                                if (focusTargetId == entry.id) {
                                    Modifier.focusRequester(previewFocus)
                                } else {
                                    Modifier
                                }
                            )
                            .onFocusChanged {
                                if (it.isFocused) onPreviewRecordingFocused(entry.id)
                            },
                        onClick = { onOpenRecording(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRecentRecordingRow(
    entry: DvrEntry,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    piconPath: String?,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TvListRow(
        selected = selected,
        onClick = onClick,
        headlineContent = {
            Text(entry.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                recordingListMetadata(entry),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.Unspecified,
            )
        },
        leadingContent = {
            PiconBox(
                imageLoader = imageLoader,
                currentSession = currentSession,
                piconPath = piconPath,
                modifier = Modifier.width(64.dp).height(42.dp),
            )
        },
        trailingContent = { RecordingDateTime(entry.start) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun RecordingMetadataPane(
    entry: DvrEntry?,
    piconPath: String?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
) {
    if (entry == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("recording-metadata-pane"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val artworkPath = (entry.image ?: entry.fanartImage)?.takeIf {
            currentSession?.let { capability ->
                resolvePiconModel(capability, "default", it)
            } != null
        }
        PiconBox(
            imageLoader = imageLoader,
            currentSession = currentSession,
            piconPath = artworkPath ?: piconPath,
            contentScale = if (artworkPath != null) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier
                .width(if (artworkPath != null) 176.dp else 92.dp)
                .height(if (artworkPath != null) 99.dp else 64.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        RecordingStatusIndicator(state = entry.state ?: DvrEntryState.UNKNOWN)
            Text(
                text = dvrStateLabel(entry.state),
                style = MaterialTheme.typography.labelLarge,
                color = when (entry.state) {
                    DvrEntryState.SCHEDULED,
                    DvrEntryState.RECORDING -> TvRecordingColor
                    DvrEntryState.MISSED,
                    DvrEntryState.INVALID,
                    DvrEntryState.RECORDING_ERROR,
                    DvrEntryState.COMPLETED_ERROR,
                    DvrEntryState.FILE_MISSING -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            text = entry.title.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        entry.subtitle?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = buildString {
                entry.channelName?.takeIf(String::isNotBlank)?.let {
                    append(it).append(" • ")
                }
                append(entry.start?.epochSeconds.recordingDateTime())
                entry.stop?.epochSeconds?.let { append('–').append(formatHm(it)) }
                val durationMinutes = recordingDurationMinutes(entry)
                if (durationMinutes != null) {
                    append(" • ").append(durationMinutes)
                    append(' ').append(stringResource(R.string.recordings_minutes_short))
                }
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        recordingEpisodeMetadata(entry)?.let {
            Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        entry.summary?.takeIf(String::isNotBlank)?.let {
            Text(text = it, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        entry.description?.takeIf { it.isNotBlank() && it != entry.summary }?.let {
            Text(
                text = it,
                maxLines = 7,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entry.subscriptionError?.name?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, maxLines = 2)
        }
        entry.playCount?.takeIf { it > 0 }?.let {
            Text(
                text = stringResource(R.string.recordings_play_count, it),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun recordingEpisodeMetadata(entry: DvrEntry): String? {
    val episode = entry.episode ?: return null
    return buildList {
    if (episode.seasonNumber != null || episode.episodeNumber != null) {
        add(
            buildString {
                episode.seasonNumber?.let { append("S").append(it.toString().padStart(2, '0')) }
                episode.episodeNumber?.let { append("E").append(it.toString().padStart(2, '0')) }
                episode.episodeCount?.let { append('/').append(it) }
            }
        )
    }
    episode.partNumber?.let { part ->
        add(buildString {
            append("Part ").append(part)
            episode.partCount?.let { append('/').append(it) }
        })
    }
}.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

@Composable
private fun RecordingSchedule(
    groups: List<DvrScheduleSection>,
    selectedKey: String?,
    selectedFocus: FocusRequester,
    onFocused: (String) -> Unit,
    onOpen: (DvrEntry) -> Unit,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    piconForEntry: (DvrEntry) -> String?,
    initialScrollIndex: Int,
    onScrollChanged: (Int) -> Unit,
) {
    if (groups.isEmpty()) {
        ModeEmptyState(R.string.recordings_schedule_empty)
        return
    }
    val entries = groups.flatMap { it.entries }
    val focusTargetKey = recordingFocusTargetKey(
        entries.map { "recording:${it.id}" },
        selectedKey,
    )
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
    val scope = rememberCoroutineScope()
    val lazyIndexes = remember(groups) {
        buildMap {
            var index = 0
            groups.forEach { section ->
                index++
                section.entries.forEach { entry -> put(entry.id, index++) }
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect(onScrollChanged)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .focusRestorer(selectedFocus)
            .testTag("recordings-schedule-list")
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = ChannelNavigation.pageDirectionForKeyCode(
                    event.nativeKeyEvent.keyCode
                ) ?: return@onPreviewKeyEvent false
                val current = entries.indexOfFirst { "recording:${it.id}" == selectedKey }
                val target = recordingListPageTargetIndex(
                    entries.size,
                    current,
                    listState.layoutInfo.visibleItemsInfo.count { it.key is Int },
                    direction,
                ) ?: return@onPreviewKeyEvent true
                onFocused("recording:${entries[target].id}")
                scope.launch {
                    listState.animateScrollToItem(lazyIndexes.getValue(entries[target].id))
                    delay(60)
                    runCatching { selectedFocus.requestFocus() }
                }
                true
            },
    ) {
        groups.forEach { section ->
            item(key = "header-${section.kind}-${section.date}") {
                RecordingSectionHeader(
                    text = scheduleSectionLabel(section),
                    recordingNow = section.kind == DvrScheduleSectionKind.RECORDING_NOW,
                )
            }
            items(section.entries, key = { it.id }) { entry ->
                RecordingListRow(
                    entry = entry,
                    piconPath = piconForEntry(entry),
                    imageLoader = imageLoader,
                    currentSession = currentSession,
                    selected = selectedKey == "recording:${entry.id}",
                    focusTarget = focusTargetKey == "recording:${entry.id}",
                    selectedFocus = selectedFocus,
                    onFocused = { onFocused("recording:${entry.id}") },
                    onClick = { onOpen(entry) },
                    kind = RecordingRowKind.SCHEDULE,
                )
            }
        }
    }
}

@Composable
private fun RecordingProblems(
    groups: Map<DvrProblemBucket, List<DvrEntry>>,
    selectedKey: String?,
    selectedFocus: FocusRequester,
    onFocused: (String) -> Unit,
    onOpen: (DvrEntry) -> Unit,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    piconForEntry: (DvrEntry) -> String?,
    initialScrollIndex: Int,
    onScrollChanged: (Int) -> Unit,
) {
    val entries = DvrProblemBucket.entries.flatMap { groups[it].orEmpty() }
    if (entries.isEmpty()) {
        ModeEmptyState(R.string.recordings_problems_empty)
        return
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
    val scope = rememberCoroutineScope()
    val focusTargetKey = recordingFocusTargetKey(
        entries.map { "recording:${it.id}" },
        selectedKey,
    )
    val lazyIndexes = remember(groups) {
        buildMap {
            var index = 0
            DvrProblemBucket.entries.forEach { bucket ->
                val bucketEntries = groups[bucket].orEmpty()
                if (bucketEntries.isNotEmpty()) index++
                bucketEntries.forEach { entry -> put(entry.id, index++) }
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect(onScrollChanged)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .focusRestorer(selectedFocus)
            .testTag("recordings-problems-list")
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = ChannelNavigation.pageDirectionForKeyCode(
                    event.nativeKeyEvent.keyCode
                ) ?: return@onPreviewKeyEvent false
                val current = entries.indexOfFirst { "recording:${it.id}" == selectedKey }
                val target = recordingListPageTargetIndex(
                    entries.size,
                    current,
                    listState.layoutInfo.visibleItemsInfo.count { it.key is Int },
                    direction,
                ) ?: return@onPreviewKeyEvent true
                onFocused("recording:${entries[target].id}")
                scope.launch {
                    listState.animateScrollToItem(lazyIndexes.getValue(entries[target].id))
                    delay(60)
                    runCatching { selectedFocus.requestFocus() }
                }
                true
            },
    ) {
        DvrProblemBucket.entries.forEach { bucket ->
            val bucketEntries = groups[bucket].orEmpty()
            if (bucketEntries.isNotEmpty()) {
                item(key = "header-$bucket") {
                    RecordingSectionHeader(
                        stringResource(
                            if (bucket == DvrProblemBucket.FAILED) {
                                R.string.recordings_failed
                            } else {
                                R.string.recordings_cancelled
                            }
                        )
                    )
                }
                items(bucketEntries, key = { it.id }) { entry ->
                    RecordingListRow(
                        entry = entry,
                        piconPath = piconForEntry(entry),
                        imageLoader = imageLoader,
                        currentSession = currentSession,
                        selected = selectedKey == "recording:${entry.id}",
                        focusTarget = focusTargetKey == "recording:${entry.id}",
                        selectedFocus = selectedFocus,
                        onFocused = { onFocused("recording:${entry.id}") },
                        onClick = { onOpen(entry) },
                        kind = RecordingRowKind.PROBLEM,
                    )
                }
            }
        }
    }
}

private enum class RecordingRowKind {
    ARCHIVE,
    SCHEDULE,
    PROBLEM,
}

@Composable
private fun RecordingListRow(
    entry: DvrEntry,
    piconPath: String?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    selected: Boolean,
    focusTarget: Boolean = selected,
    selectedFocus: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    kind: RecordingRowKind = RecordingRowKind.ARCHIVE,
) {
    val problem = kind == RecordingRowKind.PROBLEM
    val active = kind == RecordingRowKind.SCHEDULE && entry.state == DvrEntryState.RECORDING
    val metadata = recordingListMetadata(entry, problem = problem)
    TvListRow(
        selected = selected,
        onClick = onClick,
        headlineContent = {
            Text(
                text = entry.title.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("recording-list-headline-${entry.id}"),
            )
        },
        supportingContent = {
            Text(
                text = if (active) {
                    listOfNotNull(
                        stringResource(R.string.recordings_recording_now),
                        metadata.takeIf(String::isNotBlank),
                    ).joinToString(" • ")
                } else metadata,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (problem) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        leadingContent = {
            Row(
                modifier = Modifier.testTag("recording-list-leading-${entry.id}"),
                horizontalArrangement = Arrangement.spacedBy(TvSpacing8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active) {
                    RecordingStatusIndicator(
                        state = DvrEntryState.RECORDING,
                        announceState = false,
                    )
                }
                if (problem) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = stringResource(R.string.recordings_problem_indicator),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                }
                PiconBox(
                    imageLoader = imageLoader,
                    currentSession = currentSession,
                    piconPath = piconPath,
                    modifier = Modifier.width(64.dp).height(42.dp),
                )
            }
        },
        trailingContent = {
            Box(Modifier.testTag("recording-list-trailing-${entry.id}")) {
                if (kind == RecordingRowKind.SCHEDULE) ScheduleTime(entry)
                else RecordingDateTime(entry.start)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recording-list-entry-${entry.id}")
            .then(if (focusTarget) Modifier.focusRequester(selectedFocus) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocused() },
    )
}

@Composable
private fun RecordingSectionHeader(
    text: String,
    recordingNow: Boolean = false,
) {
    Row(
        modifier = Modifier
            .padding(start = 12.dp, top = 8.dp, bottom = 2.dp)
            .semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(TvSpacing8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (recordingNow) {
            RecordingStatusIndicator(
                state = DvrEntryState.RECORDING,
                announceState = false,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RecordingDateTime(start: kotlin.time.Instant?) {
    Column(
        // Size to content within a bounded range so titles keep more width.
        modifier = Modifier.widthIn(min = 72.dp, max = 110.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // Inherit ListItem content colour so focused rows stay readable.
        Text(start?.epochSeconds.recordingDay(), maxLines = 1, color = Color.Unspecified)
        Text(start?.epochSeconds?.let(::formatHm).orEmpty(), maxLines = 1, color = Color.Unspecified)
    }
}

@Composable
private fun ScheduleTime(entry: DvrEntry) {
    Column(
        modifier = Modifier.widthIn(min = 88.dp, max = 140.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(entry.start?.epochSeconds?.let(::formatHm).orEmpty(), maxLines = 1, color = Color.Unspecified)
        Text(
            text = stringResource(
                R.string.recordings_schedule_end_duration,
                entry.stop?.epochSeconds?.let(::formatHm).orEmpty(),
                recordingDurationMinutes(entry) ?: 0L,
            ),
            maxLines = 1,
            color = Color.Unspecified,
        )
    }
}

@Composable
private fun RecordingDetailsPanel(
    contentPadding: PaddingValues,
    entry: DvrEntry,
    actionResult: DvrMutationResult<*>?,
    canModifyRecordings: Boolean,
    playbackEligible: Boolean,
    initialAction: RecordingDetailsAction?,
    backEnabled: Boolean,
    onPlay: (RecordingPlaybackStart) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val primaryFocus = remember { FocusRequester() }
    val secondaryFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    var focusedAction by remember(entry.id) { mutableStateOf<RecordingDetailsAction?>(null) }
    val canCancel = canModifyRecordings &&
        (entry.state == DvrEntryState.SCHEDULED || entry.state == DvrEntryState.RECORDING)
    val canDelete = canModifyRecordings &&
        entry.state in setOf(
            DvrEntryState.COMPLETED,
            DvrEntryState.MISSED,
            DvrEntryState.INVALID,
            DvrEntryState.RECORDING_ERROR,
            DvrEntryState.COMPLETED_ERROR,
            DvrEntryState.FILE_MISSING,
        )
    val canPlay = playbackEligible && entry.state in setOf(
        DvrEntryState.COMPLETED,
        DvrEntryState.RECORDING,
    )
    val resumeSeconds = entry.playPosition?.inWholeSeconds?.takeIf {
        canPlay && entry.state == DvrEntryState.COMPLETED && it > 0L
    }
    val primaryAction = when {
        resumeSeconds != null -> RecordingDetailsAction.RESUME
        canPlay -> RecordingDetailsAction.PLAY
        canCancel -> RecordingDetailsAction.CANCEL
        canDelete -> RecordingDetailsAction.DELETE
        else -> RecordingDetailsAction.CLOSE
    }
    val availableActions = buildSet {
        if (resumeSeconds != null) {
            add(RecordingDetailsAction.RESUME)
            add(RecordingDetailsAction.BEGINNING)
        } else if (canPlay) {
            add(RecordingDetailsAction.PLAY)
        }
        if (canCancel) add(RecordingDetailsAction.CANCEL)
        if (canDelete) add(RecordingDetailsAction.DELETE)
        add(RecordingDetailsAction.CLOSE)
    }
    fun requester(action: RecordingDetailsAction): FocusRequester = when (action) {
        RecordingDetailsAction.RESUME,
        RecordingDetailsAction.PLAY -> primaryFocus
        RecordingDetailsAction.BEGINNING -> secondaryFocus
        RecordingDetailsAction.CANCEL -> cancelFocus
        RecordingDetailsAction.DELETE -> deleteFocus
        RecordingDetailsAction.CLOSE -> closeFocus
    }
    LaunchedEffect(entry.id, initialAction) {
        withFrameNanos { }
        requester(initialAction?.takeIf { it in availableActions } ?: primaryAction).requestFocus()
    }
    LaunchedEffect(availableActions, focusedAction) {
        val focused = focusedAction ?: return@LaunchedEffect
        if (focused !in availableActions) {
            withFrameNanos { }
            requester(primaryAction).requestFocus()
        }
    }
    RecordingDetailsSurface(
        contentPadding = contentPadding,
        backEnabled = backEnabled,
        onBack = onClose,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recording-details-metadata"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                entry.title.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                modifier = Modifier.semantics { heading() },
            )
            entry.subtitle?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                buildString {
                    append(entry.start?.epochSeconds.recordingDateTime())
                    entry.stop?.epochSeconds?.let { append('–').append(formatHm(it)) }
                    entry.channelName?.let { append(" • ").append(it) }
                    append(" • ").append(dvrStateLabel(entry.state))
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("recording-details-metadata-anchor"),
            )
            val failureReason = entry.subscriptionError?.name
            when {
                !failureReason.isNullOrBlank() -> Text(
                    failureReason,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                actionResult != null -> Text(
                    dvrActionResultLabel(actionResult),
                    color = if (actionResult.isDvrMutationFailure()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (
                failureReason.isNullOrBlank() &&
                actionResult == null
            ) {
                val synopsis = entry.summary?.takeIf(String::isNotBlank)
                    ?: entry.description?.takeIf(String::isNotBlank)
                synopsis?.let {
                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recording-details-playback-actions"),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (resumeSeconds != null) {
                val accessibleResumeLabel = stringResource(
                    R.string.recording_resume_from,
                    recordingDurationForAccessibility(resumeSeconds),
                )
                Button(
                    onClick = { onPlay(RecordingPlaybackStart.RESUME) },
                    modifier = Modifier
                        .focusRequester(primaryFocus)
                        .onFocusChanged {
                            if (it.isFocused) focusedAction = RecordingDetailsAction.RESUME
                        }
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = secondaryFocus
                            up = FocusRequester.Cancel
                            down = closeFocus
                        }
                        .semantics { contentDescription = accessibleResumeLabel }
                        .testTag("recording-details-resume"),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            R.string.recording_resume_from,
                            formatPlaybackDuration(
                                resumeSeconds.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
                            ),
                        )
                    )
                }
                Button(
                    onClick = { onPlay(RecordingPlaybackStart.START_OVER) },
                    modifier = Modifier
                        .focusRequester(secondaryFocus)
                        .onFocusChanged {
                            if (it.isFocused) focusedAction = RecordingDetailsAction.BEGINNING
                        }
                        .focusProperties {
                            left = primaryFocus
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = when {
                                canCancel -> cancelFocus
                                canDelete -> deleteFocus
                                else -> closeFocus
                            }
                        }
                        .testTag("recording-details-beginning"),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recording_play_from_beginning))
                }
            } else if (canPlay) {
                Button(
                    onClick = {
                        onPlay(
                            if (entry.state == DvrEntryState.RECORDING) {
                                RecordingPlaybackStart.START_OVER
                            } else {
                                RecordingPlaybackStart.RESUME
                            }
                        )
                    },
                    modifier = Modifier
                        .focusRequester(primaryFocus)
                        .onFocusChanged {
                            if (it.isFocused) focusedAction = RecordingDetailsAction.PLAY
                        }
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = closeFocus
                        }
                        .testTag("recording-details-play"),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier
                    .focusRequester(closeFocus)
                    .onFocusChanged {
                        if (it.isFocused) focusedAction = RecordingDetailsAction.CLOSE
                    }
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = when {
                            canCancel -> cancelFocus
                            canDelete -> deleteFocus
                            else -> FocusRequester.Cancel
                        }
                        up = primaryFocus
                        down = FocusRequester.Cancel
                    }
                    .testTag("recording-details-close"),
            ) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.close))
            }
            if (canCancel) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .focusRequester(cancelFocus)
                        .onFocusChanged {
                            if (it.isFocused) focusedAction = RecordingDetailsAction.CANCEL
                        }
                        .focusProperties {
                            left = closeFocus
                            right = FocusRequester.Cancel
                            up = if (resumeSeconds != null) secondaryFocus
                                else if (canPlay) primaryFocus
                                else FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }
                        .testTag("recording-details-cancel"),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cancel_recording))
                }
            }
            if (canDelete) {
                Button(
                    onClick = onDelete,
                    modifier = Modifier
                        .focusRequester(deleteFocus)
                        .onFocusChanged {
                            if (it.isFocused) focusedAction = RecordingDetailsAction.DELETE
                        }
                        .focusProperties {
                            left = closeFocus
                            right = FocusRequester.Cancel
                            up = if (resumeSeconds != null) secondaryFocus
                                else if (canPlay) primaryFocus
                                else FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }
                        .testTag("recording-details-delete"),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_recording))
                }
            }
        }
    }
}

@Composable
private fun recordingDurationForAccessibility(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3_600L
    val minutes = safeSeconds % 3_600L / 60L
    val seconds = safeSeconds % 60L
    return listOfNotNull(
        hours.takeIf { it > 0L }?.let {
            pluralStringResource(R.plurals.recording_duration_hours, it.toInt(), it)
        },
        minutes.takeIf { it > 0L }?.let {
            pluralStringResource(R.plurals.recording_duration_minutes, it.toInt(), it)
        },
        seconds.takeIf { it > 0L || hours == 0L && minutes == 0L }?.let {
            pluralStringResource(R.plurals.recording_duration_seconds, it.toInt(), it)
        },
    ).joinToString(", ")
}

@Composable
private fun RecordingDetailsSurface(
    contentPadding: PaddingValues,
    backEnabled: Boolean,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.64f))
            .onPreviewKeyEvent { event ->
                if (!backEnabled || event.key != Key.Back) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyUp) onBack()
                    true
                }
            }
            .focusGroup()
            .padding(contentPadding)
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            modifier = Modifier
                .width(560.dp)
                .heightIn(max = 432.dp)
                .testTag("recording-details-panel"),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun RecordingConfirmationDialog(
    action: PendingRecordingAction,
    title: String,
    backEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val safeFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(action) { safeFocus.requestFocus() }
    RecordingDialogSurface(
        backEnabled = backEnabled,
        onBack = onDismiss,
    ) {
        Text(
            text = stringResource(
                if (action == PendingRecordingAction.CANCEL) {
                    R.string.cancel_recording_confirm_title
                } else {
                    R.string.delete_recording_confirm_title
                },
                title,
            ),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(
                if (action == PendingRecordingAction.CANCEL) {
                    R.string.cancel_recording_confirm_message
                } else {
                    R.string.delete_recording_confirm_message
                }
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .focusRequester(safeFocus)
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = confirmFocus
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    }
                    .testTag("recording-confirmation-back"),
            ) {
                Text(stringResource(R.string.back))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .focusRequester(confirmFocus)
                    .focusProperties {
                        left = safeFocus
                        right = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    }
                    .testTag("recording-confirmation-confirm"),
            ) {
                Text(
                    stringResource(
                        if (action == PendingRecordingAction.CANCEL) {
                            R.string.cancel_recording
                        } else {
                            R.string.delete_recording
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun RecordingDialogSurface(
    backEnabled: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = TvScrimModalAlpha))
            .onPreviewKeyEvent { event ->
                if (!backEnabled || event.key != Key.Back) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyUp) onBack()
                    true
                }
            }
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(720.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun RecordingsEmptyState(
    connectionUiState: ConnectionUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = connectionUiState is ConnectionUiState.Error
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(
                when (connectionUiState) {
                    ConnectionUiState.Connecting,
                    ConnectionUiState.SyncingChannels -> R.string.recordings_loading
                    ConnectionUiState.Reconnecting -> R.string.recordings_reconnecting
                    is ConnectionUiState.Error -> R.string.recordings_server_failure
                    else -> R.string.recordings_empty
                }
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (error) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun ModeEmptyState(message: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(message),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun scheduleSectionLabel(section: DvrScheduleSection): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (section.kind) {
        DvrScheduleSectionKind.RECORDING_NOW -> stringResource(R.string.recordings_recording_now)
        DvrScheduleSectionKind.TODAY -> stringResource(R.string.today)
        DvrScheduleSectionKind.TOMORROW -> stringResource(R.string.tomorrow)
        DvrScheduleSectionKind.DATE -> section.date?.format(
            DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
        ).orEmpty()
    }
}

@Composable
private fun dvrStateLabel(state: DvrEntryState?): String = stringResource(
    when (state) {
        DvrEntryState.SCHEDULED -> R.string.recording_state_scheduled
        DvrEntryState.RECORDING -> R.string.recording_state_recording
        DvrEntryState.COMPLETED -> R.string.recording_state_completed
        DvrEntryState.MISSED,
        DvrEntryState.INVALID -> R.string.recording_state_cancelled
        DvrEntryState.RECORDING_ERROR,
        DvrEntryState.COMPLETED_ERROR,
        DvrEntryState.FILE_MISSING -> R.string.recording_state_failed
        DvrEntryState.UNKNOWN,
        null -> R.string.recording_state_unknown
    }
)

@Composable
private fun dvrActionResultLabel(result: DvrMutationResult<*>): String = stringResource(
    when (result) {
        is DvrMutationResult.Confirmed,
        is DvrMutationResult.AcceptedButUnconfirmed -> R.string.recording_action_accepted
        DvrMutationResult.AccessDenied -> R.string.recording_action_permission
        DvrMutationResult.ConnectionLimit -> R.string.recording_action_conn_limit
        DvrMutationResult.ServerRejected,
        DvrMutationResult.NotSupported -> R.string.recording_action_rejected
        DvrMutationResult.NotReady,
        DvrMutationResult.ObservationExpired,
        DvrMutationResult.Timeout,
        DvrMutationResult.TransportUnavailable -> R.string.recording_action_connection
    }
)

private fun DvrMutationResult<*>.isDvrMutationFailure(): Boolean =
    this !is DvrMutationResult.Confirmed && this !is DvrMutationResult.AcceptedButUnconfirmed

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

private fun Long?.recordingDateTime(): String = this?.let {
    Instant.ofEpochSecond(it)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm"))
}.orEmpty()

private fun Long?.recordingDay(): String = this?.let {
    Instant.ofEpochSecond(it)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM"))
}.orEmpty()

private fun recordingDurationMinutes(entry: DvrEntry): Long? {
    val start = entry.start ?: return null
    val stop = entry.stop ?: return null
    return (stop - start).inWholeMinutes.coerceAtLeast(0L)
}

private fun formatFileSize(sizeBytes: Long): String = when {
    sizeBytes >= 1_000_000_000_000L -> String.format(
        Locale.getDefault(),
        "%.1f TB",
        sizeBytes / 1_000_000_000_000.0,
    )
    sizeBytes >= 1_000_000_000L -> String.format(
        Locale.getDefault(),
        "%.1f GB",
        sizeBytes / 1_000_000_000.0,
    )
    sizeBytes >= 1_000_000L -> String.format(
        Locale.getDefault(),
        "%.1f MB",
        sizeBytes / 1_000_000.0,
    )
    else -> String.format(Locale.getDefault(), "%.1f KB", sizeBytes / 1_000.0)
}
