package at.bernhardberger.tvhplayer.images

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogoInkTest {
    @Test fun whiteLetteringOnTheGroundTurnsDarkAndKeepsItsShape() {
        val logo = Logo().bars(WHITE, 40, 60)
        val inked = checkNotNull(inkFadingLogoParts(logo.pixels, W, H, FOCUSED_ROW))
        val stroke = inked[logo.at(45, 120)]
        assertEquals(0xFF, stroke ushr 24)
        assertEquals(0, stroke and 0xFFFFFF)
        assertEquals(0, inked[logo.at(5, 5)])
    }

    @Test fun platesBrandColoursAndLetteringOnTheLogosOwnColoursStayAsTheyAre() {
        val plate = Logo().box(WHITE, 60, 40, 340, 200).bars(DARK, 90, 70, top = 70, bottom = 170)
        assertNull(inkFadingLogoParts(plate.pixels, W, H, FOCUSED_ROW))
        val brandBlock = Logo().box(ORANGE, 100, 60, 300, 180)
        assertNull(inkFadingLogoParts(brandBlock.pixels, W, H, FOCUSED_ROW))
        val onOwnColour = Logo().box(RED, 40, 40, 360, 200).bars(WHITE, 70, 80, top = 70, bottom = 170)
        assertNull(inkFadingLogoParts(onOwnColour.pixels, W, H, FOCUSED_ROW))
    }

    @Test fun aPaleColouredPartDarkensOnlyBesideAPartThatVanishes() {
        val alone = Logo().bars(PALE_GOLD, 220, 60)
        assertNull(inkFadingLogoParts(alone.pixels, W, H, FOCUSED_ROW))
        val beside = Logo().bars(WHITE, 20, 60).bars(PALE_GOLD, 220, 60)
        val inked = checkNotNull(inkFadingLogoParts(beside.pixels, W, H, FOCUSED_ROW))
        assertNotEquals(PALE_GOLD, inked[beside.at(225, 120)])
        assertEquals(PALE_GOLD and 0xFF000000.toInt(), inked[beside.at(225, 120)] and 0xFF000000.toInt())
    }

    @Test fun aLogoWithoutPalePartsIsLeftAlone() {
        val dark = Logo().bars(DARK, 40, 60)
        assertNull(inkFadingLogoParts(dark.pixels, W, H, FOCUSED_ROW))
        val empty = IntArray(W * H)
        assertNull(inkFadingLogoParts(empty, W, H, FOCUSED_ROW))
        assertArrayEquals(IntArray(W * H), empty)
    }

    private class Logo {
        val pixels = IntArray(W * H)
        fun at(x: Int, y: Int) = y * W + x

        fun box(color: Int, left: Int, top: Int, right: Int, bottom: Int) = apply {
            for (y in top until bottom) for (x in left until right) pixels[at(x, y)] = color
        }

        /** Five upright strokes, like a short wordmark, from [left] with [pitch] between them. */
        fun bars(color: Int, left: Int, pitch: Int, top: Int = 60, bottom: Int = 180) = apply {
            repeat(5) { box(color, left + it * pitch / 2, top, left + it * pitch / 2 + 12, bottom) }
        }
    }

    private companion object {
        const val W = 400
        const val H = 240
        const val FOCUSED_ROW = 0xFFE1E2E5.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val DARK = 0xFF202020.toInt()
        const val RED = 0xFFC8102E.toInt()
        const val ORANGE = 0xFFE5631A.toInt()
        const val PALE_GOLD = 0xFFE6C34A.toInt()
    }
}
