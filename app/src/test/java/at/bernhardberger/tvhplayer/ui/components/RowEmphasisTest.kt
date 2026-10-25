package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RowEmphasisTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customQuietScaleDoesNotChangePlayerDefaultAndInterpolates() {
        val emphasis = mutableFloatStateOf(0f)
        compose.setContent {
            Column {
                Box(Modifier.size(100.dp).rowHeaderEmphasis { emphasis.floatValue }.testTag("default"))
                Box(Modifier.size(100.dp).rowHeaderEmphasis(18f / 28f) { emphasis.floatValue }.testTag("details"))
                Box(Modifier.size(100.dp).testTag("full"))
            }
        }
        fun scale(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.width /
            compose.onNodeWithTag("full").fetchSemanticsNode().boundsInRoot.width
        assertEquals(14f / 24f, scale("default"), .001f)
        assertEquals(18f / 28f, scale("details"), .001f)
        compose.runOnIdle { emphasis.floatValue = .5f }
        assertEquals((18f / 28f + 1f) / 2f, scale("details"), .001f)
        compose.runOnIdle { emphasis.floatValue = 1f }
        assertEquals(1f, scale("details"), .001f)
    }

    @Test fun dimmedPlateAndArtCompositeAsOneUnit() {
        lateinit var view: android.view.View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.size(100.dp).rowContentEmphasis { 0f }.background(Color.Red)) {
                        Box(Modifier.padding(20.dp).size(40.dp).background(Color.Black.copy(alpha = .6f)))
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(android.graphics.Canvas(bitmap)) }
        // Plate remains 60% black relative to its art, before the whole row receives 40% alpha.
        assertEquals(102f, android.graphics.Color.red(bitmap.getPixel(10, 10)).toFloat(), 2f)
        assertEquals(41f, android.graphics.Color.red(bitmap.getPixel(60, 60)).toFloat(), 2f)
        bitmap.recycle()
    }

    @Test fun emphasisFallsLinearlyOnEitherSideAndClamps() {
        for ((distance, expected) in listOf(-2f to 0f, -1f to 0f, -.25f to .75f,
            0f to 1f, .5f to .5f, 1f to 0f, 2f to 0f)) {
            assertEquals(expected, rowEmphasis(distance), .0001f)
        }
    }
}
