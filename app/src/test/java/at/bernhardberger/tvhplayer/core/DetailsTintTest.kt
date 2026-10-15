package at.bernhardberger.tvhplayer.core

import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class DetailsTintTest {
    @Test fun everySeedKeepsBothThemeTextColorsAboveFourPointFiveContrast() {
        val seeds = listOf(0xFF0000, 0xFFFF00, 0x0000FF, 0xFFFFFF, 0x000000, 0x808080)
        for (seed in seeds) {
            val tint = detailsTintRgb(seed)
            for (text in listOf(0xE1E2E5, 0xC0C7CD)) {
                assertTrue("seed ${seed.toString(16)}", (luminance(text) + .05) / (luminance(tint) + .05) >= 4.5)
            }
        }
        // Every RGB result is bounded by L*(1+S), so even an all-max-channel bound is safe.
        assertTrue((luminance(0xC0C7CD) + .05) / (luminance(0x292929) + .05) >= 4.5)
    }

    @Test fun neutralSeedsStayNeutral() {
        for (seed in listOf(0xFFFFFF, 0x000000, 0x808080)) {
            assertEquals(0x131313, detailsTintRgb(seed))
        }
    }

    @Test fun saturatedSeedsKeepHueAndUseTheFixedDarkBand() {
        for (seed in listOf(0xFF0000, 0xFFFF00, 0x0000FF)) {
            val tint = detailsTintRgb(seed)
            assertEquals(rgbToHsv(seed).first, rgbToHsv(tint).first, .01f)
            val channels = listOf(tint shr 16, tint shr 8, tint).map { it and 255 }
            assertEquals(25, channels.max())
            assertEquals(12, channels.min())
        }
    }

    private fun luminance(rgb: Int): Double {
        fun linear(shift: Int): Double {
            val c = ((rgb shr shift) and 255) / 255.0
            return if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
        }
        return .2126 * linear(16) + .7152 * linear(8) + .0722 * linear(0)
    }
}
