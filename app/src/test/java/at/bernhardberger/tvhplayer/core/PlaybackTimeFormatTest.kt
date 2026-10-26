package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.ui.player.timeshiftEndpointLabel
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeFormatTest {
    @Test
    fun fallbackTimeshiftEndpointsAreNumericAndDoNotShowNegativeZero() {
        assertEquals("0:00", timeshiftEndpointLabel(true, 800))
        assertEquals("0:00", timeshiftEndpointLabel(false, 999))
        assertEquals("−0:30", timeshiftEndpointLabel(false, 30_000))
    }

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
    fun stepOffsetCarriesExplicitSign() {
        assertEquals("+3:00", formatStepOffset(180_000L))
        assertEquals("−1:30", formatStepOffset(-90_000L))
        assertEquals("+0:30", formatStepOffset(30_000L))
    }

    @Test
    fun stepOffsetPassesAnHourWithHoursAndMinutesPadded() {
        assertEquals("+1:02:00", formatStepOffset(3_720_000L))
        assertEquals("−1:00:05", formatStepOffset(-3_605_000L))
        assertEquals("+59:59", formatStepOffset(3_599_000L))
    }

    @Test
    fun zeroStepOffsetIsUnsigned() {
        assertEquals("0:00", formatStepOffset(0L))
        // Under a second moves nowhere, which the label must not claim as a direction.
        assertEquals("0:00", formatStepOffset(-900L))
        assertEquals("−0:01", formatStepOffset(-1_000L))
    }

    @Test
    fun stepOffsetUsesLatinDigitsInEveryLocaleAndKeepsTheRealMinus() {
        val previous = Locale.getDefault()
        try {
            for (tag in listOf("ar-EG-u-nu-arab", "de-DE", "fa-IR")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals(tag, "−1:30", formatStepOffset(-90_000L))
                assertEquals(tag, "+1:02:00", formatStepOffset(3_720_000L))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun stepOffsetOfTheLowestLongDoesNotOverflow() {
        assertEquals('−', formatStepOffset(Long.MIN_VALUE).first())
    }
}
