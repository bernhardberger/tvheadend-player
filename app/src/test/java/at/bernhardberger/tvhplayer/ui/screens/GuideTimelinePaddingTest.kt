package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.screens.guide.guideFocusInsets
import at.bernhardberger.tvhplayer.ui.screens.guide.guideReadableTimeRange
import at.bernhardberger.tvhplayer.ui.screens.guide.guideMinReadableOverlapSec
import at.bernhardberger.tvhplayer.ui.screens.guide.guideFocusWindowStart
import at.bernhardberger.tvhplayer.ui.screens.guide.guideInnerContinuation
import at.bernhardberger.tvhplayer.ui.screens.guide.guideTerminalViewportReserve
import at.bernhardberger.tvhplayer.ui.screens.guide.guideVisibleWindowSec
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideTimelinePaddingTest {
    @Test fun minimumReadableOverlapUsesTheTracksSecondsPerPixelAndRoundsUp() {
        assertEquals(515L, guideMinReadableOverlapSec(10800, 1007f, 48f))
        assertEquals(515L, guideMinReadableOverlapSec(10800, 2014f, 96f))
        assertEquals(720L, guideMinReadableOverlapSec(10800, 720f, 48f))
        assertEquals(1L, guideMinReadableOverlapSec(10800, 0f, 48f))
    }

    @Test fun readableSecondsRoundInsideBothPhysicalInsets() {
        assertEquals(1601L..11713L, guideReadableTimeRange(1000, 10800, 1007f, 56f, 8f))
        assertEquals(1086L..11198L, guideReadableTimeRange(1000, 10800, 1007f, 8f, 56f))
        val range = guideReadableTimeRange(1000, 10800, 1007f, 56f, 8f)
        assertEquals(1000L, guideFocusWindowStart(range.first, range.last + 1, 1000, 10800,
            1007f, 56f, 8f, 0, 20000))
        assertEquals(999L, guideFocusWindowStart(range.first - 1, range.last, 1000, 10800,
            1007f, 56f, 8f, 0, 20000))
    }

    @Test fun unmeasuredTrackUsesTheWindowUntilGeometryArrives() {
        assertEquals(1000L..11799L, guideReadableTimeRange(1000, 10800, 0f, 56f, 8f))
    }

    @Test
    fun timelineRetainsLeadingInsetAndUsesEdgeToEdgeViewport() {
        val padding = guideTimelineContentPadding(
            contentPadding = PaddingValues(
                start = 24.dp,
                top = 32.dp,
                end = 48.dp,
                bottom = 32.dp,
            ),
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(50.dp, padding.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, padding.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(8.dp, padding.calculateTopPadding())
        assertEquals(32.dp, padding.calculateBottomPadding())
    }

    @Test
    fun timelineResolvesAbsoluteInsetsForRtl() {
        val padding = guideTimelineContentPadding(
            contentPadding = PaddingValues.Absolute(
                left = 24.dp,
                top = 32.dp,
                right = 48.dp,
                bottom = 32.dp,
            ),
            layoutDirection = LayoutDirection.Rtl,
        )

        assertEquals(74.dp, padding.calculateStartPadding(LayoutDirection.Rtl))
        assertEquals(0.dp, padding.calculateEndPadding(LayoutDirection.Rtl))
    }

    @Test
    fun onlyThePhysicalOuterContinuationControlsTerminalReserve() {
        for (gutterContinuation in listOf(false, true)) {
            assertEquals(8.dp, guideTerminalViewportReserve(gutterContinuation, false, false))
            assertEquals(0.dp, guideTerminalViewportReserve(gutterContinuation, true, false))
            assertEquals(8.dp, guideTerminalViewportReserve(false, gutterContinuation, true))
            assertEquals(0.dp, guideTerminalViewportReserve(true, gutterContinuation, true))
        }
    }

    @Test
    fun onlyTheEdgeNextToTheChannelColumnFadesAndNeedsTheFadeInFocusInsets() {
        // LTR: the column is on the left, so earlier content is the inner edge; RTL mirrors it.
        assertEquals(true, guideInnerContinuation(true, false, false))
        assertEquals(false, guideInnerContinuation(false, true, false))
        assertEquals(true, guideInnerContinuation(false, true, true))
        assertEquals(false, guideInnerContinuation(true, false, true))
        assertEquals(56.dp to 8.dp, guideFocusInsets(true, true, false))
        assertEquals(8.dp to 8.dp, guideFocusInsets(false, true, false))
        assertEquals(8.dp to 56.dp, guideFocusInsets(true, true, true))
        assertEquals(8.dp to 8.dp, guideFocusInsets(true, false, true))
    }

    @Test
    fun terminalReservePadsTheTrackWithoutSubtractingTimeCapacityAgain() {
        val reserve = guideTerminalViewportReserve(false, false, false)
        for (direction in LayoutDirection.entries) {
            val padding = guideTimelineContentPadding(
                PaddingValues(start = 24.dp, top = 32.dp, end = 48.dp, bottom = 32.dp), direction, reserve,
            )
            assertEquals(50.dp, padding.calculateStartPadding(direction))
            assertEquals(8.dp, padding.calculateEndPadding(direction))
            assertEquals(8.dp, padding.calculateTopPadding())
            assertEquals(32.dp, padding.calculateBottomPadding())
        }
        assertEquals(10800L, guideVisibleWindowSec(650f, 32, 1f))
        assertEquals(7200L, guideVisibleWindowSec(650f, 43, 1f))
        assertEquals("A 176dp gutter at the shared inset would lose an hour", 7200L, guideVisibleWindowSec(646f, 32, 1f))
    }
}
