package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.core.allRecordings
import at.bernhardberger.tvhplayer.core.DvrDateSection
import at.bernhardberger.tvhplayer.core.dvrDateSectionStarts
import at.bernhardberger.tvhplayer.ui.common.formatDayHeader
import at.bernhardberger.tvhplayer.core.offeredResumePosition
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnGap
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.TvIconButtonSize
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.components.depth.*

internal fun archiveLevelId(path: List<String>): String = "archive:${path.joinToString("/")}"

/** The level the Recording now folder opens. */
internal const val RECORDING_NOW_LEVEL_ID = "recording-now"

internal fun archiveLevelPath(id: String): List<String> =
    if (id.startsWith("archive:")) id.removePrefix("archive:").split('/').filter(String::isNotEmpty)
    else emptyList()

/**
 * The items of an archive level. [root] is the archive the Show filter leaves; a folder only [fullRoot]
 * still has stays an open, empty level so the folder the viewer is in does not close under them.
 * [recordingNow] are the Recording now folder's recordings; without any the folder and its level are gone.
 */
internal fun archiveLevelItems(
    root: DvrArchiveFolder,
    levelId: String,
    fullRoot: DvrArchiveFolder = root,
    recordingNow: List<DvrEntry> = emptyList(),
): List<ArchiveListItem>? =
    when {
        levelId == RECORDING_NOW_LEVEL_ID -> recordingNow.takeIf { it.isNotEmpty() }?.map(ArchiveListItem::Recording)
        levelId.startsWith("archive:") -> archiveLevelPath(levelId).let { path ->
            root.folderAt(path)?.listItems(if (path.isEmpty()) recordingNow else emptyList())
                ?: fullRoot.folderAt(path)?.let { emptyList() }
        }
        else -> null
    }

/** Preserve every surviving parent viewport and reconcile selection by the depth owner's index policy. */
internal fun reconcileArchiveStack(
    stack: DepthStack,
    root: DvrArchiveFolder,
    fullRoot: DvrArchiveFolder = root,
    recordingNow: List<DvrEntry> = emptyList(),
): DepthStack {
    val frames = stack.frames.takeWhile { archiveLevelItems(root, it.levelId, fullRoot, recordingNow) != null }
        .map { frame ->
            val items = requireNotNull(archiveLevelItems(root, frame.levelId, fullRoot, recordingNow))
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
    onDrawerFocus: (() -> Unit)?,
    /** Up from the first row: the View options button beside the headline. */
    onFirstRowUp: () -> Unit,
    onOpenRecording: (DvrEntry) -> Unit,
    piconForEntry: (DvrEntry) -> ChannelIcon?,
    admissionForEntry: (DvrEntry) -> RecordingPlaybackAdmission?,
    modifier: Modifier = Modifier,
    onOpenContextMenu: ((DvrEntry) -> Unit)? = null,
    onOpenFolderMenu: ((DvrArchiveFolder) -> Unit)? = null,
    grouping: DvrArchiveGrouping = DvrArchiveGrouping.FLAT,
    sort: DvrArchiveSort = DvrArchiveSort.NEWEST,
    /** The archive before the Show filter: it names the folders the filter emptied and counts what it hides. */
    fullRoot: DvrArchiveFolder = root,
    show: DvrArchiveShow = DvrArchiveShow.ALL,
    /** The running recordings the Recording now folder lists at the top of the root; none hides it. */
    recordingNow: List<DvrEntry> = emptyList(),
    nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    val latestOnOpenRecording = rememberUpdatedState(onOpenRecording)
    val latestOnOpenContextMenu = rememberUpdatedState(onOpenContextMenu)
    val latestOnOpenFolderMenu = rememberUpdatedState(onOpenFolderMenu)
    val latestPiconForEntry = rememberUpdatedState(piconForEntry)
    val latestAdmissionForEntry = rememberUpdatedState(admissionForEntry)
    // Only a flat list in date order is cut into days and months; each cut sits above the run's first recording.
    val sections = remember(root, grouping, sort) {
        archiveDateSections(root, grouping, sort, nowSeconds)
    }
    // Sort and Show away from their defaults, quietly under the active headline.
    val valuesLine = rememberUpdatedState(
        DvrArchiveView(grouping, sort, show).nonDefaultValues().joinToString(" · ").ifEmpty { null },
    )
    val levels = rememberArchiveLevels(
        root, fullRoot, grouping, show, recordingNow, sections, contentPadding, valuesLine,
        latestAdmissionForEntry, latestOnOpenFolderMenu, latestPiconForEntry,
        latestOnOpenRecording, latestOnOpenContextMenu,
    )
    DepthNavigation(
        state = navigation,
        levels = levels,
        fallbackLevel = { levels.getValue(archiveLevelId(emptyList())) },
        columnWidth = TvBrowseColumnWidth,
        columnGap = TvBrowseColumnGap,
        contentPadding = contentPadding,
        isCurrent = isCurrent,
        initialFocusEnabled = initialFocusEnabled,
        backEnabled = backEnabled,
        activeFocusRequester = contentFocus,
        onRootLeft = onDrawerFocus,
        onFirstRowUp = onFirstRowUp,
        pageKeyDirection = at.bernhardberger.tvhplayer.core.ChannelNavigation::pageDirectionForKeyCode,
        modifier = modifier.testTag("recordings-archive-list"),
    )
}

@Composable
private fun rememberArchiveLevels(
    root: DvrArchiveFolder,
    fullRoot: DvrArchiveFolder,
    grouping: DvrArchiveGrouping,
    show: DvrArchiveShow,
    recordingNow: List<DvrEntry>,
    sections: Map<Long, DvrDateSection>,
    contentPadding: PaddingValues,
    valuesLine: State<String?>,
    latestAdmissionForEntry: State<(DvrEntry) -> RecordingPlaybackAdmission?>,
    latestOnOpenFolderMenu: State<((DvrArchiveFolder) -> Unit)?>,
    latestPiconForEntry: State<(DvrEntry) -> ChannelIcon?>,
    latestOnOpenRecording: State<(DvrEntry) -> Unit>,
    latestOnOpenContextMenu: State<((DvrEntry) -> Unit)?>,
): Map<String, DepthLevel> = remember(root, fullRoot, grouping, show, recordingNow, sections) {
        val levels = linkedMapOf<String, DepthLevel>()
        fun addLevel(
            path: List<String>,
            heading: @Composable () -> String,
            items: List<ArchiveListItem>,
            id: String = archiveLevelId(path),
        ) {
            // What the Show filter leaves out of this level, at any depth below it.
            val hidden = (fullRoot.folderAt(path)?.allRecordings()?.size ?: 0) -
                (root.folderAt(path)?.allRecordings()?.size ?: 0)
            levels[id] = DepthLevel(
                id = id,
                heading = { emphasis -> ArchiveLevelHeading(heading(), emphasis, valuesLine.value) },
                emptyContent = {
                    ArchiveEmptyLevel(show, atRoot = path.isEmpty(), hidden = hidden)
                },
                rows = items.mapIndexed { index, item ->
                    when (item) {
                        is ArchiveListItem.RecordingNow -> recordingNowRow(item)
                        is ArchiveListItem.Folder -> archiveFolderRow(
                            item, latestAdmissionForEntry, latestOnOpenFolderMenu, latestPiconForEntry,
                        )
                        is ArchiveListItem.Recording -> {
                            val previewId = "metadata:${item.entry.id.value}"
                            levels[previewId] = recordingPreviewLevel(
                                previewId, item.entry, contentPadding, latestPiconForEntry, latestAdmissionForEntry,
                            )
                            archiveRecordingRow(
                                item, previewId,
                                // Only the root list carries the date cuts.
                                section = sections.takeIf { path.isEmpty() }?.get(item.entry.id.value),
                                first = index == 0,
                                latestPiconForEntry, latestAdmissionForEntry, latestOnOpenRecording,
                                latestOnOpenContextMenu,
                            )
                        }
                    }
                },
            )
        }
        fun addFolder(folder: DvrArchiveFolder) {
            addLevel(folder.path, { folder.name }, folder.listItems())
            folder.folders.forEach(::addFolder)
        }
        // A folder only the unfiltered archive has is empty here: it stays reachable as the open level.
        fun addEmptied(folder: DvrArchiveFolder) {
            folder.folders.forEach { child ->
                if (archiveLevelId(child.path) !in levels) addLevel(child.path, { child.name }, emptyList())
                addEmptied(child)
            }
        }
        addLevel(root.path, { stringResource(grouping.rootHeadingRes()) }, root.listItems(recordingNow))
        root.folders.forEach(::addFolder)
        if (recordingNow.isNotEmpty()) {
            addLevel(emptyList(), { stringResource(R.string.recordings_recording_now) },
                recordingNow.map(ArchiveListItem::Recording), id = RECORDING_NOW_LEVEL_ID)
        }
        if (fullRoot !== root) addEmptied(fullRoot)
        levels
    }

private fun archiveDateSections(
    root: DvrArchiveFolder,
    grouping: DvrArchiveGrouping,
    sort: DvrArchiveSort,
    nowSeconds: () -> Long,
): Map<Long, DvrDateSection> {
    // Dates divide a flat list only when the date is what orders it.
    val byDate = sort == DvrArchiveSort.NEWEST || sort == DvrArchiveSort.OLDEST
    if (grouping != DvrArchiveGrouping.FLAT || !byDate) return emptyMap()
    val starts = dvrDateSectionStarts(
        root.recordings, java.time.Instant.ofEpochSecond(nowSeconds()), java.time.ZoneId.systemDefault(),
    )
    return root.recordings.indices.mapNotNull { index ->
        starts[index]?.let { root.recordings[index].id.value to it }
    }.toMap()
}

private fun archiveFolderRow(
    item: ArchiveListItem.Folder,
    admissionForEntry: State<(DvrEntry) -> RecordingPlaybackAdmission?>,
    onOpenFolderMenu: State<((DvrArchiveFolder) -> Unit)?>,
    piconForEntry: State<(DvrEntry) -> ChannelIcon?>,
): DepthRow = DepthRow(
    DepthItem(item.key, archiveLevelId(item.folder.path)),
) { rowModifier, activate ->
    FolderListRow(
        item.folder, selected = false, modifier = rowModifier, onClick = activate,
        resumePosition = { offeredResumePosition(admissionForEntry.value(it)) },
        onOpenContextMenu = { onOpenFolderMenu.value?.invoke(item.folder) },
        iconTint = at.bernhardberger.tvhplayer.ui.tvGenreColors(item.folder.genreCategory)?.base,
        focusedIconTint = at.bernhardberger.tvhplayer.ui.tvGenreColors(item.folder.genreCategory)?.focusedBase,
        picon = item.folder.takeIf { it.channelId != null }
            ?.let { folder -> folder.recordings.firstOrNull { it.channelId != null } }
            ?.let(piconForEntry.value),
    )
}

@Composable
internal fun dateSectionLabel(section: DvrDateSection): String = when (section) {
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
 * Every Recordings tab's first heading uses this band, so first rows line up across tabs.
 */
@Composable
internal fun recordingsHeadingBandHeight(): Dp {
    val lineHeight = with(LocalDensity.current) { RecordingsHeadlineStyle.lineHeight.toDp() }
    return maxOf(32.dp, lineHeight + 4.dp)
}

/** The Browse headline: the size of the screen headlines in Settings and the other browse screens. */
internal val RecordingsHeadlineStyle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineMedium

/** The quiet values line starts this far below the headline's baseline: it reads as the headline's. */
private val ValuesLineBaselineGap = 4.dp

/** Air under the values line before the list, clearly more than above it. */
private val ValuesLineBottomGap = 12.dp

/** The values line ends short of the View options button at the end of the headline. */
internal val ValuesLineEndReserve = TvIconButtonSize + TvSpacing16

/**
 * Column headings name the level in the screen headline's size. In the preview slot they read at
 * the row title size, on the active headline's baseline, and grow to full size as the column slides
 * into the active slot. The active one carries [valuesLine], the non-default Sort and Show values,
 * tight under its baseline; the preview slot never makes room for it, so its first row stays put.
 */
@Composable
internal fun ArchiveLevelHeading(name: String, emphasis: () -> Float, valuesLine: String?) {
    val style = RecordingsHeadlineStyle
    val previewScale = MaterialTheme.typography.titleMedium.fontSize.value / style.fontSize.value
    var baseline by remember { mutableFloatStateOf(0f) }
    val band = recordingsHeadingBandHeight()
    Layout(
        content = {
            if (name.isNotEmpty()) Text(
                name, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                onTextLayout = { baseline = it.firstBaseline },
                modifier = Modifier.graphicsLayer {
                    scaleX = lerp(previewScale, 1f, emphasis())
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0f, if (size.height > 0f) baseline / size.height else 1f)
                }.semantics { heading() }.testTag("archive-level-heading"),
            )
            if (name.isNotEmpty() && valuesLine != null) Text(
                valuesLine, style = MaterialTheme.typography.bodySmall, color = TvTextColors.secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = emphasis() }.testTag("archive-values-line"),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val title = measurables.getOrNull(0)?.measure(loose)
        val values = measurables.getOrNull(1)?.measure(
            loose.copy(maxWidth = (constraints.maxWidth - ValuesLineEndReserve.roundToPx()).coerceAtLeast(0)),
        )
        val bandPx = band.roundToPx()
        val valuesTop = (title?.get(FirstBaseline)?.takeIf { it != AlignmentLine.Unspecified } ?: bandPx) +
            ValuesLineBaselineGap.roundToPx()
        val withValues = values?.let { valuesTop + it.height + ValuesLineBottomGap.roundToPx() } ?: bandPx
        val height = lerp(bandPx.toFloat(), maxOf(bandPx, withValues).toFloat(), emphasis()).roundToInt()
        layout(constraints.maxWidth, height) {
            title?.place(0, 0)
            values?.place(0, valuesTop)
        }
    }
}
