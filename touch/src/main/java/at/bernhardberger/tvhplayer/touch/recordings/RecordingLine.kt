package at.bernhardberger.tvhplayer.touch.recordings

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** What the label above a row already says, or that the list has no date labels. */
internal enum class RowDates { DayLabel, MonthLabel, None }

/** Where a row stands: the label above it, and whether the whole list is one channel's. */
internal data class RowContext(val dates: RowDates, val oneChannel: Boolean = false)

/**
 * The clock and the reader's locale for the texts on rows and the pane; [time] writes a clock time
 * the way the rest of the app does (the shared `formatClock`, which follows the device's 12/24-hour
 * setting).
 */
internal data class RecordingClock(
    val now: Instant,
    val zone: ZoneId,
    val locale: Locale,
    val time: (ZonedDateTime) -> String,
)

/** A recording's day: the two that read as words, else the date. */
internal sealed interface RecordingDay {
    data object Today : RecordingDay
    data object Yesterday : RecordingDay
    /** Any other day; [withYear] once it is not this year's. The row writes it in the locale's way. */
    data class On(val date: LocalDate, val withYear: Boolean) : RecordingDay
}

/** One part of a row's second line, in the line's order. */
internal sealed interface RecordingLinePart {
    data class Episode(val name: String) : RecordingLinePart
    data class Time(val time: String) : RecordingLinePart
    data class Day(val day: RecordingDay) : RecordingLinePart
    data class Length(val length: Duration) : RecordingLinePart
    data class Left(val left: Duration) : RecordingLinePart
    data class Until(val time: String) : RecordingLinePart
    /** The only part that may be omitted for width. */
    data class Channel(val name: String) : RecordingLinePart
}

/**
 * A recording row's second line (touch-design §8): when and length first, then the episode
 * and the optional channel. A running recording replaces when and length with "until"; a partly
 * watched recording shows what's left instead of length. Only the episode can be ellipsized.
 */
internal fun recordingLine(item: RecordingItem, context: RowContext, clock: RecordingClock): List<RecordingLinePart> =
    buildList {
        when (val state = item.state) {
            is RecordingProgress.Recording -> add(RecordingLinePart.Until(recordingTime(state.until, clock)))
            else -> {
                item.start?.let { start ->
                    add(
                        if (context.dates == RowDates.DayLabel) {
                            RecordingLinePart.Time(recordingTime(start, clock))
                        } else {
                            RecordingLinePart.Day(recordingDay(start, clock))
                        },
                    )
                }
                when {
                    state is RecordingProgress.PartlyWatched -> add(RecordingLinePart.Left(state.left))
                    item.length != null -> add(RecordingLinePart.Length(item.length))
                }
            }
        }
        item.episode?.takeIf { it.isNotBlank() && !it.equals(item.title, ignoreCase = true) }
            ?.let { add(RecordingLinePart.Episode(it)) }
        item.channel?.takeIf { !context.oneChannel }?.let { add(RecordingLinePart.Channel(it)) }
    }

/** A clock time, "8:15 PM" or "20:15". */
internal fun recordingTime(at: Instant, clock: RecordingClock): String = clock.time(at.atZone(clock.zone))

/** Today, Yesterday, else the date ("Sat, Nov 14"), with the year once it is not this year's. */
internal fun recordingDay(at: Instant, clock: RecordingClock): RecordingDay {
    val date = at.atZone(clock.zone).toLocalDate()
    val today = clock.now.atZone(clock.zone).toLocalDate()
    return when (date) {
        today -> RecordingDay.Today
        today.minusDays(1) -> RecordingDay.Yesterday
        else -> RecordingDay.On(date, withYear = date.year != today.year)
    }
}
