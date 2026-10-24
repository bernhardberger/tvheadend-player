package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LogoGlowPolicyTest {

    private val width = 960
    private val height = 540
    private val background = 0x131313
    private val accent = 0x30607A
    private val pixels = logoGlowPixels(accent, background, width, height)

    private fun channel(x: Int, y: Int, shift: Int) = pixels[y * width + x] shr shift and 0xff

    @Test fun theAccentLightsTheTopRightCorner() {
        for ((shift, lit) in listOf(16 to 0x30, 8 to 0x60, 0 to 0x7A)) {
            val base = background shr shift and 0xff
            assertEquals(base + (lit - base) * .55f, channel(width - 1, 0, shift).toFloat(), 1.5f)
        }
        assertEquals(0xff, pixels[width - 1] ushr 24)
    }

    @Test fun theLeftAndTheBottomKeepThePageBackground() {
        for (y in 0 until height) assertEquals(0xff000000.toInt() or background, pixels[y * width])
        for (x in 0 until width) for (shift in listOf(16, 8, 0)) {
            assertTrue(channel(x, height - 1, shift) - (background shr shift and 0xff) <= 2)
        }
    }

    @Test fun aSlowDarkRampIsDitheredRatherThanStepped() {
        // Red rises by only 0x1D from the left to the corner: in plain 8 bits that is a few
        // wide bands, each a whole step brighter than the last.
        val by = height / 2 / 8 * 8
        val means = (0 until width / 8).map { bx ->
            var sum = 0
            for (y in by until by + 8) for (x in bx * 8 until bx * 8 + 8) sum += channel(x, y, 16)
            sum / 64f
        }
        val ramp = means.indices.filter { means[it] > 0x13 + .05f }
        assertTrue(ramp.size > 20)
        for (i in ramp.drop(1)) assertTrue("block $i: ${means[i - 1]} to ${means[i]}", abs(means[i] - means[i - 1]) < .5f)
        val mixed = ramp.count { bx ->
            (by until by + 8).flatMap { y -> (bx * 8 until bx * 8 + 8).map { x -> channel(x, y, 16) } }.distinct().size > 1
        }
        assertTrue("$mixed of ${ramp.size} blocks mix two levels", mixed > ramp.size / 2)
    }
}
