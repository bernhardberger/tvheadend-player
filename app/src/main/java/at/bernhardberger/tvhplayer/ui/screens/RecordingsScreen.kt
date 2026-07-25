package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedIconButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrActionFailure
import at.bernhardberger.tvhplayer.core.DvrActionResult
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.core.DvrScheduleBucket
import at.bernhardberger.tvhplayer.core.RecordingPlaybackAvailability
import at.bernhardberger.tvhplayer.core.buildDvrArchive
import at.bernhardberger.tvhplayer.core.groupDvrSchedule
import at.bernhardberger.tvhplayer.core.partitionDvrLibrary
import at.bernhardberger.tvhplayer.core.recordingGridPageTargetIndex
import at.bernhardberger.tvhplayer.core.recordingPlaybackAvailability
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.ui.TvScreenPadding
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val ARCHIVE_COLUMNS = 3

private enum class PendingRecordingAction {
    CANCEL,
    DELETE,
}

private sealed interface ArchiveGridItem {
    val key: String

    data class Folder(val folder: DvrArchiveFolder) : ArchiveGridItem {
        override val key = "folder:${folder.path.joinToString("/")}"
    }

    data class Recording(val entry: DvrEntry) : ArchiveGridItem {
        override val key = "recording:${entry.id}"
    }
}

@Composable
fun RecordingsScreen(
    repository: DvrRepository = koinInject(),
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    onRetry: () -> Unit = {},
    onPlayRecording: (Int) -> Unit = {},
) {
    val entries by repository.entries.collectAsStateWithLifecycle()
    val library = remember(entries) { partitionDvrLibrary(entries) }
    val archive = remember(library.archive) { buildDvrArchive(library.archive) }
    val scope = rememberCoroutineScope()
    val contentFocus = remember { FocusRequester() }
    val selectedKeys = remember { mutableStateMapOf<String, String>() }
    val archiveScrollPositions = remember { mutableStateMapOf<String, Int>() }
    var mode by remember { mutableStateOf(DvrLibraryMode.ARCHIVE) }
    var archivePath by remember { mutableStateOf(emptyList<String>()) }
    var requestContentFocus by remember { mutableStateOf(true) }
    var detailsEntry by remember { mutableStateOf<DvrEntry?>(null) }
    var pendingAction by remember { mutableStateOf<PendingRecordingAction?>(null) }
    var actionResult by remember { mutableStateOf<DvrActionResult?>(null) }

    val archiveFolder = archive.folderAt(archivePath) ?: archive
    val archiveItems = remember(archiveFolder) {
        archiveFolder.folders.map(ArchiveGridItem::Folder) +
            archiveFolder.recordings.map(ArchiveGridItem::Recording)
    }
    val scheduleGroups = remember(library.schedule) {
        groupDvrSchedule(library.schedule, System.currentTimeMillis() / 1000L)
    }
    val location = when (mode) {
        DvrLibraryMode.ARCHIVE -> "archive:${archivePath.joinToString("/")}"
        DvrLibraryMode.SCHEDULE -> "schedule"
        DvrLibraryMode.PROBLEMS -> "problems"
    }
    val itemKeys = when (mode) {
        DvrLibraryMode.ARCHIVE -> archiveItems.map { it.key }
        DvrLibraryMode.SCHEDULE -> library.schedule.map { "recording:${it.id}" }
        DvrLibraryMode.PROBLEMS -> library.problems.map { "recording:${it.id}" }
    }

    LaunchedEffect(location, itemKeys, requestContentFocus) {
        if (itemKeys.isEmpty() || !requestContentFocus) return@LaunchedEffect
        if (selectedKeys[location] !in itemKeys) selectedKeys[location] = itemKeys.first()
        delay(80)
        runCatching { contentFocus.requestFocus() }
        requestContentFocus = false
    }

    BackHandler(enabled = archivePath.isNotEmpty() && detailsEntry == null) {
        archivePath = archivePath.dropLast(1)
        requestContentFocus = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(TvScreenPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recordings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
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
                    scope.launch {
                        delay(40)
                        runCatching { contentFocus.requestFocus() }
                    }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (mode) {
                DvrLibraryMode.ARCHIVE -> buildString {
                    append(stringResource(R.string.recordings_archive))
                    archivePath.forEach { append(" / ").append(it) }
                }
                DvrLibraryMode.SCHEDULE -> stringResource(R.string.recordings_schedule_summary)
                DvrLibraryMode.PROBLEMS -> stringResource(R.string.recordings_problems_summary)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        if (entries.isEmpty()) {
            RecordingsEmptyState(connectionUiState, onRetry)
        } else {
            when (mode) {
                DvrLibraryMode.ARCHIVE -> key(location) {
                    ArchiveGrid(
                        items = archiveItems,
                        selectedKey = selectedKeys[location],
                        selectedFocus = contentFocus,
                        initialScrollIndex = archiveScrollPositions[location] ?: 0,
                        onScrollChanged = { archiveScrollPositions[location] = it },
                        onFocused = { selectedKeys[location] = it },
                        onOpenFolder = {
                            archivePath = it.path
                            requestContentFocus = true
                        },
                        onOpenRecording = {
                            detailsEntry = it
                            actionResult = null
                        },
                    )
                }
                DvrLibraryMode.SCHEDULE -> RecordingSchedule(
                    groups = scheduleGroups,
                    selectedKey = selectedKeys[location],
                    selectedFocus = contentFocus,
                    onFocused = { selectedKeys[location] = it },
                    onOpen = {
                        detailsEntry = it
                        actionResult = null
                    },
                )
                DvrLibraryMode.PROBLEMS -> RecordingProblems(
                    entries = library.problems,
                    selectedKey = selectedKeys[location],
                    selectedFocus = contentFocus,
                    onFocused = { selectedKeys[location] = it },
                    onOpen = {
                        detailsEntry = it
                        actionResult = null
                    },
                )
            }
        }
    }

    val opened = detailsEntry
    if (opened != null) {
        val authoritative = entries.firstOrNull { it.id == opened.id } ?: opened
        RecordingDetailsPanel(
            entry = authoritative,
            actionResult = actionResult,
            onPlay = { onPlayRecording(authoritative.id) },
            onCancel = { pendingAction = PendingRecordingAction.CANCEL },
            onDelete = { pendingAction = PendingRecordingAction.DELETE },
            onClose = {
                detailsEntry = null
                actionResult = null
                requestContentFocus = true
            },
        )
    }

    val action = pendingAction
    val target = detailsEntry
    if (action != null && target != null) {
        RecordingConfirmationDialog(
            action = action,
            title = target.title,
            onDismiss = { pendingAction = null },
            onConfirm = {
                pendingAction = null
                scope.launch {
                    actionResult = when (action) {
                        PendingRecordingAction.CANCEL -> repository.cancelEntry(target.id)
                        PendingRecordingAction.DELETE -> repository.deleteEntry(target.id)
                    }
                }
            },
        )
    }
}

@Composable
private fun RecordingModeTabs(
    selected: DvrLibraryMode,
    onFocused: (DvrLibraryMode) -> Unit,
    onClick: (DvrLibraryMode) -> Unit,
    onMoveToContent: () -> Unit,
) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = Modifier
            .width(450.dp)
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    onMoveToContent().let { true }
            },
    ) {
        DvrLibraryMode.entries.forEach { mode ->
            Tab(
                selected = selected == mode,
                onFocus = { onFocused(mode) },
                onClick = { onClick(mode) },
            ) {
                Text(
                    text = stringResource(
                        when (mode) {
                            DvrLibraryMode.ARCHIVE -> R.string.recordings_archive
                            DvrLibraryMode.SCHEDULE -> R.string.recordings_schedule
                            DvrLibraryMode.PROBLEMS -> R.string.recordings_problems
                        }
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ArchiveGrid(
    items: List<ArchiveGridItem>,
    selectedKey: String?,
    selectedFocus: FocusRequester,
    initialScrollIndex: Int,
    onScrollChanged: (Int) -> Unit,
    onFocused: (String) -> Unit,
    onOpenFolder: (DvrArchiveFolder) -> Unit,
    onOpenRecording: (DvrEntry) -> Unit,
) {
    if (items.isEmpty()) {
        ModeEmptyState(R.string.recordings_archive_empty)
        return
    }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialScrollIndex)
    val scope = rememberCoroutineScope()
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex }.collect(onScrollChanged)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(ARCHIVE_COLUMNS),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .testTag("recordings-archive-grid")
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = ChannelNavigation.pageDirectionForKeyCode(
                    event.nativeKeyEvent.keyCode
                ) ?: return@onPreviewKeyEvent false
                val current = items.indexOfFirst { it.key == selectedKey }
                val target = recordingGridPageTargetIndex(
                    itemCount = items.size,
                    currentIndex = current,
                    columns = ARCHIVE_COLUMNS,
                    visibleItemCount = gridState.layoutInfo.visibleItemsInfo.size,
                    direction = direction,
                ) ?: return@onPreviewKeyEvent true
                onFocused(items[target].key)
                scope.launch {
                    gridState.animateScrollToItem(target)
                    delay(60)
                    runCatching { selectedFocus.requestFocus() }
                }
                true
            },
    ) {
        items(items, key = { it.key }) { item ->
            val modifier = Modifier
                .then(if (item.key == selectedKey) Modifier.focusRequester(selectedFocus) else Modifier)
                .onFocusChanged { if (it.isFocused) onFocused(item.key) }
            when (item) {
                is ArchiveGridItem.Folder -> FolderCard(
                    folder = item.folder,
                    onClick = { onOpenFolder(item.folder) },
                    modifier = modifier,
                )
                is ArchiveGridItem.Recording -> RecordingCard(
                    entry = item.entry,
                    onClick = { onOpenRecording(item.entry) },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: DvrArchiveFolder,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val itemCount = folder.folders.size + folder.recordings.size
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f / 9f),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.recordings_folder_items, itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecordingCard(
    entry: DvrEntry,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f / 9f),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = entry.start.recordingDateTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RecordingSchedule(
    groups: Map<DvrScheduleBucket, List<DvrEntry>>,
    selectedKey: String?,
    selectedFocus: FocusRequester,
    onFocused: (String) -> Unit,
    onOpen: (DvrEntry) -> Unit,
) {
    if (groups.values.all(List<DvrEntry>::isEmpty)) {
        ModeEmptyState(R.string.recordings_schedule_empty)
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize().focusGroup(),
    ) {
        DvrScheduleBucket.entries.forEach { bucket ->
            val bucketEntries = groups[bucket].orEmpty()
            if (bucketEntries.isNotEmpty()) {
                item(key = "header-$bucket") {
                    Text(
                        text = scheduleBucketLabel(bucket),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(bucketEntries, key = { it.id }) { entry ->
                    RecordingListRow(
                        entry = entry,
                        selected = selectedKey == "recording:${entry.id}",
                        selectedFocus = selectedFocus,
                        onFocused = { onFocused("recording:${entry.id}") },
                        onClick = { onOpen(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingProblems(
    entries: List<DvrEntry>,
    selectedKey: String?,
    selectedFocus: FocusRequester,
    onFocused: (String) -> Unit,
    onOpen: (DvrEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        ModeEmptyState(R.string.recordings_problems_empty)
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize().focusGroup(),
    ) {
        items(entries, key = { it.id }) { entry ->
            RecordingListRow(
                entry = entry,
                selected = selectedKey == "recording:${entry.id}",
                selectedFocus = selectedFocus,
                onFocused = { onFocused("recording:${entry.id}") },
                onClick = { onOpen(entry) },
                problem = true,
            )
        }
    }
}

@Composable
private fun RecordingListRow(
    entry: DvrEntry,
    selected: Boolean,
    selectedFocus: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    problem: Boolean = false,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = buildString {
                    append(entry.start.recordingDateTime()).append('–').append(formatHm(entry.stop))
                    entry.channelName?.let { append(" • ").append(it) }
                    if (problem) {
                        entry.failureReason?.takeIf(String::isNotBlank)?.let {
                            append(" • ").append(it)
                        }
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (problem) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            if (problem) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = stringResource(R.string.recordings_problems),
                    tint = MaterialTheme.colorScheme.error,
                )
            } else {
                RecordingStatusIndicator(state = entry.state)
            }
        },
        scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.focusRequester(selectedFocus) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocused() },
    )
}

@Composable
private fun RecordingDetailsPanel(
    entry: DvrEntry,
    actionResult: DvrActionResult?,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val canCancel = entry.state == DvrState.SCHEDULED || entry.state == DvrState.RECORDING
    val canDelete = entry.state == DvrState.COMPLETED || entry.state == DvrState.FAILED ||
        entry.state == DvrState.CANCELLED
    val playbackAvailability = recordingPlaybackAvailability(entry)
    val canPlay = playbackAvailability is RecordingPlaybackAvailability.Ready
    LaunchedEffect(entry.id, entry.state) {
        if (canPlay) initialFocus.requestFocus() else closeFocus.requestFocus()
    }
    BackHandler(onBack = onClose)
    RecordingDialogSurface {
        Text(entry.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
        entry.subtitle?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            buildString {
                append(entry.start.recordingDateTime()).append('–').append(formatHm(entry.stop))
                entry.channelName?.let { append(" • ").append(it) }
                append(" • ").append(dvrStateLabel(entry.state))
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entry.summary?.takeIf(String::isNotBlank)?.let { Text(it, maxLines = 3) }
        entry.description?.takeIf { it.isNotBlank() && it != entry.summary }?.let {
            Text(it, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        entry.failureReason?.takeIf(String::isNotBlank)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, maxLines = 2)
        }
        actionResult?.let {
            Text(
                dvrActionResultLabel(it),
                color = if (it is DvrActionResult.Failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canPlay) {
                IconButton(onClick = onPlay, modifier = Modifier.focusRequester(initialFocus)) {
                    Icon(Icons.Filled.PlayArrow, stringResource(R.string.play))
                }
            }
            if (canCancel) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Stop, stringResource(R.string.cancel_recording))
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.delete_recording))
                }
            }
            OutlinedIconButton(onClick = onClose, modifier = Modifier.focusRequester(closeFocus)) {
                Icon(Icons.Filled.Close, stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun RecordingConfirmationDialog(
    action: PendingRecordingAction,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val safeFocus = remember { FocusRequester() }
    LaunchedEffect(action) { safeFocus.requestFocus() }
    BackHandler(onBack = onDismiss)
    RecordingDialogSurface {
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
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(safeFocus),
            ) {
                Text(stringResource(R.string.back))
            }
            Button(onClick = onConfirm) {
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
private fun RecordingDialogSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.76f))
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
) {
    val error = connectionUiState is ConnectionUiState.Error
    Column(
        modifier = Modifier.fillMaxSize(),
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
private fun scheduleBucketLabel(bucket: DvrScheduleBucket): String = stringResource(
    when (bucket) {
        DvrScheduleBucket.RECORDING_NOW -> R.string.recordings_recording_now
        DvrScheduleBucket.TODAY -> R.string.today
        DvrScheduleBucket.TOMORROW -> R.string.tomorrow
        DvrScheduleBucket.LATER -> R.string.recordings_later
    }
)

@Composable
private fun dvrStateLabel(state: DvrState): String = stringResource(
    when (state) {
        DvrState.SCHEDULED -> R.string.recording_state_scheduled
        DvrState.RECORDING -> R.string.recording_state_recording
        DvrState.COMPLETED -> R.string.recording_state_completed
        DvrState.FAILED -> R.string.recording_state_failed
        DvrState.CANCELLED -> R.string.recording_state_cancelled
        DvrState.UNKNOWN -> R.string.recording_state_unknown
    }
)

@Composable
private fun dvrActionResultLabel(result: DvrActionResult): String = stringResource(
    when (result) {
        is DvrActionResult.Accepted -> R.string.recording_action_accepted
        is DvrActionResult.Failed -> when (result.reason) {
            DvrActionFailure.PERMISSION_DENIED -> R.string.recording_action_permission
            DvrActionFailure.CONFLICT -> R.string.recording_action_conflict
            DvrActionFailure.REJECTED -> R.string.recording_action_rejected
            DvrActionFailure.CONNECTION -> R.string.recording_action_connection
        }
    }
)

private fun Long.recordingDateTime(): String = Instant.ofEpochSecond(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm"))
