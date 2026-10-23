package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNowStatusPolicyTest {
    @Test fun runningRecordingsMarkTheirChannelWithOrWithoutErrors() {
        val entries = listOf(
            DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR, DvrEntryState.SCHEDULED,
            DvrEntryState.COMPLETED_ERROR,
        ).mapIndexed { index, state ->
            DvrEntry.create(DvrEntryId(index + 1L), state = state, channelId = ChannelId(index + 1L))
        }

        assertEquals(setOf(ChannelId(1), ChannelId(2)), activeRecordingChannelIds(entries))
    }
}
