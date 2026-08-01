package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastPlayedChannelPolicyTest {
    private val channels = listOf(10, 20, 30)

    @Test
    fun persistedCurrentChannel_isRestored() {
        assertEquals(20, LastPlayedChannelPolicy.resolve(channels, 20))
    }

    @Test
    fun missingPersistedChannel_fallsBackToFirstCurrentChannel() {
        assertEquals(10, LastPlayedChannelPolicy.resolve(channels, null))
    }

    @Test
    fun stalePersistedChannel_fallsBackToFirstCurrentChannel() {
        assertEquals(10, LastPlayedChannelPolicy.resolve(channels, 99))
    }

    @Test
    fun emptyChannelList_hasNoPlayableChannel() {
        assertNull(LastPlayedChannelPolicy.resolve(emptyList(), 20))
    }

    @Test
    fun recentlyPlayedChannelsAreDeduplicatedMostRecentFirst() {
        assertEquals(
            listOf(20, 10, 30),
            pushRecentChannelId(current = listOf(10, 20, 30), channelId = 20),
        )
    }

    @Test
    fun recentlyPlayedChannelsRespectTheBound() {
        assertEquals(
            listOf(40, 10, 20),
            pushRecentChannelId(
                current = listOf(10, 20, 30),
                channelId = 40,
                limit = 3,
            ),
        )
    }
}
