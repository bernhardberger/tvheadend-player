package at.bernhardberger.tvhplayer.viewmodels

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelScopeStateTest {
    @Test
    fun currentAuthorityAndItsResolvedChannelsShareOneStateValue() {
        val channel = Channel.create(
            id = ChannelId(7),
            name = "Seven",
            number = 7,
        )

        val state = resolveChannelScopeState(
            channelState = ChannelRepositoryState.Current(
                ChannelCatalog.create(channels = listOf(channel)),
            ),
            activeTagId = null,
        )

        assertTrue(state.channelCatalogCurrent)
        assertEquals(listOf(channel), state.scope.visibleChannels)
    }

    @Test
    fun noncurrentEmptyRepositoryStateCannotBeAuthoritative() {
        val state = resolveChannelScopeState(
            channelState = ChannelRepositoryState.Empty,
            activeTagId = null,
        )

        assertFalse(state.channelCatalogCurrent)
        assertTrue(state.scope.visibleChannels.isEmpty())
    }

    @Test
    fun staleAndSynchronizingCatalogsRemainVisibleWithoutCurrentAuthority() {
        val channel = Channel.create(id = ChannelId(9), name = "Nine")
        val catalog = ChannelCatalog.create(channels = listOf(channel))

        listOf(
            ChannelRepositoryState.Stale(catalog),
            ChannelRepositoryState.Synchronizing(catalog),
        ).forEach { repositoryState ->
            val state = resolveChannelScopeState(repositoryState, activeTagId = null)

            assertFalse(state.channelCatalogCurrent)
            assertEquals(listOf(channel), state.scope.visibleChannels)
        }
    }
}
