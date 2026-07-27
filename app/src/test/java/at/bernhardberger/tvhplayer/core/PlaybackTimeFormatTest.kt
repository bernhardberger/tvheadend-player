package at.bernhardberger.tvhplayer.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeFormatTest {
    @Test
    fun subMinutePositionUsesMinuteSecondForm() {
        assertEquals("0:00", formatPlaybackDuration(0L))
        assertEquals("0:04", formatPlaybackDuration(4_000L))
    }

    @Test
    fun subHourPositionOmitsLeadingHour() {
        assertEquals("29:56", formatPlaybackDuration(1_796_000L))
    }

    @Test
    fun hourBoundaryAddsHourComponent() {
        assertEquals("59:59", formatPlaybackDuration(3_599_000L))
        assertEquals("1:00:00", formatPlaybackDuration(3_600_000L))
    }

    @Test
    fun negativeInputClampsToZero() {
        assertEquals("0:00", formatPlaybackDuration(-1L))
    }

    @Test
    fun formatterIsLocaleIndependent() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1:01:01", formatPlaybackDuration(3_661_000L))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun deltaCarriesExplicitSign() {
        assertEquals("+0:30", formatPlaybackDelta(30_000L))
        assertEquals("−1:15", formatPlaybackDelta(-75_000L))
    }

    @Test
    fun zeroDeltaIsPositive() {
        assertEquals("+0:00", formatPlaybackDelta(0L))
    }
}
