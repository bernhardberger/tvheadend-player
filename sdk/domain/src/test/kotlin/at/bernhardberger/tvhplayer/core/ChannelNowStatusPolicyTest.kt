package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNowStatusPolicyTest {
    @Test
    fun activeRecordingChannelIdsIncludeRunningEntriesWithoutProgrammeEvents() {
        val entries = listOf(
            dvrEntry(id = 1, eventId = 101, state = DvrState.RECORDING),
            dvrEntry(id = 2, eventId = 102, state = DvrState.SCHEDULED),
            dvrEntry(id = 3, eventId = null, state = DvrState.RECORDING),
        )

        assertEquals(setOf(1, 3), activeRecordingChannelIds(entries))
    }

    @Test
    fun channelCanBePlayingAndRecordingAtTheSameTime() {
        assertEquals(
            ChannelNowStatus(playingNow = true, recordingNow = true),
            channelNowStatus(
                channelId = 7,
                playingChannelId = 7,
                recordingChannelIds = setOf(7),
            ),
        )
    }

    @Test
    fun anotherChannelsRecordingDoesNotMarkThisChannel() {
        assertEquals(
            ChannelNowStatus(playingNow = false, recordingNow = false),
            channelNowStatus(
                channelId = 8,
                playingChannelId = 7,
                recordingChannelIds = setOf(9),
            ),
        )
    }

    private fun dvrEntry(id: Int, eventId: Int?, state: DvrState) = DvrEntry(
        id = id,
        eventId = eventId,
        channelId = id,
        start = 0L,
        stop = 1L,
        title = "Programme $id",
        state = state,
    )
}
