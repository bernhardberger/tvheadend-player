package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent

import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors

import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.profiling.ProfileCompositionLifetime
import at.bernhardberger.tvhplayer.profiling.profileViewportItem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import at.bernhardberger.tvhplayer.core.recordingIsRunning
import at.bernhardberger.tvhplayer.core.shouldComposeTimelineCell
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.tv.material3.Border
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.GuideRows
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.core.programmeCategory
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabel
import at.bernhardberger.tvhplayer.ui.tvGenreColors
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.ui.components.AppErrorAlert
import at.bernhardberger.tvhplayer.core.EpgColumnDataState
import at.bernhardberger.tvhplayer.core.epgColumnDataState
import at.bernhardberger.tvhplayer.core.epgWindowCoverageProven
import at.bernhardberger.tvhplayer.core.TimelineEventSpan
import at.bernhardberger.tvhplayer.ui.components.SkeletonLine
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.LocalContentColor
import at.bernhardberger.tvhplayer.core.timelineEventSpan
import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha
import at.bernhardberger.tvhplayer.ui.TvSpacing4
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.components.LocalTabOwner
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.components.LocalArtworkContext
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.StatusIconSize
import at.bernhardberger.tvhplayer.ui.screens.formatDateTime
import at.bernhardberger.tvhplayer.ui.screens.guideEmptyMessageRes

private val GuideLogoWidth = 48.dp
private val GuideLogoHeight = 28.dp
private val GuideHeaderVerticalPadding = 4.dp
/** Material for TV's dense list item content padding above and below its text. */
private val GuideCellVerticalPadding = 10.dp

/**
 * A Guide row's height: Material for TV's dense list item minimum (two lines 56dp, one line 40dp)
 * at the current font scale, grown to fit the channel header and the programme title, with the
 * start time below it in [GuideRows.REGULAR] rows, inside the list item's vertical content padding.
 */
@Composable
internal fun guideTimelineRowHeight(rows: GuideRows = GuideRows.REGULAR): Dp = with(LocalDensity.current) {
    val numberLine = MaterialTheme.typography.labelMedium.lineHeight.toDp()
    val headerContent = maxOf(
        GuideLogoHeight,
        numberLine,
        MaterialTheme.typography.titleSmall.lineHeight.toDp(),
    ) + GuideHeaderVerticalPadding * 2
    // Headers either show number/logo or number/name, never all three.
    maxOf(
        (if (rows == GuideRows.DENSE) 40.dp else 56.dp) * fontScale,
        headerContent,
        guideCellTextHeight(rows) + GuideCellVerticalPadding * 2,
    )
}

/** A cell's text: the title, and in [GuideRows.REGULAR] rows the start time below it. */
@Composable
private fun guideCellTextHeight(rows: GuideRows): Dp = with(LocalDensity.current) {
    val title = MaterialTheme.typography.titleSmall.lineHeight.toDp()
    if (rows == GuideRows.DENSE) title else title + MaterialTheme.typography.labelSmall.lineHeight.toDp()
}

/** How far down a row its title line ends: the cell's text is centred in the row. */
@Composable
internal fun guideRowTitleBottom(rows: GuideRows): Dp = with(LocalDensity.current) {
    (guideTimelineRowHeight(rows) - guideCellTextHeight(rows)) / 2 + MaterialTheme.typography.titleSmall.lineHeight.toDp()
}

@Composable
internal fun guideRulerHeight(): Dp = with(LocalDensity.current) {
    MaterialTheme.typography.labelSmall.lineHeight.toDp() + RulerVerticalSpace
}

/** Without the headline's gap, keep the lifted pill below a scaled header button. */
@Composable
internal fun guideHeaderRulerClearance(): Dp = with(LocalDensity.current) {
    val pillHeight = MaterialTheme.typography.labelSmall.lineHeight.toDp() + RulerPillVerticalPadding * 2
    val overflow = maxOf(0.dp, RulerPillLift - (guideRulerHeight() - pillHeight) / 2)
    // ButtonDefaults.scale() in TV Material 1.1.0 uses 1.1 for focus (also RoundIconButton).
    // ButtonScale's fields are internal, so this mirrors that library default, not a custom scale.
    val focusGrowth = guideHeaderMinHeight() * ((1.1 - 1.0) / 2).toFloat()
    kotlin.math.ceil((overflow + focusGrowth).toPx()).toInt().toDp()
}

@Composable
internal fun TimelineTimeRuler(
    windowStartSec: Long,
    windowEndSec: Long,
    nowSecProvider: () -> Long,
    modifier: Modifier = Modifier,
    earlierContent: Boolean = false,
    laterContent: Boolean = false,
) {
    val nowSec = nowSecProvider()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(guideRulerHeight()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The channel column needs no heading: the space only aligns the ruler with the track.
        Spacer(Modifier.width(GuideChannelWidth + GuideChannelGap))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("epg-time-ruler"),
        ) {
            val density = LocalDensity.current
            val trackWidthPx = with(density) { maxWidth.toPx() }
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val firstTick = Math.floorDiv(windowStartSec + 1799L, 1800L) * 1800L
            val ticks = (firstTick until windowEndSec step 1800L).toList()
            val nowClock = if (nowSec in windowStartSec until windowEndSec) formatClock(nowSec) else null
            val boundaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTrackAlpha)
            val nowColor = MaterialTheme.colorScheme.primary
            val pillStyle = MaterialTheme.typography.labelSmall
            val textMeasurer = rememberTextMeasurer()
            val nowX = guideTimePositionPx(nowSec, windowStartSec, windowEndSec, constraints.maxWidth.toFloat()).roundToInt()
            // Where the pill will stand, known before layout, so the faded labels it touches can stay out.
            val pillSpan = nowClock?.let { clock ->
                val pillWidth = textMeasurer.measure(clock, pillStyle, maxLines = 1).size.width +
                    with(density) { (RulerPillPadding * 2).roundToPx() }
                val left = (nowX - pillWidth / 2).coerceIn(0, (constraints.maxWidth - pillWidth).coerceAtLeast(0))
                left until left + pillWidth
            }
            // Each half hour is a boundary line and its label.
            Layout(
                content = {
                    ticks.forEach { tick ->
                        Box(Modifier.background(boundaryColor).testTag("epg-ruler-boundary-$tick"))
                        Text(
                            text = formatHm(tick),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small).guideViewportFades(
                    0f, trackWidthPx, guideInnerContinuation(earlierContent, laterContent, isRtl), isRtl,
                ),
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val lines = ticks.indices.map { measurables[2 * it].measure(Constraints.fixed(1.dp.roundToPx(), height)) }
                val labels = ticks.indices.map { measurables[2 * it + 1].measure(Constraints(maxHeight = height)) }
                // Physical placement: chronology runs left to right in both layout directions.
                layout(width, height) {
                    ticks.forEachIndexed { index, tick ->
                        val x = guideTimePositionPx(tick, windowStartSec, windowEndSec, width.toFloat()).roundToInt()
                        lines[index].place(x, 0)
                        // The label starts at its half-hour's boundary line. One the pill would touch
                        // stays out rather than half covered.
                        val label = labels[index]
                        if (pillSpan == null || x > pillSpan.last || x + label.width <= pillSpan.first) {
                            label.place(x, (height - label.height) / 2)
                        }
                    }
                }
            }
            // Now stays solid over the inner fade: its pill sits a little above the labels and its
            // line starts at the pill's bottom edge, continuing into the grid.
            if (nowClock != null && pillSpan != null) {
                Layout(
                    content = {
                        Box(Modifier.background(nowColor).testTag("epg-now-stem"))
                        Box(
                            Modifier
                                .testTag("epg-now-pill")
                                .background(nowColor, CircleShape)
                                .padding(horizontal = RulerPillPadding, vertical = RulerPillVerticalPadding),
                        ) {
                            Text(
                                text = nowClock,
                                style = pillStyle,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { measurables, constraints ->
                    val height = constraints.maxHeight
                    val pill = measurables[1].measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = height))
                    // Let the lifted pill draw into the space above the band without clipping it.
                    val pillTop = (height - pill.height) / 2 - RulerPillLift.roundToPx()
                    val stemTop = pillTop + pill.height
                    val stem = measurables[0].measure(Constraints.fixed(2.dp.roundToPx(), (height - stemTop).coerceAtLeast(0)))
                    layout(constraints.maxWidth, height) {
                        stem.place(nowX - stem.width / 2, stemTop)
                        pill.place(pillSpan.first, pillTop)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TimelineChannelRow(
    channel: Channel,
    channelIndex: Int,
    number: Long?,
    selectedEventId: EventId?,
    eventFocusRequesters: MutableMap<EventId, FocusRequester>,
    windowStartSec: Long,
    windowEndSec: Long,
    nowSecProvider: () -> Long,
    events: List<EpgEventEntry>,
    hasCachedEvents: Boolean,
    connectionUiState: ConnectionUiState,
    coveragePending: Boolean,
    recordingForEvent: (EpgEventEntry) -> DvrEntry?,
    onFocused: (EpgEventEntry) -> Unit,
    onOpenDetails: (EpgEventEntry) -> Unit,
    onOpenContextMenu: ((EpgEventEntry) -> Unit)? = null,
    visibleRowWidthPx: Int? = null,
    focusStartInset: Dp = 0.dp,
    focusEndInset: Dp = 0.dp,
    earlierContent: Boolean = false,
    laterContent: Boolean = false,
    rows: GuideRows = GuideRows.REGULAR,
    genre: ProgrammeCategory = ProgrammeCategory.ALL,
    genreColors: Boolean = true,
    coverage: EpgCoverage? = null,
    clipSelectedToViewport: Boolean = false,
) {
    val nowSec = nowSecProvider()
    // One state for the row's cells outside the chosen genre.
    val outsideGenre = if (genre == ProgrammeCategory.ALL) null else {
        stringResource(R.string.epg_cell_not_genre, programmeCategoryLabel(genre))
    }
    // Share one zone lookup across the row's cells, refreshed on the existing clock.
    val owner = LocalTabOwner.current
    val active = owner?.isCurrent != false
    val formattingZone = remember(nowSec) { java.time.ZoneId.systemDefault() }
    if (BuildConfig.PROFILE_TRACE) ProfileCompositionLifetime("guideRow:$channelIndex")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(guideTimelineRowHeight(rows))
            .testTag("epg-channel-row-${channel.id.value}")
            .profileViewportItem { "guideRow:$channelIndex:$windowStartSec:${events.size}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimelineChannelHeader(
            channel = channel,
            number = number,
            selected = selectedEventId != null,
        )
        Spacer(Modifier.width(GuideChannelGap))
        // No track behind the cells: they sit on the page, one 4dp gutter apart, like the channel
        // tiles. Clipped at the faded inner edge, so a cell never paints past its fade into the channel gap.
        val layoutDirection = LocalLayoutDirection.current
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .guideFadedEdgeClip(
                    guideInnerContinuation(earlierContent, laterContent, layoutDirection == LayoutDirection.Rtl),
                    layoutDirection == LayoutDirection.Rtl,
                ),
        ) {
            val density = LocalDensity.current
            val visibleTrackWidthPx = visibleRowWidthPx?.minus(
                with(density) { GuideChannelWidth.roundToPx() + GuideChannelGap.roundToPx() },
            )
            // The window keeps the programme on air even where it runs past an edge.
            val onAirId by remember(events, nowSecProvider) {
                derivedStateOf {
                    val now = nowSecProvider()
                    events.firstOrNull { now >= it.start.epochSeconds && now < it.stop.epochSeconds }?.id
                }
            }
            events.forEach { event ->
                val span = timelineEventSpan(
                    eventStartSec = event.start.epochSeconds,
                    eventEndSec = event.stop.epochSeconds,
                    windowStartSec = windowStartSec,
                    windowEndSec = windowEndSec,
                ) ?: return@forEach
                val isFocusTarget = selectedEventId == event.id
                val fullStart = maxWidth * span.startFraction
                val fullEnd = maxWidth * span.endFraction
                val readableWidth = (maxWidth - focusStartInset - focusEndInset).coerceAtLeast(0.dp)
                // A changed inset can hide the old fragment before the focus effect repositions
                // its window. Keep a nonzero cell during that handoff instead of clipping it away.
                val clipFocus = isFocusTarget && fullEnd > focusStartInset && fullStart < maxWidth - focusEndInset &&
                    (clipSelectedToViewport || fullEnd - fullStart > readableWidth || event.id == onAirId)
                // Vertical navigation keeps the time window, even for a short off-air programme
                // crossing an edge. Its focused fragment stays clear of both reserves.
                val start = if (clipFocus) maxOf(fullStart, focusStartInset) else fullStart
                val end = if (clipFocus) minOf(fullEnd, maxWidth - focusEndInset) else fullEnd
                val width = (end - start).coerceAtLeast(0.dp)
                if (!shouldComposeTimelineCell(
                        // Shell clipping is logical-leading; the time axis is physical.
                        startPx = with(density) {
                            (if (layoutDirection == LayoutDirection.Rtl) maxWidth - end else start).roundToPx()
                        },
                        widthPx = with(density) { width.roundToPx() },
                        visibleWidthPx = visibleTrackWidthPx,
                        isFocusTarget = isFocusTarget,
                    )
                ) return@forEach
                key(event.id) {
                    val focusRequester = remember(event.id) { FocusRequester() }
                    DisposableEffect(event.id, focusRequester, eventFocusRequesters, active) {
                        // Synchronous input must only find requesters for committed cells.
                        if (active) eventFocusRequesters[event.id] = focusRequester
                        onDispose {
                            if (eventFocusRequesters[event.id] === focusRequester) {
                                eventFocusRequesters.remove(event.id)
                            }
                        }
                    }
                    TimelineProgrammeCell(
                        event = event,
                        channel = channel,
                        recording = recordingForEvent(event),
                        nowSec = nowSec,
                        formattingZone = formattingZone,
                        selected = isFocusTarget,
                        focusRequester = focusRequester,
                        onFocused = { onFocused(event) },
                        onOpenDetails = { onOpenDetails(event) },
                        onOpenContextMenu = onOpenContextMenu?.let { open -> { open(event) } },
                        rows = rows,
                        genre = genre,
                        genreColors = genreColors,
                        outsideGenre = outsideGenre,
                        modifier = Modifier
                            .align(AbsoluteAlignment.TopLeft)
                            .absoluteOffset(x = start)
                            .width(width)
                            .fillMaxHeight()
                            .profileViewportItem { "guideCell:$channelIndex:${event.id.value}" },
                    )
                }
            }

            if (events.isEmpty()) {
                val state = epgColumnDataState(
                    visibleEvents = events,
                    windowStartSec = windowStartSec,
                    windowEndSec = windowEndSec,
                    connectionState = connectionUiState,
                    coveragePending = coveragePending,
                    hasCachedEvents = hasCachedEvents,
                    windowCoverageProven = epgWindowCoverageProven(coverage, windowEndSec, nowSec),
                )
                if (state == EpgColumnDataState.LOADING) {
                    TimelineRowSkeleton(
                        windowStartSec = windowStartSec,
                        windowEndSec = windowEndSec,
                        trackWidth = maxWidth,
                        visibleTrackWidthPx = visibleTrackWidthPx,
                        rows = rows,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    TimelineRowState(state = state, modifier = Modifier.matchParentSize())
                }
            }

        }
    }
}

@Composable
internal fun TimelineChannelHeader(
    channel: Channel,
    number: Long?,
    selected: Boolean = false,
) {
    val currentSession = LocalArtworkContext.current.currentSession
    var logoFailed by remember(channel.icon, currentSession) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .width(GuideChannelWidth)
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                contentDescription = channelTitleText(number, channel.name.orEmpty())
            }
            .testTag("epg-channel-header-${channel.id.value}"),
        colors = SurfaceDefaults.colors(
            containerColor = if (selected) {
                TvSurfaceColors.containerHighest
            } else {
                TvSurfaceColors.container.copy(alpha = TvPanelDenseAlpha)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = TvSpacing12, vertical = GuideHeaderVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing8),
        ) {
            if (number != null) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (channel.icon != null && currentSession != null && !logoFailed) {
                PiconBox(
                    piconPath = channel.icon,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = GuideLogoWidth)
                        .height(GuideLogoHeight)
                        .testTag("epg-channel-picon-${channel.id.value}"),
                    onError = { logoFailed = true },
                )
            } else {
                Text(
                    text = channel.name.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A title-line mark after a gap, always at [StatusIconSize]: a cell too narrow for it leaves it
 * out rather than shrinking it beside full-size marks in wider cells.
 */
private fun Modifier.markAtFullSizeOrNone(): Modifier = layout { measurable, constraints ->
    val gap = TvSpacing4.roundToPx()
    val size = StatusIconSize.roundToPx()
    if (constraints.maxWidth < gap + size || constraints.maxHeight < size) {
        layout(0, 0) {}
    } else {
        val mark = measurable.measure(Constraints.fixed(size, size))
        layout(gap + size, size) { mark.placeRelative(gap, 0) }
    }
}

@Composable
internal fun TimelineProgrammeCell(
    event: EpgEventEntry,
    channel: Channel,
    recording: DvrEntry?,
    nowSec: Long,
    formattingZone: java.time.ZoneId,
    selected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier,
    onOpenContextMenu: (() -> Unit)? = null,
    rows: GuideRows = GuideRows.REGULAR,
    genre: ProgrammeCategory = ProgrammeCategory.ALL,
    genreColors: Boolean = true,
    /** The state a cell outside the chosen [genre] reads, such as "Not Sport". */
    outsideGenre: String? = null,
) {
    val contextMenu = at.bernhardberger.tvhplayer.ui.components.rememberBrowseContextMenuTrigger(onOpenContextMenu)
    val stateText = when {
        event.start.epochSeconds <= nowSec && nowSec < event.stop.epochSeconds ->
            stringResource(R.string.epg_state_now)
        event.start.epochSeconds > nowSec -> stringResource(R.string.epg_state_future)
        else -> stringResource(R.string.epg_state_past)
    }
    // Locale is observable; the row refreshes the shared zone on its normal clock.
    // No standalone time-zone listener or per-cell system lookup is needed here.
    val locale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val startSec = event.start.epochSeconds
    val stopSec = event.stop.epochSeconds
    val uses24Hour = AppClock.uses24Hour
    val startDate = remember(startSec, locale, formattingZone, uses24Hour) { startSec.formatDateTime(locale) }
    val startTime = remember(startSec, locale, formattingZone, uses24Hour) { formatHm(startSec) }
    val stopTime = remember(stopSec, locale, formattingZone, uses24Hour) { formatHm(stopSec) }
    var focused by remember { mutableStateOf(false) }
    val family = programmeCategory(event)
    // A chosen genre dims, never hides: other programmes stay plain and quiet until focused.
    val outside = genre != ProgrammeCategory.ALL && family != genre
    val colors = if (genreColors && !outside) tvGenreColors(family) else null
    val dimmed = outside && !focused
    val cellDescription = stringResource(
        R.string.epg_cell_description,
        channel.name.orEmpty(),
        startDate,
        stopTime,
        event.title.orEmpty(),
        stateText,
    )
    // A dimmed cell keeps its label and says why in its state; it stays enabled and reachable.
    val outsideState = outsideGenre?.takeIf { outside }
    val contentAlpha = if (dimmed) Modifier.alpha(GuideOutsideGenreAlpha) else Modifier

    val markState = recording?.state?.takeIf {
        recordingIsRunning(recording) || it == DvrEntryState.SCHEDULED || it == DvrEntryState.COMPLETED
    }
    // A failed or missed recording is the `error` "!" alone; one still running with errors adds it
    // beside its dot.
    val failed = when (recording?.state) {
        DvrEntryState.RECORDING_ERROR, DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING -> R.string.recording_state_failed
        DvrEntryState.MISSED -> R.string.recording_notice_missed
        else -> null
    }
    Box(modifier = modifier) {
        // Recordings carry their mark at the right end of the title line, as channel and Recordings
        // rows do: the title ellipsizes first and the time line keeps its width. Cancelled and invalid
        // ones carry none, which keeps the grid quiet.
        val headline: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = contentAlpha) {
                Text(
                    // Always render a label so no focusable cell is visually blank.
                    text = event.title.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Focus keeps the cell's dark fill, so both marks keep their unfocused colors.
                if (markState != null) {
                    RecordingStatusIndicator(
                        state = markState,
                        modifier = Modifier.markAtFullSizeOrNone().testTag("guide-cell-recording-mark"),
                    )
                }
                if (failed != null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_error_outlined),
                        // The dot of a running recording already names its errors.
                        contentDescription = stringResource(failed).takeIf { markState == null },
                        modifier = Modifier.markAtFullSizeOrNone().size(StatusIconSize).testTag("guide-cell-failed-mark"),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        // Dense rows leave the start time to the ruler and the headline; the description still reads it.
        val supporting: (@Composable () -> Unit)? = if (rows == GuideRows.DENSE) null else {
            { ProgrammeStartText(start = startTime, modifier = contentAlpha) }
        }
        val cellModifier = Modifier
            .tabFocus()
            .fillMaxSize()
            .padding(horizontal = TvSpacing4 / 2)
            // The cell's own fill, which focus keeps: the focused container is transparent.
            .background((colors?.tint ?: TvSurfaceColors.containerHigh).copy(alpha = TvPanelDenseAlpha), MaterialTheme.shapes.small)
            .focusRequester(focusRequester)
            .then(contextMenu.modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .semantics {
                contentDescription = cellDescription
                if (outsideState != null) stateDescription = outsideState
            }
        // Focus keeps the cell's fill and light content and adds a ring in the cell's shape. The cell
        // does not scale, so it never grows over its neighbours.
        val onCell = MaterialTheme.colorScheme.onSurface
        val itemColors = ListItemDefaults.colors(
            focusedContainerColor = Color.Transparent, focusedContentColor = onCell,
            pressedContainerColor = Color.Transparent, pressedContentColor = onCell,
            focusedSelectedContainerColor = Color.Transparent, focusedSelectedContentColor = onCell,
            pressedSelectedContainerColor = Color.Transparent, pressedSelectedContentColor = onCell,
        )
        // The library centers the stroke on the outline; the negative inset moves it inside the cell.
        val ring = Border(BorderStroke(GuideFocusRingWidth, onCell), inset = -GuideFocusRingWidth / 2,
            shape = MaterialTheme.shapes.small)
        val itemBorder = ListItemDefaults.border(
            focusedBorder = ring, pressedBorder = ring, focusedSelectedBorder = ring, pressedSelectedBorder = ring,
        )
        val itemShape = ListItemDefaults.shape(MaterialTheme.shapes.small)
        DenseListItem(
            selected = selected,
            onClick = onOpenDetails,
            onLongClick = contextMenu.onLongClick,
            headlineContent = headline,
            supportingContent = supporting,
            colors = itemColors,
            scale = GuideCellScale,
            border = itemBorder,
            shape = itemShape,
            modifier = cellModifier,
        )
    }
}

/** The now pill's side padding, and how far it sits above the labels' centre line. */
private val RulerPillPadding = 6.dp
private val RulerPillLift = 4.dp
private val RulerPillVerticalPadding = 1.dp
private val RulerVerticalSpace = 4.dp

/** A focused programme cell's ring. It draws inside the cell, which keeps its size on focus. */
private val GuideFocusRingWidth = 2.dp
private val GuideCellScale = ListItemDefaults.scale(
    focusedScale = 1f, pressedScale = 1f, focusedSelectedScale = 1f, pressedSelectedScale = 1f,
)

/** Programmes outside the chosen genre draw their content at half strength. */
private const val GuideOutsideGenreAlpha = 0.5f

/**
 * The programme's [start] alone, since the next programme's start is its end. It is left out only where
 * even that does not fit. Secondary, not tertiary, metadata: tertiary falls below 4.5:1 on the genre tints.
 */
@Composable
private fun ProgrammeStartText(start: String, modifier: Modifier = Modifier) {
    Layout(modifier = modifier, content = {
        // The cell's description reads the full range.
        Text(text = start, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall,
            color = TvTextColors.secondary, modifier = Modifier.testTag("guide-cell-time"))
    }) { measurables, constraints ->
        val time = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        if (time.width > constraints.maxWidth) layout(0, 0) {} else layout(time.width, time.height) { time.placeRelative(0, 0) }
    }
}

/**
 * A row whose programmes are still loading: passive cells in the grid's cell shape, one per hour
 * of the window, each with a title line and, in [GuideRows.REGULAR] rows, a time line. The row says it is loading.
 */
@Composable
private fun TimelineRowSkeleton(
    windowStartSec: Long,
    windowEndSec: Long,
    trackWidth: Dp,
    visibleTrackWidthPx: Int?,
    rows: GuideRows,
    modifier: Modifier,
) {
    val description = stringResource(R.string.epg_loading)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    Box(
        modifier
            .semantics { contentDescription = description }
            .testTag("guide-row-skeleton"),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            guideSkeletonSpans(windowStartSec, windowEndSec).forEachIndexed { index, span ->
                val start = trackWidth * span.startFraction
                val width = trackWidth * (span.endFraction - span.startFraction)
                if (!shouldComposeTimelineCell(
                        startPx = with(density) {
                            (if (layoutDirection == LayoutDirection.Rtl) trackWidth - start - width else start).roundToPx()
                        },
                        widthPx = with(density) { width.roundToPx() },
                        visibleWidthPx = visibleTrackWidthPx,
                        isFocusTarget = false,
                    )
                ) return@forEachIndexed
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(x = start)
                        .width(width)
                        .fillMaxHeight()
                        .padding(horizontal = TvSpacing4 / 2)
                        .background(TvSurfaceColors.containerHigh.copy(alpha = TvPanelDenseAlpha), MaterialTheme.shapes.small)
                        .padding(horizontal = TvSpacing12),
                ) {
                    SkeletonLine(
                        style = MaterialTheme.typography.titleSmall,
                        widthFraction = GuideSkeletonTitleFractions[index % GuideSkeletonTitleFractions.size],
                    )
                    if (rows == GuideRows.REGULAR) {
                        SkeletonLine(style = MaterialTheme.typography.labelSmall, widthFraction = GuideSkeletonTimeFraction)
                    }
                }
            }
        }
    }
}

/** The window split at its whole hours, so the placeholders stand where programmes often begin. */
private fun guideSkeletonSpans(windowStartSec: Long, windowEndSec: Long): List<TimelineEventSpan> {
    val duration = windowEndSec - windowStartSec
    if (duration <= 0L) return emptyList()
    val bounds = buildList {
        add(windowStartSec)
        var hour = (windowStartSec / GuideSkeletonBlockSec + 1) * GuideSkeletonBlockSec
        while (hour < windowEndSec) {
            add(hour)
            hour += GuideSkeletonBlockSec
        }
        add(windowEndSec)
    }
    return bounds.zipWithNext { start, end ->
        TimelineEventSpan(
            startFraction = (start - windowStartSec).toFloat() / duration,
            endFraction = (end - windowStartSec).toFloat() / duration,
        )
    }
}

private const val GuideSkeletonBlockSec = 3_600L
private val GuideSkeletonTitleFractions = floatArrayOf(0.7f, 0.5f, 0.6f)
private const val GuideSkeletonTimeFraction = 0.4f

/**
 * The whole Guide while it prepares its first frame: the header's empty row, the loading
 * headline below when shown, and [GuideSkeletonGrid] below, in the real screen's places.
 */
@Composable
internal fun GuidePreparationSkeleton(
    windowStartSec: Long,
    windowEndSec: Long,
    nowSecProvider: () -> Long,
    headline: (@Composable () -> Unit)?,
    contentPadding: PaddingValues,
    timelineContentPadding: PaddingValues,
    startPadding: Dp,
    endPadding: Dp,
    rows: GuideRows,
) {
    Column(Modifier.fillMaxSize()) {
        // The header draws no title, so its row stands empty until the real buttons arrive.
        Spacer(
            Modifier
                .padding(top = contentPadding.calculateTopPadding())
                .heightIn(min = guideHeaderMinHeight()),
        )
        headline?.invoke()
        if (headline == null) Spacer(Modifier.height(guideHeaderRulerClearance()))
        GuideSkeletonGrid(
            windowStartSec = windowStartSec,
            windowEndSec = windowEndSec,
            nowSecProvider = nowSecProvider,
            contentPadding = timelineContentPadding,
            rows = rows,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/**
 * The Guide before it has channels to show: the time ruler over as many whole rows of channel and
 * programme placeholders as fit, with the grid's [contentPadding]. It says once that it is loading
 * and takes no focus.
 */
@Composable
internal fun GuideSkeletonGrid(
    windowStartSec: Long,
    windowEndSec: Long,
    nowSecProvider: () -> Long,
    contentPadding: PaddingValues,
    rows: GuideRows,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.epg_loading)
    val layoutDirection = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(layoutDirection)
    val end = contentPadding.calculateEndPadding(layoutDirection)
    val rowHeight = guideTimelineRowHeight(rows)
    Column(
        modifier
            .testTag("guide-skeleton")
            .clearAndSetSemantics { contentDescription = description },
    ) {
        TimelineTimeRuler(
            windowStartSec = windowStartSec,
            windowEndSec = windowEndSec,
            nowSecProvider = nowSecProvider,
            modifier = Modifier.padding(start = start, end = end),
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = start, top = contentPadding.calculateTopPadding(), end = end),
        ) {
            val count = ((maxHeight + TvSpacing4) / (rowHeight + TvSpacing4)).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(TvSpacing4)) {
                repeat(count) {
                    Row(Modifier.fillMaxWidth().height(rowHeight)) {
                        Box(
                            Modifier
                                .width(GuideChannelWidth)
                                .fillMaxHeight()
                                .background(TvSurfaceColors.container.copy(alpha = TvPanelDenseAlpha), MaterialTheme.shapes.small),
                        )
                        Spacer(Modifier.width(GuideChannelGap))
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            TimelineRowSkeleton(
                                windowStartSec = windowStartSec,
                                windowEndSec = windowEndSec,
                                trackWidth = maxWidth,
                                visibleTrackWidthPx = null,
                                rows = rows,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A row's one-line reason for having no programmes, in a passive cell across the row. */
@Composable
private fun TimelineRowState(
    state: EpgColumnDataState,
    modifier: Modifier = Modifier,
) {
    val text = stringResource(
        when (state) {
            EpgColumnDataState.READY -> R.string.epg_state_ready
            EpgColumnDataState.LOADING -> R.string.epg_loading
            EpgColumnDataState.NO_DATA -> R.string.epg_no_data
            EpgColumnDataState.EMPTY_DAY -> R.string.epg_empty_day
            EpgColumnDataState.NOT_LOADED -> R.string.epg_not_loaded
            EpgColumnDataState.PARTIAL -> R.string.epg_partial
            EpgColumnDataState.STALE -> R.string.epg_stale
            EpgColumnDataState.PERMISSION_DENIED -> R.string.epg_permission_denied
            EpgColumnDataState.RECONNECTING -> R.string.epg_reconnecting
            EpgColumnDataState.SERVER_FAILURE -> R.string.epg_server_failure
        }
    )
    Box(
        modifier = modifier
            .padding(horizontal = TvSpacing4 / 2)
            .background(TvSurfaceColors.containerHigh.copy(alpha = TvPanelDenseAlpha), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = TvSpacing16),
        )
    }
}

@Composable
internal fun GuidePassiveNotice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = SurfaceDefaults.colors(
            containerColor = TvSurfaceColors.containerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
internal fun GuideEmptyState(
    alert: AppError?,
    isEmptyTag: Boolean,
    connectionUiState: ConnectionUiState,
    channelCatalogCurrent: Boolean,
    onAction: (AppErrorAction) -> Unit,
    actionFocus: FocusRequester,
    leadingExit: FocusRequester,
    upExit: FocusRequester,
) {
    if (alert != null) {
        AppErrorAlert(
            error = alert,
            onAction = onAction,
            modifier = Modifier.fillMaxSize().testTag("guide-empty-state"),
            actionFocus = actionFocus,
            leadingExit = leadingExit,
            upExit = upExit,
        )
        return
    }
    val message = stringResource(
        guideEmptyMessageRes(
            isEmptyTag = isEmptyTag,
            connectionUiState = connectionUiState,
            channelCatalogCurrent = channelCatalogCurrent,
        ),
    )
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("guide-empty-state"),
        colors = SurfaceDefaults.colors(
            containerColor = TvSurfaceColors.container.copy(alpha = TvPanelDenseAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, style = MaterialTheme.typography.titleLarge)
        }
    }
}
