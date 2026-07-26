package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChannelAccentPolicyTest {

    @Test
    fun logoBackgroundsAreRejected() {
        // The three things a picon is mostly made of.
        assertFalse(isUsableAccent(0xFFFFFF))
        assertFalse(isUsableAccent(0x000000))
        assertFalse(isUsableAccent(0x808080))
        // Near-white antialiasing fringes too.
        assertFalse(isUsableAccent(0xFAFAFA))
    }

    @Test
    fun brandColoursAreAccepted() {
        assertTrue(isUsableAccent(0xE2001A)) // ORF red
        assertTrue(isUsableAccent(0x0057B8)) // a broadcaster blue
        assertTrue(isUsableAccent(0x00A3A1)) // teal
    }

    @Test
    fun normalizePreservesHueAndClampsIntoTheDarkBand() {
        val source = 0xE2001A
        val (sourceHue, _, _) = rgbToHsv(source)
        val (hue, saturation, value) = rgbToHsv(normalizeAccentRgb(source))

        assertTrue("hue drifted", abs(hue - sourceHue) < 2f)
        assertTrue("saturation below band: $saturation", saturation >= 0.34f)
        assertTrue("saturation above band: $saturation", saturation <= 0.61f)
        assertTrue("value outside band: $value", abs(value - 0.38f) < 0.02f)
    }

    @Test
    fun normalizeLiftsAnUndersaturatedInputIntoTheBand() {
        val dull = 0x6A6E75
        val (_, saturation, _) = rgbToHsv(normalizeAccentRgb(dull))
        assertTrue("expected saturation lifted, got $saturation", saturation >= 0.34f)
    }

    @Test
    fun selectTakesTheFirstUsableCandidate() {
        val chosen = selectAccentRgb(listOf(null, 0xFFFFFF, 0xE2001A, 0x0057B8))
        val (hue, _, _) = rgbToHsv(chosen)
        val (expectedHue, _, _) = rgbToHsv(0xE2001A)
        assertTrue("expected the red candidate", abs(hue - expectedHue) < 2f)
    }

    @Test
    fun selectFallsBackToNeutralWhenNothingIsUsable() {
        assertEquals(NEUTRAL_ACCENT_RGB, selectAccentRgb(listOf(null, 0xFFFFFF, 0x000000)))
        assertEquals(NEUTRAL_ACCENT_RGB, selectAccentRgb(emptyList()))
    }

    @Test
    fun hsvRoundTripsWithinRounding() {
        for (rgb in listOf(0xE2001A, 0x0057B8, 0x00A3A1, 0x7B2D8E, 0x123456)) {
            val (h, s, v) = rgbToHsv(rgb)
            val back = hsvToRgb(h, s, v)
            val dr = abs(((back shr 16) and 0xFF) - ((rgb shr 16) and 0xFF))
            val dg = abs(((back shr 8) and 0xFF) - ((rgb shr 8) and 0xFF))
            val db = abs((back and 0xFF) - (rgb and 0xFF))
            assertTrue("round trip drifted for ${rgb.toString(16)}", dr <= 2 && dg <= 2 && db <= 2)
        }
    }
}
