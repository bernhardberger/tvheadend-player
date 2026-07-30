package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentChannelReadinessTest {

    @Test
    fun disconnectedTransportWithPublishedMetadataWaits() {
        assertEquals(
            CurrentChannelReadiness.Waiting,
            deriveCurrentChannelReadiness(
                connectionState = ConnectionState.Disconnected,
                metadataReady = true,
                channels = listOf(channel(id = 1)),
            ),
        )
    }

    @Test
    fun connectedTransportWithRetainedChannelsButMetadataNotReadyWaits() {
        assertEquals(
            CurrentChannelReadiness.Waiting,
            deriveCurrentChannelReadiness(
                connectionState = connected,
                metadataReady = false,
                channels = listOf(channel(id = 1)),
            ),
        )
    }

    @Test
    fun connectedTransportWithReadyMetadataCarriesExactChannelSnapshot() {
        val channels = listOf(channel(id = 1), channel(id = 2))

        assertEquals(
            CurrentChannelReadiness.Ready(channels),
            deriveCurrentChannelReadiness(
                connectionState = connected,
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
                connectionState = connected,
                metadataReady = true,
                channels = emptyList(),
            ),
        )
    }

    @Test
    fun readyStateCopiesItsSourceSnapshot() {
        val expected = channel(id = 1)
        val source = mutableListOf(expected)

        val readiness = deriveCurrentChannelReadiness(
            connectionState = connected,
            metadataReady = true,
            channels = source,
        ) as CurrentChannelReadiness.Ready
        source.clear()

        assertEquals(listOf(expected), readiness.channels)
    }

    private fun channel(id: Int) = ChannelUi(
        id = id,
        name = "Channel $id",
        number = id,
        icon = null,
    )

    private companion object {
        val connected = ConnectionState.Connected(
            host = "example.invalid",
            port = 9982,
            htspVersion = 42,
        )
    }
}
