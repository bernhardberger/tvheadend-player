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

    @Test fun settledGlowPulsesSubtlyWithoutMovingAndStopsWhenDisabled() {
        var enabled by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.size(960.dp, 540.dp).background(Color(0xFF0F1014))) {
                StartupBrandBackground(millis = { StartupBrandDurationMillis }, enabled = enabled)
            }
        }
        rule.waitUntil(5_000) {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodesWithTag("startup-background-prepared").fetchSemanticsNodes().isNotEmpty()
        }
        rule.mainClock.advanceTimeByFrame()
        val background = rule.onNodeWithTag("startup-background-prepared")
        val initial = background.captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(3_000)
        val peak = background.captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(3_000)
        val returned = background.captureToImage().toPixelMap()

        var brighterPixels = 0
        for (y in 0 until initial.height) for (x in 0 until initial.width) {
            val delta = peak[x, y].blue - initial[x, y].blue
            // The light-only plate only brightens: shifting it would also darken pixels.
            assertTrue("The plate stays still and the pulse stays subtle", delta in -0.001f..0.025f)
            if (delta > 0.002f) brighterPixels++
            assertTrue("A full cycle returns to the existing glow",
                abs(returned[x, y].blue - initial[x, y].blue) <= 1.01f / 255f)
        }
        assertTrue("Settled loading must visibly breathe, not freeze",
            brighterPixels > initial.width * initial.height / 100)

        rule.runOnIdle { enabled = false }
        rule.mainClock.advanceTimeByFrame()
        background.assertDoesNotExist()
        rule.mainClock.advanceTimeBy(6_000)
        background.assertDoesNotExist()
    }
}
