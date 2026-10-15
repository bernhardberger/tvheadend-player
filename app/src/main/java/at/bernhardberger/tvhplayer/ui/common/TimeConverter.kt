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
    startSec: Long?, stopSec: Long? = null, dateOnly: Boolean = false,
    locale: Locale = Locale.getDefault(), nowSec: Long = System.currentTimeMillis() / 1000L,
): String {
    if (startSec == null) return ""
    return formatRecordingDate(startSec, dateOnly, locale, nowSec) +
        (stopSec?.takeUnless { dateOnly }?.let {
            "–${formatClock(Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()), locale)}"
        } ?: "")
}
