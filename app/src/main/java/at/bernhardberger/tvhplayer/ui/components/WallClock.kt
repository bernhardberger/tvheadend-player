package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.common.clockWidthSamples
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/** The space between a header's last action and the wall clock. */
val WallClockGap = 24.dp

/** The test tag of a browse header's wall clock. */
internal const val WALL_CLOCK_TAG = "wall-clock"

/** From the time's baseline to the date's: the date sits close under the time. */
private val WallClockBaselineStep = 16.sp

/**
 * The passive wall clock of the browse headers and the player header: the time over the date,
 * end-aligned in a width reserved for the widest time and date of the locale, so its neighbours
 * never move when the minute, hour or date changes. It stands in one header band
 * ([TvBrowseHeaderHeight]) without growing it: the block is centred on the band and overflows it,
 * so it stands at the same place on every screen. It takes no focus and reads as one statement.
 * [nowSec] is read once a minute.
 */
@Composable
fun WallClock(nowSec: () -> Long, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val density = LocalDensity.current
    val minuteSec by remember(nowSec) { derivedStateOf { Math.floorDiv(nowSec(), 60L) * 60L } }
    val uses24Hour = AppClock.uses24Hour
    val timeStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium)
    val dateStyle = MaterialTheme.typography.bodyMedium
    // In 12-hour time the day period is one type step smaller and quieter; the digits keep the size.
    val meridiem = SpanStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize, color = TvTextColors.secondary)
    val markers = remember(locale) { meridiemMarkers(locale) }
    val timeText = formatClock(Instant.ofEpochSecond(minuteSec).atZone(ZoneId.systemDefault()), locale, uses24Hour)
    val date = formatRecordingDate(minuteSec, dateOnly = true, locale = locale, nowSec = minuteSec)
    val measurer = rememberTextMeasurer()
    val widthPx = remember(locale, uses24Hour, timeStyle, dateStyle, meridiem, density, measurer) {
        val times = clockWidthSamples(locale, uses24Hour).map {
            measurer.measure(styledClock(it, markers, meridiem), timeStyle, maxLines = 1, softWrap = false).size.width
        }
        val dates = wallClockDateSamples().map {
            measurer.measure(formatRecordingDate(it, dateOnly = true, locale = locale, nowSec = it), dateStyle,
                maxLines = 1, softWrap = false).size.width
        }
        ceil((times + dates).max().toDouble()).toInt()
    }
    val description = "$timeText, $date"
    Layout(
        content = {
            Text(styledClock(timeText, markers, meridiem), style = timeStyle, color = TvTextColors.primary,
                maxLines = 1, softWrap = false, textAlign = TextAlign.End)
            Text(date, style = dateStyle, color = TvTextColors.secondary,
                maxLines = 1, softWrap = false, textAlign = TextAlign.End)
        },
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) { measurables, constraints ->
        val lineConstraints = Constraints.fixedWidth(widthPx)
        val time = measurables[0].measure(lineConstraints)
        val day = measurables[1].measure(lineConstraints)
        val dateTop = time[FirstBaseline] + WallClockBaselineStep.roundToPx() - day[FirstBaseline]
        val height = constraints.constrainHeight(TvBrowseHeaderHeight.roundToPx())
        val top = (height - dateTop - day.height) / 2
        layout(constraints.constrainWidth(widthPx), height) {
            time.placeRelative(0, top)
            day.placeRelative(0, top + dateTop)
        }
    }
}

/** The locale's day-period markers ("AM", "PM"), to set them apart in a 12-hour time. */
private fun meridiemMarkers(locale: Locale): List<String> = DateTimeFormatter.ofPattern("a", locale).let { format ->
    listOf(LocalTime.of(10, 0), LocalTime.of(22, 0)).map(format::format).distinct().filter(String::isNotBlank)
}

private fun styledClock(text: String, markers: List<String>, meridiem: SpanStyle): AnnotatedString {
    val marker = markers.firstOrNull { text.contains(it) } ?: return AnnotatedString(text)
    val start = text.lastIndexOf(marker)
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(meridiem) { append(marker) }
        append(text.substring(start + marker.length))
    }
}

/**
 * Every weekday in every month with a two-digit day: the date line's widest cases. Noon UTC keeps
 * each sample on a two-digit day in any zone, so the width depends only on locale and style.
 */
private fun wallClockDateSamples(): List<Long> = (1..12).flatMap { month ->
    (10..16).map { day -> LocalDate.of(2026, month, day).atTime(12, 0).toEpochSecond(ZoneOffset.UTC) }
}
