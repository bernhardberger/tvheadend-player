package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgrammeActionPolicyTest {
    private val event = EpgEvent.create(
        id = EventId(21), channelId = ChannelId(7),
        start = Instant.fromEpochSeconds(1_800_000_000),
        stop = Instant.fromEpochSeconds(1_800_003_600),
    )
    private val beforeStart = 1_799_999_900L

    @Test
    fun runningRecordingOfUpcomingProgrammeOffersStopInsteadOfCancel() {
        assertEquals(
            listOf(ProgrammeAction.STOP_RECORDING),
            programmeActions(event, beforeStart, recording(DvrEntryState.RECORDING)),
        )
    }

    @Test
    fun scheduledRecordingOfUpcomingProgrammeKeepsCancel() {
        assertEquals(
            listOf(ProgrammeAction.CANCEL_RECORDING),
            programmeActions(event, beforeStart, recording(DvrEntryState.SCHEDULED)),
        )
    }

    @Test
    fun readOnlyAccessHidesStopAndCancel() {
        listOf(DvrEntryState.RECORDING, DvrEntryState.SCHEDULED).forEach { state ->
            assertEquals(
                emptyList<ProgrammeAction>(),
                programmeActions(event, beforeStart, recording(state), canModifyRecordings = false),
            )
        }
    }

    private fun recording(state: DvrEntryState) =
        DvrEntry.create(id = DvrEntryId(31), eventId = event.id, state = state)
}
