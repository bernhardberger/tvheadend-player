package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The marks row opening and closing draws a cut's span growing out of its start tick and back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingMarkTrackAnimationTest {
    @get:Rule val compose = createComposeRule()

    private val cut = RecordingMark(RecordingMarkKind.CUT, 2_000, 8_000)
    private var open by mutableStateOf(false)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.width(800.dp).height(10.dp).background(Color.Gray)) {
                    val marks = requireNotNull(timelineMarks(listOf(cut), 10_000, selected = cut.takeIf { open }, open = open))
                    TimelineMarkLayer(marks, { 0f }, Color(0xFFFF8E32), Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
    }

    /** True where the span's stripes alter the bar, at [fraction] of the bar's width. */
    private fun striped(fraction: Float): Boolean {
        val image = compose.onNodeWithTag("recording-marks-track").captureToImage().toPixelMap()
        return image[(image.width * fraction).toInt(), image.height / 2] != Color.Gray
    }

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    @Test fun theSpanGrowsOutOfTheTickWhenTheRowOpensAndSettlesOnTheFullSpan() {
        show()
        assertEquals(false, striped(0.5f))
        compose.runOnIdle { open = true }
        advance(16) // the frame that starts the animation
        advance(16)
        advance(30)
        assertEquals("near the tick", true, striped(0.25f))
        assertEquals("far end not reached yet", false, striped(0.75f))
        advance(400)
        assertEquals(true, striped(0.75f))
        assertEquals("beyond the span", false, striped(0.85f))
    }

    @Test fun theSpanShrinksBackToTheTickWhenTheRowCloses() {
        open = true
        show()
        assertEquals(true, striped(0.75f))
        compose.runOnIdle { open = false }
        advance(16) // the frame that starts the animation
        advance(60)
        assertEquals("far end gone", false, striped(0.75f))
        advance(400)
        assertEquals(false, striped(0.5f))
        val image = compose.onNodeWithTag("recording-marks-track").captureToImage().toPixelMap()
        val tick = (158..161).any { x -> (0 until image.height).any { y -> image[x, y] != Color.Gray } }
        assertEquals("the tick stays", true, tick)
    }
}
