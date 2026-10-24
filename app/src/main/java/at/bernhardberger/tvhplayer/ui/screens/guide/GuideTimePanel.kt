package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.GuideWindowBounds
import at.bernhardberger.tvhplayer.core.floorGuideWindowToHour
import at.bernhardberger.tvhplayer.core.moveGuideWindowByDays
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.player.PlaybackPanelPadding
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelList
import at.bernhardberger.tvhplayer.ui.screens.BrowseSidePanel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The Guide's day and time: Now and the days the window reaches on the left, the focused day's
 * times on the right, which follow the days without taking focus. Right enters a day's times,
 * Left or Back returns to the day; Now, a day or a time jumps and closes.
 */
@Composable
internal fun GuideTimePanel(
    windowStartSec: Long,
    bounds: GuideWindowBounds,
    zoneId: ZoneId,
    nowSecProvider: () -> Long,
    onJump: (Long) -> Unit,
    onNow: () -> Unit,
    onClose: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val nowMinuteSec by remember(nowSecProvider) { derivedStateOf { nowSecProvider() / 60 * 60 } }
    val today = Instant.ofEpochSecond(nowMinuteSec).atZone(zoneId).toLocalDate()
    val windowDay = Instant.ofEpochSecond(windowStartSec).atZone(zoneId).toLocalDate()
    val firstDay = Instant.ofEpochSecond(bounds.earliestStartSec).atZone(zoneId).toLocalDate()
    val lastDay = Instant.ofEpochSecond(bounds.latestStartSec).atZone(zoneId).toLocalDate()
    val days = generateSequence(firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.toList()
    val dayFocus = remember(days) { days.associateWith { FocusRequester() } }
    val presetFocus = remember { GuideTimePeriod.entries.associateWith { FocusRequester() } }
    // The day whose times show: the focused day, today while Now has focus, and while the times
    // have focus, the day they were entered from.
    var timesDay by remember { mutableStateOf(windowDay) }
    var nowFocused by remember { mutableStateOf(false) }
    var inTimes by remember { mutableStateOf(false) }
    val presets = guideTimePresets(timesDay, windowStartSec, nowMinuteSec, bounds, zoneId)

    fun enterTimes() {
        val entered = guideTimePresets(timesDay, windowStartSec, nowMinuteSec, bounds, zoneId)
        val index = guideTimePresetEntry(entered, windowStartSec, zoneId) ?: return
        runCatching { presetFocus.getValue(entered[index].period).requestFocus() }
    }
    fun backToDay() {
        runCatching { dayFocus[timesDay]?.requestFocus() }
    }

    val dayEntries = buildList {
        add(BrowsePanelEntry.Choice(
            key = "guide-time-now",
            label = stringResource(R.string.now),
            selected = false,
            onClick = onNow,
            trailing = formatClock(Instant.ofEpochSecond(nowMinuteSec).atZone(zoneId), locale),
            modifier = Modifier.onFocusChanged {
                nowFocused = it.isFocused
                if (it.isFocused) timesDay = today
            },
        ))
        add(BrowsePanelEntry.Divider)
        days.forEach { day ->
            // The day whose times have focus keeps a selected container, its chevron pointing at them.
            val open = inTimes && day == timesDay
            add(BrowsePanelEntry.Choice(
                key = "guide-time-day-${ChronoUnit.DAYS.between(today, day)}",
                label = guideDayLabel(day, today, zoneId, locale),
                selected = day == windowDay || open,
                opensLevel = open,
                onClick = {
                    if (day == windowDay) onClose() else {
                        val delta = ChronoUnit.DAYS.between(windowDay, day).toInt()
                        onJump(floorGuideWindowToHour(moveGuideWindowByDays(windowStartSec, delta, bounds, zoneId), zoneId))
                    }
                },
                modifier = Modifier.focusRequester(dayFocus.getValue(day))
                    .onFocusChanged { if (it.isFocused) timesDay = day },
            ))
        }
    }
    val presetEntries = presets.map { preset ->
        BrowsePanelEntry.Choice(
            key = "guide-time-preset-${preset.period.hour.toString().padStart(2, '0')}",
            label = stringResource(preset.period.label),
            leading = formatClock(Instant.ofEpochSecond(preset.startSec).atZone(zoneId), locale),
            selected = preset.checked,
            enabled = preset.enabled,
            onClick = { onJump(preset.startSec) },
            modifier = Modifier.focusRequester(presetFocus.getValue(preset.period)),
        )
    }
    BrowseSidePanel(
        title = stringResource(R.string.guide_time_title),
        panelTag = "guide-time-panel",
        // Back in the times returns to their day; only Back on the days closes the panel.
        onClose = { if (inTimes) backToDay() else onClose() },
        width = GuideTimePanelWidth,
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionRight -> {
                        if (event.isFirstDown() && !nowFocused) enterTimes()
                        true
                    }
                    Key.DirectionLeft -> true
                    else -> false
                }
            }) {
                BrowsePanelList(dayEntries, radio = false, focusKey = "guide-time-day-${ChronoUnit.DAYS.between(today, windowDay)}")
            }
            Box(Modifier.padding(vertical = PlaybackPanelPadding).width(1.dp).fillMaxHeight()
                .background(MaterialTheme.colorScheme.borderVariant))
            Column(Modifier.weight(1f).testTag("guide-time-presets")
                .onFocusChanged { inTimes = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (event.isFirstDown()) backToDay()
                            true
                        }
                        Key.DirectionRight -> true
                        else -> false
                    }
                }) {
                // Level with Now and on the rows' text column, so the day reads as the heading of its times.
                Box(Modifier.fillMaxWidth().padding(start = PlaybackPanelPadding + 16.dp, end = PlaybackPanelPadding, top = 20.dp)
                    .heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        guideDayLabel(timesDay, today, zoneId, locale),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                BrowsePanelList(presetEntries, radio = false, takeFocus = false)
            }
        }
    }
}

private fun KeyEvent.isFirstDown() = type == KeyEventType.KeyDown && nativeKeyEvent.repeatCount == 0

/** Two panes of about 212dp each beside a divider. */
private val GuideTimePanelWidth = 464.dp

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
 * jump constrained. A period is checked only when the window starts exactly at it.
 */
internal fun guideTimePresets(
    day: LocalDate,
    windowStartSec: Long,
    nowSec: Long,
    bounds: GuideWindowBounds,
    zoneId: ZoneId,
): List<GuideTimePreset> {
    val starts = GuideTimePeriod.entries.map { ZonedDateTime.ofLocal(day.atTime(it.hour, 0), zoneId, null).toEpochSecond() }
    val ends = starts.drop(1) + day.plusDays(1).atStartOfDay(zoneId).toEpochSecond()
    return GuideTimePeriod.entries.mapIndexed { index, period ->
        GuideTimePreset(
            period = period,
            startSec = starts[index],
            enabled = ends[index] > nowSec && starts[index] <= bounds.latestStartSec,
            checked = windowStartSec == starts[index],
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
