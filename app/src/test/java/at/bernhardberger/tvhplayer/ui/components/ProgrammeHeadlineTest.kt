package at.bernhardberger.tvhplayer.ui.components

import at.bernhardberger.tvhplayer.testutil.tagBounds
import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgrammeHeadlineTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultGuideTimingStaysOnOneLine() {
        val timing = "Wednesday, October 28, 2026 • 10:00 AM – 12:00 PM"
        compose.setContent {
            MaterialTheme {
                ProgrammeHeadline(eyebrow = {}, title = "Programme", tagPrefix = "guide-headline",
                    synopsisLines = 0, timing = timing, modifier = Modifier.width(160.dp))
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(timing).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
    }

    private fun showTiming(width: Int, progress: Float?, minutesLeft: Int?) = compose.setContent {
        MaterialTheme {
            ProgrammeHeadline(eyebrow = {}, title = "Programme", tagPrefix = "t", synopsisLines = 0,
                timing = "19:20 – 20:25", progress = progress, minutesLeft = minutesLeft,
                modifier = Modifier.width(width.dp).testTag("headline"))
        }
    }

    private fun barBounds(): Rect = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun timeLeftSitsRightOfTheBar() {
        showTiming(600, progress = .4f, minutesLeft = 40)
        compose.onNodeWithTag("t-time-left", useUnmergedTree = true).assertTextEquals("40m left")
        assertTrue("the time line comes first, then the bar, then the label",
            compose.tagBounds("t-metadata").left <= barBounds().left && barBounds().right <= compose.tagBounds("t-time-left").left)
    }

    @Test fun timeLeftNeedsTheBar() {
        showTiming(600, progress = null, minutesLeft = 40)
        compose.onNodeWithTag("t-time-left", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun aNarrowColumnShrinksTheBarAndKeepsTheLabel() {
        showTiming(200, progress = .4f, minutesLeft = 40)
        val bar = barBounds()
        val label = compose.tagBounds("t-time-left")
        assertTrue("the bar gives way rather than the label: $bar", bar.width in 1f..159f)
        assertTrue("the label stays inside the column: $label", label.right <= compose.tagBounds("headline").right && bar.right <= label.left)
    }
}
