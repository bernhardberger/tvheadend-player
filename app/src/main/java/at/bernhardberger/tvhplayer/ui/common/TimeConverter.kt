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
import java.util.concurrent.ConcurrentHashMap

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
): String = formatter("$datePattern ${clockPattern(uses24Hour)}", locale).format(time)

fun formatClock(
    time: TemporalAccessor,
    locale: Locale = Locale.getDefault(),
    uses24Hour: Boolean = AppClock.uses24Hour,
): String = formatter(clockPattern(uses24Hour), locale).format(time)

/** Formatters are immutable and the guide formats several times per cell, so each pattern is built once. */
private val formatters = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

private fun formatter(pattern: String, locale: Locale): DateTimeFormatter =
    formatters.getOrPut(pattern to locale) { DateTimeFormatter.ofPattern(pattern, locale) }

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

fun formatPlaybackDuration(seconds: Long, resources: android.content.res.Resources): String {
    val minutes = seconds.coerceAtLeast(0L) / 60
    return if (minutes < 60) resources.getString(at.bernhardberger.tvhplayer.R.string.duration_minutes_short, minutes)
    else if (minutes % 60 == 0L) resources.getString(at.bernhardberger.tvhplayer.R.string.duration_hours_short, minutes / 60)
    else resources.getString(at.bernhardberger.tvhplayer.R.string.duration_hours_minutes_short, minutes / 60, minutes % 60)
}

fun formatRecordingSchedule(startSec: Long?, stopSec: Long? = null, dateOnly: Boolean = false): String {
    if (startSec == null) return ""
    val start = Instant.ofEpochSecond(startSec).atZone(ZoneId.systemDefault())
    val datePattern = if (Locale.getDefault().language == "de") "EEE, d. MMM," else "EEE, MMM d,"
    if (dateOnly) return formatter(datePattern.removeSuffix(","), Locale.getDefault()).format(start)
    return formatDateAndClock(start, datePattern) + (stopSec?.let { "–${formatClock(it)}" } ?: "")
}
