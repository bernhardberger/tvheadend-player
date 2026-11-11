package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import at.bernhardberger.tvhplayer.core.allRecordings
import at.bernhardberger.tvhplayer.core.DvrDateSection
import at.bernhardberger.tvhplayer.core.dvrDateSectionStarts
import at.bernhardberger.tvhplayer.ui.common.formatDayHeader
import at.bernhardberger.tvhplayer.core.offeredResumePosition
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnGap
import at.bernhardberger.tvhplayer.ui.TvIconButtonSize
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.depth.*

internal fun archiveLevelId(path: List<String>): String = "archive:${path.joinToString("/")}"

internal fun archiveLevelPath(id: String): List<String> =
    if (id.startsWith("archive:")) id.removePrefix("archive:").split('/').filter(String::isNotEmpty)
    else emptyList()

/**
 * The items of an archive level. [root] is the archive the Show filter leaves; a folder only [fullRoot]
 * still has stays an open, empty level so the folder the viewer is in does not close under them.
 */
internal fun archiveLevelItems(
    root: DvrArchiveFolder,
    levelId: String,
    fullRoot: DvrArchiveFolder = root,
): List<ArchiveListItem>? =
    when {
        levelId.startsWith("archive:") -> archiveLevelPath(levelId).let { path ->
            root.folderAt(path)?.listItems() ?: fullRoot.folderAt(path)?.let { emptyList() }
        }
        else -> null
    }

/** Preserve every surviving parent viewport and reconcile selection by the depth owner's index policy. */
internal fun reconcileArchiveStack(
    stack: DepthStack,
    root: DvrArchiveFolder,
    fullRoot: DvrArchiveFolder = root,
): DepthStack {
    val frames = stack.frames.takeWhile { archiveLevelItems(root, it.levelId, fullRoot) != null }
        .map { frame ->
            val items = requireNotNull(archiveLevelItems(root, frame.levelId, fullRoot))
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
    /** Up from the first row: the chip row above the list. */
    onFirstRowUp: () -> Unit,
    onOpenRecording: (DvrEntry) -> Unit,
    piconForEntry: (DvrEntry) -> ChannelIcon?,
    admissionForEntry: (DvrEntry) -> RecordingPlaybackAdmission?,
    modifier: Modifier = Modifier,
    onOpenContextMenu: ((DvrEntry) -> Unit)? = null,
    onOpenFolderMenu: ((DvrArchiveFolder) -> Unit)? = null,
    grouping: DvrArchiveGrouping = DvrArchiveGrouping.FOLDERS,
    sort: DvrArchiveSort = DvrArchiveSort.NEWEST,
    /** The archive before the Show filter: it names the folders the filter emptied and counts what it hides. */
    fullRoot: DvrArchiveFolder = root,
    show: DvrArchiveShow = DvrArchiveShow.ALL,
    /** A card above the list drives the preview slot instead of the focused row. */
    previewOverride: (@Composable () -> Unit)? = null,
    /** Pixels the preview pane rises above the list's top: the header above it belongs to the left column only. */
    previewRise: () -> Int = { 0 },
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
    val levels = rememberArchiveLevels(
        root, fullRoot, show, sections, contentPadding,
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
        onRootBack = onModeFocus,
        onRootLeft = onDrawerFocus,
        onFirstRowUp = onFirstRowUp,
        pageKeyDirection = at.bernhardberger.tvhplayer.core.ChannelNavigation::pageDirectionForKeyCode,
        // Below the heading band a row's own pane has, so a card's pane sits where that one does.
        previewOverride = previewOverride?.let { content ->
            { Column { ArchiveLevelHeading("") { 0f }; content() } }
        },
        previewRise = previewRise,
        modifier = modifier.testTag("recordings-archive-list"),
    )
}

@Composable
private fun rememberArchiveLevels(
    root: DvrArchiveFolder,
    fullRoot: DvrArchiveFolder,
    show: DvrArchiveShow,
    sections: Map<Long, DvrDateSection>,
    contentPadding: PaddingValues,
    latestAdmissionForEntry: State<(DvrEntry) -> RecordingPlaybackAdmission?>,
    latestOnOpenFolderMenu: State<((DvrArchiveFolder) -> Unit)?>,
    latestPiconForEntry: State<(DvrEntry) -> ChannelIcon?>,
    latestOnOpenRecording: State<(DvrEntry) -> Unit>,
    latestOnOpenContextMenu: State<((DvrEntry) -> Unit)?>,
): Map<String, DepthLevel> = remember(root, fullRoot, show, sections) {
        val levels = linkedMapOf<String, DepthLevel>()
        fun addLevel(path: List<String>, heading: @Composable () -> String, items: List<ArchiveListItem>) {
            val id = archiveLevelId(path)
            // What the Show filter leaves out of this level, at any depth below it.
            val hidden = (fullRoot.folderAt(path)?.allRecordings()?.size ?: 0) -
                (root.folderAt(path)?.allRecordings()?.size ?: 0)
            levels[id] = DepthLevel(
                id = id,
                heading = { emphasis -> ArchiveLevelHeading(heading(), emphasis) },
                emptyContent = {
                    ArchiveEmptyLevel(show, atRoot = path.isEmpty(), hidden = hidden)
                },
                rows = items.mapIndexed { index, item ->
                    when (item) {
                        is ArchiveListItem.Folder -> archiveFolderRow(
                            item, latestAdmissionForEntry, latestOnOpenFolderMenu, latestPiconForEntry,
                        )
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
                                // Only the root list carries the date cuts.
                                val section = if (path.isEmpty()) sections[item.entry.id.value] else null
                                Column {
                                    if (section != null) {
                                        ArchiveDateDivider(dateSectionLabel(section), first = index == 0)
                                    }
                                    RecordingListRow(
                                        entry = item.entry, piconPath = latestPiconForEntry.value(item.entry),
                                        resumePosition = offeredResumePosition(
                                            latestAdmissionForEntry.value(item.entry),
                                        ),
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
            addLevel(folder.path, {
                if (folder.path.isEmpty()) stringResource(R.string.recordings_archive_all) else folder.name
            }, folder.listItems())
            folder.folders.forEach(::addFolder)
        }
        // A folder only the unfiltered archive has is empty here: it stays reachable as the open level.
        fun addEmptied(folder: DvrArchiveFolder) {
            folder.folders.forEach { child ->
                if (archiveLevelId(child.path) !in levels) addLevel(child.path, { child.name }, emptyList())
                addEmptied(child)
            }
        }
        addFolder(root)
        if (fullRoot !== root) addEmptied(fullRoot)
        levels
    }

private fun archiveDateSections(
    root: DvrArchiveFolder,
    grouping: DvrArchiveGrouping,
    sort: DvrArchiveSort,
    nowSeconds: () -> Long,
): Map<Long, DvrDateSection> {
    if (grouping != DvrArchiveGrouping.NONE || sort != DvrArchiveSort.NEWEST) return emptyMap()
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
 * Every Recordings tab's first heading uses this band, so first rows line up across tabs. It is as tall
 * as a header icon button with a margin, the height the other tabs' headings already hold.
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
private fun ArchiveLevelHeading(name: String, emphasis: () -> Float) {
    Box(Modifier.fillMaxWidth().height(recordingsHeadingBandHeight()), contentAlignment = Alignment.CenterStart) {
        if (name.isNotEmpty()) Text(
            name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                scaleX = lerp(ArchiveHeadingPreviewScale, 1f, emphasis())
                scaleY = scaleX
                transformOrigin = TransformOrigin(0f, 1f)
            }.semantics { heading() }.testTag("archive-level-heading"),
        )
    }
}
