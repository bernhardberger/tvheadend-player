package at.bernhardberger.tvhplayer.ui.screens.guide

import at.bernhardberger.tvhplayer.core.GuideWindowBounds
import at.bernhardberger.tvhplayer.core.guideWindowBounds
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideTimePresetsTest {
    private val utc = ZoneOffset.UTC
    private val berlin = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 10, 4)
    private val anyStart = GuideWindowBounds(0, Long.MAX_VALUE / 2)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day, zone: ZoneId = utc) =
        date.atTime(hour, minute).atZone(zone).toEpochSecond()
    private fun enabled(presets: List<GuideTimePreset>) = presets.map { it.enabled }

    @Test fun todayDisablesEndedPeriodsInPlaceAndChecksTheWindowsPreset() {
        val now = at(20, 15)
        val presets = guideTimePresets(day, at(20), now, guideWindowBounds(now, utc), utc)
        assertEquals(GuideTimePeriod.entries, presets.map { it.period })
        assertEquals(listOf(6, 12, 15, 18, 20, 22).map { at(it) }, presets.map { it.startSec })
        // Evening ended at 20:00; Prime time and Late night still run.
        assertEquals(listOf(false, false, false, false, true, true), enabled(presets))
        assertEquals(listOf(GuideTimePeriod.PRIME_TIME), presets.filter { it.checked }.map { it.period })
        assertEquals(4, guideTimePresetEntry(presets, at(20), utc))
    }

    @Test fun onlyAWindowStartingExactlyAtAPresetChecksIt() {
        val presets = guideTimePresets(day, at(20, 30), at(9), anyStart, utc)
        assertEquals(emptyList<GuideTimePreset>(), presets.filter { it.checked })
        assertEquals(GuideTimePeriod.PRIME_TIME, presets[guideTimePresetEntry(presets, at(20, 30), utc)!!].period)
        // The same time on another day checks nothing there.
        assertEquals(emptyList<GuideTimePreset>(),
            guideTimePresets(day.plusDays(1), at(20), at(9), anyStart, utc).filter { it.checked })
    }

    @Test fun aRunningPeriodBeforeTheEarliestBoundStaysEnabled() {
        val presets = guideTimePresets(day, at(16), at(16, 30), GuideWindowBounds(at(16), at(23)), utc)
        // Afternoon started at 15:00, before the bound, but runs until 18:00.
        assertEquals(listOf(false, false, true, true, true, true), enabled(presets))
    }

    @Test fun periodsStartingAfterTheLatestBoundAreDisabled() {
        val bounds = GuideWindowBounds(at(0), at(17))
        val presets = guideTimePresets(day, at(14), at(1), bounds, utc)
        assertEquals(listOf(true, true, true, false, false, false), enabled(presets))
        // A window in a disabled period enters at the last enabled one before it.
        assertEquals(2, guideTimePresetEntry(presets, at(20), utc))
        assertEquals(1, guideTimePresetEntry(presets, at(13, 30), utc))
    }

    @Test fun beforeSixTheEntryIsMorningOrTheNextEnabledPeriod() {
        assertEquals(0, guideTimePresetEntry(guideTimePresets(day, at(2), at(1), anyStart, utc), at(2), utc))
        // At 13:00 Morning has ended; Midday is the next enabled period.
        assertEquals(1, guideTimePresetEntry(guideTimePresets(day, at(2), at(13), anyStart, utc), at(2), utc))
    }

    @Test fun lateNightRunsUntilMidnightThenTheDayHasNoEntry() {
        assertEquals(listOf(false, false, false, false, false, true),
            enabled(guideTimePresets(day, at(22), at(23, 59), anyStart, utc)))
        val ended = guideTimePresets(day, at(22), at(0, date = day.plusDays(1)), anyStart, utc)
        assertEquals(List(6) { false }, enabled(ended))
        assertNull(guideTimePresetEntry(ended, at(22), utc))
    }

    @Test fun theAutumnClockChangeDayResolvesItsPresetsInLocalTime() {
        val change = LocalDate.of(2026, 10, 25)
        val presets = guideTimePresets(change, at(1, date = change, zone = berlin), 0, anyStart, berlin)
        assertEquals(listOf(6, 12, 15, 18, 20, 22).map { at(it, date = change, zone = berlin) }, presets.map { it.startSec })
        assertEquals(Instant.parse("2026-10-25T05:00:00Z").epochSecond, presets.first().startSec)
        // Late night runs to local midnight, an hour later in UTC than the day before.
        val lastMinute = Instant.parse("2026-10-25T22:59:00Z").epochSecond
        assertEquals(true, guideTimePresets(change, 0, lastMinute, anyStart, berlin).last().enabled)
        assertEquals(false, guideTimePresets(change, 0, lastMinute + 60, anyStart, berlin).last().enabled)
        // 02:30 occurs twice; either occurrence is before 06:00 and enters at Morning.
        val repeated = LocalDateTime.of(2026, 10, 25, 2, 30)
        berlin.rules.getValidOffsets(repeated).forEach { offset ->
            assertEquals(0, guideTimePresetEntry(presets, repeated.toEpochSecond(offset), berlin))
        }
    }

    @Test fun theSpringClockChangeDayResolvesItsPresetsInLocalTime() {
        val change = LocalDate.of(2026, 3, 29)
        val presets = guideTimePresets(change, at(1, date = change, zone = berlin), 0, anyStart, berlin)
        assertEquals(Instant.parse("2026-03-29T04:00:00Z").epochSecond, presets.first().startSec)
        assertEquals(Instant.parse("2026-03-29T20:00:00Z").epochSecond, presets.last().startSec)
        assertEquals(0, guideTimePresetEntry(presets, at(1, 30, date = change, zone = berlin), berlin))
    }
}
