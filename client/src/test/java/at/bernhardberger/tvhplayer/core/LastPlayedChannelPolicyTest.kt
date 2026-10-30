package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import org.junit.Assert.assertEquals
import org.junit.Test

class LastPlayedChannelPolicyTest {
    @Test
    fun storedHistoryKeepsTheNewestTwentyIdsOnce() {
        var history = emptyList<ChannelId>()
        repeat(21) { index ->
            history = pushRecentChannelId(history, ChannelId(index + 1L))
        }

        assertEquals(20, history.size)
        assertEquals((21L downTo 2L).map { ChannelId(it) }, history)

        // Watching a stored channel again moves it to the front instead of duplicating it.
        history = pushRecentChannelId(history, ChannelId(10L))
        assertEquals(20, history.size)
        assertEquals(ChannelId(10L), history.first())
        assertEquals(1, history.count { it == ChannelId(10L) })
        assertEquals(ChannelId(2L), history.last())

        // A new channel at the limit drops the oldest stored one.
        history = pushRecentChannelId(history, ChannelId(22L))
        assertEquals(20, history.size)
        assertEquals(ChannelId(22L), history.first())
        assertEquals(0, history.count { it == ChannelId(2L) })
    }
}
