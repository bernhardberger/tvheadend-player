package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgrammeActionPolicyTest {
    @Test
    fun currentProgrammeOffersWatch() {
        assertEquals(
            listOf(ProgrammeAction.WATCH),
            programmeActions(event(100, 200), nowSec = 150, recording = null),
        )
    }

    @Test
    fun futureProgrammeOffersRecordOrCancelFromAuthoritativeState() {
        assertEquals(
            listOf(ProgrammeAction.RECORD),
            programmeActions(event(200, 300), nowSec = 100, recording = null),
        )
        assertEquals(
            listOf(ProgrammeAction.CANCEL_RECORDING),
            programmeActions(
                event(200, 300),
                nowSec = 100,
                recording = recording(DvrState.SCHEDULED),
            ),
        )
    }

    @Test
    fun hidesRecordAndCancelWhenServerDeniesDvrWrite() {
        assertEquals(
            emptyList<ProgrammeAction>(),
            programmeActions(
                event(200, 300),
                nowSec = 100,
                recording = null,
                canModifyRecordings = false,
            ),
        )
        assertEquals(
            emptyList<ProgrammeAction>(),
            programmeActions(
                event(200, 300),
                nowSec = 100,
                recording = recording(DvrState.SCHEDULED),
                canModifyRecordings = false,
            ),
        )
        // Watch-from-start for completed files is playback, not DVR write.
        assertEquals(
            listOf(ProgrammeAction.WATCH_FROM_START),
            programmeActions(
                event(100, 200),
                nowSec = 300,
                recording = recording(DvrState.COMPLETED),
                canModifyRecordings = false,
            ),
        )
    }

    @Test
    fun hidesRecordAndCancelWhileWriteCapabilityIsUnknown() {
        // Repository maps Unknown → canModifyRecordings=false so UI never flashes dead buttons.
        assertEquals(
            emptyList<ProgrammeAction>(),
            programmeActions(
                event(200, 300),
                nowSec = 100,
                recording = null,
                canModifyRecordings = false,
            ),
        )
    }

    @Test
    fun pastProgrammeIsInspectOnlyUnlessServerCanStartIt() {
        assertEquals(
            emptyList<ProgrammeAction>(),
            programmeActions(event(100, 200), nowSec = 300, recording = null),
        )
        assertEquals(
            listOf(ProgrammeAction.WATCH_FROM_START),
            programmeActions(
                event(100, 200),
                nowSec = 300,
                recording = recording(DvrState.COMPLETED),
            ),
        )
        assertEquals(
            listOf(ProgrammeAction.WATCH_FROM_START),
            programmeActions(
                event(100, 200),
                nowSec = 300,
                recording = null,
                serverTimeshiftCoversEvent = true,
            ),
        )
    }

    @Test
    fun disappearedProgrammeFallsBackToNearestAtSameTime() {
        val events = listOf(event(50, 80, id = 1), event(105, 180, id = 2))

        assertEquals(2, nearestProgrammeAt(events, targetStartSec = 100)?.eventId)
    }

    private fun event(start: Long, stop: Long, id: Int = 7) = EpgEventEntry(
        eventId = id,
        channelId = 3,
        start = start,
        stop = stop,
        title = "Programme",
    )

    private fun recording(state: DvrState) = DvrEntry(
        id = 9,
        eventId = 7,
        channelId = 3,
        start = 100,
        stop = 200,
        title = "Programme",
        state = state,
    )
}
