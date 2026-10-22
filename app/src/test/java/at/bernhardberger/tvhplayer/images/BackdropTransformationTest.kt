package at.bernhardberger.tvhplayer.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackdropTransformationTest {
    private val adaptive = BackdropTransformation(16, 1, dimAlpha = 0.6f, minDimAlpha = 0.4f)

    @Test
    fun anOrdinaryOrBrightPictureTakesTheFullDim() {
        assertEquals("ordinary", 0.4f * 102, red(adaptive, 102), 2f)
        assertEquals("bright", 0.4f * 230, red(adaptive, 230), 2f)
    }

    @Test
    fun aDarkPictureIsDimmedLessButNeverBelowTheFloor() {
        // An ordinary picture, 0.4 bright, ends at 0.16. 77 is 0.3 bright: dimmed by 0.47 to reach it.
        assertEquals("darker", 0.16f / 0.3f * 77, red(adaptive, 77), 2f)
        // 51 is 0.2 bright: it would need 0.2, so it stops at the floor of 0.4.
        assertEquals("dark", 0.6f * 51, red(adaptive, 51), 2f)
        assertEquals("very dark", 0.6f * 20, red(adaptive, 20), 2f)
    }

    @Test
    fun withoutAFloorEveryPictureTakesTheSameDim() {
        assertEquals(0.4f * 51, red(BackdropTransformation(16, 1, dimAlpha = 0.6f), 51), 2f)
    }

    private fun red(transformation: BackdropTransformation, grey: Int): Float {
        val input = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(grey, grey, grey)) }
        val output = runBlocking { transformation.transform(input, Size(32, 18)) }
        return Color.red(output.getPixel(8, 4)).toFloat()
    }
}
