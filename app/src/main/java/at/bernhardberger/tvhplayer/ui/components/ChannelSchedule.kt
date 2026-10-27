package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.programmeCategory
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.common.clockWidthSamples
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatDayHeader
import at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabel
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.player.PlaybackPanelHeaderTop
import at.bernhardberger.tvhplayer.ui.player.PlaybackPanelPadding
import at.bernhardberger.tvhplayer.ui.player.PlaybackRowTextInset
import at.bernhardberger.tvhplayer.ui.player.playerMinutesLeft
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Width-independent schedule column: its caller owns navigation, data and the surrounding sheet.
 * [loading] with rows means later days are still arriving; [viewerBehindMs] is the player's own
 * behind-live distance, zero at the live edge.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ChannelSchedule(
    channel: Channel?, events: List<EpgEvent>, nowSec: Long, viewerSec: Long?,
    loading: Boolean, offline: Boolean,
    recording: (EpgEvent) -> DvrEntryState?,
    onOpen: (EpgEvent) -> Unit, onClose: () -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    initialEvent: EpgEvent? = scheduleInitialEvent(events, viewerSec, nowSec),
    onFocused: (EpgEvent) -> Unit = {},
    onContextMenu: ((EpgEvent) -> Unit)? = null,
    viewerBehindMs: Long? = null,
) {
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val today = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
    fun date(event: EpgEvent) = scheduleEventDay(event, today, zone)
    val days = remember(events, zone, today) { events.map(::date).distinct() }
    val initialIndex = events.indexOfFirst { it.id == initialEvent?.id }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    // The band starts a row lower, so the row above the focused one stays clear of the top fade.
    val bringIntoView = rememberListEdgeBringIntoView(listState, 0.dp, topOverflow = rememberScheduleRowHeight())
    val scope = rememberCoroutineScope()
    val rowFocus = remember(events.map { it.id }) { events.associate { it.id to FocusRequester() } }
    val selectedTabFocus = remember { FocusRequester() }
    val emptyFocus = remember { FocusRequester() }
    var selectedDay by remember { mutableStateOf(initialEvent?.let(::date)) }
    var entered by remember { mutableStateOf(false) }
    var focusedEvent by remember { mutableStateOf<EpgEvent?>(null) }
    var rowOwnsFocus by remember { mutableStateOf(false) }
    val channelName = channel?.name ?: stringResource(R.string.epg_search_unknown_channel)
    val title = stringResource(R.string.channel_schedule_pane, channelName)
    val timeWidth = rememberScheduleTimeWidth()
    // A row placed where focus scrolling would put it: inside the band between the list's fades.
    suspend fun scrollToRow(index: Int) {
        listState.scrollToItem(index)
        val layout = listState.layoutInfo
        listState.scrollToItem(index, bringIntoView.calculateScrollDistance(
            offset = layout.beforeContentPadding.toFloat(),
            size = (layout.visibleItemsInfo.firstOrNull()?.size ?: 0).toFloat(),
            containerSize = layout.viewportSize.height.toFloat(),
            scrollBackAvailablePx = if (index == 0) 0f else Float.POSITIVE_INFINITY,
        ).roundToInt())
    }
    // Decide before removal lets Compose move focus to the tab as a fallback.
    val removed = focusedEvent?.takeIf { old -> rowOwnsFocus && events.none { it.id == old.id } }
    LaunchedEffect(loading, events) {
        if (events.isEmpty() || !entered || removed != null) {
            withFrameNanos { }
            if (events.isNotEmpty()) {
                val target = removed?.let { old -> events.firstOrNull { it.start >= old.stop }
                    ?: scheduleInitialEvent(events, viewerSec, nowSec) } ?: events[initialIndex]
                scrollToRow(events.indexOf(target))
                withFrameNanos { }
                entered = rowFocus[target.id]?.requestFocus() == true
            } else { entered = false; emptyFocus.requestFocus() }
        }
    }
    Column(modifier.focusGroup().semantics { paneTitle = title }, verticalArrangement = Arrangement.spacedBy(TvSpacing12)) {
        // The options sheet's header: its title on the rows' text keyline, the clock as its quiet value.
        Row(Modifier.fillMaxWidth().padding(start = ScheduleTextKeyline, end = ScheduleTextKeyline, top = PlaybackPanelHeaderTop),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TvSpacing12)) {
            PiconBox(channel?.icon, Modifier.size(56.dp, 32.dp))
            ChannelTitle(channel?.number, channelName, Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge)
            Text(formatClock(nowSec), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextTertiaryAlpha))
        }
        if (offline) Row(Modifier.fillMaxWidth().padding(horizontal = ScheduleTextKeyline).semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TvSpacing12)) {
            ProgrammeStatusLine(stringResource(R.string.channel_schedule_offline), R.drawable.ic_error_outlined,
                "channel-schedule-offline", Modifier.weight(1f), maxLines = 2)
            Button(onClick = onRetry, modifier = Modifier.onFocusChanged { if (it.isFocused) rowOwnsFocus = false }) { Text(stringResource(R.string.retry)) }
        }
        if (events.isEmpty() && loading) {
            val description = stringResource(R.string.channel_schedule_loading)
            // The rows' own shape still loading; the passive skeleton holds focus and speaks once.
            Column(Modifier.weight(1f).focusRequester(emptyFocus).focusable()
                .clearAndSetSemantics { contentDescription = description }.testTag("channel-schedule-loading"),
                verticalArrangement = Arrangement.spacedBy(TvSpacing12)) {
                Row(Modifier.padding(horizontal = ScheduleTextKeyline), horizontalArrangement = Arrangement.spacedBy(TvSpacing16 * 2)) {
                    repeat(3) { SkeletonLine(Modifier.width(56.dp), style = MaterialTheme.typography.titleMedium, widthFraction = 1f) }
                }
                val skeleton = rememberLazyListState()
                LazyColumn(state = skeleton, userScrollEnabled = false, contentPadding = ScheduleListPadding,
                    modifier = Modifier.fillMaxSize().listEdgeFades(skeleton)) {
                    items(ScheduleSkeletonRows) { ScheduleSkeletonRow(timeWidth) }
                }
            }
        } else if (events.isEmpty()) {
            Column(Modifier.padding(horizontal = ScheduleTextKeyline), verticalArrangement = Arrangement.spacedBy(TvSpacing12)) {
                Text(stringResource(R.string.no_epg), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.channel_schedule_empty), style = MaterialTheme.typography.bodyMedium,
                    color = TvTextColors.secondary)
                Button(onClick = onClose, modifier = Modifier.padding(top = TvSpacing12).focusRequester(emptyFocus)) {
                    Text(stringResource(R.string.close))
                }
            }
        } else {
            // The selected day's tab stays whole in view wherever the list or the tabs moved it.
            val tabInView = remember(days) { days.associateWith { BringIntoViewRequester() } }
            LaunchedEffect(tabInView, selectedDay) {
                withFrameNanos { }
                selectedDay?.let(tabInView::get)?.bringIntoView()
            }
            AppTabRow(days.indexOf(selectedDay).coerceAtLeast(0), AppTabStyle.Section,
                modifier = Modifier.padding(horizontal = PlaybackPanelPadding),
                selectedTabFocus = selectedTabFocus,
                onMoveToContent = {
                    val target = if (selectedDay == today) scheduleInitialEvent(events.filter { date(it) == today }, viewerSec, nowSec)
                        else events.firstOrNull { date(it) == selectedDay }
                    target?.let { event -> scope.launch {
                        scrollToRow(events.indexOf(event))
                        withFrameNanos { }
                        rowFocus[event.id]?.requestFocus()
                    } }
                    true
                }) {
                days.forEachIndexed { index, day ->
                    val target = if (day == today) scheduleInitialEvent(events.filter { date(it) == day }, viewerSec, nowSec)
                        else events.firstOrNull { date(it) == day }
                    AppTab(selected = selectedDay == day,
                         onFocus = {
                                rowOwnsFocus = false
                                selectedDay = day
                                target?.let { event -> scope.launch { scrollToRow(events.indexOf(event)) } }
                        }, first = index == 0, last = index == days.lastIndex,
                        modifier = Modifier.bringIntoViewRequester(tabInView.getValue(day)).testTag("schedule-tab-$day"),
                        label = when (day) {
                            today -> stringResource(R.string.channel_schedule_today)
                            today.plusDays(1) -> stringResource(R.string.channel_schedule_tomorrow)
                            else -> day.format(DateTimeFormatter.ofPattern("EEE d", locale))
                        })
                }
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
                LazyColumn(state = listState, contentPadding = ScheduleListPadding,
                    modifier = Modifier.weight(1f).listEdgeFades(listState).testTag("channel-schedule-list")) {
                    itemsIndexed(events, key = { _, event -> event.id.value }) { index, event ->
                        val day = date(event)
                        val previous = events.getOrNull(index - 1)
                        val gaps = previous?.let { scheduleGaps(it.stop.epochSeconds, event.start.epochSeconds, zone) }.orEmpty()
                        // The tabs name the first day; later days start under their own heading.
                        var headingDay = previous?.let(::date) ?: day
                        Column {
                            gaps.forEach { gap ->
                                if (headingDay != gap.day) ScheduleDayHeading(formatDayHeader(gap.day, locale))
                                headingDay = gap.day
                                ScheduleNote(formatHm(gap.start), stringResource(R.string.channel_schedule_gap, formatHm(gap.stop)), timeWidth)
                            }
                            if (headingDay != day) ScheduleDayHeading(formatDayHeader(day, locale))
                            ChannelScheduleRow(event, nowSec, viewerSec, viewerBehindMs, recording(event), timeWidth,
                                onClick = { onOpen(event) },
                                onContextMenu = onContextMenu?.let { open -> { open(event) } },
                                modifier = Modifier.focusRequester(rowFocus.getValue(event.id))
                                    .focusProperties {
                                        left = FocusRequester.Cancel; right = FocusRequester.Cancel
                                        if (index == 0) up = selectedTabFocus
                                        if (index == events.lastIndex) down = FocusRequester.Cancel
                                    }
                                    .onFocusChanged { if (it.isFocused) { rowOwnsFocus = true; focusedEvent = event; selectedDay = day; onFocused(event) } })
                        }
                    }
                    item {
                        ScheduleNote(null, if (loading) stringResource(R.string.channel_schedule_loading_more)
                            else stringResource(R.string.channel_schedule_end, formatDayHeader(days.last(), locale)),
                            timeWidth, Modifier.testTag("channel-schedule-footer"), announce = true)
                    }
                }
            }
        }
    }
}

/** The text keyline of the panel: its padding plus a list row's own text inset. */
private val ScheduleTextKeyline = PlaybackPanelPadding + PlaybackRowTextInset

/** Rows sit inside the panel's padding and run to its bottom edge behind the shared fade. */
private val ScheduleListPadding = PaddingValues(start = PlaybackPanelPadding, end = PlaybackPanelPadding, bottom = ListBottomReserve)

private val ScheduleArtworkSize = DpSize(80.dp, 45.dp)
private const val ScheduleSkeletonRows = 5

/** The start time's column: the widest clock text in the headline's role, so titles align. */
@Composable
private fun rememberScheduleTimeWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleMedium
    val density = LocalDensity.current
    val samples = clockWidthSamples(uses24Hour = AppClock.uses24Hour)
    return remember(measurer, style, density, samples) {
        with(density) { samples.maxOf { measurer.measure(it, style).size.width }.toDp() }
    }
}

/**
 * A running programme's row at the current font scale: the list item's padding around the taller
 * of its picture and its two measured lines with the progress strip.
 */
@Composable
internal fun rememberScheduleRowHeight(): Dp {
    val measurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    return remember(measurer, typography, density) {
        with(density) {
            val lines = measurer.measure("0", typography.titleMedium).size.height +
                measurer.measure("0", typography.bodyMedium).size.height
            ScheduleRowVerticalPadding * 2 +
                maxOf(ScheduleArtworkSize.height, lines.toDp() + ScheduleProgressGap + ChannelRowProgressHeight)
        }
    }
}

/**
 * androidx.tv:tv-material's ListItem content padding above and below its content. The library keeps
 * `ListItemDefaults.ContentPadding` internal, so this copies it; ChannelScheduleFocusTest measures a
 * real row against [rememberScheduleRowHeight] and fails when a library update changes it.
 */
private val ScheduleRowVerticalPadding = 12.dp

/** A day's start in the continuous list: its date as a quiet heading over a divider. */
@Composable
private fun ScheduleDayHeading(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = TvSpacing16, bottom = TvSpacing8)) {
        Text(text, Modifier.padding(horizontal = PlaybackRowTextInset).semantics { heading() },
            color = TvTextColors.secondary, style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(Modifier.padding(top = TvSpacing8).padding(horizontal = PlaybackRowTextInset))
    }
}

/** A passive line in the row grid, a gap or the list's end, read as one piece of text. */
@Composable
private fun ScheduleNote(time: String?, text: String, timeWidth: Dp, modifier: Modifier = Modifier, announce: Boolean = false) {
    val spoken = listOfNotNull(time, text).joinToString(" ")
    Box(modifier.clearAndSetSemantics {
        this.text = AnnotatedString(spoken)
        if (announce) liveRegion = LiveRegionMode.Polite
    }) {
        ListItem(selected = false, onClick = {},
            headlineContent = {
                // Notes are not programmes: both columns in the quiet secondary tone.
                CompositionLocalProvider(LocalContentColor provides TvTextColors.secondary) {
                    ScheduleHeadline(time, timeWidth) {
                        Text(text, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false })
    }
}

/** A row's shape with its time, title and supporting lines still loading. */
@Composable
private fun ScheduleSkeletonRow(timeWidth: Dp) {
    ListItem(selected = false, onClick = {},
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonLine(Modifier.width(timeWidth), widthFraction = 1f)
                Spacer(Modifier.width(TvSpacing16))
                SkeletonLine(Modifier.weight(1f))
            }
        },
        supportingContent = { SkeletonLine(Modifier.padding(start = timeWidth + TvSpacing16), widthFraction = 0.5f) },
        trailingContent = { ScheduleArtworkSlot(null) },
        modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false })
}

/**
 * A row's picture at its trailing end, so times and titles start at one x in every row. Most
 * programmes have none: the header already names the channel, so they show nothing, and the slot
 * keeps only the picture's height so rows with and without one stand equally tall.
 */
@Composable
private fun ScheduleArtworkSlot(image: ArtworkId?) {
    Box(Modifier.height(ScheduleArtworkSize.height), contentAlignment = Alignment.Center) {
        if (image != null) ProgrammeArtwork(image, null, null, Modifier.size(ScheduleArtworkSize.width, ScheduleArtworkSize.height))
    }
}

/** The headline's two columns: the start time, then [content] on the titles' line. */
@Composable
private fun ScheduleHeadline(time: String?, timeWidth: Dp, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(timeWidth)) { if (time != null) Text(time, maxLines = 1) }
        Spacer(Modifier.width(TvSpacing16))
        content()
    }
}

@Composable
private fun ChannelScheduleRow(
    event: EpgEvent, nowSec: Long, viewerSec: Long?, viewerBehindMs: Long?, recording: DvrEntryState?, timeWidth: Dp,
    onClick: () -> Unit, modifier: Modifier = Modifier,
    onContextMenu: (() -> Unit)? = null,
) {
    val contextMenu = rememberBrowseContextMenuTrigger(onContextMenu)
    var focused by remember { mutableStateOf(false) }
    val start = event.start.epochSeconds
    val stop = event.stop.epochSeconds
    // Ended for the viewer: what lies between a behind-live viewer and live is still ahead of them.
    val ended = stop <= (viewerSec ?: nowSec)
    val watched = viewerSec != null && event.isRunning(viewerSec)
    val failed = recording in ScheduleFailedStates
    val resources = LocalResources.current
    // The player bar's own distance and wording; the live edge says nothing.
    val behind = viewerBehindMs?.takeIf { watched && it > 0L }
    // Time facts lead and the category ends the line, so an ellipsis only ever cuts the category.
    val facts = listOfNotNull(
        when {
            behind != null -> stringResource(R.string.timeshift_behind_live, formatPlaybackDuration(behind))
            event.isRunning(nowSec) -> stringResource(R.string.recordings_until, formatHm(stop)) + ScheduleSeparator +
                playerMinutesLeft(((stop - nowSec + 59) / 60).toInt())
            ended -> stringResource(R.string.channel_schedule_ended)
            else -> formatDurationMinutes((stop - start + 59) / 60, resources)
        },
        if (failed) stringResource(if (recording == DvrEntryState.MISSED) R.string.recording_notice_missed else R.string.details_status_failed) else null,
        programmeCategory(event)?.let { programmeCategoryLabel(it) },
    )
    val at = if (watched) viewerSec else nowSec
    val progress = event.progress(at).takeIf { event.isRunning(at) }
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = contextMenu.onLongClick,
        headlineContent = {
            // An ended programme's lines step back, as the list's other secondary text does.
            val headlineColor = LocalContentColor.current.let { if (ended) it.copy(alpha = TvTextSecondaryAlpha) else it }
            CompositionLocalProvider(LocalContentColor provides headlineColor) {
                ScheduleHeadline(formatHm(start), timeWidth) {
                    Text(event.title.orEmpty(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (recording != null && !failed) {
                        Spacer(Modifier.width(TvSpacing8))
                        RecordingStatusIndicator(recording, focused = focused)
                    }
                    if (failed) {
                        Spacer(Modifier.width(TvSpacing8))
                        Icon(painterResource(R.drawable.ic_error_outlined), contentDescription = null,
                            modifier = Modifier.size(StatusIconSize), tint = errorMarkColor(focused))
                    }
                }
            }
        },
        supportingContent = {
            Column(Modifier.padding(start = timeWidth + TvSpacing16)) {
                // The mockup's 14sp second line, a step below the 16sp headline.
                Text(facts.joinToString(ScheduleSeparator), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                if (progress != null) {
                    Spacer(Modifier.height(ScheduleProgressGap))
                    ProgressStrip(progress, Modifier.fillMaxWidth(), color = progressColor(watched),
                        height = ChannelRowProgressHeight,
                        trackColor = if (focused) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = TvTextDisabledAlpha)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = TvTrackAlpha))
                }
            }
        },
        trailingContent = { ScheduleArtworkSlot(event.image) },
        modifier = modifier.then(contextMenu.modifier).fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }.testTag("schedule-row-${event.id.value}"),
    )
}

/** The separator of the channel rows' programme line. */
private const val ScheduleSeparator = " • "
private val ScheduleProgressGap = 3.dp
private val ScheduleFailedStates = setOf(DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING, DvrEntryState.MISSED)

internal fun scheduleEventDay(event: EpgEvent, today: java.time.LocalDate, zone: ZoneId): java.time.LocalDate {
    val start = Instant.ofEpochSecond(event.start.epochSeconds).atZone(zone).toLocalDate()
    return if (start < today && event.stop.epochSeconds > today.atStartOfDay(zone).toEpochSecond()) today else start
}

internal data class ScheduleGap(val day: java.time.LocalDate, val start: Long, val stop: Long)

internal fun scheduleGaps(start: Long, stop: Long, zone: ZoneId): List<ScheduleGap> = buildList {
    var from = start
    while (from < stop) {
        val day = Instant.ofEpochSecond(from).atZone(zone).toLocalDate()
        val until = minOf(stop, day.plusDays(1).atStartOfDay(zone).toEpochSecond())
        add(ScheduleGap(day, from, until))
        from = until
    }
}

/** Unknown viewer timing falls back to on air; a schedule gap chooses the next programme. */
internal fun scheduleInitialEvent(events: List<EpgEvent>, viewerSec: Long?, nowSec: Long): EpgEvent? {
    val at = viewerSec ?: nowSec
    return events.firstOrNull { it.start.epochSeconds <= at && at < it.stop.epochSeconds }
        ?: events.firstOrNull { it.start.epochSeconds >= at } ?: events.lastOrNull()
}
