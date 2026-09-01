package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.core.DvrProblemBucket
import at.bernhardberger.tvhplayer.core.DvrScheduleSection
import at.bernhardberger.tvhplayer.core.DvrScheduleSectionKind
import at.bernhardberger.tvhplayer.core.recordingFocusTargetKey
import at.bernhardberger.tvhplayer.core.recordingListMetadata
import at.bernhardberger.tvhplayer.core.recordingListPageTargetIndex
import at.bernhardberger.tvhplayer.core.resolvePiconModel
import at.bernhardberger.tvhplayer.core.summarizeDvrFolder
import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.TvListRow
import coil3.ImageLoader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal sealed interface ArchiveListItem {
    val key: String

    data class Folder(val folder: DvrArchiveFolder) : ArchiveListItem {
        override val key = "folder:${folder.path.joinToString("/")}"
    }

    data class Recording(val entry: DvrEntry) : ArchiveListItem {
        override val key = "recording:${recordingItemKey(entry.id)}"
    }
}

internal fun recordingItemKey(id: DvrEntryId): Long = id.value

internal fun DvrArchiveFolder.listItems(): List<ArchiveListItem> =
    folders.map(ArchiveListItem::Folder) + recordings.map(ArchiveListItem::Recording)

@Composable
internal fun RecordingModeTabs(
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
internal fun RecordingBrowserSurface(
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
internal fun ArchiveList(
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
internal fun FolderMetadataPane(
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
                itemsIndexed(
                    summary.recentRecordings,
                    key = { _, entry -> recordingItemKey(entry.id) },
                ) {
                        _, entry ->
                    FolderRecentRecordingRow(
                        entry = entry,
                        imageLoader = imageLoader,
                        currentSession = currentSession,
                        piconPath = piconForEntry(entry),
                        selected = focusTargetId == entry.id,
                        modifier = Modifier
                            .testTag("folder-preview-recording-${recordingItemKey(entry.id)}")
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
internal fun RecordingMetadataPane(
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
                    episode.seasonNumber?.let {
                        append("S").append(it.toString().padStart(2, '0'))
                    }
                    episode.episodeNumber?.let {
                        append("E").append(it.toString().padStart(2, '0'))
                    }
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
internal fun RecordingSchedule(
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
        entries.map { "recording:${recordingItemKey(it.id)}" },
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
                val current = entries.indexOfFirst {
                    "recording:${recordingItemKey(it.id)}" == selectedKey
                }
                val target = recordingListPageTargetIndex(
                    entries.size,
                    current,
                    listState.layoutInfo.visibleItemsInfo.count { it.key is Int },
                    direction,
                ) ?: return@onPreviewKeyEvent true
                onFocused("recording:${recordingItemKey(entries[target].id)}")
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
            items(section.entries, key = { recordingItemKey(it.id) }) { entry ->
                RecordingListRow(
                    entry = entry,
                    piconPath = piconForEntry(entry),
                    imageLoader = imageLoader,
                    currentSession = currentSession,
                    selected = selectedKey == "recording:${recordingItemKey(entry.id)}",
                    focusTarget = focusTargetKey == "recording:${recordingItemKey(entry.id)}",
                    selectedFocus = selectedFocus,
                    onFocused = {
                        onFocused("recording:${recordingItemKey(entry.id)}")
                    },
                    onClick = { onOpen(entry) },
                    kind = RecordingRowKind.SCHEDULE,
                )
            }
        }
    }
}

@Composable
internal fun RecordingProblems(
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
        entries.map { "recording:${recordingItemKey(it.id)}" },
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
                val current = entries.indexOfFirst {
                    "recording:${recordingItemKey(it.id)}" == selectedKey
                }
                val target = recordingListPageTargetIndex(
                    entries.size,
                    current,
                    listState.layoutInfo.visibleItemsInfo.count { it.key is Int },
                    direction,
                ) ?: return@onPreviewKeyEvent true
                onFocused("recording:${recordingItemKey(entries[target].id)}")
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
                items(bucketEntries, key = { recordingItemKey(it.id) }) { entry ->
                    RecordingListRow(
                        entry = entry,
                        piconPath = piconForEntry(entry),
                        imageLoader = imageLoader,
                        currentSession = currentSession,
                        selected = selectedKey == "recording:${recordingItemKey(entry.id)}",
                        focusTarget =
                            focusTargetKey == "recording:${recordingItemKey(entry.id)}",
                        selectedFocus = selectedFocus,
                        onFocused = {
                            onFocused("recording:${recordingItemKey(entry.id)}")
                        },
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
                modifier = Modifier.testTag(
                    "recording-list-headline-${recordingItemKey(entry.id)}"
                ),
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
                modifier = Modifier.testTag(
                    "recording-list-leading-${recordingItemKey(entry.id)}"
                ),
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
            Box(
                Modifier.testTag(
                    "recording-list-trailing-${recordingItemKey(entry.id)}"
                )
            ) {
                if (kind == RecordingRowKind.SCHEDULE) ScheduleTime(entry)
                else RecordingDateTime(entry.start)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recording-list-entry-${recordingItemKey(entry.id)}")
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
internal fun RecordingsEmptyState(
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
internal fun dvrStateLabel(state: DvrEntryState?): String = stringResource(
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

internal fun Long?.recordingDateTime(): String = this?.let {
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
