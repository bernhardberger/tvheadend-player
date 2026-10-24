package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class TopLevelBrowseHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun normalHeaderKeepsTheMinimumSlot() = checkSlot(fontScale = 1f)
    @Test fun largeFilterGrowsTheSlotWithoutClippingOrOverlappingTheNextRow() = checkSlot(fontScale = 2f)

    @Test fun titleIsThePaneTitleAndNeverDrawn() {
        compose.setContent {
            TVHeadendPlayerTheme { TopLevelBrowseHeader("Channels", Modifier.testTag("header")) }
        }
        compose.onNodeWithTag("header").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Channels"))
        compose.onNodeWithText("Channels").assertDoesNotExist()
    }

    @Test fun filterStaysVisibleAtTheLeadingEdgeInTheSecondaryTone() {
        compose.setContent {
            TVHeadendPlayerTheme { TopLevelBrowseHeader("News", Modifier.testTag("header"), filter = "Movies") }
        }
        compose.onNodeWithText("News").assertDoesNotExist()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(TvTextColors.secondary, layouts.single().layoutInput.style.color)
        assertEquals(0f, compose.onNodeWithText("Movies").fetchSemanticsNode().boundsInRoot.left, .1f)
    }

    private fun checkSlot(fontScale: Float) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                TVHeadendPlayerTheme {
                    Column(Modifier.fillMaxSize()) {
                        TopLevelBrowseHeader("Channels", Modifier.testTag("header"), filter = "News")
                        Text("Next row")
                    }
                }
            }
        }
        val header = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val filter = compose.onNodeWithText("News")
        val layouts = mutableListOf<TextLayoutResult>()
        filter.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals("The requested font scale reaches the filter", fontScale, layout.layoutInput.density.fontScale, .001f)
        val filterBounds = filter.fetchSemanticsNode().boundsInRoot
        assertFalse("The filter has no visual overflow", layout.hasVisualOverflow)
        assertTrue("The full line fits in the slot", header.height >= layout.size.height)
        assertTrue("Filter stays within the header", filterBounds.top >= header.top && filterBounds.bottom <= header.bottom)
        assertTrue("The next row follows the grown header",
            compose.onNodeWithText("Next row").fetchSemanticsNode().boundsInRoot.top >= header.bottom)
        if (fontScale == 1f) assertEquals(40f, header.height, .1f)
        else assertTrue("Large text grows beyond the 40dp minimum (header=$header, text=${layout.size})", header.height > 40f)
    }
}
