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

    @Test
    fun theGuidePictureFadesToClearAlongItsWholeLeftAndBottomEdgesAndKeepsMoreThanTheKitScrim() {
        val input = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val output = runBlocking { GuideScrimTransformation.transform(input, Size(160, 90)) }
        val kit = runBlocking { CinematicScrimTransformation.transform(input, Size(160, 90)) }
        fun alpha(bitmap: Bitmap, x: Int, y: Int) = Color.alpha(bitmap.getPixel(x, y))

        assertEquals(400, output.width)
        assertEquals(225, output.height)
        // No cut: clear along the whole left column, top row included, and the whole bottom row.
        val left = (0 until output.height).map { alpha(output, 0, it) }
        assertTrue("$left", left.all { it == 0 })
        val bottom = (0 until output.width).map { alpha(output, it, output.height - 1) }
        assertTrue("$bottom", bottom.all { it == 0 })
        // The picture is gone from 64% of its height down, where the time ruler sits.
        assertEquals(0, alpha(output, output.width - 1, (output.height * .64f).toInt() + 1))
        // Along the top row it rises steadily from the left edge to about a third of 90% at the top-right,
        // the header fading it.
        val top = (0 until output.width step 20).map { alpha(output, it, 0) }
        assertTrue("$top", top.zipWithNext().all { (left, right) -> right >= left })
        assertEquals(80f, alpha(output, output.width - 1, 0).toFloat(), 3f)
        // Wider and softer than the kit's scrim: past the header band it keeps at least as much.
        val belowHeader = (output.height * .35f).toInt() + 1
        val wider = (output.width / 2 until output.width step 20).map { x ->
            Pair(x, alpha(output, x, belowHeader) - alpha(kit, x, belowHeader))
        }
        assertTrue("$wider", wider.all { it.second >= -2 })
    }

    @Test
    fun theImmersivePictureIsTheKitsWithItsTopEasedOutUnderTheHeader() {
        val input = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val output = runBlocking { ImmersiveScrimTransformation.transform(input, Size(160, 90)) }
        val kit = runBlocking { CinematicScrimTransformation.transform(input, Size(160, 90)) }
        fun alpha(bitmap: Bitmap, x: Int, y: Int) = Color.alpha(bitmap.getPixel(x, y))
        val x = (output.width * 912.4f / 960f).toInt()

        assertEquals(alpha(kit, x, 0) * .35f, alpha(output, x, 0).toFloat(), 3f)
        val below = (output.height * .25f).toInt() + 1
        assertEquals(alpha(kit, x, below).toFloat(), alpha(output, x, below).toFloat(), 2f)
    }
}
