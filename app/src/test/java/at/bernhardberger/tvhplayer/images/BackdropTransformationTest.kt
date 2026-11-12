package at.bernhardberger.tvhplayer.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    fun analysisReadsOnlyOneSourceRowAndKeepsReferenceReduction() {
        val reduced = artworkAnalysis(4, 4, 2, 2) { y, row ->
            assertEquals("bounded source scratch row", 4, row.size)
            for (x in 0 until 4) row[x] = Color.rgb(x * 40, y * 30, 10)
        }
        // Reference widened-triangle reduction, including byte rounding after the horizontal pass.
        assertTrue(intArrayOf(Color.rgb(29, 21, 10), Color.rgb(91, 21, 10),
            Color.rgb(29, 69, 10), Color.rgb(91, 69, 10)).contentEquals(reduced))
    }

    @Test
    fun skinnyAndSinglePixelImagesHaveBoundedAnalysis() = runBlocking {
        for ((width, height) in listOf(1 to 2880, 2 to 2880, 1 to 1, 2880 to 1, 48 to 64)) {
            val (analysisWidth, analysisHeight) = artworkAnalysisSize(width, height)
            assertEquals("reference analysis width, including small sources", 96, analysisWidth)
            assertEquals("proportional height capped for pathological sources",
                kotlin.math.round(height * 96.0 / width).toInt().coerceIn(1, 256), analysisHeight)
            assertTrue("analysis pixel budget", analysisWidth * analysisHeight <= 96 * 256)
            assertTrue("analysis height bound", analysisHeight <= 256)
            val input = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            for (mode in listOf(ProgrammeArtworkMode.CROP, ProgrammeArtworkMode.BOX, ProgrammeArtworkMode.TITLE_CARD)) {
                val output = ProgrammeArtworkTransformation(mode).transform(input, Size(160, 90))
                assertTrue(output.width > 0 && output.height > 0)
            }
        }
    }

    @Test
    fun landscapeInTheRailKeepsBothImageEdges() = runBlocking {
        val input = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        for (x in 0 until input.width) {
            input.setPixel(x, 0, Color.RED)
            input.setPixel(x, 89, Color.BLUE)
        }
        val output = ProgrammeArtworkTransformation(ProgrammeArtworkMode.TITLE_CARD, ratio = 196.0 / 110)
            .transform(input, Size(196, 110))
        assertEquals(Color.RED, output.getPixel(output.width / 2, 0))
        assertEquals(Color.BLUE, output.getPixel(output.width / 2, output.height - 1))
    }

    @Test
    fun nearLandscapeFlatBackdropsAreNotRejected() = runBlocking {
        for ((width, height) in listOf(1000 to 563, 392 to 221)) {
            val input = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            val output = ProgrammeArtworkTransformation(ProgrammeArtworkMode.BACKDROP).transform(input, Size(160, 90))
            assertEquals(Color.BLUE, output.getPixel(80, 45))
        }
    }

    @Test
    fun matchingLandscapeIsTheOriginalBitmapInEveryMode() = runBlocking {
        val landscape = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        for (mode in ProgrammeArtworkMode.entries) {
            assertSame(landscape, ProgrammeArtworkTransformation(mode).transform(landscape, Size(160, 90)))
        }
    }

    @Test
    fun boxedPortraitKeepsTheWholeCoverWithHardEdges() = runBlocking {
        val portrait = Bitmap.createBitmap(60, 90, Bitmap.Config.ARGB_8888)
        portrait.eraseColor(Color.WHITE)
        for (x in 0 until 60) {
            portrait.setPixel(x, 0, Color.RED)
            portrait.setPixel(x, 89, Color.BLUE)
        }
        val boxed = ProgrammeArtworkTransformation(ProgrammeArtworkMode.BOX, fallback = Color.DKGRAY)
            .transform(portrait, Size(160, 90))
        assertEquals(160, boxed.width)
        assertEquals(90, boxed.height)
        assertEquals(Color.RED, boxed.getPixel(80, 0))
        assertEquals(Color.BLUE, boxed.getPixel(80, 89))
        assertNotEquals(Color.WHITE, boxed.getPixel(0, 45))
        assertEquals(Color.WHITE, boxed.getPixel(50, 45))
        assertEquals(Color.WHITE, boxed.getPixel(109, 45))
        assertNotEquals(Color.WHITE, boxed.getPixel(110, 45))
    }

    @Test
    fun cacheKeysDistinguishEveryArtworkTreatment() {
        val crop = ProgrammeArtworkTransformation().cacheKey
        for (variant in listOf(ProgrammeArtworkTransformation(ProgrammeArtworkMode.BOX),
            ProgrammeArtworkTransformation(ProgrammeArtworkMode.TITLE_CARD),
            ProgrammeArtworkTransformation(ProgrammeArtworkMode.BACKDROP),
            ProgrammeArtworkTransformation(ratio = 4.0 / 3), ProgrammeArtworkTransformation(avoidPlate = true),
            ProgrammeArtworkTransformation(plateAtEnd = true), ProgrammeArtworkTransformation(fallback = Color.DKGRAY))) {
            assertNotEquals(crop, variant.cacheKey)
        }
    }

    @Test
    fun bottomTitleLeavesTheBottomCornerFill() = runBlocking {
        val titleCover = Bitmap.createBitmap(96, 144, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        for (y in 132..140) for (x in 12..83 step 2) titleCover.setPixel(x, y, Color.WHITE)
        val titleCard = ProgrammeArtworkTransformation(ProgrammeArtworkMode.TITLE_CARD, fallback = Color.DKGRAY)
            .transform(titleCover, Size(160, 90))
        assertEquals(Color.DKGRAY, titleCard.getPixel(80, 89))
    }

    @Test
    fun backdropsRejectFlatArtButKeepSceneBearingArt() {
        val flat = Bitmap.createBitmap(96, 144, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        org.junit.Assert.assertThrows(NoProgrammeBackdropException::class.java) {
            runBlocking { ProgrammeArtworkTransformation(ProgrammeArtworkMode.BACKDROP).transform(flat, Size(160, 90)) }
        }
        val scene = Bitmap.createBitmap(96, 144, Bitmap.Config.ARGB_8888)
        for (y in 0 until scene.height) for (x in 0 until scene.width) {
            scene.setPixel(x, y, if (x % 2 == 0) Color.DKGRAY else Color.WHITE)
        }
        val output = runBlocking {
            ProgrammeArtworkTransformation(ProgrammeArtworkMode.BACKDROP).transform(scene, Size(160, 90))
        }
        assertEquals(160, output.width)
        assertEquals(90, output.height)
    }

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
