package at.bernhardberger.tvhplayer.ui.screens.recordings

import at.bernhardberger.tvhplayer.ui.notifications.label

import androidx.compose.foundation.background

import at.bernhardberger.tvhplayer.ui.components.AppTabRow
import at.bernhardberger.tvhplayer.ui.components.AppTabStyle
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import kotlin.math.roundToInt

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ConnectionRecoveryAction
import at.bernhardberger.tvhplayer.core.primaryRecoveryAction
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.core.DvrProblemBucket
import at.bernhardberger.tvhplayer.core.DvrScheduleSection
import at.bernhardberger.tvhplayer.core.DvrScheduleSectionKind
import at.bernhardberger.tvhplayer.core.recordingFocusTargetKey
import at.bernhardberger.tvhplayer.core.recordingListMetadata
import at.bernhardberger.tvhplayer.core.recordingListPageTargetIndex
import at.bernhardberger.tvhplayer.core.summarizeDvrFolder
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.LocalTabOwner
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import coil3.ImageLoader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Job
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
    selectedFocus: FocusRequester,
    modifier: Modifier = Modifier,
    onFocused: (DvrLibraryMode) -> Unit,
    onClick: (DvrLibraryMode) -> Unit,
    onMoveToContent: () -> Unit,
) {
    AppTabRow(
        selectedTabIndex = selected.ordinal,
        style = AppTabStyle.Page,
        selectedTabFocus = selectedFocus,
        modifier = modifier.onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown &&
                event.key == Key.DirectionDown &&
                onMoveToContent().let { true }
        },
    ) {
        DvrLibraryMode.entries.forEach { mode ->
            AppTab(
                selected = selected == mode,
                label = stringResource(
                    when (mode) {
                        DvrLibraryMode.ARCHIVE -> R.string.recordings_archive
                        DvrLibraryMode.SCHEDULE -> R.string.recordings_schedule
                        DvrLibraryMode.PROBLEMS -> R.string.recordings_problems
                    }
                ),
                onFocus = { onFocused(mode) },
                onClick = { onClick(mode) },
            )
        }
    }
}

@Composable
internal fun FolderListRow(
    folder: DvrArchiveFolder,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val summary = remember(folder) { summarizeDvrFolder(folder) }
    val owner = LocalTabOwner.current
    ListItem(
        selected = selected,
        onClick = { if (owner?.isCurrent != false) onClick() },
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
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = null,
                tint = androidx.tv.material3.LocalContentColor.current,
                modifier = Modifier.size(32.dp),
            )
        },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_keyboard_arrow_right), contentDescription = null)
        },
        modifier = modifier
            .tabFocus()
            .fillMaxWidth()
            .testTag("recordings-folder-${folder.path.joinToString("/")}"),
    )
}

@Composable
internal fun RecordingMetadataPane(
    entry: DvrEntry?,
    piconPath: ArtworkId?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
) {
    if (entry == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_video_library),
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
            .testTag("recording-metadata-pane"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val artworkPath = currentSession?.let { ArtworkId.parse(entry.image ?: entry.fanartImage) }
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
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        entry.subtitle?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        entry.playPosition?.inWholeSeconds?.takeIf { it > 0 }?.let { seconds ->
            Text(
                stringResource(R.string.recording_resume_from,
                    at.bernhardberger.tvhplayer.core.formatPlaybackDuration(
                        seconds.coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
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
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        recordingEpisodeMetadata(entry)?.let {
            Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        entry.summary?.takeIf(String::isNotBlank)?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        entry.description?.takeIf { it.isNotBlank() && it != entry.summary }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 7,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        entry.subscriptionError?.label()?.let {
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun RecordingSchedule(
    groups: List<DvrScheduleSection>,
    selectedKey: String?,
    selectedFocus: FocusRequester,
    onFocused: (String) -> Unit,
    onOpen: (DvrEntry) -> Unit,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    piconForEntry: (DvrEntry) -> ArtworkId?,
    initialScrollIndex: Int,
    onScrollChanged: (Int) -> Unit,
) {
    if (groups.isEmpty()) {
        ModeEmptyState(R.string.recordings_schedule_empty)
        return
    }
    val owner = LocalTabOwner.current
    val active = owner?.isCurrent != false
    val entries = groups.flatMap { it.entries }
    var pageTargetKey by remember { mutableStateOf<String?>(null) }
    var pendingPageKey by remember { mutableStateOf<String?>(null) }
    val focusTargetKey = recordingFocusTargetKey(
        entries.map { "recording:${recordingItemKey(it.id)}" },
        pageTargetKey ?: selectedKey,
    )
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
    val bringIntoViewSpec = LocalBringIntoViewSpec.current
    val scope = rememberCoroutineScope()
    var pageFocusJob by remember { mutableStateOf<Job?>(null) }
    val lazyIndexes = remember(groups) {
        buildMap {
            var index = 0
            groups.forEach { section ->
                index++
                section.entries.forEach { entry -> put(entry.id, index++) }
            }
        }
    }
    DisposableEffect(active) {
        onDispose { pageFocusJob?.cancel() }
    }
    LaunchedEffect(listState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }.collect {
            if (owner?.isCurrent != false) onScrollChanged(it)
        }
    }
    LazyColumn(
        state = listState,
        userScrollEnabled = active,
        // Full-width native rows grow by more than the narrow archive rows.
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged {
                if (!it.hasFocus) {
                    pageFocusJob?.cancel()
                    pageTargetKey = null
                    pendingPageKey = null
                }
            }
            .focusGroup()
            .focusRestorer(selectedFocus)
            .testTag("recordings-schedule-list")
            .onPreviewKeyEvent { event ->
                if (owner?.isCurrent == false) return@onPreviewKeyEvent true
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                pageFocusJob?.cancel()
                pageTargetKey = null
                val direction = ChannelNavigation.pageDirectionForKeyCode(
                    event.nativeKeyEvent.keyCode
                ) ?: run {
                    pendingPageKey = null
                    return@onPreviewKeyEvent false
                }
                val current = entries.indexOfFirst {
                    "recording:${recordingItemKey(it.id)}" == pendingPageKey
                }.takeIf { it >= 0 } ?: entries.indexOfFirst {
                    "recording:${recordingItemKey(it.id)}" == selectedKey
                }
                val target = recordingListPageTargetIndex(
                    entries.size,
                    current,
                    listState.layoutInfo.visibleItemsInfo.count { it.key is Long },
                    direction,
                ) ?: run {
                    pendingPageKey = null
                    return@onPreviewKeyEvent true
                }
                pendingPageKey = "recording:${recordingItemKey(entries[target].id)}"
                pageFocusJob = scope.launch {
                    val layout = listState.layoutInfo
                    val focusOffset = bringIntoViewSpec.calculateScrollDistance(
                        layout.beforeContentPadding.toFloat(),
                        (layout.visibleItemsInfo.firstOrNull { it.key is Long }?.size ?: 0).toFloat(),
                        layout.viewportSize.height.toFloat(),
                    ).roundToInt()
                    listState.animateScrollToItem(lazyIndexes.getValue(entries[target].id), focusOffset)
                    if (owner?.isCurrent == false) return@launch
                    pageTargetKey = "recording:${recordingItemKey(entries[target].id)}"
                }
                true
            },
    ) {
        groups.forEachIndexed { index, section ->
            item(key = "header-${section.kind}-${section.date}") {
                RecordingSectionHeader(
                    text = scheduleSectionLabel(section),
                    recordingNow = section.kind == DvrScheduleSectionKind.RECORDING_NOW,
                    first = index == 0,
                )
            }
            items(section.entries, key = { recordingItemKey(it.id) }) { entry ->
                val rowKey = "recording:${recordingItemKey(entry.id)}"
                if (active && pageTargetKey == rowKey) {
                    LaunchedEffect(rowKey) {
                        if (owner?.isCurrent != false && pageTargetKey == rowKey) {
                            runCatching { selectedFocus.requestFocus() }
                            pageTargetKey = null
                            pendingPageKey = null
                        }
                    }
                }
                RecordingListRow(
                    entry = entry,
                    piconPath = piconForEntry(entry),
                    imageLoader = imageLoader,
                    currentSession = currentSession,
                    selected = selectedKey == "recording:${recordingItemKey(entry.id)}",
                    focusTarget = active && focusTargetKey == "recording:${recordingItemKey(entry.id)}",
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun RecordingProblems(
    groups: Map<DvrProblemBucket, List<DvrEntry>>,
    selectedKey: String?,
    selectedFocus: FocusRequester,
    onFocused: (String) -> Unit,
    onOpen: (DvrEntry) -> Unit,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    piconForEntry: (DvrEntry) -> ArtworkId?,
    initialScrollIndex: Int,
    onScrollChanged: (Int) -> Unit,
) {
    val entries = DvrProblemBucket.entries.flatMap { groups[it].orEmpty() }
    if (entries.isEmpty()) {
        ModeEmptyState(R.string.recordings_problems_empty)
        return
    }
    val owner = LocalTabOwner.current
    val active = owner?.isCurrent != false
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
    val bringIntoViewSpec = LocalBringIntoViewSpec.current
    val scope = rememberCoroutineScope()
    var pageFocusJob by remember { mutableStateOf<Job?>(null) }
    var pageTargetKey by remember { mutableStateOf<String?>(null) }
    var pendingPageKey by remember { mutableStateOf<String?>(null) }
    val focusTargetKey = recordingFocusTargetKey(
        entries.map { "recording:${recordingItemKey(it.id)}" },
        pageTargetKey ?: selectedKey,
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
    DisposableEffect(active) {
        onDispose { pageFocusJob?.cancel() }
    }
    LaunchedEffect(listState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }.collect {
            if (owner?.isCurrent != false) onScrollChanged(it)
        }
    }
    LazyColumn(
        state = listState,
        userScrollEnabled = active,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged {
                if (!it.hasFocus) {
                    pageFocusJob?.cancel()
                    pageTargetKey = null
                    pendingPageKey = null
                }
            }
            .focusGroup()
            .focusRestorer(selectedFocus)
            .testTag("recordings-problems-list")
            .onPreviewKeyEvent { event ->
                if (owner?.isCurrent == false) return@onPreviewKeyEvent true
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                pageFocusJob?.cancel()
                pageTargetKey = null
                val direction = ChannelNavigation.pageDirectionForKeyCode(
                    event.nativeKeyEvent.keyCode
                ) ?: run {
                    pendingPageKey = null
                    return@onPreviewKeyEvent false
                }
                val current = entries.indexOfFirst {
                    "recording:${recordingItemKey(it.id)}" == pendingPageKey
                }.takeIf { it >= 0 } ?: entries.indexOfFirst {
                    "recording:${recordingItemKey(it.id)}" == selectedKey
                }
                val target = recordingListPageTargetIndex(
                    entries.size,
                    current,
                    listState.layoutInfo.visibleItemsInfo.count { it.key is Long },
                    direction,
                ) ?: run {
                    pendingPageKey = null
                    return@onPreviewKeyEvent true
                }
                pendingPageKey = "recording:${recordingItemKey(entries[target].id)}"
                pageFocusJob = scope.launch {
                    val layout = listState.layoutInfo
                    val focusOffset = bringIntoViewSpec.calculateScrollDistance(
                        layout.beforeContentPadding.toFloat(),
                        (layout.visibleItemsInfo.firstOrNull { it.key is Long }?.size ?: 0).toFloat(),
                        layout.viewportSize.height.toFloat(),
                    ).roundToInt()
                    listState.animateScrollToItem(lazyIndexes.getValue(entries[target].id), focusOffset)
                    if (owner?.isCurrent == false) return@launch
                    pageTargetKey = "recording:${recordingItemKey(entries[target].id)}"
                }
                true
            },
    ) {
        val firstBucket = DvrProblemBucket.entries.firstOrNull { groups[it].orEmpty().isNotEmpty() }
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
                        ),
                        first = bucket == firstBucket,
                    )
                }
                items(bucketEntries, key = { recordingItemKey(it.id) }) { entry ->
                    val rowKey = "recording:${recordingItemKey(entry.id)}"
                    if (active && pageTargetKey == rowKey) {
                        LaunchedEffect(rowKey) {
                            if (owner?.isCurrent != false && pageTargetKey == rowKey) {
                                runCatching { selectedFocus.requestFocus() }
                                pageTargetKey = null
                                pendingPageKey = null
                            }
                        }
                    }
                    RecordingListRow(
                        entry = entry,
                        piconPath = piconForEntry(entry),
                        imageLoader = imageLoader,
                        currentSession = currentSession,
                        selected = selectedKey == "recording:${recordingItemKey(entry.id)}",
                        focusTarget =
                            active && focusTargetKey == "recording:${recordingItemKey(entry.id)}",
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

internal enum class RecordingRowKind {
    ARCHIVE,
    SCHEDULE,
    PROBLEM,
}

@Composable
internal fun RecordingListRow(
    entry: DvrEntry,
    piconPath: ArtworkId?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    focusTarget: Boolean = selected,
    selectedFocus: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    kind: RecordingRowKind = RecordingRowKind.ARCHIVE,
) {
    val problem = kind == RecordingRowKind.PROBLEM
    val active = kind == RecordingRowKind.SCHEDULE && entry.state == DvrEntryState.RECORDING
    val metadata = recordingListMetadata(entry, problemLabel = entry.subscriptionError?.takeIf { problem }?.label())
    val owner = LocalTabOwner.current
    ListItem(
        selected = selected,
        onClick = { if (owner?.isCurrent != false) onClick() },
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
            RecordingSupportingMetadata(entry, metadata)
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
                        painter = painterResource(R.drawable.ic_error_outlined),
                        contentDescription = stringResource(R.string.recordings_problem_indicator),
                        tint = androidx.tv.material3.LocalContentColor.current,
                        modifier = Modifier.size(20.dp),
                    )
                }
                PiconBox(
                    imageLoader = imageLoader,
                    currentSession = currentSession,
                    piconPath = piconPath,
                    placeholderTint = androidx.tv.material3.LocalContentColor.current,
                    modifier = Modifier.size(32.dp),
                )
            }
        },
        modifier = modifier
            .tabFocus()
            .fillMaxWidth()
            .testTag("recording-list-entry-${recordingItemKey(entry.id)}")
            .then(if (focusTarget && selectedFocus != null) Modifier.focusRequester(selectedFocus) else Modifier)
            .onFocusChanged { if (owner?.isCurrent != false && it.isFocused) onFocused() },
    )
}

@Composable
private fun RecordingSectionHeader(
    text: String,
    recordingNow: Boolean = false,
    first: Boolean = false,
) {
    // Headings use the Archive heading band; later ones keep a gap to the section above.
    Box(Modifier.padding(top = if (first) 0.dp else 8.dp).height(recordingsHeadingBandHeight())) {
        Row(
            modifier = Modifier.semantics { heading() },
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
}

@Composable
private fun RecordingSupportingMetadata(entry: DvrEntry, metadata: String) {
    Column(
        modifier = Modifier.testTag("recording-list-metadata-${recordingItemKey(entry.id)}"),
    ) {
        Text(
            text = buildList {
                entry.start?.epochSeconds.recordingDateTime().takeIf(String::isNotBlank)?.let(::add)
                recordingDurationMinutes(entry)?.let {
                    add("$it ${stringResource(R.string.recordings_minutes_short)}")
                }
                add(dvrStateLabel(entry.state))
            }.joinToString(" • "),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (metadata.isNotBlank()) Text(
            text = metadata,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun RecordingsEmptyState(
    connectionUiState: ConnectionUiState,
    onRetry: () -> Unit,
    retryFocus: FocusRequester,
    upFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val retryAvailable = connectionUiState.primaryRecoveryAction() == ConnectionRecoveryAction.RETRY
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
        if (retryAvailable) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .tabFocus()
                    .focusRequester(retryFocus)
                    .focusProperties { up = upFocus },
            ) { Text(stringResource(R.string.retry)) }
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
