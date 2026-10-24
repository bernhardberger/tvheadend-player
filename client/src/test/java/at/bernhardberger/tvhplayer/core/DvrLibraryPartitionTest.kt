package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import java.time.ZoneOffset
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DvrLibraryPartitionTest {
    private fun entry(id: Long, state: DvrEntryState, start: Long) =
        DvrEntry.create(DvrEntryId(id), state = state, start = Instant.fromEpochSeconds(start))

    @Test fun runningRecordingWithErrorsIsScheduledAsRecordingNowAndNotAProblem() {
        val scheduled = entry(1, DvrEntryState.SCHEDULED, 500)
        val withErrors = entry(2, DvrEntryState.RECORDING_ERROR, 200)
        val recording = entry(3, DvrEntryState.RECORDING, 100)
        val failed = entry(4, DvrEntryState.COMPLETED_ERROR, 50)

        val library = partitionDvrLibrary(listOf(scheduled, withErrors, recording, failed))

        assertEquals(listOf(recording, withErrors, scheduled), library.schedule)
        assertEquals(listOf(failed), library.problems)
        val recordingNow = groupDvrSchedule(library.schedule, nowSec = 0, zoneId = ZoneOffset.UTC).first()
        assertEquals(DvrScheduleSectionKind.RECORDING_NOW, recordingNow.kind)
        assertEquals(listOf(recording, withErrors), recordingNow.entries)
        assertEquals(listOf(failed), groupDvrProblems(listOf(withErrors, failed))[DvrProblemBucket.FAILED])
    }

    @Test fun missedRecordingsAreFailuresAndOnlyInvalidOnesAreCanceled() {
        val missed = entry(1, DvrEntryState.MISSED, 300)
        val failed = entry(2, DvrEntryState.COMPLETED_ERROR, 200)
        val invalid = entry(3, DvrEntryState.INVALID, 100)

        val groups = groupDvrProblems(partitionDvrLibrary(listOf(missed, failed, invalid)).problems)

        assertEquals(listOf(missed, failed), groups[DvrProblemBucket.FAILED])
        assertEquals(listOf(invalid), groups[DvrProblemBucket.CANCELLED])
    }
}
