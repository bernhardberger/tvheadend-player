package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelsViewportPaddingTest {
    @Test
    fun browseContentRetainsLeadingInsetAndUsesEdgeToEdgeTrailingViewport() {
        val padding = channelsBrowseViewportPadding(
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(24.dp, padding.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, padding.calculateEndPadding(LayoutDirection.Ltr))
    }

    @Test
    fun browseContentResolvesAbsoluteLeadingInsetForRtl() {
        val padding = channelsBrowseViewportPadding(
            contentPadding = PaddingValues.Absolute(left = 24.dp, right = 48.dp),
            layoutDirection = LayoutDirection.Rtl,
        )

        assertEquals(48.dp, padding.calculateStartPadding(LayoutDirection.Rtl))
        assertEquals(0.dp, padding.calculateEndPadding(LayoutDirection.Rtl))
    }

    /** The list column runs to the screen bottom under its fade; only details keep the inset. */
    @Test
    fun detailPanelRetainsTheTrailingAndBottomSafeInsetsInsideTheViewport() {
        val padding = channelsDetailPanePadding(
            contentPadding = PaddingValues(start = 24.dp, top = 32.dp, end = 48.dp, bottom = 32.dp),
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(0.dp, padding.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(48.dp, padding.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, padding.calculateTopPadding())
        assertEquals(32.dp, padding.calculateBottomPadding())
        assertEquals(
            0.dp,
            channelsBrowseViewportPadding(
                contentPadding = PaddingValues(start = 24.dp, top = 32.dp, end = 48.dp, bottom = 32.dp),
                layoutDirection = LayoutDirection.Ltr,
            ).calculateBottomPadding(),
        )
    }
}
