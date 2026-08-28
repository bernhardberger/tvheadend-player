package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentChannelReadinessTest {
    @Test
    fun disconnectedTransportWithPublishedMetadataWaits() {
        assertEquals(
            CurrentChannelReadiness.Waiting,
            deriveCurrentChannelReadiness(
                connected = false,
                metadataReady = true,
                channels = listOf(Channel.create(ChannelId(1))),
            ),
        )
    }

    @Test
    fun connectedTransportWithRetainedChannelsButMetadataNotReadyWaits() {
        assertEquals(
            CurrentChannelReadiness.Waiting,
            deriveCurrentChannelReadiness(
                connected = true,
                metadataReady = false,
                channels = listOf(Channel.create(ChannelId(1))),
            ),
        )
    }

    @Test
    fun connectedTransportWithReadyMetadataCarriesExactChannelSnapshot() {
        val channels = listOf(Channel.create(ChannelId(1)), Channel.create(ChannelId(2)))

        assertEquals(
            CurrentChannelReadiness.Ready(channels),
            deriveCurrentChannelReadiness(
                connected = true,
                metadataReady = true,
                channels = channels,
            ),
        )
    }

    @Test
    fun connectedTransportWithReadyEmptyMetadataIsActionable() {
        assertEquals(
            CurrentChannelReadiness.Ready(emptyList()),
            deriveCurrentChannelReadiness(
                connected = true,
                metadataReady = true,
                channels = emptyList(),
            ),
        )
    }

    @Test
    fun readyStateCopiesItsSourceSnapshot() {
        val expected = Channel.create(ChannelId(1))
        val source = mutableListOf(expected)

        val readiness = deriveCurrentChannelReadiness(
            connected = true,
            metadataReady = true,
            channels = source,
        ) as CurrentChannelReadiness.Ready
        source.clear()

        assertEquals(listOf(expected), readiness.channels)
    }

}
