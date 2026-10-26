package at.bernhardberger.tvhplayer.ui.screens.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideHeadlineDropTest {
    @Test
    fun aRowEndingFlushWithTheEdgeMovesTheGridSoTheCutRowShowsWhatIsWanted() {
        // 1080px screen, grid from 480px, rows 120px apart: five rows end flush with the edge.
        val drop = guideHeadlineDropPx(viewportPx = 1080, gridTopPx = 480, stridePx = 120, wantedPx = 77)
        assertEquals(43, drop)
        assertEquals(77, (1080 - 480 - drop).mod(120))
    }

    @Test
    fun aCutRowShowingMoreThanWantedMovesDownByTheDifference() {
        assertEquals(10, guideHeadlineDropPx(viewportPx = 1080, gridTopPx = 513, stridePx = 120, wantedPx = 77))
    }

    @Test
    fun theDropNeverExceedsHalfAStrideOfWholePixels() {
        // An odd stride at a fractional density: half of 91px is 45px, so a drop of 45 applies and 46 does not.
        assertEquals(45, guideHeadlineDropPx(viewportPx = 810, gridTopPx = 360, stridePx = 91, wantedPx = 41))
        assertEquals(0, guideHeadlineDropPx(viewportPx = 810, gridTopPx = 360, stridePx = 91, wantedPx = 40))
        for (gridTop in 300..420) for (wanted in 1..90) {
            val drop = guideHeadlineDropPx(viewportPx = 810, gridTopPx = gridTop, stridePx = 91, wantedPx = wanted)
            assertTrue("gridTop=$gridTop wanted=$wanted drop=$drop", drop in 0..45)
        }
    }
}
