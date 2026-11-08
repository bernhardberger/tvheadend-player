package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrDateSection
import at.bernhardberger.tvhplayer.core.dvrDateSectionStarts
import at.bernhardberger.tvhplayer.ui.common.formatDayHeader
import at.bernhardberger.tvhplayer.core.offeredResumePosition
import at.bernhardberger.tvhplayer.core.runningRecordings
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnGap
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import at.bernhardberger.tvhplayer.ui.TvIconButtonSize
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.depth.*

internal fun archiveLevelId(path: List<String>): String = "archive:${path.joinToString("/")}"

internal const val RecordingNowLevelId = "recording-now"

internal fun archiveLevelPath(id: String): List<String> =
    if (id.startsWith("archive:")) id.removePrefix("archive:").split('/').filter(String::isNotEmpty)
    else emptyList()

internal fun archiveLevelItems(root: DvrArchiveFolder, levelId: String): List<ArchiveListItem>? =
    when {
        levelId == RecordingNowLevelId -> root.runningRecordings().takeIf { it.isNotEmpty() }
            ?.map(ArchiveListItem::Recording)
        levelId.startsWith("archive:") -> root.folderAt(archiveLevelPath(levelId))?.listItems()
        else -> null
    }

/** Preserve every surviving parent viewport and reconcile selection by the depth owner's index policy. */
internal fun reconcileArchiveStack(stack: DepthStack, root: DvrArchiveFolder): DepthStack {
    val frames = stack.frames.takeWhile { archiveLevelItems(root, it.levelId) != null }
        .map { frame ->
            val items = requireNotNull(archiveLevelItems(root, frame.levelId))
            DepthStack(listOf(frame)).reconcile(items.map { DepthItem(it.key) }).active
        }
    return stack.copy(
        frames = frames.ifEmpty { listOf(DepthFrame(archiveLevelId(emptyList()))) },
        visit = stack.visit + if (frames.size != stack.frames.size) 1 else 0,
    )
}

/** Archive supplies content and identities; the shared Settings depth host owns navigation. */
@Composable
internal fun ArchiveDepthContent(
    root: DvrArchiveFolder,
    navigation: DepthNavigationState,
    contentPadding: PaddingValues,
    isCurrent: Boolean,
    initialFocusEnabled: Boolean,
    backEnabled: Boolean,
    contentFocus: FocusRequester,
    onModeFocus: () -> Unit,
    onDrawerFocus: (() -> Unit)?,
    onOpenRecording: (DvrEntry) -> Unit,
    piconForEntry: (DvrEntry) -> ChannelIcon?,
    admissionForEntry: (DvrEntry) -> RecordingPlaybackAdmission?,
    modifier: Modifier = Modifier,
    onOpenContextMenu: ((DvrEntry) -> Unit)? = null,
    onOpenFolderMenu: ((DvrArchiveFolder) -> Unit)? = null,
    grouping: DvrArchiveGrouping = DvrArchiveGrouping.FOLDERS,
    sort: DvrArchiveSort = DvrArchiveSort.NEWEST,
    /** The archive has recordings but Hide watched leaves none to show: the empty state says so. */
    emptyBecauseWatched: Boolean = false,
    /** Hide watched is on: the grouping button takes the tonal fill while it filters the list. */
    hideWatched: Boolean = false,
    pill: ArchivePillHooks? = null,
    nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    val latestOnOpenRecording = rememberUpdatedState(onOpenRecording)
    val latestOnOpenContextMenu = rememberUpdatedState(onOpenContextMenu)
    val latestOnOpenFolderMenu = rememberUpdatedState(onOpenFolderMenu)
    val latestPiconForEntry = rememberUpdatedState(piconForEntry)
    val latestAdmissionForEntry = rememberUpdatedState(admissionForEntry)
    // Only a flat list in date order is cut into days and months; each cut sits above the run's first recording.
    val sections = remember(root, grouping, sort) {
        if (grouping != DvrArchiveGrouping.NONE || sort != DvrArchiveSort.NEWEST) emptyMap()
        else {
            val starts = dvrDateSectionStarts(root.recordings, java.time.Instant.ofEpochSecond(nowSeconds()), java.time.ZoneId.systemDefault())
            root.recordings.indices.mapNotNull { index -> starts[index]?.let { root.recordings[index].id.value to it } }.toMap()
        }
    }
    // The root heading ends before the View options button at the row's end, whatever width its value gives it;
    // deeper levels have no button. The heading reads the measured width where it lays out.
    val buttonWidthPx = remember { mutableIntStateOf(0) }
    val levels = remember(root, sections, emptyBecauseWatched, pill != null) {
        val levels = linkedMapOf<String, DepthLevel>()
        fun addLevel(id: String, heading: @Composable () -> String, items: List<ArchiveListItem>) {
            levels[id] = DepthLevel(
                id = id,
                heading = { emphasis ->
                    ArchiveLevelHeading(
                        heading(),
                        emphasis,
                        endReserve = if (pill != null && id == archiveLevelId(emptyList())) {
                            { buttonWidthPx.intValue }
                        } else null,
                    )
                },
                emptyContent = {
                    if (emptyBecauseWatched) Column(verticalArrangement = Arrangement.spacedBy(TvSpacing8)) {
                        Text(stringResource(R.string.recordings_archive_all_watched), style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.recordings_archive_all_watched_hint), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Text(stringResource(R.string.recordings_archive_empty), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                },
                rows = items.mapIndexed { index, item ->
                    when (item) {
                        is ArchiveListItem.RecordingNow -> {
                            addLevel(RecordingNowLevelId, { stringResource(R.string.recordings_recording_now_folder) },
                                item.entries.map(ArchiveListItem::Recording))
                            DepthRow(DepthItem(item.key, RecordingNowLevelId)) { rowModifier, activate ->
                                Column {
                                    RecordingNowFolderRow(item.entries.size, rowModifier, activate)
                                    HorizontalDivider(modifier = Modifier.padding(vertical = TvSpacing8))
                                }
                            }
                        }
                        is ArchiveListItem.Folder -> DepthRow(
                            DepthItem(item.key, archiveLevelId(item.folder.path)),
                        ) { rowModifier, activate ->
                            FolderListRow(item.folder, selected = false, modifier = rowModifier, onClick = activate,
                                resumePosition = { offeredResumePosition(latestAdmissionForEntry.value(it)) },
                                onOpenContextMenu = { latestOnOpenFolderMenu.value?.invoke(item.folder) },
                                picon = item.folder.takeIf { it.channelId != null }
                                    ?.let { folder -> folder.recordings.firstOrNull { it.channelId != null } }
                                    ?.let(latestPiconForEntry.value))
                        }
                        is ArchiveListItem.Recording -> {
                            val previewId = "metadata:${item.entry.id.value}"
                            levels[previewId] = DepthLevel(
                                id = previewId,
                                rows = emptyList(),
                                heading = { ArchiveLevelHeading("", it) },
                                passiveContent = {
                                    RecordingMetadataPane(item.entry, latestPiconForEntry.value(item.entry),
                                        latestAdmissionForEntry.value(item.entry),
                                        Modifier.padding(bottom = contentPadding.calculateBottomPadding()))
                                },
                            )
                            DepthRow(
                                DepthItem(item.key),
                                onActivate = { latestOnOpenRecording.value(item.entry) },
                                previewLevelId = previewId,
                            ) { rowModifier, activate ->
                                // Only the root list carries the cuts; the Recording now level is a plain list.
                                val section = if (id == archiveLevelId(emptyList())) sections[item.entry.id.value] else null
                                Column {
                                    if (section != null) ArchiveDateDivider(dateSectionLabel(section), first = index == 0)
                                    RecordingListRow(
                                        entry = item.entry, piconPath = latestPiconForEntry.value(item.entry),
                                        resumePosition = offeredResumePosition(latestAdmissionForEntry.value(item.entry)),
                                        selected = false, modifier = rowModifier,
                                        onClick = activate,
                                        onOpenContextMenu = { latestOnOpenContextMenu.value?.invoke(item.entry) },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
        fun addFolder(folder: DvrArchiveFolder) {
            addLevel(archiveLevelId(folder.path), {
                if (folder.path.isEmpty()) stringResource(R.string.recordings_archive_all) else folder.name
            }, folder.listItems())
            folder.folders.forEach(::addFolder)
        }
        addFolder(root)
        levels
    }
    val atRoot = navigation.stack.active.levelId == archiveLevelId(emptyList())
    var pillFocused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        DepthNavigation(
            state = navigation,
            levels = levels,
            fallbackLevel = { levels.getValue(archiveLevelId(emptyList())) },
            columnWidth = TvBrowseColumnWidth,
            columnGap = TvBrowseColumnGap,
            contentPadding = contentPadding,
            isCurrent = isCurrent,
            initialFocusEnabled = initialFocusEnabled,
            backEnabled = backEnabled && !pillFocused,
            activeFocusRequester = contentFocus,
            onRootBack = onModeFocus,
            onRootLeft = onDrawerFocus,
            // At the root the grouping choice is the stop between the list and the tabs; inside a group it is not there.
            onFirstRowUp = if (pill != null && atRoot) pill.focus else onModeFocus,
            pageKeyDirection = at.bernhardberger.tvhplayer.core.ChannelNavigation::pageDirectionForKeyCode,
            modifier = modifier.testTag("recordings-archive-list"),
        )
        if (pill != null && atRoot && isCurrent) {
            // Right-aligned in the title row, ending where the list column ends (above the row chevrons).
            Box(
                Modifier.padding(start = contentPadding.calculateStartPadding(LocalLayoutDirection.current))
                    .width(TvBrowseColumnWidth).height(recordingsHeadingBandHeight()),
                contentAlignment = Alignment.CenterEnd,
            ) {
                ArchiveGroupingButton(
                    grouping = grouping,
                    active = hideWatched,
                    modifier = Modifier.onSizeChanged { buttonWidthPx.intValue = it.width },
                    focusRequester = pill.focusRequester,
                    onOpen = pill.open,
                    onUp = pill.up,
                    onDown = { contentFocus.requestFocus() },
                    onLeft = onDrawerFocus,
                    onFocusChanged = { pillFocused = it; pill.onFocusChanged(it) },
                )
            }
        }
    }
}

/** What the screen decides about the grouping button: where focus goes from it and what opening it does. */
internal class ArchivePillHooks(
    val focusRequester: FocusRequester,
    val open: () -> Unit,
    /** Focus to the tabs. */
    val up: () -> Unit,
    /** Focus to the button, from the list's first row. */
    val focus: () -> Unit,
    val onFocusChanged: (Boolean) -> Unit,
)

@Composable
private fun dateSectionLabel(section: DvrDateSection): String = when (section) {
    DvrDateSection.Today -> stringResource(R.string.recordings_date_today)
    DvrDateSection.Yesterday -> stringResource(R.string.recordings_date_yesterday)
    is DvrDateSection.Day -> formatDayHeader(section.date, LocalConfiguration.current.locales[0])
    is DvrDateSection.Month -> {
        val locale = LocalConfiguration.current.locales[0]
        java.time.format.DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale,
        ).format(section.month)
    }
    DvrDateSection.Undated -> stringResource(R.string.recordings_date_unknown)
}

/**
 * Every Recordings tab's first heading uses this band, so first rows line up across tabs. It holds a
 * header button with a margin, so the Archive's view options button sits in it on the heading's line and
 * the first row's focus growth stays clear of the button.
 */
@Composable
internal fun recordingsHeadingBandHeight(): Dp {
    val lineHeight = with(LocalDensity.current) { MaterialTheme.typography.titleLarge.lineHeight.toDp() }
    return maxOf(32.dp, lineHeight + 4.dp, TvIconButtonSize + TvSpacing8)
}

/** The preview-slot heading reads at the row title size: titleMedium 16sp of titleLarge 22sp. */
internal const val ArchiveHeadingPreviewScale = 16f / 22f

/**
 * Column headings are the second level under the Recordings title: titleLarge like the
 * Schedule day headings. In the preview slot they read at the row title size and grow
 * to full size as the column slides into the active slot.
 */
@Composable
private fun ArchiveLevelHeading(name: String, emphasis: () -> Float, endReserve: (() -> Int)? = null) {
    // The measured width of the button at the end of the row (pixels), plus the shared gap.
    val reserve = endReserve?.let { with(LocalDensity.current) { it().toDp() } + TvGridGutter } ?: 0.dp
    Box(Modifier.fillMaxWidth().height(recordingsHeadingBandHeight()), contentAlignment = Alignment.CenterStart) {
        if (name.isNotEmpty()) Text(
            name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = reserve).graphicsLayer {
                scaleX = lerp(ArchiveHeadingPreviewScale, 1f, emphasis())
                scaleY = scaleX
                transformOrigin = TransformOrigin(0f, 1f)
            }.semantics { heading() }.testTag("archive-level-heading"),
        )
    }
}
