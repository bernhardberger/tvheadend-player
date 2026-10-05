package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.screens.guide.guideTerminalViewportReserve
import at.bernhardberger.tvhplayer.ui.screens.guide.guideVisibleWindowSec
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideTimelinePaddingTest {
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
        assertEquals(56.dp, padding.calculateBottomPadding())
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
            assertEquals(64.dp, guideTerminalViewportReserve(gutterContinuation, false, false, 16.dp))
            assertEquals(0.dp, guideTerminalViewportReserve(gutterContinuation, true, false, 16.dp))
            assertEquals(64.dp, guideTerminalViewportReserve(false, gutterContinuation, true, 16.dp))
            assertEquals(0.dp, guideTerminalViewportReserve(true, gutterContinuation, true, 16.dp))
        }
    }

    @Test
    fun terminalReservePadsTheTrackWithoutSubtractingTimeCapacityAgain() {
        val reserve = guideTerminalViewportReserve(false, false, false, 16.dp)
        for (direction in LayoutDirection.entries) {
            val padding = guideTimelineContentPadding(
                PaddingValues(start = 24.dp, top = 32.dp, end = 48.dp, bottom = 32.dp), direction, reserve,
            )
            assertEquals(50.dp, padding.calculateStartPadding(direction))
            assertEquals(64.dp, padding.calculateEndPadding(direction))
            assertEquals(8.dp, padding.calculateTopPadding())
            assertEquals(56.dp, padding.calculateBottomPadding())
        }
        assertEquals(10800L, guideVisibleWindowSec(650f, 32, 1f))
        assertEquals(7200L, guideVisibleWindowSec(650f, 43, 1f))
        assertEquals("A 176dp gutter at the shared inset would lose an hour", 7200L, guideVisibleWindowSec(646f, 32, 1f))
    }
}
