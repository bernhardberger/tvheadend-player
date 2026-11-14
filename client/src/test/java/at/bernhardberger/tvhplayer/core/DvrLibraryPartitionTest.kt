package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import java.time.ZoneOffset
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DvrLibraryPartitionTest {
    @Test fun allMissingFilesAreRemovedInsteadOfProblems() {
        val deleted = DvrEntry.create(DvrEntryId(10), state = DvrEntryState.REMOVED, files = emptyList())
        val missing = DvrEntry.create(DvrEntryId(11), state = DvrEntryState.REMOVED,
            files = listOf(DvrRecordingFile.create()))
        val deletedAfterRestart = DvrEntry.create(DvrEntryId(12), state = DvrEntryState.REMOVED)
        val failed = DvrEntry.create(DvrEntryId(13), state = DvrEntryState.COMPLETED_ERROR, files = emptyList())
        val library = partitionDvrLibrary(listOf(deleted, missing, deletedAfterRestart, failed))

        assertEquals(listOf(failed), library.problems)
        assertEquals(listOf(failed), dvrProblemClearTargets(listOf(deleted, missing, deletedAfterRestart, failed)).fileless)
        assertEquals(emptyList<DvrEntry>(), partitionDvrLibrary(listOf(deleted)).problems)
        assertEquals(emptyList<DvrEntry>(), partitionDvrLibrary(listOf(deletedAfterRestart)).problems)
        assertEquals(emptyList<DvrEntry>(), partitionDvrLibrary(listOf(missing)).problems)
    }

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
        assertEquals(DvrProblemCounts(failed = 1, canceled = 0), dvrProblemCounts(listOf(withErrors, failed)))
    }

    @Test fun missedRecordingsAreFailuresAndOnlyInvalidOnesAreCanceled() {
        val missed = entry(1, DvrEntryState.MISSED, 300)
        val failed = entry(2, DvrEntryState.COMPLETED_ERROR, 200)
        val invalid = entry(3, DvrEntryState.INVALID, 100)

        val problems = partitionDvrLibrary(listOf(missed, failed, invalid)).problems

        assertEquals(DvrProblemCounts(failed = 2, canceled = 1), dvrProblemCounts(problems))
        val partial = DvrEntry.create(DvrEntryId(4), state = DvrEntryState.COMPLETED_ERROR,
            files = listOf(DvrRecordingFile.create()))
        val targets = dvrProblemClearTargets(listOf(missed, failed, invalid, partial,
            entry(5, DvrEntryState.RECORDING_ERROR, 400), entry(6, DvrEntryState.COMPLETED, 500),
            entry(7, DvrEntryState.REMOVED, 600)))
        assertEquals(listOf(missed, failed, invalid), targets.fileless)
        assertEquals(listOf(partial), targets.partial)
    }

    @Test fun problemsAreOneListNewestFirstUnderTheirLocalStartDay() {
        val day = 86_400L
        val now = 10 * day + 12 * 3_600
        val earlierToday = entry(1, DvrEntryState.INVALID, 10 * day + 3_600)
        val laterToday = entry(2, DvrEntryState.MISSED, 10 * day + 7_200)
        val yesterday = entry(3, DvrEntryState.COMPLETED_ERROR, 9 * day + 3_600)
        val older = entry(4, DvrEntryState.INVALID, 7 * day + 3_600)
        val undated = DvrEntry.create(DvrEntryId(5), state = DvrEntryState.MISSED)
        val kept = entry(6, DvrEntryState.COMPLETED, 10 * day + 9_000)

        val sections = groupDvrProblems(listOf(undated, older, earlierToday, kept, yesterday, laterToday), now,
            ZoneOffset.UTC)

        assertEquals(listOf(DvrScheduleSectionKind.TODAY, DvrScheduleSectionKind.YESTERDAY,
            DvrScheduleSectionKind.DATE, DvrScheduleSectionKind.DATE), sections.map { it.kind })
        assertEquals(listOf(listOf(laterToday, earlierToday), listOf(yesterday), listOf(older), listOf(undated)),
            sections.map { it.entries })
        assertEquals(java.time.LocalDate.ofEpochDay(7), sections[2].date)
        assertEquals(null, sections[3].date)
        // A local day, not a UTC one: an hour east, the evening before midnight UTC is already the next day.
        val lateEvening = entry(7, DvrEntryState.MISSED, 9 * day + 23 * 3_600 + 1_800)
        assertEquals(DvrScheduleSectionKind.TODAY,
            groupDvrProblems(listOf(lateEvening), now, ZoneOffset.ofHours(1)).single().kind)
    }
}
