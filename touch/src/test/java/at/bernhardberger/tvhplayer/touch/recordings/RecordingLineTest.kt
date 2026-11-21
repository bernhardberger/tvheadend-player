package at.bernhardberger.tvhplayer.touch.recordings

import at.bernhardberger.tvhplayer.touch.recordings.RecordingLinePart.Channel
import at.bernhardberger.tvhplayer.touch.recordings.RecordingLinePart.Day
import at.bernhardberger.tvhplayer.touch.recordings.RecordingLinePart.Episode
import at.bernhardberger.tvhplayer.touch.recordings.RecordingLinePart.Left
import at.bernhardberger.tvhplayer.touch.recordings.RecordingLinePart.Length
import at.bernhardberger.tvhplayer.touch.recordings.RecordingLinePart.Time
import at.bernhardberger.tvhplayer.touch.recordings.RecordingLinePart.Until
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import at.bernhardberger.tvhplayer.ui.common.formatClock
import org.junit.Assert.assertEquals
import org.junit.Test

// The first pure policy in :touch, so it has its own JVM test: the row-line rule of touch-design §8.
class RecordingLineTest {
    private val clock = RecordingClock(Instant.parse("2026-11-16T20:00:00Z"), ZoneId.of("Europe/Vienna"), Locale.US) {
        formatClock(it, Locale.US, uses24Hour = false)
    }
    private val underDay = RowContext(RowDates.DayLabel)
    private val inFolder = RowContext(RowDates.None)

    @Test
    fun partsFollowTheListsContext() {
        val match = item(start = "2026-11-14T20:15", episode = "Kingsbridge – Northport")
        val cases = listOf(
            Triple(match, underDay, listOf(Time("8:15 PM"), Length(105), Episode("Kingsbridge – Northport"), Channel)),
            Triple(
                match,
                inFolder,
                listOf(Day(day(2026, 11, 14)), Length(105), Episode("Kingsbridge – Northport"), Channel),
            ),
            // Under a month label the day is written, as in a list without labels.
            Triple(match, RowContext(RowDates.MonthLabel), listOf(Day(day(2026, 11, 14)), Length(105), Episode("Kingsbridge – Northport"), Channel)),
            // One channel's list leaves the channel out.
            Triple(match, RowContext(RowDates.None, oneChannel = true), listOf(Day(day(2026, 11, 14)), Length(105), Episode("Kingsbridge – Northport"))),
            Triple(item(start = "2026-11-16T12:15", length = 45), underDay, listOf(Time("12:15 PM"), Length(45), Channel)),
            Triple(item(start = "2026-11-16T12:15"), inFolder, listOf(Day(RecordingDay.Today), Length(105), Channel)),
            Triple(item(start = "2026-11-15T23:30"), inFolder, listOf(Day(RecordingDay.Yesterday), Length(105), Channel)),
            Triple(item(start = "2025-12-30T20:00"), inFolder, listOf(Day(RecordingDay.On(LocalDate.of(2025, 12, 30), true)), Length(105), Channel)),
            // An episode named like the title adds nothing; a partly watched one tells what is left.
            Triple(
                item(start = "2026-11-11T20:30", episode = "harbor kickoff", state = RecordingProgress.PartlyWatched(minutes(52), 0.5f)),
                underDay,
                listOf(Time("8:30 PM"), Left(minutes(52)), Channel),
            ),
            // Still running: "until" stands in for when and length.
            Triple(
                item(
                    start = "2026-11-16T20:30", episode = "Northport – Eastmoor",
                    state = RecordingProgress.Recording(at("2026-11-16T22:15"), 0.2f),
                ),
                inFolder,
                listOf(Until("10:15 PM"), Episode("Northport – Eastmoor"), Channel),
            ),
        )
        cases.forEach { (item, context, expected) ->
            assertEquals("$context", expected, recordingLine(item, context, clock).map(::spaced))
        }
    }

    private fun item(
        start: String,
        length: Long = 105,
        episode: String? = null,
        state: RecordingProgress = RecordingProgress.Unwatched,
    ) = RecordingItem(
        id = start, title = "Harbor Kickoff", episode = episode, channel = "Harbor Sport",
        start = at(start), length = minutes(length), state = state,
    )

    private fun at(local: String) = java.time.LocalDateTime.parse(local).atZone(clock.zone).toInstant()

    private fun minutes(count: Long) = Duration.ofMinutes(count)

    private fun day(year: Int, month: Int, day: Int) = RecordingDay.On(LocalDate.of(year, month, day), withYear = false)

    // Clock texts write "8:15 PM" with a no-break space; the rule is about the parts.
    private fun spaced(part: RecordingLinePart) = when (part) {
        is Time -> Time(part.time.replace('\u00a0', ' '))
        is Until -> Until(part.time.replace('\u00a0', ' '))
        else -> part
    }

    private companion object {
        val Channel = Channel("Harbor Sport")

        @Suppress("TestFunctionName")
        fun Length(minutes: Long) = Length(Duration.ofMinutes(minutes))
    }
}
