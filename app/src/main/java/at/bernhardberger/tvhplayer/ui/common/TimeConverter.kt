package at.bernhardberger.tvhplayer.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.ClockFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
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

/** Details refresh on minute boundaries independently of the Guide's five-second now line. */
@Composable
internal fun rememberDetailsEpochSeconds(
    nowSecProvider: () -> Long = { System.currentTimeMillis() / 1000L },
): Long {
    val clock by rememberUpdatedState(nowSecProvider)
    var nowSec by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((60L - Math.floorMod(clock(), 60L)) * 1000L)
            nowSec = clock()
        }
    }
    return nowSec
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

/** Numeric calendar date in the selected app locale, independent of the clock preference. */
fun formatShortDate(time: TemporalAccessor, locale: Locale = Locale.getDefault()): String =
    formatter(android.text.format.DateFormat.getBestDateTimePattern(locale, "yMd"), locale).format(time)

/** Full weekday and month for schedule section headings, in locale-specific order. */
fun formatDayHeader(time: TemporalAccessor, locale: Locale): String =
    formatter(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMMEEEEd"), locale).format(time)

/** Labeled premiere facts retain the medium date; compact metadata uses [formatShortDate]. */
fun formatFirstAired(epochMillis: Long, locale: Locale): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale).format(java.util.Date(epochMillis))

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

/** Minutes only, in the configured app locale. Callers retain their floor/ceiling policy. */
fun formatDurationMinutes(
    minutes: Long,
    resources: android.content.res.Resources,
    spoken: Boolean = false,
): String = android.icu.text.MeasureFormat.getInstance(
    resources.configuration.locales[0],
    if (spoken) android.icu.text.MeasureFormat.FormatWidth.WIDE else android.icu.text.MeasureFormat.FormatWidth.NARROW,
).format(android.icu.util.Measure(minutes.coerceAtLeast(0L), android.icu.util.MeasureUnit.MINUTE))

/** A recorded length: seconds while under a minute, otherwise whole minutes as [formatDurationMinutes]. */
fun formatRecordedDuration(
    seconds: Long,
    resources: android.content.res.Resources,
    spoken: Boolean = false,
): String = if (seconds !in 1L until 60L) formatDurationMinutes(seconds / 60L, resources, spoken)
else android.icu.text.MeasureFormat.getInstance(
    resources.configuration.locales[0],
    if (spoken) android.icu.text.MeasureFormat.FormatWidth.WIDE else android.icu.text.MeasureFormat.FormatWidth.NARROW,
).format(android.icu.util.Measure(seconds, android.icu.util.MeasureUnit.SECOND))

/** Whole-hour jump amounts, with the unit written out in the configured app locale. */
fun formatWholeHours(hours: Long, resources: android.content.res.Resources): String =
    android.icu.text.MeasureFormat.getInstance(
        resources.configuration.locales[0],
        android.icu.text.MeasureFormat.FormatWidth.WIDE,
    ).format(android.icu.util.Measure(hours, android.icu.util.MeasureUnit.HOUR))

/** Recording dates retain the year only when needed to distinguish them from this year's dates. */
fun formatRecordingDate(
    epochSec: Long,
    dateOnly: Boolean = false,
    locale: Locale = Locale.getDefault(),
    nowSec: Long = System.currentTimeMillis() / 1000L,
    uses24Hour: Boolean = AppClock.uses24Hour,
): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochSecond(epochSec).atZone(zone)
    val currentYear = Instant.ofEpochSecond(nowSec).atZone(zone).year
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale,
        if (date.year == currentYear) "MMMEd" else "yMMMEd")
    return if (dateOnly) formatter(pattern, locale).format(date)
        else formatDateAndClock(date, pattern, locale, uses24Hour)
}

fun formatRecordingSchedule(
    startSec: Long?, dateOnly: Boolean = false,
    locale: Locale = Locale.getDefault(), nowSec: Long = System.currentTimeMillis() / 1000L,
): String = startSec?.let { formatRecordingDate(it, dateOnly, locale, nowSec) }.orEmpty()

/** DateIntervalFormat is mutable and not thread-safe, so each cached instance is used under its own lock. */
private val intervalFormats = ConcurrentHashMap<Pair<String, Locale>, android.icu.text.DateIntervalFormat>()

private val rangeSeparator = Regex("[\\p{Zs}\\s]*[–—‒―~～-][\\p{Zs}\\s]*")
private val whitespace = Regex("[\\p{Zs}\\s]")

/**
 * Breaks may only fall at the range separator, so each endpoint keeps its date and time together.
 * The separator is the last dash with whitespace on both sides, else the last dash ("18:58–21:05").
 */
private fun keepEndpointsTogether(range: String): String {
    val dashes = rangeSeparator.findAll(range).toList()
    val separator = dashes.lastOrNull { it.value.first().isWhitespace() && it.value.last().isWhitespace() }
        ?: dashes.lastOrNull() ?: return range.replace(whitespace, "\u00a0")
    return range.substring(0, separator.range.first).replace(whitespace, "\u00a0") + separator.value +
        range.substring(separator.range.last + 1).replace(whitespace, "\u00a0")
}

/**
 * A start–end range through ICU's DateIntervalFormat: the shared date or day period appears once,
 * and dash, spacing and order follow the locale ("Tue, Sep 15, 6:58 – 9:05 PM", "Di., 15. Sept., 18:58–21:05").
 * The 12/24-hour choice is the app's, not the locale's. [withDate] adds the day, with the year
 * only when an endpoint is outside the current year, as [formatRecordingDate] does.
 */
fun formatTimeInterval(
    startSec: Long,
    stopSec: Long,
    withDate: Boolean = false,
    locale: Locale = Locale.getDefault(),
    nowSec: Long = System.currentTimeMillis() / 1000L,
    uses24Hour: Boolean = AppClock.uses24Hour,
): String {
    val zone = ZoneId.systemDefault()
    val currentYear = Instant.ofEpochSecond(nowSec).atZone(zone).year
    val years = listOf(startSec, stopSec).map { Instant.ofEpochSecond(it).atZone(zone).year }
    val skeleton = (if (!withDate) "" else if (years.all { it == currentYear }) "MMMEd" else "yMMMEd") +
        if (uses24Hour) "Hm" else "hm"
    val format = intervalFormats.getOrPut(skeleton to locale) {
        android.icu.text.DateIntervalFormat.getInstance(skeleton, locale)
    }
    val start = Instant.ofEpochSecond(startSec).atZone(zone)
    val stop = Instant.ofEpochSecond(maxOf(stopSec, startSec)).atZone(zone)
    // Without a date ICU would add numeric dates to a midnight-crossing range, so a clock range puts both local
    // times on one fixed UTC day; that also keeps a DST gap from shifting the end.
    val clockDay = java.time.LocalDate.ofEpochDay(0)
    val clockStart = clockDay.atTime(start.toLocalTime()).toInstant(ZoneOffset.UTC)
    val clockStop = clockDay.atTime(stop.toLocalTime()).toInstant(ZoneOffset.UTC)
    // A whole-day entry (06:00 to 06:00 next day) would collapse to one clock time, so it shows its dates.
    if (!withDate && stopSec > startSec && clockStart == clockStop) {
        return formatTimeInterval(startSec, stopSec, true, locale, nowSec, uses24Hour)
    }
    val range = synchronized(format) {
        format.setTimeZone(android.icu.util.TimeZone.getTimeZone(if (withDate) zone.id else "UTC"))
        if (withDate) format.format(android.icu.util.DateInterval(start.toInstant().toEpochMilli(), stop.toInstant().toEpochMilli()))
        else format.format(android.icu.util.DateInterval(clockStart.toEpochMilli(), clockStop.toEpochMilli()))
    }
    return keepEndpointsTogether(range)
}

/** The spoken form of [formatTimeInterval] for the clock times alone: built from the instants, never from display text. */
fun formatTimeIntervalSpoken(
    startSec: Long,
    stopSec: Long,
    resources: android.content.res.Resources,
    locale: Locale = Locale.getDefault(),
    uses24Hour: Boolean = AppClock.uses24Hour,
): String {
    val zone = ZoneId.systemDefault()
    fun clock(sec: Long) = formatClock(Instant.ofEpochSecond(sec).atZone(zone), locale, uses24Hour)
    // A stop that does not follow the start is displayed as the start alone, so it is spoken that way too.
    return if (stopSec > startSec) resources.getString(R.string.player_time_range_spoken, clock(startSec), clock(stopSec))
        else clock(startSec)
}
