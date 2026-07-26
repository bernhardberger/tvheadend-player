package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContentPolicyTest {
    @Test
    fun omitsEmptySectionsAndBuildsNowPlaying() {
        val channels = mapOf(1 to ChannelUi(id = 1, name = "ORF1", number = 1, icon = null))
        val model = buildHomeDashboard(
            channelsById = channels,
            activeServiceId = 1,
            activeRecordingId = null,
            activeProgrammeTitle = "News",
            recentChannelIds = listOf(1),
            onNowEvents = emptyList(),
            recordings = emptyList(),
            nowSec = 1_000L,
        )
        assertEquals("News", model.nowPlaying?.programmeTitle)
        assertTrue(model.onNow.isEmpty())
        assertTrue(model.latestRecordings.isEmpty())
    }

    @Test
    fun recentChannelsDedupAndCap() {
        val pushed = pushRecentChannelId(listOf(2, 3, 1), channelId = 1, limit = 3)
        assertEquals(listOf(1, 2, 3), pushed)
        val capped = (1..12).fold(emptyList<Int>()) { acc, id ->
            pushRecentChannelId(acc, id, limit = 8)
        }
        assertEquals(8, capped.size)
        assertEquals(12, capped.first())
    }

    @Test
    fun upcomingRecordingsAreNotMarkedPlayable() {
        val model = buildHomeDashboard(
            channelsById = emptyMap(),
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            recentChannelIds = emptyList(),
            onNowEvents = emptyList(),
            recordings = listOf(
                DvrEntry(
                    id = 9,
                    eventId = 1,
                    channelId = 1,
                    start = 2_000,
                    stop = 2_100,
                    title = "Later",
                    state = DvrState.SCHEDULED,
                    channelName = "ORF1",
                ),
            ),
            nowSec = 1_000L,
        )
        assertEquals(1, model.upcomingRecordings.size)
        assertFalse(model.upcomingRecordings.first().playable)
        assertNull(model.nowPlaying)
    }

    @Test
    fun onNowUsesCurrentEventsOnly() {
        val channel = ChannelUi(id = 4, name = "Servus", number = 4, icon = null)
        val now = EpgEventEntry(1, 4, 900, 1_100, "Live show")
        val past = EpgEventEntry(2, 4, 700, 800, "Past")
        val model = buildHomeDashboard(
            channelsById = mapOf(4 to channel),
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            recentChannelIds = emptyList(),
            onNowEvents = listOf(channel to now, channel to past),
            recordings = emptyList(),
            nowSec = 1_000L,
        )
        assertEquals(listOf("Live show"), model.onNow.map { it.programmeTitle })
    }
}
