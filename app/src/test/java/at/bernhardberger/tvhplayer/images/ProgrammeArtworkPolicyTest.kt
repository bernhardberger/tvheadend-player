package at.bernhardberger.tvhplayer.images

import org.junit.Assert.*
import org.junit.Test

class ProgrammeArtworkPolicyTest {
    private val neutral = 0xff191c1e.toInt()

    @Test fun matchingLandscapeKeepsTheWholeImage() {
        assertEquals(ArtworkWindow(0, 0, 96, 54), attentionWindow(IntArray(96 * 54), 96, 54, 16.0 / 9, true))
        for ((width, height) in listOf(160 to 90, 1000 to 563, 392 to 221)) {
            assertFalse(needsArtworkTreatment(width, height, 196.0 / 110))
        }
        // The source is just inside the margin, but rounding the analysis height crosses it.
        val ratio = 16.0 / 9
        assertTrue(needsArtworkTreatment(130, 75, ratio))
        val (width, height) = artworkAnalysisSize(130, 75)
        val pixels = IntArray(width * height)
        for (window in listOf(titleCardWindow(pixels, width, height, ratio),
            titleBackdropWindow(pixels, width, height, ratio))) {
            assertEquals("treated windows retain the slot ratio", ratio, window.width / window.height, .0001)
        }
    }

    @Test fun titleBandStaysAboveOurText() {
        val crop = titleCardWindow(titlePixels(110..117), 96, 144, 16.0 / 9)
        assertTrue(crop.y <= 110)
        assertTrue((118 - crop.y) / crop.height < .55)
    }

    @Test fun denseTitleIsNotLostInTheTexturedBodyAboveIt() {
        // The lower-density body touches the logo and makes the initial band exceed 38% of the cover.
        val crop = titleCardWindow(titlePixels(96..105, textureRows = 45..95), 96, 144, 16.0 / 9)
        assertTrue("the title stays inside the picture", crop.y <= 96)
        assertTrue("the title clears the card's text instead of using the upper-scene fallback",
            (106 - crop.y) / crop.height < .55)
    }

    @Test fun bottomTitleCanExtendIntoBottomCornerFill() {
        val pixels = titlePixels(132..140)
        val crop = titleCardWindow(pixels, 96, 144, 16.0 / 9)
        assertTrue(crop.y + crop.height > 144)
        assertEquals(neutral, bottomArtworkFill(pixels, 96, 144, neutral))
        assertNotEquals(neutral, bottomArtworkFill(IntArray(96 * 144) { 0xffaa4422.toInt() }, 96, 144, neutral))
    }

    @Test fun noTitleOrFaceUsesTheUpperScene() {
        val crop = titleCardWindow(IntArray(96 * 144) { 0xff202020.toInt() }, 96, 144, 16.0 / 9)
        assertEquals(144 * .08, crop.y, .001)
    }

    @Test fun faceFallbackRoundsSkinChannelsAtTheThreshold() {
        val pixels = IntArray(96 * 144) { 0xff202020.toInt() }
        // Y=59.735 rounds to 60; truncation incorrectly loses this otherwise usable skin region.
        for (y in 60..65) for (x in 20..75) pixels[y * 96 + x] = 0xff642d1e.toInt()
        val crop = titleCardWindow(pixels, 96, 144, 16.0 / 9)
        assertEquals(60 - .10 * crop.height, crop.y, .001)
    }

    @Test fun coverGuttersHaveMatchingParity() {
        val crop = coverWindow(3, 4, 16, 9)
        assertEquals(crop.x, 16 - crop.x - crop.width)
        assertEquals(9, crop.height)
    }

    @Test fun coverClearsTheStartPlateWithTheSmallestShift() {
        val centered = coverWindow(3, 4, 356, 200)
        assertEquals(centered, coverWindow(3, 4, 356, 200, startClearance = centered.x))
        val plateEnd = centered.x + 4
        val shifted = coverWindow(3, 4, 356, 200, startClearance = plateEnd)
        assertEquals(plateEnd, shifted.x)
        assertEquals(centered.width, shifted.width)
        val rtl = coverWindow(3, 4, 356, 200, startClearance = plateEnd, plateAtEnd = true)
        assertEquals(shifted.x, 356 - rtl.x - rtl.width)
        val clamped = coverWindow(3, 4, 160, 90, startClearance = 100)
        assertEquals(92, clamped.x)
        assertEquals("an impossible start clearance must not push the cover outside", 160, clamped.x + clamped.width)
    }

    @Test fun flatBackdropIsAbsentButTextureRemains() {
        assertFalse(backdropHasScene(IntArray(96 * 54) { 0xff202020.toInt() }, 96, 54))
        assertTrue(backdropHasScene(IntArray(96 * 54) {
            if (it % 2 == 0) 0xff444444.toInt() else 0xff888888.toInt()
        }, 96, 54))
    }

    @Test fun backdropExcludesTheTitleBand() {
        val crop = titleBackdropWindow(titlePixels(30..38), 96, 144, 16.0 / 9)
        assertTrue(crop.y >= 39 + 144 * .03 || crop.y + crop.height <= 30 - 144 * .03)
    }

    private fun titlePixels(rows: IntRange, textureRows: IntRange = IntRange.EMPTY): IntArray = IntArray(96 * 144) {
        if (it % 2 == 0 && ((it / 96 in rows && it % 96 in 12..83) ||
                (it / 96 in textureRows && it % 96 in 12..47))) 0xffffffff.toInt() else 0xff202020.toInt()
    }

    @Test fun uprightAndSquareCoversKeepTheirFullHeightCentered() {
        for (width in listOf(60, 90, 100, 125)) {
            val bounds = coverWindow(width, 100, 160, 90)
            assertEquals(90, bounds.height)
            assertEquals(0, bounds.y)
            assertTrue(kotlin.math.abs(bounds.x * 2 + bounds.width - 160) <= 1)
        }
        assertTrue(usesCoverBox(125, 100))
        assertFalse(usesCoverBox(126, 100))
    }

    @Test fun unusableCornersProducePlainFallbackWithoutNoise() {
        for (color in listOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xff808080.toInt())) {
            assertTrue(artworkFill(IntArray(96 * 144) { color }, 96, 144, 32, 18, neutral).all { it == neutral })
        }
    }

    @Test fun yellowGreenIsLessSaturatedAndEveryUsableCornerIsDark() {
        val colors = fillCorners(IntArray(100) { 0xffaacc22.toInt() }, 10, 10, neutral)
        colors.forEach {
            val hsl = artworkHsl(it)
            // At this lightness, rounding RGB to bytes can move saturation by about .02.
            assertTrue(hsl[2] <= 0.22)
            assertTrue(hsl[1] in 0.055..0.125)
        }
    }

    @Test fun clashingUsableOppositeCornersBlendBeforeClamping() {
        val pixels = IntArray(100 * 100) { 0xff808080.toInt() }
        for (y in 0 until 10) for (x in 0 until 15) {
            pixels[y * 100 + x] = 0xffdd4422.toInt()
            pixels[(99 - y) * 100 + 99 - x] = 0xff2244dd.toInt()
        }
        val corners = fillCorners(pixels, 100, 100, neutral)
        assertEquals(corners[0], corners[3])
        assertNotEquals(neutral, corners[0])
        assertEquals(neutral, corners[1])
        assertEquals(neutral, corners[2])
        for (y in 0 until 10) for (x in 0 until 15) {
            pixels[y * 100 + x] = 0xffff0000.toInt()
            pixels[(99 - y) * 100 + 99 - x] = 0xff00ffff.toInt()
        }
        val desaturatedBlend = fillCorners(pixels, 100, 100, neutral)
        assertEquals(neutral, desaturatedBlend[0])
        assertEquals(neutral, desaturatedBlend[3])
    }

    @Test fun cropAvoidsPuttingDetailUnderTheLogoPlate() {
        val pixels = IntArray(96 * 144) { 0xff444444.toInt() }
        for (y in 65..78) for (x in 5..28) pixels[y * 96 + x] =
            if ((x + y) % 2 == 0) 0xffffffff.toInt() else 0xff000000.toInt()
        val window = attentionWindow(pixels, 96, 144, 16.0 / 9, true)
        // The detailed patch is retained, but above the bottom-start plate's top edge.
        assertTrue(window.y <= 65 && window.y + window.height >= 79)
        assertTrue(window.y + window.height * 0.64 > 78)
    }
}
