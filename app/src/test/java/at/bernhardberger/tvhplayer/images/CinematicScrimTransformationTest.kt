package at.bernhardberger.tvhplayer.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CinematicScrimTransformationTest {
    @Test
    fun thePictureIsCroppedToTheFrameAndFadesFromItsTopRightToClearAtItsBottomLeft() {
        val input = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val output = runBlocking { CinematicScrimTransformation.transform(input, Size(160, 90)) }

        // Cropped to 16:9 at the decode's own resolution, not scaled down to the frame.
        assertEquals(400, output.width)
        assertEquals(225, output.height)
        // The kit's scrim is 10% at its centre on the top edge near the right, so 90% of the picture stays there.
        val corner = output.getPixel((output.width * 912.4f / 960f).toInt(), 0)
        assertEquals(230f, Color.alpha(corner).toFloat(), 3f)
        assertEquals(Color.WHITE and 0xffffff, corner and 0xffffff)
        assertEquals(0, Color.alpha(output.getPixel(0, output.height - 1)))
        // Along the top edge the picture fades steadily towards the left.
        val top = (output.width - 1 downTo 0 step 40).map { Color.alpha(output.getPixel(it, 0)) }
        assertTrue("$top", top.zipWithNext().all { (right, left) -> left <= right })
        assertTrue("$top", top.first() > top.last())
    }
}
