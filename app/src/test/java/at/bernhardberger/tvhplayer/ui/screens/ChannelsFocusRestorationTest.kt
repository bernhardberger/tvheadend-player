package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsFocusRestorationTest {
    @Test
    fun longLazyItemKeyMatchesItsTypedChannelId() {
        val channelId = ChannelId(4_294_967_295L)

        assertTrue(channelLazyItemMatches(channelId.value, channelId))
    }

    @Test
    fun targetPrefersPerTagFocusThenSelectionThenFirstVisibleChannel() {
        val first = ChannelId(1)
        val selected = ChannelId(2)
        val remembered = ChannelId(3)
        val visible = listOf(first, selected, remembered)

        assertEquals(remembered, restoredChannelId(visible, remembered, selected))
        assertEquals(selected, restoredChannelId(visible, ChannelId(99), selected))
        assertEquals(first, restoredChannelId(visible, ChannelId(99), ChannelId(98)))
    }

}
