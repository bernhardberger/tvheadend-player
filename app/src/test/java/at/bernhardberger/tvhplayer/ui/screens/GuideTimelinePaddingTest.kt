package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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

        assertEquals(24.dp, padding.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, padding.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(2.dp, padding.calculateTopPadding())
        assertEquals(34.dp, padding.calculateBottomPadding())
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

        assertEquals(48.dp, padding.calculateStartPadding(LayoutDirection.Rtl))
        assertEquals(0.dp, padding.calculateEndPadding(LayoutDirection.Rtl))
    }
}
