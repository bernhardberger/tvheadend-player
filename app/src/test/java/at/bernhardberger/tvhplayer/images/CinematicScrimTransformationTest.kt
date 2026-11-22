package at.bernhardberger.tvhplayer.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import at.bernhardberger.tvhplayer.core.detailsRelativeLuminance
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
        val swatch = Color.rgb(33, 42, 51)
        val input = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(swatch) }
        val output = runBlocking { CinematicScrimTransformation(1f, false).transform(input, Size(160, 90)) }

        // Cropped to 16:9 at the decode's own resolution, not scaled down to the frame.
        assertEquals(400, output.width)
        assertEquals(225, output.height)
        // The kit's scrim is 10% at its centre on the top edge near the right, so 90% of the picture stays there.
        val corner = output.getPixel((output.width * 912.4f / 960f).toInt(), 0)
        assertEquals(230f, Color.alpha(corner).toFloat(), 3f)
        assertEquals(Color.red(swatch).toFloat(), Color.red(corner).toFloat(), 1f)
        assertEquals(Color.green(swatch).toFloat(), Color.green(corner).toFloat(), 1f)
        assertEquals(Color.blue(swatch).toFloat(), Color.blue(corner).toFloat(), 1f)
        assertEquals(0, Color.alpha(output.getPixel(0, output.height - 1)))
        // Along the top edge the picture fades steadily towards the left.
        val top = (output.width - 1 downTo 0 step 40).map { Color.alpha(output.getPixel(it, 0)) }
        assertTrue("$top", top.zipWithNext().all { (right, left) -> left <= right })
        assertTrue("$top", top.first() > top.last())
    }

    @Test
    fun theGuidePictureFadesToClearAlongItsWholeLeftAndBottomEdgesAndKeepsMoreThanTheKitScrim() {
        val input = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val output = runBlocking { GuideScrimTransformation(1f, false).transform(input, Size(160, 90)) }
        val kit = runBlocking { CinematicScrimTransformation(1f, false).transform(input, Size(160, 90)) }
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
        // Along the top row it rises steadily from the left edge to 90% at the top-right.
        val top = (0 until output.width step 20).map { alpha(output, it, 0) }
        assertTrue("$top", top.zipWithNext().all { (left, right) -> right >= left })
        assertEquals(230f, alpha(output, output.width - 1, 0).toFloat(), 3f)
        // Wider and softer than the kit's scrim: across the picture's top half it keeps at least as much.
        val wider = (output.width / 2 until output.width step 20).flatMap { x ->
            (0 until output.height / 3 step 15).map { y -> Triple(x, y, alpha(output, x, y) - alpha(kit, x, y)) }
        }
        assertTrue("$wider", wider.all { it.third >= -2 })
    }

    @Test
    fun whiteArtKeepsClockInkReadableOnTheHeaderEndInBothDirections() = runBlocking {
        val failures = mutableListOf<String>()
        for (guide in listOf(false, true)) for (rtl in listOf(false, true)) {
            val alpha = listOf(255, 192).map { shade ->
                val width = if (guide) 480 else 758
                val art = Bitmap.createBitmap(width, width * 9 / 16, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.rgb(shade, shade, shade))
                }
                val mask = if (guide) GuideScrimTransformation(1f, rtl) else CinematicScrimTransformation(1f, rtl)
                val output = mask.transform(art, Size(art.width, art.height))
                fun xAt(end: Int) = if (rtl) end else width - 1 - end
                val backgrounds = (24 until 76).flatMap { y ->
                    (55 until 160).map { end ->
                        26 + (shade - 26) * Color.alpha(output.getPixel(xAt(end), y)) / 255.0
                    }
                }.sorted()
                val background = backgrounds[(backgrounds.size * .9).toInt()]
                val luminance = detailsRelativeLuminance(background / 255, background / 255, background / 255)
                fun dateChannel(ink: Int) = (ink * .8 + background * .2) / 255
                val date = detailsRelativeLuminance(dateChannel(192), dateChannel(199), dateChannel(205))
                val time = detailsRelativeLuminance(225 / 255.0, 226 / 255.0, 229 / 255.0)
                val timeContrast = (time + .05) / (luminance + .05)
                val dateContrast = (date + .05) / (luminance + .05)
                val scenario = "guide=$guide rtl=$rtl shade=$shade time=$timeContrast date=$dateContrast"
                if (timeContrast < 4.5 || dateContrast < 4.5 || (shade == 255 && dateContrast >= 4.75)) failures += scenario
                Color.alpha(output.getPixel(xAt(95), 48))
            }
            if (alpha[1] <= alpha[0]) failures += "guide=$guide rtl=$rtl: $alpha"
        }
        assertTrue("$failures", failures.isEmpty())
    }

    @Test
    fun mixedArtKeepsAtLeastNinetyPercentOfTheClockAreaReadable() = runBlocking {
        // At maximum shade the gray ranks brighter, but with no shade most of the red fails.
        val red = Color.rgb(215, 0, 0)
        val gray = Color.rgb(66, 66, 66)
        val mixed = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888).apply {
            eraseColor(red)
            setPixels(IntArray(21 * 52) { gray }, 0, 21, 320, 24, 21, 52)
        }
        val band = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
            setPixels(IntArray(480 * 6) { Color.WHITE }, 0, 480, 0, 60, 480, 6)
        }
        assertClockCoverage("Red/gray split", mixed)
        assertClockCoverage("White band on black", band)
    }

    private suspend fun assertClockCoverage(input: String, art: Bitmap) {
        val output = GuideScrimTransformation(1f, false).transform(art, Size(art.width, art.height))
        val readable = (24 until 76).sumOf { y ->
            (55 until 160).count { end ->
                val pixel = output.getPixel(output.width - 1 - end, y)
                val alpha = Color.alpha(pixel) / 255.0
                fun background(channel: Int) = (channel * alpha + 26 * (1 - alpha)) / 255
                val r = background(Color.red(pixel))
                val g = background(Color.green(pixel))
                val b = background(Color.blue(pixel))
                fun date(ink: Int, bg: Double) = .8 * ink / 255 + .2 * bg
                val foreground = detailsRelativeLuminance(date(192, r), date(199, g), date(205, b))
                val contrast = (foreground + .05) / (detailsRelativeLuminance(r, g, b) + .05)
                contrast >= 4.5
            }
        } / (52 * 105.0)
        assertTrue("$input clock area readable share: $readable", readable >= .90)
    }

    @Test
    fun darkArtKeepsTheUnshadedMaskPixelForPixel() = runBlocking {
        val input = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(33, 42, 51))
        }
        val size = Size(160, 90)
        val unshaded = 8f // At this density the 90px frame stops before the clock.
        for (rtl in listOf(false, true)) {
            val guide = GuideScrimTransformation(1f, rtl).transform(input, size)
            assertTrue(guide.sameAs(GuideScrimTransformation(unshaded, rtl).transform(input, size)))
            val cinematic = CinematicScrimTransformation(1f, rtl).transform(input, size)
            assertTrue(cinematic.sameAs(CinematicScrimTransformation(unshaded, rtl).transform(input, size)))
        }
    }

    @Test
    fun clockFadeHasNoStepAcrossTheHeaderActionsOrClock() = runBlocking {
        val input = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val output = GuideScrimTransformation(1f, false).transform(input, Size(480, 270))
        val row = (560..920).map { Color.alpha(output.getPixel(it - 480, 48)) }
        // The existing Guide mask is flat from x768 on this row: any increase there is in the clock fade.
        val plateau = row.drop(768 - 560)
        assertTrue(plateau.first() > plateau.last())
        assertTrue(plateau.zipWithNext().all { (left, right) -> right <= left })
        assertTrue(row.zipWithNext().all { (left, right) -> kotlin.math.abs(right - left) <= 3 })
    }
}
