package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEpisode
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgSeriesLinkId
import at.bernhardberger.tvheadend.sdk.core.EventId
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class OtherAiringsTest {
    private val now = 1_800_000_000L

    @Test fun seriesLinkMatchesAcrossChannelsAndIgnoresTitles() {
        val shown = event(1, 0, title = "Ridge Light", seriesLink = "crid://ridge")
        val events = listOf(shown,
            event(2, 3, title = "Ridge Light: Winter", seriesLink = "crid://ridge", channel = 9),
            event(3, 2, title = "Ridge Light"),
            event(4, 1, title = "Other", seriesLink = "crid://ridge"))
        assertEquals(listOf(4L, 2L), ids(otherAirings(shown, events, now)))
    }

    @Test fun seriesLinkIdMatchesWhenThereIsNoSeriesLinkUri() {
        val shown = event(1, 0, title = "Ridge Light", seriesLinkId = 7)
        val events = listOf(shown, event(2, 1, title = "Other", seriesLinkId = 7), event(3, 2, title = "Ridge Light"))
        assertEquals(listOf(2L), ids(otherAirings(shown, events, now)))
    }

    @Test fun titleFallbackIsExactTrimmedAndCaseInsensitive() {
        val shown = event(1, 0, title = " Ridge Light ")
        val events = listOf(shown, event(2, 1, title = "ridge light"), event(3, 2, title = "Ridge Light: Winter"),
            event(4, 3, title = "Ridge Light", seriesLink = "crid://other"))
        assertEquals(listOf(2L, 4L), ids(otherAirings(shown, events, now)))
        assertEquals(emptyList<Long>(), ids(otherAirings(event(5, 0, title = " "), events, now)))
    }

    @Test fun excludesSelfAndEndedButIncludesRunningElsewhereAndStartingNow() {
        val shown = event(1, 2, title = "Ridge Light")
        val airing = EpgEvent.create(EventId(3), ChannelId(9), Instant.fromEpochSeconds(now - 1800),
            Instant.fromEpochSeconds(now + 1800), title = "Ridge Light")
        // Ended at the boundary: stop == now is past.
        val ended = EpgEvent.create(EventId(4), ChannelId(1), Instant.fromEpochSeconds(now - 3600),
            Instant.fromEpochSeconds(now), title = "Ridge Light")
        val events = listOf(shown, event(2, -3, title = "Ridge Light"), airing, ended,
            event(5, 0, title = "Ridge Light"))
        assertEquals(listOf(3L, 5L), ids(otherAirings(shown, events, now)))
    }

    @Test fun sameStartOnTwoChannelsAndPageStartAreAllIncludedExceptPageEvent() {
        val shown = event(1, 2, title = "Ridge Light")
        val events = listOf(shown, event(2, 2, "Ridge Light", channel = 9),
            event(3, 3, "Ridge Light", channel = 9), event(4, 3, "Ridge Light"))
        assertEquals(listOf(2L, 3L, 4L), ids(otherAirings(shown, events, now)))
    }

    @Test fun sortsByStartAndCapsAtTwenty() {
        val shown = event(1, 0, title = "Ridge Light")
        val events = listOf(shown) + (30 downTo 2).map { event(it.toLong(), it, title = "Ridge Light") }
        val airings = otherAirings(shown, events, now)
        assertEquals((2L..21L).toList(), ids(airings))
        assertEquals(listOf(2L, 3L), ids(otherAirings(shown, events, now, limit = 2)))
    }

    @Test fun sameStartSortsByChannelNumberThenStableInputOrder() {
        val shown = event(1, 0, "Ridge Light")
        val events = listOf(event(4, 1, "Ridge Light", channel = 9),
            event(3, 1, "Ridge Light", channel = 8), event(2, 1, "Ridge Light", channel = 7))
        assertEquals(listOf(3L, 2L, 4L), ids(otherAirings(shown, events, now,
            channelNumber = { if (it == ChannelId(9)) 200 else 100 })))
        assertEquals(listOf(4L, 3L, 2L), ids(otherAirings(shown, events, now)))
    }

    private fun ids(events: List<EpgEvent>) = events.map { it.id.value }

    /** [hours] from now; each airing lasts an hour. */
    private fun event(id: Long, hours: Int, title: String?, seriesLink: String? = null, seriesLinkId: Long? = null,
        channel: Long = 1) = EpgEvent.create(EventId(id), ChannelId(channel),
        Instant.fromEpochSeconds(now + hours * 3600L), Instant.fromEpochSeconds(now + hours * 3600L + 3600),
        title = title, seriesLinkUri = seriesLink,
        episode = seriesLinkId?.let { EpgEpisode(null, EpgSeriesLinkId(it), null, null, null, null, null, null, null) })
}
