package at.bernhardberger.tvhplayer.ui.screens.recordings

import at.bernhardberger.tvhplayer.ui.notifications.label

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background

import at.bernhardberger.tvhplayer.ui.components.AppTabRow
import at.bernhardberger.tvhplayer.ui.components.AppTabStyle
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import kotlin.math.roundToInt
import kotlin.time.Duration

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.browseAppError
import at.bernhardberger.tvhplayer.ui.components.AppErrorAlert
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.core.DvrProblemBucket
import at.bernhardberger.tvhplayer.core.DvrScheduleSection
import at.bernhardberger.tvhplayer.core.DvrScheduleSectionKind
import at.bernhardberger.tvhplayer.core.recordingFocusTargetKey
import at.bernhardberger.tvhplayer.core.RecordingWatchState
import at.bernhardberger.tvhplayer.core.recordingIsRunning
import at.bernhardberger.tvhplayer.core.recordingWatchState
import at.bernhardberger.tvhplayer.core.recordingLengthSeconds
import at.bernhardberger.tvhplayer.core.offeredResumePosition
import at.bernhardberger.tvhplayer.core.recordingTimeLeft
import at.bernhardberger.tvhplayer.ui.player.playerMinutesLeft
import at.bernhardberger.tvhplayer.core.recordingPaddedStop
import at.bernhardberger.tvhplayer.core.recordingSizeBytes
import at.bernhardberger.tvhplayer.core.recordingStoppedEarly
import at.bernhardberger.tvhplayer.core.recordingListMetadata
import at.bernhardberger.tvhplayer.core.recordingListPageTargetIndex
import at.bernhardberger.tvhplayer.core.summarizeDvrFolder
import at.bernhardberger.tvhplayer.core.watchedCount
import at.bernhardberger.tvhplayer.core.runningRecordings
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnGap
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing4
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.components.LocalArtworkContext
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.LocalTabOwner
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.RecordingBadge
import at.bernhardberger.tvhplayer.ui.components.StatusIconSize
import at.bernhardberger.tvhplayer.ui.components.ListBottomReserve
import at.bernhardberger.tvhplayer.ui.components.SkeletonLine
import at.bernhardberger.tvhplayer.ui.components.depth.DepthListTopReserve
import at.bernhardberger.tvhplayer.ui.components.listEdgeFades
import at.bernhardberger.tvhplayer.ui.components.rememberListEdgeBringIntoView
import at.bernhardberger.tvhplayer.ui.components.recordingMarkColor
import at.bernhardberger.tvhplayer.ui.common.formatDayHeader
import at.bernhardberger.tvhplayer.ui.common.formatFileSize
import at.bernhardberger.tvhplayer.ui.common.rememberDetailsEpochSeconds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.aspectRatio
import at.bernhardberger.tvhplayer.core.programmeAgeRating
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordedDuration
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.components.ProgrammeEyebrow
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadline
import at.bernhardberger.tvhplayer.ui.components.ProgrammePanePicture
import at.bernhardberger.tvhplayer.ui.components.ProgrammePanePictureGap
import at.bernhardberger.tvhplayer.ui.components.ProgrammePaneWidth
import at.bernhardberger.tvhplayer.ui.components.ProgrammeStatusLine
import at.bernhardberger.tvhplayer.ui.components.programmeAboutFacts
import at.bernhardberger.tvhplayer.ui.components.programmeHeaderMetadata
import at.bernhardberger.tvhplayer.ui.components.programmeSynopsis
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal sealed interface ArchiveListItem {
    val key: String

    data class RecordingNow(val entries: List<DvrEntry>) : ArchiveListItem {
        override val key = "meta:recording-now"
    }

    data class Folder(val folder: DvrArchiveFolder) : ArchiveListItem {
        override val key = "folder:${folder.path.joinToString("/")}"
    }

    data class Recording(val entry: DvrEntry) : ArchiveListItem {
        override val key = "recording:${recordingItemKey(entry.id)}"
    }
}

internal fun recordingItemKey(id: DvrEntryId): Long = id.value

internal fun DvrArchiveFolder.listItems(): List<ArchiveListItem> = buildList {
    if (path.isEmpty()) {
        runningRecordings().takeIf { it.isNotEmpty() }?.let { add(ArchiveListItem.RecordingNow(it)) }
    }
    addAll(folders.map(ArchiveListItem::Folder))
    addAll(recordings.map(ArchiveListItem::Recording))
}

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
                // Nothing focusable follows the tabs in the header row.
                last = mode == DvrLibraryMode.entries.last(),
            )
        }
    }
}

/**
 * A folder row ends its name with the watched check once everything in it is watched, or with the
 * clock loader filled by the share watched to the end, which its line then counts.
 */
@Composable
internal fun FolderListRow(
    folder: DvrArchiveFolder,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    resumePosition: (DvrEntry) -> Duration? = { null },
    onOpenContextMenu: (() -> Unit)? = null,
) {
    val summary = remember(folder) { summarizeDvrFolder(folder) }
    val latestResumePosition by rememberUpdatedState(resumePosition)
    // Resume positions arrive with the entries, so a new folder is the only reason to count again.
    val watched = remember(folder) { folder.watchedCount { latestResumePosition(it) } }
    val watchState = when {
        watched == 0 -> RecordingWatchState.Unwatched
        watched == summary.recordingCount -> RecordingWatchState.Watched
        else -> RecordingWatchState.PartlyWatched(watched.toDouble() / summary.recordingCount)
    }
    val owner = LocalTabOwner.current
    val contextMenu = at.bernhardberger.tvhplayer.ui.components.rememberBrowseContextMenuTrigger(
        onOpenContextMenu, enabled = { owner?.isCurrent != false })
    val tag = folder.path.joinToString("/")
    ListItem(
        selected = selected,
        onClick = { if (owner?.isCurrent != false) onClick() },
        onLongClick = contextMenu.onLongClick,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (watchState != RecordingWatchState.Unwatched) {
                    Spacer(Modifier.width(TvSpacing8))
                    RecordingWatchMark(watchState, Modifier.testTag("recordings-folder-watched-$tag"))
                }
            }
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
                    if (watched in 1 until summary.recordingCount) {
                        append(" • ").append(pluralStringResource(R.plurals.recordings_folder_watched_count, watched, watched))
                    }
                    if (summary.totalSizeBytes > 0) {
                        append(" • ").append(formatFileSize(summary.totalSizeBytes, LocalContext.current))
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.Unspecified,
                modifier = Modifier.testTag("recordings-folder-supporting-$tag"),
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
            .testTag("recordings-folder-$tag")
            .then(contextMenu.modifier),
    )
}

private val RecordingNowDotRadius = 5.dp
private val RecordingNowDotGap = 1.5.dp

@Composable
internal fun RecordingNowFolderRow(
    recordingCount: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val owner = LocalTabOwner.current
    var focused by remember { mutableStateOf(false) }
    ListItem(
        selected = false,
        onClick = { if (owner?.isCurrent != false) onClick() },
        headlineContent = {
            Text(stringResource(R.string.recordings_recording_now_folder), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(pluralStringResource(R.plurals.recordings_in_progress_count, recordingCount, recordingCount),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            // The red dot sits on the folder's top-right corner, cut out of the folder by a thin gap. It
            // is the channel rows' 10dp recording dot.
            val dotCenter = { size: Size -> Offset(size.width * 0.915f, size.height * 0.25f) }
            Icon(
                painterResource(R.drawable.ic_folder),
                contentDescription = null,
                tint = androidx.tv.material3.LocalContentColor.current,
                modifier = Modifier
                    .size(32.dp)
                    .drawWithContent {
                        drawContent()
                        drawCircle(recordingMarkColor(focused), radius = RecordingNowDotRadius.toPx(), center = dotCenter(size))
                    }
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawCircle(Color.Black, radius = (RecordingNowDotRadius + RecordingNowDotGap).toPx(), center = dotCenter(size), blendMode = BlendMode.Clear)
                    },
            )
        },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_keyboard_arrow_right), contentDescription = null)
        },
        modifier = modifier
            .tabFocus()
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .testTag("recordings-recording-now-folder"),
    )
}

/**
 * The focused recording beside the list, laid out as the Channels pane: its picture, then the
 * channel and day with the recording's state, what it is, its facts, when it ran with how far it
 * was watched, and the synopsis; how it ended and whether it was watched close it. Beside the
 * Schedule a padded stop and the rule that set it up join them; beside the Problems what went
 * wrong is always said in words, with what is left of the file.
 */
@Composable
internal fun RecordingMetadataPane(
    entry: DvrEntry?,
    piconPath: ArtworkId?,
    admission: RecordingPlaybackAdmission?,
    modifier: Modifier = Modifier,
    kind: RecordingRowKind = RecordingRowKind.ARCHIVE,
) {
    if (entry == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_video_library),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    // The minute clock moves a running recording's length and the end of watching it from now.
    val nowSec = rememberDetailsEpochSeconds()
    val watchState = recordingWatchState(entry, offeredResumePosition(admission), nowSec)
    val timeLeft = remember(entry, admission) { recordingTimeLeft(entry, admission) }
    val facts = remember(entry, resources, locale) {
        programmeHeaderMetadata(programmeAboutFacts(null, entry, resources, locale), resources, entry.episode, null, locale, entry.contentGenre)
    }
    val stoppedEarly = remember(entry) { recordingStoppedEarly(entry) != null }
    val failure = entry.subscriptionError?.label()
        ?: if (kind == RecordingRowKind.PROBLEM) problemStateLabel(entry.state) else null
    val watched = watchState == RecordingWatchState.Watched
    val rule = if (kind == RecordingRowKind.SCHEDULE) recordingRuleLabel(entry) else null
    val partialSize = if (kind == RecordingRowKind.PROBLEM) recordingSizeBytes(entry)?.takeIf { it > 0 } else null
    // The Schedule row's own words for the padded stop, when the padding moves it.
    val paddedStop = recordingPaddedStop(entry)?.takeIf {
        kind == RecordingRowKind.SCHEDULE && it.epochSeconds != entry.stop?.epochSeconds
    }?.let { stringResource(R.string.recordings_until, formatClock(it.epochSeconds)) }
    // Level with the first row, below the list's own top padding.
    Column(modifier.fillMaxSize().padding(top = 4.dp).testTag("recording-metadata-pane")) {
        ProgrammePanePicture(
            art = LocalArtworkContext.current.currentSession?.let { entry.image ?: entry.fanartImage },
            logo = piconPath, accent = entry.channelId, logoTag = "recording-metadata-logo",
            modifier = Modifier.width(ProgrammePaneWidth).aspectRatio(16f / 9f).testTag("recording-metadata-art"),
        )
        ProgrammeHeadline(
            eyebrow = {
                ProgrammeEyebrow(piconPath, listOfNotNull(
                    entry.channelName?.takeIf(String::isNotBlank),
                    entry.start?.epochSeconds?.let { formatRecordingDate(it, dateOnly = true, locale = locale) },
                ).joinToString(" • "), Modifier.testTag("recording-metadata-eyebrow")) {
                    // The pane's eyebrow badge, as on the details page; a completed recording needs none.
                    RecordingBadge(entry.state)
                }
            },
            title = entry.title.orEmpty(),
            tagPrefix = "recording-metadata",
            // As many whole lines as the column leaves: the synopsis is what a recording is chosen by.
            synopsisLines = Int.MAX_VALUE,
            modifier = Modifier.padding(top = ProgrammePanePictureGap).weight(1f).widthIn(max = ProgrammePaneWidth),
            subtitle = entry.subtitle,
            facts = facts,
            rating = entry.rating?.let { programmeAgeRating(it.label, it.age) },
            timing = buildString {
                append(entry.start?.epochSeconds?.let { start ->
                    entry.stop?.epochSeconds?.let { formatTimeInterval(start, it) } ?: formatClock(start)
                }.orEmpty())
                paddedStop?.let {
                    if (isNotEmpty()) append(" • ")
                    append(it)
                }
                // A scheduled entry has recorded nothing yet; its length would be only the plan.
                recordingLengthSeconds(entry, nowSec)?.takeIf { it > 0 && entry.state != DvrEntryState.SCHEDULED }?.let {
                    if (isNotEmpty()) append(" • ")
                    append(resources.getString(R.string.recording_length_recorded, formatRecordedDuration(it, resources)))
                }
            }.takeIf(String::isNotEmpty),
            // How far it was watched, as the details page's timeline.
            timingMaxLines = 2,
            progress = (watchState as? RecordingWatchState.PartlyWatched)?.fraction?.toFloat(),
            progressColor = MaterialTheme.colorScheme.tertiary,
            synopsis = programmeSynopsis(entry.title, entry.subtitle, entry.summary, entry.description),
            fitSynopsis = true,
            titleStyle = MaterialTheme.typography.titleLarge,
            synopsisStyle = MaterialTheme.typography.bodyMedium,
            // Under its time, how it ended and how watching it would go, each with its row's mark.
            status = if (failure == null && !stoppedEarly && !watched && timeLeft == null && rule == null &&
                partialSize == null) null else { statusModifier ->
                Column(statusModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rule?.let { (text, icon) ->
                        ProgrammeStatusLine(text, icon, "recording-metadata-rule", maxLines = 2)
                    }
                    failure?.let {
                        ProgrammeStatusLine(it, R.drawable.ic_error_outlined, "recording-metadata-failure",
                            color = MaterialTheme.colorScheme.error, maxLines = 2)
                    }
                    partialSize?.let {
                        ProgrammeStatusLine(stringResource(R.string.recording_partial_file, formatFileSize(it, LocalContext.current)),
                            R.drawable.ic_folder, "recording-metadata-partial-file")
                    }
                    if (stoppedEarly) {
                        ProgrammeStatusLine(stringResource(R.string.recordings_stopped_early),
                            R.drawable.ic_warning_outlined, "recording-metadata-stopped-early")
                    }
                    when {
                        watched -> ProgrammeStatusLine(stringResource(R.string.recording_watched),
                            R.drawable.ic_check_circle_outlined, "recording-metadata-watched")
                        timeLeft != null -> {
                            val endsAt = stringResource(R.string.recording_ends_at, formatClock(nowSec + timeLeft.inWholeSeconds))
                            val partly = watchState as? RecordingWatchState.PartlyWatched
                            ProgrammeStatusLine(
                                if (partly == null) endsAt
                                else "${playerMinutesLeft(((timeLeft.inWholeSeconds + 59L) / 60L).toInt())} • $endsAt",
                                partly?.let { partlyWatchedIcon(it.fraction) }, "recording-metadata-time-left",
                            )
                        }
                    }
                }
            },
        )
    }
}

/** A failed or cancelled recording's state in the words its notice uses. */
@Composable
private fun problemStateLabel(state: DvrEntryState?): String? = when (state) {
    DvrEntryState.FILE_MISSING -> R.string.error_303_title
    DvrEntryState.MISSED -> R.string.recording_notice_missed
    DvrEntryState.COMPLETED_ERROR -> R.string.recording_notice_failed
    DvrEntryState.INVALID -> R.string.recording_notice_canceled
    else -> null
}?.let { stringResource(it) }

/** The rule a scheduled recording comes from, with its mark, when the server says it has one. */
@Composable
private fun recordingRuleLabel(entry: DvrEntry): Pair<String, Int>? = when {
    entry.autorecRuleId != null -> stringResource(R.string.recording_from_series_rule) to R.drawable.ic_event
    entry.timerecRuleId != null -> stringResource(R.string.recording_from_timer_rule) to R.drawable.ic_schedule
    else -> null
}

/**
 * A Schedule or Problems list in Archive's first column, with the focused recording's pane where
 * Archive shows its preview: below the heading band, level with the first row. The pane is passive;
 * focus stays in the list.
 */
@Composable
private fun RecordingListWithPane(
    entry: DvrEntry?,
    kind: RecordingRowKind,
    piconForEntry: (DvrEntry) -> ArtworkId?,
    admissionForEntry: (DvrEntry) -> RecordingPlaybackAdmission?,
    paneBottom: Dp,
    list: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(TvBrowseColumnWidth).fillMaxHeight()) { list() }
        Box(
            Modifier
                .padding(start = TvBrowseColumnGap, top = recordingsHeadingBandHeight())
                .width(TvBrowseColumnWidth)
                .fillMaxHeight()
                .focusProperties { canFocus = false },
        ) {
            RecordingMetadataPane(
                entry = entry,
                piconPath = entry?.let(piconForEntry),
                admission = entry?.let(admissionForEntry),
                modifier = Modifier.padding(bottom = paneBottom),
                kind = kind,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun RecordingSchedule(
    groups: List<DvrScheduleSection>,
    selectedKey: String?,
    selectedFocus: FocusRequester,
    onFocused: (String) -> Unit,
    onOpen: (DvrEntry) -> Unit,
    onOpenContextMenu: ((DvrEntry) -> Unit)? = null,
    piconForEntry: (DvrEntry) -> ArtworkId?,
    admissionForEntry: (DvrEntry) -> RecordingPlaybackAdmission?,
    initialScrollIndex: Int,
    onScrollChanged: (Int) -> Unit,
    paneBottom: Dp = 0.dp,
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
    val bringIntoViewSpec = rememberListEdgeBringIntoView(listState, 0.dp)
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
    val paneEntry = entries.firstOrNull { "recording:${recordingItemKey(it.id)}" == focusTargetKey }
    RecordingListWithPane(paneEntry, RecordingRowKind.SCHEDULE, piconForEntry, admissionForEntry, paneBottom) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
            LazyColumn(
                state = listState,
                userScrollEnabled = active,
                contentPadding = PaddingValues(bottom = ListBottomReserve),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .listEdgeFades(listState)
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
                        // As in the Archive, nothing to the right of a row takes focus: the pane is passive.
                        if (event.key == Key.DirectionRight) return@onPreviewKeyEvent true
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
                            resumePosition = offeredResumePosition(admissionForEntry(entry)),
                            selected = selectedKey == "recording:${recordingItemKey(entry.id)}",
                            focusTarget = active && focusTargetKey == "recording:${recordingItemKey(entry.id)}",
                            selectedFocus = selectedFocus,
                            onFocused = {
                                onFocused("recording:${recordingItemKey(entry.id)}")
                            },
                            onClick = { onOpen(entry) },
                            onOpenContextMenu = onOpenContextMenu?.let { open -> { open(entry) } },
                            kind = RecordingRowKind.SCHEDULE,
                        )
                    }
                }
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
    onOpenContextMenu: ((DvrEntry) -> Unit)? = null,
    piconForEntry: (DvrEntry) -> ArtworkId?,
    admissionForEntry: (DvrEntry) -> RecordingPlaybackAdmission?,
    initialScrollIndex: Int,
    onScrollChanged: (Int) -> Unit,
    paneBottom: Dp = 0.dp,
) {
    val entries = DvrProblemBucket.entries.flatMap { groups[it].orEmpty() }
    if (entries.isEmpty()) {
        ModeEmptyState(R.string.recordings_problems_empty)
        return
    }
    val owner = LocalTabOwner.current
    val active = owner?.isCurrent != false
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
    val bringIntoViewSpec = rememberListEdgeBringIntoView(listState, 0.dp)
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
    val paneEntry = entries.firstOrNull { "recording:${recordingItemKey(it.id)}" == focusTargetKey }
    RecordingListWithPane(paneEntry, RecordingRowKind.PROBLEM, piconForEntry, admissionForEntry, paneBottom) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
            LazyColumn(
                state = listState,
                userScrollEnabled = active,
                contentPadding = PaddingValues(bottom = ListBottomReserve),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .listEdgeFades(listState)
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
                        // As in the Archive, nothing to the right of a row takes focus: the pane is passive.
                        if (event.key == Key.DirectionRight) return@onPreviewKeyEvent true
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
                                resumePosition = offeredResumePosition(admissionForEntry(entry)),
                                selected = selectedKey == "recording:${recordingItemKey(entry.id)}",
                                focusTarget =
                                    active && focusTargetKey == "recording:${recordingItemKey(entry.id)}",
                                selectedFocus = selectedFocus,
                                onFocused = {
                                    onFocused("recording:${recordingItemKey(entry.id)}")
                                },
                                onClick = { onOpen(entry) },
                                onOpenContextMenu = onOpenContextMenu?.let { open -> { open(entry) } },
                                kind = RecordingRowKind.PROBLEM,
                            )
                        }
                    }
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
    resumePosition: Duration?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    focusTarget: Boolean = selected,
    selectedFocus: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    kind: RecordingRowKind = RecordingRowKind.ARCHIVE,
    onOpenContextMenu: (() -> Unit)? = null,
) {
    val problem = kind == RecordingRowKind.PROBLEM
    val metadata = recordingListMetadata(entry, problemLabel = entry.subscriptionError?.takeIf { problem }?.label())
    val owner = LocalTabOwner.current
    val contextMenu = at.bernhardberger.tvhplayer.ui.components.rememberBrowseContextMenuTrigger(
        onOpenContextMenu, enabled = { owner?.isCurrent != false })
    var focused by remember { mutableStateOf(false) }
    val itemKey = recordingItemKey(entry.id)
    val nowSec = if (recordingIsRunning(entry)) rememberDetailsEpochSeconds() else 0L
    val watchState = recordingWatchState(entry, resumePosition, nowSec)
    ListItem(
        selected = selected,
        onClick = { if (owner?.isCurrent != false) onClick() },
        onLongClick = contextMenu.onLongClick,
        // Running and scheduled recordings carry their mark at the right end of the title line,
        // as channel rows do, so the lines above and below keep their full width.
        headlineContent = {
            val markState = entry.state?.takeIf { recordingIsRunning(entry) || it == DvrEntryState.SCHEDULED }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("recording-list-headline-$itemKey"),
                )
                if (markState != null) {
                    Spacer(Modifier.width(TvSpacing8))
                    RecordingStatusIndicator(
                        state = markState,
                        focused = focused,
                        modifier = Modifier.testTag("recording-list-state-$itemKey"),
                    )
                    if (markState == DvrEntryState.RECORDING_ERROR) {
                        Spacer(Modifier.width(TvSpacing4))
                        Icon(
                            painter = painterResource(R.drawable.ic_error_outlined),
                            contentDescription = null,
                            modifier = Modifier.size(StatusIconSize).testTag("recording-list-errors-$itemKey"),
                            tint = errorMarkColor(focused),
                        )
                    }
                }
                // Watch state ends the line, so its marks form one column down the list.
                if (watchState != RecordingWatchState.Unwatched) {
                    Spacer(Modifier.width(TvSpacing8))
                    RecordingWatchMark(watchState, Modifier.testTag("recording-list-watched-$itemKey"))
                }
            }
        },
        overlineContent = {
            RecordingOverline(entry, focused)
        },
        supportingContent = metadata.takeIf(String::isNotBlank)?.let { text ->
            {
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("recording-list-supporting-${recordingItemKey(entry.id)}"),
                )
            }
        },
        leadingContent = {
            PiconBox(
                piconPath = piconPath,
                placeholderTint = androidx.tv.material3.LocalContentColor.current,
                // The logo sits on the row's own fill: the screen's background, light with focus.
                ground = if (focused) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.background,
                prefetchGround = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.size(32.dp).testTag("recording-list-leading-${recordingItemKey(entry.id)}"),
            )
        },
        modifier = modifier
            .tabFocus()
            .fillMaxWidth()
            .testTag("recording-list-entry-${recordingItemKey(entry.id)}")
            .then(contextMenu.modifier)
            .then(if (focusTarget && selectedFocus != null) Modifier.focusRequester(selectedFocus) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (owner?.isCurrent != false && it.isFocused) onFocused()
            },
    )
}

@Composable
private fun RecordingSectionHeader(
    text: String,
    first: Boolean = false,
) {
    // Headings use the Archive heading band; later ones keep a gap to the section above.
    Box(Modifier.padding(top = if (first) 0.dp else 8.dp).height(recordingsHeadingBandHeight())) {
        Text(
            text = text,
            modifier = Modifier.align(Alignment.CenterStart).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The row's overline: the programme start and length (a running recording says when it stops), then
 * symbols for an early stop, a failure or a cancellation whose words are read out by accessibility
 * services. Only a failure is drawn in the error colour; the others stay muted.
 */
@Composable
private fun RecordingOverline(entry: DvrEntry, focused: Boolean) {
    val leading = buildList {
        entry.start?.epochSeconds.recordingDateTime(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
            .takeIf(String::isNotBlank)?.let(::add)
        recordingLengthLabel(entry)?.let(::add)
    }.joinToString(" • ")
    val marks = buildList {
        if (recordingIsRunning(entry)) return@buildList
        // The pane spells the early stop out with the planned times.
        if (recordingStoppedEarly(entry) != null) add(RecordingOverlineMark.STOPPED_EARLY to stringResource(R.string.recordings_stopped_early))
        recordingStateMark(entry.state)?.let { add(it to dvrStateLabel(entry.state)) }
    }
    val failedTint = errorMarkColor(focused)
    Text(
        text = buildAnnotatedString {
            append(leading)
            marks.forEach { (mark, label) ->
                if (length > 0) append(' ')
                appendInlineContent(mark.name, label)
            }
        },
        inlineContent = remember(failedTint) {
            RecordingOverlineMark.entries.associate { mark ->
                mark.name to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                    Icon(
                        painter = painterResource(mark.icon),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().testTag(mark.tag),
                        tint = if (mark == RecordingOverlineMark.FAILED) failedTint else androidx.tv.material3.LocalContentColor.current,
                    )
                }
            }
        },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("recording-list-metadata-${recordingItemKey(entry.id)}"),
    )
}

private enum class RecordingOverlineMark(@DrawableRes val icon: Int, val tag: String) {
    STOPPED_EARLY(R.drawable.ic_warning_outlined, "recording-list-stopped-early"),
    FAILED(R.drawable.ic_error_outlined, "recording-list-state-failed"),
    CANCELLED(R.drawable.ic_cancel_outlined, "recording-list-state-cancelled"),
}

/** A completed recording is the normal case and carries no mark; running and scheduled ones carry theirs at the row's end. */
private fun recordingStateMark(state: DvrEntryState?): RecordingOverlineMark? = when (state) {
    DvrEntryState.COMPLETED_ERROR,
    DvrEntryState.FILE_MISSING,
    DvrEntryState.MISSED -> RecordingOverlineMark.FAILED
    DvrEntryState.INVALID -> RecordingOverlineMark.CANCELLED
    else -> null
}

/** The error colour of a mark: pink on the dark surface, dark red on a focused, light row. */
@Composable
private fun errorMarkColor(focused: Boolean): Color =
    if (focused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.error

/** The Recordings 1xx alert, shown only while there are no recordings to list. */
internal fun recordingsAppError(
    empty: Boolean,
    connectionUiState: ConnectionUiState,
    connectionFailure: ConnectionUiState? = connectionUiState,
): AppError? = if (empty) connectionFailure?.browseAppError() else null

/** The passive text of an empty Recordings list without an alert; the skeleton speaks the loading one. */
internal fun recordingsEmptyMessageRes(connectionUiState: ConnectionUiState): Int = when (connectionUiState) {
    ConnectionUiState.Connecting,
    ConnectionUiState.SyncingChannels -> R.string.recordings_loading
    ConnectionUiState.Reconnecting -> R.string.recordings_reconnecting
    else -> R.string.recordings_empty
}

/** An empty library still on its way shows [RecordingsSkeleton] rather than a message. */
internal fun recordingsLoading(
    connectionUiState: ConnectionUiState,
    connectionFailure: ConnectionUiState? = connectionUiState,
): Boolean = recordingsAppError(empty = true, connectionUiState, connectionFailure) == null &&
    recordingsEmptyMessageRes(connectionUiState) == R.string.recordings_loading

/**
 * The Recordings header's height, shared with [RecordingsPreparationSkeleton]'s empty header row so
 * the skeleton does not move when the real screen replaces it: 40dp, or the mode tabs' label line
 * with its padding once text grows.
 */
@Composable
@ReadOnlyComposable
internal fun recordingsHeaderMinHeight(): Dp = with(LocalDensity.current) {
    maxOf(TvBrowseHeaderHeight, MaterialTheme.typography.titleSmall.lineHeight.toDp() + TvSpacing8 * 2)
}

/**
 * The whole Recordings screen while it prepares its first frame: the header's empty row, then
 * [RecordingsSkeleton] in the place it takes below the real header while the library syncs.
 */
@Composable
internal fun RecordingsPreparationSkeleton(contentPadding: PaddingValues, startPadding: Dp) {
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Spacer(Modifier.height(recordingsHeaderMinHeight()))
        Spacer(Modifier.height(TvSpacing12))
        RecordingsSkeleton(Modifier.padding(start = startPadding + TvBrowseLeadingInset))
    }
}

/**
 * The Archive while the recordings are on their way: the root's blank heading band, then passive
 * rows in the shape of recording rows in the active column's place, fading out at the bottom as
 * the list does. It takes no focus and says once that recordings are loading.
 */
@Composable
internal fun RecordingsSkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.recordings_loading)
    val list = rememberLazyListState()
    Column(
        modifier
            .width(TvBrowseColumnWidth)
            .fillMaxHeight()
            .testTag("recordings-skeleton")
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Spacer(Modifier.height(recordingsHeadingBandHeight()))
        LazyColumn(
            state = list,
            userScrollEnabled = false,
            contentPadding = PaddingValues(top = DepthListTopReserve, bottom = ListBottomReserve),
            modifier = Modifier.fillMaxSize().listEdgeFades(list),
        ) {
            items(RecordingsSkeletonRows) { RecordingSkeletonRow(it) }
        }
    }
}

/** A recording row's native shape with its overline, title and supporting lines still loading. */
@Composable
private fun RecordingSkeletonRow(index: Int) {
    ListItem(
        selected = false,
        onClick = {},
        overlineContent = { SkeletonLine(widthFraction = RecordingSkeletonOverlineFraction) },
        headlineContent = {
            SkeletonLine(widthFraction = RecordingSkeletonTitleFractions[index % RecordingSkeletonTitleFractions.size])
        },
        supportingContent = { SkeletonLine(widthFraction = RecordingSkeletonSupportingFraction) },
        // The picon's place stays empty, so the lines start where the titles will.
        leadingContent = { Spacer(Modifier.size(32.dp)) },
        modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
    )
}

private const val RecordingsSkeletonRows = 12
private val RecordingSkeletonTitleFractions = floatArrayOf(0.8f, 0.6f, 0.7f)
private const val RecordingSkeletonOverlineFraction = 0.5f
private const val RecordingSkeletonSupportingFraction = 0.4f

@Composable
internal fun RecordingsEmptyState(
    connectionUiState: ConnectionUiState,
    connectionFailure: ConnectionUiState?,
    onAction: (AppErrorAction) -> Unit,
    actionFocus: FocusRequester,
    upFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val alert = recordingsAppError(empty = true, connectionUiState = connectionUiState, connectionFailure = connectionFailure)
    if (alert != null) {
        AppErrorAlert(
            error = alert,
            onAction = onAction,
            modifier = modifier.fillMaxSize(),
            actionFocus = actionFocus,
            leadingExit = LocalBrowseNavigationFocus.current ?: FocusRequester.Default,
            upExit = upFocus,
        )
        return
    }
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(recordingsEmptyMessageRes(connectionUiState)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
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
        DvrScheduleSectionKind.DATE -> section.date?.let { formatDayHeader(it, locale) }.orEmpty()
    }
}

@Composable
internal fun dvrStateLabel(state: DvrEntryState?): String = stringResource(
    when (state) {
        DvrEntryState.SCHEDULED -> R.string.recording_state_scheduled
        DvrEntryState.RECORDING -> R.string.recording_state_recording
        DvrEntryState.COMPLETED -> R.string.recording_state_completed
        DvrEntryState.MISSED -> R.string.recording_state_missed
        DvrEntryState.INVALID -> R.string.recording_state_cancelled
        DvrEntryState.RECORDING_ERROR -> R.string.recording_state_recording_errors
        DvrEntryState.COMPLETED_ERROR,
        DvrEntryState.FILE_MISSING -> R.string.recording_state_failed
        else -> R.string.recording_state_unknown
    }
)

internal fun Long?.recordingDateTime(locale: java.util.Locale = java.util.Locale.getDefault()): String = this?.let {
    at.bernhardberger.tvhplayer.ui.common.formatRecordingDate(it, locale = locale)
}.orEmpty()

/** The recording's day and times as one range; without a stop only the start, without a start only the stop's clock. */
internal fun recordingTimeRange(startSec: Long?, stopSec: Long?, locale: java.util.Locale): String = when {
    startSec != null && stopSec != null -> at.bernhardberger.tvhplayer.ui.common.formatTimeInterval(
        startSec, stopSec, withDate = true, locale = locale)
    startSec != null -> startSec.recordingDateTime(locale)
    stopSec != null -> at.bernhardberger.tvhplayer.ui.common.formatClock(stopSec)
    else -> ""
}

/** While recording, until the stop with its padding; otherwise the length actually recorded. */
@Composable
private fun recordingLengthLabel(entry: DvrEntry): String? =
    if (recordingIsRunning(entry)) recordingPaddedStop(entry)?.let {
        stringResource(R.string.recordings_until, at.bernhardberger.tvhplayer.ui.common.formatClock(it.epochSeconds))
    }
    // Only a running recording measures to now, and that took the branch above.
    else recordingLengthSeconds(entry, nowSec = 0L)?.takeIf { it > 0 }?.let {
        at.bernhardberger.tvhplayer.ui.common.formatRecordedDuration(it, androidx.compose.ui.platform.LocalResources.current)
    }

/**
 * Muted watch mark at the end of a recording row's title line: a check when watched, a clock
 * loader filled about as far as the saved position when partly watched.
 */
@Composable
private fun RecordingWatchMark(state: RecordingWatchState, modifier: Modifier = Modifier) {
    @DrawableRes val icon = when (state) {
        RecordingWatchState.Unwatched -> return
        RecordingWatchState.Watched -> R.drawable.ic_check_circle_outlined
        is RecordingWatchState.PartlyWatched -> partlyWatchedIcon(state.fraction)
    }
    val description =
        if (state == RecordingWatchState.Watched) R.string.recording_watched else R.string.recording_partly_watched
    Icon(
        painter = painterResource(icon),
        contentDescription = stringResource(description),
        modifier = modifier.size(StatusIconSize),
        tint = androidx.tv.material3.LocalContentColor.current.copy(alpha = TvTextSecondaryAlpha),
    )
}

/** Material's clock loader comes in six steps; an unknown share shows the middle one. */
@DrawableRes
private fun partlyWatchedIcon(fraction: Double?): Int = when {
    fraction == null -> R.drawable.ic_clock_loader_40_outlined
    fraction < 0.15 -> R.drawable.ic_clock_loader_10_outlined
    fraction < 0.3 -> R.drawable.ic_clock_loader_20_outlined
    fraction < 0.5 -> R.drawable.ic_clock_loader_40_outlined
    fraction < 0.7 -> R.drawable.ic_clock_loader_60_outlined
    fraction < 0.85 -> R.drawable.ic_clock_loader_80_outlined
    else -> R.drawable.ic_clock_loader_90_outlined
}
