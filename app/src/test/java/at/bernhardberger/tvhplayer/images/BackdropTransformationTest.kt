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
    private val blur = BackdropTransformation(16, 1)

    @Test
    fun theBlurKeepsADarkAndABrightPicturesBrightness() {
        for (grey in listOf(20, 51, 230)) {
            // Stripes, so the blur has something to average: its mean must stay the picture's.
            val input = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888)
            for (x in 0 until 32) for (y in 0 until 18) {
                val value = if (x % 4 < 2) grey else grey / 2
                input.setPixel(x, y, Color.rgb(value, value, value))
            }
            val output = runBlocking { blur.transform(input, Size(32, 18)) }
            assertEquals("grey $grey", meanRed(input), meanRed(output), 2f)
        }
    }

    private fun meanRed(bitmap: Bitmap): Float {
        var sum = 0L
        for (x in 0 until bitmap.width) for (y in 0 until bitmap.height) sum += Color.red(bitmap.getPixel(x, y))
        return sum.toFloat() / (bitmap.width * bitmap.height)
    }
}
