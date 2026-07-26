package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrFile
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContentPolicyTest {
    @Test
    fun initialFocusUsesFirstAvailableAction() {
        val empty = HomeDashboardModel(
            nowPlaying = null,
            recentChannels = emptyList(),
            onNow = emptyList(),
            latestRecordings = emptyList(),
            recordingNow = emptyList(),
            upcomingRecordings = emptyList(),
        )
        assertEquals(HomeFocusTarget.STATUS_ACTION, homeInitialFocusTarget(empty))
        assertEquals(
            HomeFocusTarget.ON_NOW,
            homeInitialFocusTarget(
                empty.copy(
                    onNow = listOf(HomeRecentChannel(1, "ORF1", "News")),
                )
            ),
        )
        assertEquals(
            HomeFocusTarget.RECENT_CHANNEL,
            homeInitialFocusTarget(
                empty.copy(
                    recentChannels = listOf(HomeRecentChannel(1, "ORF1", "News")),
                    onNow = listOf(HomeRecentChannel(2, "ORF2", "Weather")),
                )
            ),
        )
    }

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
    fun latestContainsOnlyCompletedRecordingsWithAvailableFiles() {
        fun entry(id: Int, state: DvrState, withFile: Boolean) = DvrEntry(
            id = id,
            eventId = id,
            channelId = 1,
            start = 900L + id,
            stop = 1_100L + id,
            title = "Recording $id",
            state = state,
            files = if (withFile) listOf(DvrFile(path = "/recording-$id.ts")) else emptyList(),
        )
        val model = buildHomeDashboard(
            channelsById = emptyMap(),
            activeServiceId = null,
            activeRecordingId = null,
            activeProgrammeTitle = null,
            recentChannelIds = emptyList(),
            onNowEvents = emptyList(),
            recordings = listOf(
                entry(1, DvrState.COMPLETED, withFile = true),
                entry(2, DvrState.COMPLETED, withFile = false),
                entry(3, DvrState.RECORDING, withFile = true),
                entry(4, DvrState.FAILED, withFile = true),
            ),
            nowSec = 1_000L,
        )

        assertEquals(listOf(1), model.latestRecordings.map { it.id })
        assertEquals(listOf(1), model.latestRecordings.map { it.channelId })
        assertEquals(listOf(3), model.recordingNow.map { it.id })
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
