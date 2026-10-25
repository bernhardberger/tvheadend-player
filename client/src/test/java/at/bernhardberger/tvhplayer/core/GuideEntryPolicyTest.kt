package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideEntryPolicyTest {
    private val channels = (1L..5L).map(::ChannelId)

    @Test fun warmPlaybackWinsOverRememberedChannel() {
        assertEquals(ChannelId(5), guideEntryChannelId(channels, ChannelId(5), ChannelId(2), ChannelId(3), ChannelId(4)))
    }

    @Test fun withoutPlaybackTheRememberedChannelWins() {
        assertEquals(ChannelId(2), guideEntryChannelId(channels, null, ChannelId(2), ChannelId(3), ChannelId(4)))
    }

    @Test fun playingOutsideTheGroupDoesNotChangeTheGroup() {
        assertEquals(ChannelId(2), guideEntryChannelId(channels, ChannelId(9), ChannelId(2), ChannelId(3), ChannelId(4)))
    }

    @Test fun unavailableIdentitiesFallBackToLastPlayedThenSelectedThenFirst() {
        assertEquals(ChannelId(3), guideEntryChannelId(channels, null, ChannelId(9), ChannelId(3), ChannelId(4)))
        assertEquals(ChannelId(4), guideEntryChannelId(channels, null, null, ChannelId(9), ChannelId(4)))
        assertEquals(ChannelId(1), guideEntryChannelId(channels, null, null, null, null))
        assertNull(guideEntryChannelId(emptyList(), ChannelId(5), ChannelId(2), null, null))
    }
}
