package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.GuideWindowBounds
import at.bernhardberger.tvhplayer.core.floorGuideWindowToHour
import at.bernhardberger.tvhplayer.core.moveGuideWindowByDays
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelList
import at.bernhardberger.tvhplayer.ui.screens.BrowseSidePanel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The Guide's day and time: Now, then the days the window reaches and the hours of the checked
 * day. A day applies at once and keeps the panel open, like View options; Now and an hour jump
 * and close, and Back closes what was applied.
 */
@Composable
internal fun GuideTimePanel(
    windowStartSec: Long,
    bounds: GuideWindowBounds,
    zoneId: ZoneId,
    nowSecProvider: () -> Long,
    onDay: (Long) -> Unit,
    onHour: (Long) -> Unit,
    onNow: () -> Unit,
    onClose: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val today by remember(nowSecProvider, zoneId) {
        derivedStateOf { Instant.ofEpochSecond(nowSecProvider()).atZone(zoneId).toLocalDate() }
    }
    val windowDay = Instant.ofEpochSecond(windowStartSec).atZone(zoneId).toLocalDate()
    val firstDay = Instant.ofEpochSecond(bounds.earliestStartSec).atZone(zoneId).toLocalDate()
    val lastDay = Instant.ofEpochSecond(bounds.latestStartSec).atZone(zoneId).toLocalDate()
    val days = generateSequence(firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.toList()
    val hours = guideTimeHours(windowStartSec, bounds, zoneId)
    val entries = buildList {
        add(BrowsePanelEntry.Choice(
            key = "guide-time-now",
            label = stringResource(R.string.now),
            selected = false,
            onClick = onNow,
        ))
        add(BrowsePanelEntry.Divider)
        add(BrowsePanelEntry.Section(stringResource(R.string.guide_time_day)))
        days.forEach { day ->
            add(BrowsePanelEntry.Choice(
                key = "guide-time-day-${ChronoUnit.DAYS.between(today, day)}",
                label = guideDayLabel(day, today, zoneId, locale),
                selected = day == windowDay,
                onClick = {
                    if (day != windowDay) {
                        val delta = ChronoUnit.DAYS.between(windowDay, day).toInt()
                        onDay(floorGuideWindowToHour(moveGuideWindowByDays(windowStartSec, delta, bounds, zoneId), zoneId))
                    }
                },
            ))
        }
        if (hours.isNotEmpty()) add(BrowsePanelEntry.Section(stringResource(R.string.guide_time_hour)))
        hours.forEach { hour ->
            add(BrowsePanelEntry.Choice(
                key = "guide-time-hour-${hour.start.hour.toString().padStart(2, '0')}",
                label = formatClock(hour.start, locale),
                selected = hour.selected,
                onClick = { onHour(hour.start.toEpochSecond()) },
            ))
        }
    }
    BrowseSidePanel(stringResource(R.string.guide_time_title), "guide-time-panel", onClose) {
        BrowsePanelList(entries, radio = false)
    }
}

internal data class GuideTimeHour(val start: ZonedDateTime, val selected: Boolean)

/**
 * The local hours of the window's day whose window can start, the window's own hour checked.
 * An hour a clock change skips is left out, or starts where its remainder begins; a repeated
 * autumn hour is listed once, in the occurrence the window is in.
 */
internal fun guideTimeHours(windowStartSec: Long, bounds: GuideWindowBounds, zoneId: ZoneId): List<GuideTimeHour> {
    val window = Instant.ofEpochSecond(windowStartSec).atZone(zoneId)
    return (0..23).mapNotNull { hour ->
        val local = window.toLocalDate().atTime(hour, 0)
        val occurrences = zoneId.rules.getValidOffsets(local).map { ZonedDateTime.ofLocal(local, zoneId, it) }
            .ifEmpty { listOf(ZonedDateTime.ofLocal(local, zoneId, null)).filter { it.hour == hour } }
            .filter { it.toEpochSecond() in bounds.earliestStartSec..bounds.latestStartSec }
        val start = occurrences.firstOrNull { hour == window.hour && it.offset == window.offset }
            ?: occurrences.firstOrNull()
        start?.let { GuideTimeHour(it, hour == window.hour) }
    }
}
