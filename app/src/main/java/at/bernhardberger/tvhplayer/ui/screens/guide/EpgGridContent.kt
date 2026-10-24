package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent

import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.profiling.ProfileCompositionLifetime
import at.bernhardberger.tvhplayer.profiling.profileViewportItem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
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
import at.bernhardberger.tvhplayer.ui.TvSpacing24
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.components.LocalTabOwner
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.components.LocalArtworkContext
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.StatusIconSize
import at.bernhardberger.tvhplayer.ui.screens.formatDateTime
import at.bernhardberger.tvhplayer.ui.screens.guideEmptyMessageRes

private fun GuideRows.logoWidth(): Dp = if (this == GuideRows.DENSE) 48.dp else 60.dp
private fun GuideRows.logoHeight(): Dp = if (this == GuideRows.DENSE) 28.dp else 36.dp
private fun GuideRows.headerHorizontalPadding(): Dp = if (this == GuideRows.DENSE) TvSpacing12 else TvSpacing16
private fun GuideRows.headerVerticalPadding(): Dp = if (this == GuideRows.DENSE) 4.dp else 6.dp

/**
 * A Guide row's height: Material for TV's two-line list item minimum (dense 56dp, regular 64dp)
 * at the current font scale, grown to fit the channel header and one title and time line with
 * the list item's vertical content padding (dense 10dp, regular 12dp on each side).
 */
@Composable
internal fun guideTimelineRowHeight(rows: GuideRows = GuideRows.DENSE): Dp = with(LocalDensity.current) {
    val titleLine = MaterialTheme.typography.titleSmall.lineHeight.toDp()
    val numberLine = MaterialTheme.typography.labelMedium.lineHeight.toDp()
    val headerContent = maxOf(
        rows.logoHeight(),
        numberLine,
        titleLine,
    ) + rows.headerVerticalPadding() * 2
    // One programme title and time line, including native ListItem content padding;
    // headers either show number/logo or number/name, never all three.
    val dense = rows == GuideRows.DENSE
    maxOf(
        (if (dense) 56.dp else 64.dp) * fontScale,
        headerContent,
        titleLine + MaterialTheme.typography.bodySmall.lineHeight.toDp() + if (dense) 20.dp else TvSpacing24,
    )
}

@Composable
internal fun TimelineTimeRuler(
    windowStartSec: Long,
    windowEndSec: Long,
    nowSecProvider: () -> Long,
    modifier: Modifier = Modifier,
    earlierContent: Boolean = false,
    laterContent: Boolean = false,
    rows: GuideRows = GuideRows.DENSE,
) {
    val nowSec = nowSecProvider()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) {
                maxOf(28.dp, MaterialTheme.typography.labelMedium.lineHeight.toDp() + 12.dp)
            }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .width(guideChannelWidth(rows))
                .fillMaxHeight(),
            shape = MaterialTheme.shapes.small,
            colors = SurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                Text(
                    text = stringResource(R.string.epg_channels_heading),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = rows.headerHorizontalPadding()),
                )
            }
        }
        Spacer(Modifier.width(GuideChannelGap))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .testTag("epg-time-ruler"),
        ) {
            val density = LocalDensity.current
            val trackWidthPx = with(density) { maxWidth.toPx() }
            Box(Modifier.fillMaxSize().guideViewportFades(0f, trackWidthPx, earlierContent, laterContent)) {
            val firstTick = Math.floorDiv(windowStartSec + 1799L, 1800L) * 1800L
            for (tick in firstTick until windowEndSec step 1800L) {
                val markerOffset = with(density) {
                    guideTimePositionPx(tick, windowStartSec, windowEndSec, trackWidthPx).toDp()
                }
                // The label starts at its half-hour's boundary line, which runs the band's height
                // where that time's programmes start; centred, it shares the heading's baseline.
                Box(
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(x = markerOffset)
                        .fillMaxHeight(),
                    contentAlignment = AbsoluteAlignment.CenterLeft,
                ) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTrackAlpha)
                            )
                            .testTag("epg-ruler-boundary-$tick")
                    )
                    Text(
                        text = formatHm(tick),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                    )
                }
            }
            if (nowSec in windowStartSec until windowEndSec) {
                val nowOffset = with(density) {
                    guideTimePositionPx(nowSec, windowStartSec, windowEndSec, trackWidthPx).toDp()
                }
                Box(
                    modifier = Modifier
                        .align(AbsoluteAlignment.BottomLeft)
                        .absoluteOffset(x = nowOffset - 4.dp)
                        .width(8.dp)
                        .height(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                )
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
    rows: GuideRows = GuideRows.DENSE,
    genre: ProgrammeCategory = ProgrammeCategory.ALL,
    genreColors: Boolean = true,
    coverage: EpgCoverage? = null,
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
            rows = rows,
        )
        Spacer(Modifier.width(GuideChannelGap))
        // No track behind the cells: they sit on the page, one 4dp gutter apart, like the channel
        // tiles. Not clipped either: a clip would cut the focused first or last cell's library scale.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current
            val visibleTrackWidthPx = visibleRowWidthPx?.minus(
                with(density) { guideChannelWidth(rows).roundToPx() + GuideChannelGap.roundToPx() },
            )
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
                val oversized = isFocusTarget && fullEnd - fullStart > readableWidth
                // Only oversized programmes use a readable fragment. Normal cells retain
                // their full time allocation; the shared terminal viewport reserves scale.
                val start = if (oversized) maxOf(fullStart, focusStartInset) else fullStart
                val end = if (oversized) minOf(fullEnd, maxWidth - focusEndInset) else fullEnd
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
                        width = width,
                        rows = rows,
                        genre = genre,
                        genreColors = genreColors,
                        outsideGenre = outsideGenre,
                        modifier = Modifier
                            .align(AbsoluteAlignment.TopLeft)
                            // The 4dp gap is narrower than the focus scale: the focused
                            // cell draws above its later neighbour.
                            .zIndex(if (isFocusTarget) 1f else 0f)
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
    rows: GuideRows = GuideRows.DENSE,
) {
    val currentSession = LocalArtworkContext.current.currentSession
    var logoFailed by remember(channel.icon, currentSession) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .width(guideChannelWidth(rows))
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
                .padding(horizontal = rows.headerHorizontalPadding(), vertical = rows.headerVerticalPadding()),
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
                        .widthIn(max = rows.logoWidth())
                        .height(rows.logoHeight())
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
    width: Dp,
    modifier: Modifier,
    onOpenContextMenu: (() -> Unit)? = null,
    rows: GuideRows = GuideRows.DENSE,
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
    val timeRange = remember(startSec, stopSec, locale, formattingZone, uses24Hour) {
        formatTimeInterval(startSec, stopSec, locale = locale, uses24Hour = uses24Hour)
    }
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
                if (markState != null) {
                    // The mark follows the cell's content colour, which inverts on focus.
                    RecordingStatusIndicator(
                        state = markState,
                        focused = focused,
                        modifier = Modifier.markAtFullSizeOrNone().testTag("guide-cell-recording-mark"),
                    )
                }
                if (failed != null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_error_outlined),
                        // The dot of a running recording already names its errors.
                        contentDescription = stringResource(failed).takeIf { markState == null },
                        modifier = Modifier.markAtFullSizeOrNone().size(StatusIconSize).testTag("guide-cell-failed-mark"),
                        tint = if (focused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        val supporting: (@Composable () -> Unit)? = if (width >= 90.dp) {
            { ProgrammeTimeText(range = timeRange, start = startTime, modifier = contentAlpha) }
        } else {
            null
        }
        val cellModifier = Modifier
            .tabFocus()
            .fillMaxSize()
            .padding(horizontal = TvSpacing4 / 2)
            // The panel shape is drawn, not clipped: a clip here would cancel the
            // library focus scale the surface applies outside this modifier.
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
        // The family's wash replaces the light focused container; the content keeps its focused colors.
        val itemColors = colors?.let {
            val onWash = MaterialTheme.colorScheme.inverseOnSurface
            ListItemDefaults.colors(
                focusedContainerColor = it.wash, focusedContentColor = onWash,
                pressedContainerColor = it.wash, pressedContentColor = onWash,
                focusedSelectedContainerColor = it.wash, focusedSelectedContentColor = onWash,
                pressedSelectedContainerColor = it.wash, pressedSelectedContentColor = onWash,
            )
        } ?: ListItemDefaults.colors()
        when (rows) {
            GuideRows.DENSE -> DenseListItem(
                selected = selected,
                onClick = onOpenDetails,
                onLongClick = contextMenu.onLongClick,
                headlineContent = headline,
                supportingContent = supporting,
                colors = itemColors,
                modifier = cellModifier,
            )
            GuideRows.REGULAR -> ListItem(
                selected = selected,
                onClick = onOpenDetails,
                onLongClick = contextMenu.onLongClick,
                headlineContent = headline,
                supportingContent = supporting,
                colors = itemColors,
                modifier = cellModifier,
            )
        }
    }
}

/** Programmes outside the chosen genre draw their content at half strength. */
private const val GuideOutsideGenreAlpha = 0.5f

/** The programme's [range] when it fits the cell on one line, else its [start] alone: a 12-hour range runs wider. */
@Composable
private fun ProgrammeTimeText(range: String, start: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodySmall
    Layout(modifier = modifier, content = {
        Text(text = range, maxLines = 1, softWrap = false, style = style)
        // The cell's description already reads the full range.
        Text(text = start, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style,
            modifier = Modifier.clearAndSetSemantics {})
    }) { measurables, constraints ->
        val full = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val shown = full.takeIf { it.width <= constraints.maxWidth } ?: measurables[1].measure(constraints.copy(minWidth = 0))
        layout(shown.width, shown.height) { shown.placeRelative(0, 0) }
    }
}

/**
 * A row whose programmes are still loading: passive cells in the grid's cell shape, one per hour
 * of the window, each with a title and a time line. The row says it is loading.
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
    val horizontalPadding = if (rows == GuideRows.DENSE) TvSpacing12 else TvSpacing16
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
                        .padding(horizontal = horizontalPadding),
                ) {
                    SkeletonLine(
                        style = MaterialTheme.typography.titleSmall,
                        widthFraction = GuideSkeletonTitleFractions[index % GuideSkeletonTitleFractions.size],
                    )
                    // As in programme cells, a narrow cell has no time line.
                    if (width >= 90.dp) {
                        SkeletonLine(style = MaterialTheme.typography.bodySmall, widthFraction = GuideSkeletonTimeFraction)
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
