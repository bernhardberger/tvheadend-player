package at.bernhardberger.tvhplayer.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class StartupBrandBackgroundAnimationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun glowFadesInThenBreathesContinuouslyWithoutMovingAndStopsWhenDisabled() {
        var enabled by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.size(960.dp, 540.dp).background(Color(0xFF0F1014))) {
                StartupBrandBackground(enabled = enabled)
            }
        }
        rule.waitUntil(5_000) {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodesWithTag("startup-background-prepared").fetchSemanticsNodes().isNotEmpty()
        }
        rule.mainClock.advanceTimeByFrame()
        val background = rule.onNodeWithTag("startup-background-prepared")
        val arriving = background.captureToImage().toPixelMap()
        // The fade-in ends on the resting glow, where breathing begins.
        rule.mainClock.advanceTimeBy(400)
        rule.mainClock.advanceTimeByFrame()
        val initial = background.captureToImage().toPixelMap()
        var arrivedPixels = 0
        for (y in 0 until initial.height) for (x in 0 until initial.width) {
            val delta = initial[x, y].blue - arriving[x, y].blue
            assertTrue("The plate fades in without moving", delta >= -0.001f)
            if (delta > 3f / 255f) arrivedPixels++
        }
        assertTrue("The plate fades in to its resting glow",
            arrivedPixels > initial.width * initial.height / 100)
        rule.mainClock.advanceTimeBy(500)
        val earlyRise = background.captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(1_500)
        val peak = background.captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(2_000)
        val returned = background.captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(2_000)
        val nextPeak = background.captureToImage().toPixelMap()

        var brighterPixels = 0
        var earlyBrightnessGain = 0.0
        var peakBrightnessGain = 0.0
        for (y in 0 until initial.height) for (x in 0 until initial.width) {
            val delta = peak[x, y].blue - initial[x, y].blue
            // The light-only plate only brightens: shifting it would also darken pixels.
            assertTrue("The plate stays still and the glow stays bounded", delta in -0.001f..0.075f)
            if (delta > 3f / 255f) brighterPixels++
            earlyBrightnessGain += (earlyRise[x, y].blue - initial[x, y].blue).coerceAtLeast(0f)
            peakBrightnessGain += delta.coerceAtLeast(0f)
            assertTrue("A full cycle returns to the existing glow",
                abs(returned[x, y].blue - initial[x, y].blue) <= 1.01f / 255f)
            assertTrue("Breathing continues after the first cycle",
                abs(nextPeak[x, y].blue - peak[x, y].blue) <= 1.01f / 255f)
        }
        assertTrue("Settled loading has a meaningful brightness range",
            brighterPixels > initial.width * initial.height / 100)
        assertTrue("The inhale starts gently rather than linearly or with a sudden jump",
            earlyBrightnessGain / peakBrightnessGain in 0.05..0.22)

        rule.runOnIdle { enabled = false }
        rule.mainClock.advanceTimeByFrame()
        background.assertDoesNotExist()
        rule.mainClock.advanceTimeBy(4_000)
        background.assertDoesNotExist()
    }
}
