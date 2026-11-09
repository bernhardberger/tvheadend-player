package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A card past its row's start and the text on it, on black, at the screen's own pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class MutedPastRowStartTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var state: LazyListState

    private fun show(emphasis: State<Float>, focusedIndex: Int = -1) {
        compose.setContent {
            state = rememberLazyListState()
            LazyRow(Modifier.background(Color.Black), state, PaddingValues(start = 200.dp)) {
                itemsIndexed(List(20) { it }) { index, _ ->
                    MutedPastRowStart(state, index, 0f, { emphasis.value }, { index == focusedIndex }) {
                        Column {
                            Box(Modifier.size(100.dp).background(Color.White).testTag("card-$index"))
                            Box(Modifier.size(100.dp).mutedTextPastRowStart().background(Color.White).testTag("text-$index"))
                        }
                    }
                }
            }
        }
        compose.runOnIdle { runBlocking { state.scrollToItem(1) } }
        compose.waitForIdle()
    }

    private fun brightness(tag: String): Float {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return compose.onRoot().captureToImage().toPixelMap()[bounds.center.x.toInt(), bounds.center.y.toInt()].red
    }

    @Test fun textIsGoneAndTheCardStaysDimInTheFocusedRow() {
        val emphasis = mutableStateOf(1f)
        show(emphasis)
        assertEquals(RowPastStartAlpha, brightness("card-0"), 0.01f)
        assertEquals(0f, brightness("text-0"), 0.01f)
        // The row's own dimming is its parent's; this is only what is added to that.
        compose.runOnIdle { emphasis.value = 0f }
        assertEquals(0.12f / RowDimAlpha, brightness("card-0"), 0.01f)
        assertEquals(0f, brightness("text-0"), 0.01f)
    }

    @Test fun aFocusedCardAndACardInPlaceKeepTheirText() {
        show(emphasis = mutableStateOf(1f), focusedIndex = 0)
        assertEquals(1f, brightness("text-0"), 0.01f)
        assertEquals(1f, brightness("text-1"), 0.01f)
    }
}
