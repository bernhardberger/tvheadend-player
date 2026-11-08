package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.annotation.StringRes
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
import at.bernhardberger.tvhplayer.ui.screens.BrowseLevelsPanel
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelLevel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The Guide's day and time: Now and the days the window reaches on the root, each day opening its
 * times as a level on Right. OK on Now, a day or a time jumps and closes; Back in the times returns
 * to their day.
 */
@Composable
internal fun GuideTimePanel(
    windowStartSec: Long,
    windowEndSec: Long,
    bounds: GuideWindowBounds,
    zoneId: ZoneId,
    nowSecProvider: () -> Long,
    onJump: (Long) -> Unit,
    onNow: () -> Unit,
    onClose: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val nowMinuteSec by remember(nowSecProvider) { derivedStateOf { nowSecProvider() / 60 * 60 } }
    // The days and which times have ended stay as the panel opened, so a period ending, or
    // midnight, never disables or moves the row that has focus. Only Now's clock runs on.
    val openedNowSec = remember { nowSecProvider() }
    val openedSec = openedNowSec / 60 * 60
    val panelBounds = remember { bounds }
    val today = Instant.ofEpochSecond(openedSec).atZone(zoneId).toLocalDate()
    // The time the date button names: now while the window shows it, else the window's start.
    val nowShown = guideWindowShowsNow(openedNowSec, windowStartSec, windowEndSec)
    val shownSec = if (nowShown) openedSec else windowStartSec
    val windowDay = Instant.ofEpochSecond(shownSec).atZone(zoneId).toLocalDate()
    val firstDay = Instant.ofEpochSecond(panelBounds.earliestStartSec).atZone(zoneId).toLocalDate()
    val lastDay = Instant.ofEpochSecond(panelBounds.latestStartSec).atZone(zoneId).toLocalDate()
    val days = generateSequence(firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.toList()

    val entries = buildList {
        add(BrowsePanelEntry.Choice(
            key = "guide-time-now",
            label = stringResource(R.string.now),
            selected = false,
            onClick = onNow,
            trailing = formatClock(Instant.ofEpochSecond(nowMinuteSec).atZone(zoneId), locale),
        ))
        add(BrowsePanelEntry.Divider)
        days.forEach { day ->
            val dayLabel = guideDayLabel(day, today, zoneId, locale)
            val presets = guideTimePresets(day, shownSec, openedSec, panelBounds, zoneId, nowShown)
            val entry = guideTimePresetEntry(presets, shownSec, zoneId)
            add(BrowsePanelEntry.Choice(
                key = "guide-time-day-${ChronoUnit.DAYS.between(today, day)}",
                label = dayLabel,
                selected = day == windowDay,
                // Right opens the day's times; a day with none left to jump to opens nothing.
                level = entry?.let {
                    BrowsePanelLevel(
                        title = dayLabel,
                        entries = guideTimeEntries(presets, zoneId, locale, onJump),
                        radio = false,
                        focusKey = guideTimeEntryKey(presets[it].period),
                    )
                },
                levelOnOk = false,
                onClick = {
                    if (day == windowDay) onClose() else {
                        val delta = ChronoUnit.DAYS.between(windowDay, day).toInt()
                        onJump(floorGuideWindowToHour(moveGuideWindowByDays(shownSec, delta, panelBounds, zoneId), zoneId))
                    }
                },
            ))
        }
    }
    BrowseLevelsPanel(
        title = stringResource(R.string.guide_time_title),
        panelTag = "guide-time-panel",
        onClose = onClose,
        entries = entries,
        focusKey = "guide-time-day-${ChronoUnit.DAYS.between(today, windowDay)}",
    )
}

@Composable
private fun guideTimeEntries(
    presets: List<GuideTimePreset>,
    zoneId: ZoneId,
    locale: Locale,
    onJump: (Long) -> Unit,
): List<BrowsePanelEntry> = presets.map { preset ->
    BrowsePanelEntry.Choice(
        key = guideTimeEntryKey(preset.period),
        label = stringResource(preset.period.label),
        leading = formatClock(Instant.ofEpochSecond(preset.startSec).atZone(zoneId), locale),
        selected = preset.checked,
        enabled = preset.enabled,
        onClick = { onJump(preset.startSec) },
    )
}

private fun guideTimeEntryKey(period: GuideTimePeriod) = "guide-time-preset-${period.hour.toString().padStart(2, '0')}"

/** The fixed times every day offers; each period runs until the next, the last until midnight. */
internal enum class GuideTimePeriod(val hour: Int, @StringRes val label: Int) {
    MORNING(6, R.string.guide_time_morning),
    MIDDAY(12, R.string.guide_time_midday),
    AFTERNOON(15, R.string.guide_time_afternoon),
    EVENING(18, R.string.guide_time_evening),
    PRIME_TIME(20, R.string.guide_time_prime_time),
    LATE_NIGHT(22, R.string.guide_time_late_night),
}

internal data class GuideTimePreset(
    val period: GuideTimePeriod,
    val startSec: Long,
    val enabled: Boolean,
    val checked: Boolean,
)

/**
 * The periods of [day]: a period whose whole span has ended by [nowSec], or that starts after the
 * last window start, is disabled; one still running before the earliest bound stays enabled, its
 * jump constrained. A period is checked only when the window starts exactly at it, or, while the
 * window shows now ([nowShown], [windowStartSec] then being now), when it holds now.
 */
internal fun guideTimePresets(
    day: LocalDate,
    windowStartSec: Long,
    nowSec: Long,
    bounds: GuideWindowBounds,
    zoneId: ZoneId,
    nowShown: Boolean = false,
): List<GuideTimePreset> {
    val starts = GuideTimePeriod.entries.map { ZonedDateTime.ofLocal(day.atTime(it.hour, 0), zoneId, null).toEpochSecond() }
    val ends = starts.drop(1) + day.plusDays(1).atStartOfDay(zoneId).toEpochSecond()
    return GuideTimePeriod.entries.mapIndexed { index, period ->
        GuideTimePreset(
            period = period,
            startSec = starts[index],
            enabled = ends[index] > nowSec && starts[index] <= bounds.latestStartSec,
            checked = windowStartSec == starts[index] || nowShown && windowStartSec >= starts[index] && windowStartSec < ends[index],
        )
    }
}

/**
 * The preset Right enters at: the period holding the window's time of day (Morning before 06:00),
 * else the next enabled one, else the last enabled one before it; null when none is enabled.
 */
internal fun guideTimePresetEntry(presets: List<GuideTimePreset>, windowStartSec: Long, zoneId: ZoneId): Int? {
    val time = Instant.ofEpochSecond(windowStartSec).atZone(zoneId).toLocalTime()
    val containing = presets.indexOfLast { LocalTime.of(it.period.hour, 0) <= time }.coerceAtLeast(0)
    return (containing..presets.lastIndex).firstOrNull { presets[it].enabled }
        ?: (containing downTo 0).firstOrNull { presets[it].enabled }
}
