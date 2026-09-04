package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun noncurrentEmptyCatalogCannotPresentAuthoritativeEmpty() {
        assertEquals(
            ConnectionUiState.SyncingChannels,
            ConnectionUiState.Ready.forEmptyChannelPresentation(channelCatalogCurrent = false),
        )
    }

    @Test
    fun currentEmptyCatalogPreservesAuthoritativeReadyState() {
        assertEquals(
            ConnectionUiState.Ready,
            ConnectionUiState.Ready.forEmptyChannelPresentation(channelCatalogCurrent = true),
        )
    }

    @Test
    fun connectionFailureRemainsActionableBeforeCatalogIsCurrent() {
        val failure = ConnectionUiState.Error(
            ConnectionFailureKind.AUTHENTICATION,
            SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED,
        )

        assertEquals(
            failure,
            failure.forEmptyChannelPresentation(channelCatalogCurrent = false),
        )
    }

    @Test
    fun filteredEmptyRequiresCurrentReadyAuthority() {
        assertFalse(
            shouldPresentEmptyTag(
                channelCatalogCurrent = false,
                connectionState = ConnectionUiState.Ready,
                hasChannelsOutsideActiveTag = true,
                activeTagSelected = true,
            ),
        )
        assertFalse(
            shouldPresentEmptyTag(
                channelCatalogCurrent = true,
                connectionState = ConnectionUiState.Error(
                    ConnectionFailureKind.UNREACHABLE,
                    SessionRecoveryDisposition.AUTOMATIC_BACKOFF,
                ),
                hasChannelsOutsideActiveTag = true,
                activeTagSelected = true,
            ),
        )
        assertTrue(
            shouldPresentEmptyTag(
                channelCatalogCurrent = true,
                connectionState = ConnectionUiState.Ready,
                hasChannelsOutsideActiveTag = true,
                activeTagSelected = true,
            ),
        )
    }

}
