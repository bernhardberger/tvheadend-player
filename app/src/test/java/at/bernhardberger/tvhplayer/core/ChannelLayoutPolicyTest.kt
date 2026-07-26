package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelLayoutPolicyTest {
    @Test
    fun defaultsToListWithDetails() {
        assertEquals(
            ChannelBrowseLayout.LIST_WITH_DETAILS,
            resolveChannelBrowseLayout(null),
        )
        assertEquals(
            ChannelBrowseLayout.LIST_WITH_DETAILS,
            resolveChannelBrowseLayout("unknown"),
        )
    }

    @Test
    fun resolvesLargeCards() {
        assertEquals(
            ChannelBrowseLayout.LARGE_CARDS,
            resolveChannelBrowseLayout(ChannelBrowseLayout.LARGE_CARDS.name),
        )
    }

    @Test
    fun initialsUseUpToTwoSignificantCharacters() {
        assertEquals("O1", channelInitials("ORF 1 HD"))
        assertEquals("S", channelInitials("ServusTV"))
        assertEquals("?", channelInitials("   "))
        assertEquals("1", channelInitials("12er"))
    }
}
