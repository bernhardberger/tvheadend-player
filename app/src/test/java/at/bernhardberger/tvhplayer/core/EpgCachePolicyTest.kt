package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgCachePolicyTest {
    @Test
    fun retentionWindowKeepsSixHoursPastAndTwentyFourHoursFuture() {
        val anchor = 1_000_000L

        val window = epgRetentionWindow(anchor)

        assertEquals(anchor - 6 * 3600L, window.fromSec)
        assertEquals(anchor + 24 * 3600L, window.toSec)
    }

    @Test
    fun evictionDropsEventsOutsideTheMovingBoundedWindow() {
        val anchor = 1_000_000L
        val window = epgRetentionWindow(anchor)
        val events = listOf(
            event(id = 1, start = window.fromSec - 7200, stop = window.fromSec - 1),
            event(id = 2, start = window.fromSec - 1, stop = window.fromSec + 1),
            event(id = 3, start = anchor, stop = anchor + 1800),
            event(id = 4, start = window.toSec + 1, stop = window.toSec + 3600),
        )

        val retained = evictEpgOutsideWindow(events, window)

        assertEquals(listOf(2, 3), retained.map { it.eventId })
        assertTrue(retained.zipWithNext().all { (left, right) -> left.start <= right.start })
    }

    private fun event(id: Int, start: Long, stop: Long) = EpgEventEntry(
        eventId = id,
        channelId = 1,
        start = start,
        stop = stop,
        title = "Event $id",
    )
}
