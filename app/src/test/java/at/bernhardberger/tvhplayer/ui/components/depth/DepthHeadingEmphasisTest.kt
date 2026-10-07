package at.bernhardberger.tvhplayer.ui.components.depth

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A heading's emphasis follows its column between the preview and active slots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class DepthHeadingEmphasisTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun previewHeadingGrowsWhileItsColumnSlidesIntoTheActiveSlot() {
        val seen = mutableStateMapOf<String, Float>()
        fun level(id: String, rows: List<DepthRow>) = DepthLevel(id, rows, heading = { emphasis ->
            val value = emphasis()
            SideEffect { seen[id] = value }
        })
        fun row(id: String, child: String? = null) = DepthRow(DepthItem(id, child)) { modifier, _ ->
            Box(modifier.size(40.dp).focusable())
        }
        val levels = mapOf(
            "root" to level("root", listOf(row("child", "child"))),
            "child" to level("child", listOf(row("leaf"))),
        )
        val navigation = DepthNavigationState(DepthStack(listOf(DepthFrame("root", "child"))))
        compose.setContent {
            DepthNavigation(navigation, levels, { levels.getValue("root") }, columnWidth = 340.dp,
                columnGap = 92.dp, contentPadding = PaddingValues(), modifier = Modifier.fillMaxSize())
        }
        compose.waitForIdle()
        assertEquals("active heading", 1f, seen.getValue("root"), 0.001f)
        assertEquals("preview heading", 0f, seen.getValue("child"), 0.001f)

        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle() // Composes the push, which starts the slide.
        compose.mainClock.advanceTimeBy(DepthSlideMillis / 5L)
        val (arriving, leaving) = compose.runOnIdle { seen.getValue("child") to seen.getValue("root") }
        assertTrue("arriving heading is mid-growth ($arriving)", arriving > 0f && arriving < 1f)
        assertEquals("the two headings trade emphasis along the slide", 1f, arriving + leaving, 0.01f)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("arrived heading", 1f, seen.getValue("child"), 0.001f)
    }
}
