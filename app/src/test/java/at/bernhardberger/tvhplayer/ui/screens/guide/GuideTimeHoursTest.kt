package at.bernhardberger.tvhplayer.ui.screens.guide

import at.bernhardberger.tvhplayer.core.GuideWindowBounds
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideTimeHoursTest {
    private val anyStart = GuideWindowBounds(0, Long.MAX_VALUE / 2)

    private fun labels(hours: List<GuideTimeHour>) = hours.map { "%02d:%02d".format(it.start.hour, it.start.minute) }

    @Test fun aHalfHourClockChangeKeepsTheWholeHoursAfterIt() {
        // Lord Howe Island moves from 02:00 to 02:30 on 2026-10-04.
        val zone = ZoneId.of("Australia/Lord_Howe")
        val window = LocalDateTime.of(2026, 10, 4, 19, 0).atZone(zone).toEpochSecond()
        val hours = guideTimeHours(window, anyStart, zone)
        assertEquals(listOf("00:00", "01:00", "02:30") + (3..23).map { "%02d:00".format(it) }, labels(hours))
        assertEquals(listOf(19), hours.filter { it.selected }.map { it.start.hour })
    }

    @Test fun theSkippedSpringHourIsLeftOut() {
        val zone = ZoneId.of("Europe/Berlin")
        val window = LocalDateTime.of(2026, 3, 29, 10, 0).atZone(zone).toEpochSecond()
        val hours = guideTimeHours(window, anyStart, zone)
        assertEquals(23, hours.size)
        assertEquals(listOf(0, 1) + (3..23).toList(), hours.map { it.start.hour })
    }

    @Test fun theRepeatedAutumnHourIsCheckedInTheOccurrenceTheWindowIsIn() {
        val zone = ZoneId.of("Europe/Berlin")
        val local = LocalDateTime.of(2026, 10, 25, 2, 0)
        val first = zoned(local, zone, ZoneOffset.ofHours(2))
        val second = zoned(local, zone, ZoneOffset.ofHours(1))
        assertEquals(3600L, second - first)
        for (window in listOf(first, second, second + 15 * 60)) {
            val hours = guideTimeHours(window, anyStart, zone)
            assertEquals(24, hours.size)
            val checked = hours.single { it.selected }
            assertEquals(2, checked.start.hour)
            assertEquals(if (window == first) first else second, checked.start.toEpochSecond())
        }
        // Away from it, 02:00 is its first occurrence.
        val evening = LocalDateTime.of(2026, 10, 25, 20, 0).atZone(zone).toEpochSecond()
        assertEquals(first, guideTimeHours(evening, anyStart, zone).single { it.start.hour == 2 }.start.toEpochSecond())
    }

    @Test fun anEarliestBoundInTheSecondAutumnOccurrenceKeepsThatHour() {
        val zone = ZoneId.of("Europe/Berlin")
        val second = zoned(LocalDateTime.of(2026, 10, 25, 2, 0), zone, ZoneOffset.ofHours(1))
        val evening = LocalDateTime.of(2026, 10, 25, 8, 0).atZone(zone).toEpochSecond()
        val hours = guideTimeHours(evening, GuideWindowBounds(second, Long.MAX_VALUE / 2), zone)
        assertEquals((2..23).toList(), hours.map { it.start.hour })
        assertEquals(second, hours.first().start.toEpochSecond())
    }

    @Test fun onlyHoursWithinTheBoundsAreOffered() {
        val zone = ZoneId.of("Europe/Berlin")
        val day = LocalDateTime.of(2026, 10, 5, 0, 0)
        val bounds = GuideWindowBounds(day.withHour(14).atZone(zone).toEpochSecond(), day.withHour(17).atZone(zone).toEpochSecond())
        val hours = guideTimeHours(day.withHour(15).atZone(zone).toEpochSecond(), bounds, zone)
        assertEquals(listOf(14, 15, 16, 17), hours.map { it.start.hour })
    }

    private fun zoned(local: LocalDateTime, zone: ZoneId, offset: ZoneOffset) =
        java.time.ZonedDateTime.ofLocal(local, zone, offset).toEpochSecond()
}
