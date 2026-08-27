package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.data.Channel
import org.junit.Assert.assertEquals
import org.junit.Test

class WarmLivePlayerTargetTest {
    @Test
    fun readySnapshotRestoresTheActiveChannelsExactIdentity() {
        assertEquals(
            WarmLivePlayerTarget(channelId = 22, channelName = "News HD"),
            warmLivePlayerTarget(
                activeChannelId = 22,
                readiness = CurrentChannelReadiness.Ready(
                    listOf(channel(11, "Other"), channel(22, "News HD")),
                ),
            ),
        )
    }

    @Test
    fun waitingOrMissingSnapshotFallsBackToTheActiveServiceWithoutStaleIdentity() {
        assertEquals(
            WarmLivePlayerTarget(channelId = 22, channelName = ""),
            warmLivePlayerTarget(activeChannelId = 22, readiness = CurrentChannelReadiness.Waiting),
        )
        assertEquals(
            WarmLivePlayerTarget(channelId = 22, channelName = ""),
            warmLivePlayerTarget(
                activeChannelId = 22,
                readiness = CurrentChannelReadiness.Ready(listOf(channel(11, "Other"))),
            ),
        )
    }

    private fun channel(id: Int, name: String) = Channel(id, name, id, null)
}
