package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import org.junit.Assert.assertEquals
import org.junit.Test

class WarmLivePlayerTargetTest {
    @Test
    fun readySnapshotRestoresTheActiveChannelsExactIdentity() {
        assertEquals(
            WarmLivePlayerTarget(channelId = ChannelId(22), channelName = "News HD"),
            warmLivePlayerTarget(
                activeChannelId = ChannelId(22),
                readiness = CurrentChannelReadiness.Ready(
                    listOf(Channel.create(ChannelId(11), name = "Other"), Channel.create(ChannelId(22), name = "News HD")),
                ),
            ),
        )
    }

    @Test
    fun waitingOrMissingSnapshotFallsBackToTheActiveServiceWithoutStaleIdentity() {
        assertEquals(
            WarmLivePlayerTarget(channelId = ChannelId(22), channelName = ""),
            warmLivePlayerTarget(activeChannelId = ChannelId(22), readiness = CurrentChannelReadiness.Waiting),
        )
        assertEquals(
            WarmLivePlayerTarget(channelId = ChannelId(22), channelName = ""),
            warmLivePlayerTarget(
                activeChannelId = ChannelId(22),
                readiness = CurrentChannelReadiness.Ready(listOf(Channel.create(ChannelId(11), name = "Other"))),
            ),
        )
    }

}
