package at.bernhardberger.tvhplayer.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import at.bernhardberger.tvhplayer.settings.ClockFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * The app-wide 12/24-hour choice. Snapshot state, so composition that formats a
 * time recomposes when the setting or the device clock format changes. Defaults
 * to 24-hour until the activity applies the device setting, which keeps tests and
 * captures deterministic.
 */
object AppClock {
    var format by mutableStateOf(ClockFormat.SYSTEM)
    var systemUses24Hour by mutableStateOf(true)
    val uses24Hour: Boolean get() = format.uses24Hour(systemUses24Hour)
}

/** The time pattern; the non-breaking space keeps "8:15 PM" on one line. */
internal fun clockPattern(uses24Hour: Boolean): String = if (uses24Hour) "HH:mm" else "h:mm a"

/** [datePattern] followed by the current clock, e.g. "EEE d MMM" → "Mon 6 Oct 20:15". */
fun formatDateAndClock(
    time: TemporalAccessor,
    datePattern: String,
    locale: Locale = Locale.getDefault(),
    uses24Hour: Boolean = AppClock.uses24Hour,
): String = DateTimeFormatter.ofPattern("$datePattern ${clockPattern(uses24Hour)}", locale).format(time)

fun formatClock(
    time: TemporalAccessor,
    locale: Locale = Locale.getDefault(),
    uses24Hour: Boolean = AppClock.uses24Hour,
): String = DateTimeFormatter.ofPattern(clockPattern(uses24Hour), locale).format(time)

/**
 * Widest-case clock texts for sizing a fixed label box: every digit is [digit], and 12-hour
 * mode yields one sample per day-period marker. Measure each and keep the widest.
 */
fun clockWidthSamples(
    locale: Locale = Locale.getDefault(),
    uses24Hour: Boolean = AppClock.uses24Hour,
    digit: Char = '8',
): List<String> = listOf(LocalTime.of(10, 0), LocalTime.of(22, 0))
    .map { formatClock(it, locale, uses24Hour).replace(Regex("\\d"), digit.toString()) }
    .distinct()

fun formatClock(epochSec: Long): String =
    formatClock(Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()))

fun formatHms(sec: Long): String {
    val s = sec.coerceAtLeast(0L)
    val h = (s / 3600).toInt()
    val m = ((s % 3600) / 60).toInt()
    val ss = (s % 60).toInt()
    return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%02d:%02d".format(m, ss)
}

fun floorToMinutes(unixSec: Long, minutes: Int): Long {
    val step = minutes * 60L
    return (unixSec / step) * step
}

fun formatHm(unixSec: Long): String = formatClock(unixSec)