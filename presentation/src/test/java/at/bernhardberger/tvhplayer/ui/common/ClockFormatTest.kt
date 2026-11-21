package at.bernhardberger.tvhplayer.ui.common

import at.bernhardberger.tvhplayer.settings.ClockFormat
import at.bernhardberger.tvhplayer.settings.resolveClockFormat
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockFormatTest {
    private val evening = LocalDateTime.of(2026, 10, 5, 20, 15)
    private val morning = LocalDateTime.of(2026, 10, 5, 8, 5)

    @Test
    fun `System follows the device and an override wins`() {
        assertTrue(ClockFormat.SYSTEM.uses24Hour(systemUses24Hour = true))
        assertFalse(ClockFormat.SYSTEM.uses24Hour(systemUses24Hour = false))
        assertFalse(ClockFormat.TWELVE_HOUR.uses24Hour(systemUses24Hour = true))
        assertTrue(ClockFormat.TWENTY_FOUR_HOUR.uses24Hour(systemUses24Hour = false))
    }

    @Test
    fun `stored values resolve and unknown values fall back to System`() {
        assertEquals(ClockFormat.SYSTEM, resolveClockFormat(null))
        assertEquals(ClockFormat.SYSTEM, resolveClockFormat("bogus"))
        assertEquals(ClockFormat.TWELVE_HOUR, resolveClockFormat("TWELVE_HOUR"))
        assertEquals(ClockFormat.TWENTY_FOUR_HOUR, resolveClockFormat("TWENTY_FOUR_HOUR"))
    }

    @Test
    fun `24-hour clock in both locales`() {
        for (locale in listOf(Locale.ENGLISH, Locale.GERMAN)) {
            assertEquals("20:15", formatClock(evening, locale, uses24Hour = true))
            assertEquals("08:05", formatClock(morning, locale, uses24Hour = true))
        }
    }

    @Test
    fun `12-hour clock keeps the marker on the same line in both locales`() {
        for (locale in listOf(Locale.ENGLISH, Locale.GERMAN)) {
            assertEquals("8:15 PM", formatClock(evening, locale, uses24Hour = false))
            assertEquals("8:05 AM", formatClock(morning, locale, uses24Hour = false))
        }
    }

    @Test
    fun `date and clock follow the same choice`() {
        assertEquals("Mon 5 Oct 20:15", formatDateAndClock(evening, "EEE d MMM", Locale.ENGLISH, uses24Hour = true))
        assertEquals("Mon 5 Oct 8:15 PM", formatDateAndClock(evening, "EEE d MMM", Locale.ENGLISH, uses24Hour = false))
        assertEquals("Mo. 5 Okt. 20:15", formatDateAndClock(evening, "EEE d MMM", Locale.GERMAN, uses24Hour = true))
    }
}
