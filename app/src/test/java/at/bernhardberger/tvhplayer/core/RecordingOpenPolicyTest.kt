package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalTvheadendApi::class, TvheadendTestingApi::class)
class RecordingOpenPolicyTest {
    private fun entry(
        state: DvrEntryState = DvrEntryState.COMPLETED,
        position: Long? = null,
        duration: Long? = 600,
        files: List<DvrRecordingFile>? = null,
        dataSize: Long? = null,
    ) = DvrEntry.create(DvrEntryId(1), state = state, playPosition = position?.seconds,
        start = Instant.fromEpochSeconds(0), stop = duration?.let(Instant::fromEpochSeconds),
        files = files, dataSizeBytes = dataSize)

    private fun file(path: String = "/rec/a.ts") = TvheadendTestResultFactory.recordingFile(fileId = 1, serverPath = path)

    private fun open(
        entry: DvrEntry,
        capability: RecordingProgressCapability = RecordingProgressCapability.SUPPORTED,
    ) = recordingOpenAction(entry, SessionObservation.create(recordingProgressCapability = capability)
        .recordingPlaybackAdmission(entry))

    @Test fun unstartedAndFinishedPlayFromBeginning() {
        listOf(null, 0L, 570L, 600L, 800L).forEach {
            assertEquals(RecordingOpenAction.START_OVER, open(entry(position = it)))
        }
    }
    @Test fun positiveProgressBelowSdkCompletionPointPrompts() {
        listOf(1L, 300L, 569L).forEach {
            assertEquals(RecordingOpenAction.RESUME_CHOICE, open(entry(position = it)))
        }
        assertEquals(RecordingOpenAction.RESUME_CHOICE, open(entry(position = 300, duration = null)))
    }
    @Test fun completedRecordingWithoutSupportedProgressPlaysWithoutResume() {
        listOf(RecordingProgressCapability.UNKNOWN, RecordingProgressCapability.UNSUPPORTED).forEach {
            assertEquals("$it", RecordingOpenAction.START_OVER, open(entry(position = 300), it))
        }
    }
    @Test fun growingRecordingsUseExistingPrimaryPlaybackWithoutPrompt() {
        for (state in listOf(DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) {
            assertEquals("$state", RecordingOpenAction.START_OVER, open(entry(state, files = listOf(file()))))
            assertEquals("$state", RecordingOpenAction.RESUME, open(entry(state, 590, files = listOf(file()))))
            assertEquals("$state", RecordingOpenAction.RESUME, open(entry(state, 590, files = listOf(file("/rec/A.TS")))))
        }
    }
    @Test fun growingRecordingWithoutOneTransportStreamFileOpensDetails() {
        mapOf(
            "no files" to null,
            "empty files" to emptyList(),
            "two files" to listOf(file(), file("/rec/b.ts")),
            "blank path" to listOf(file(" ")),
            "no path" to listOf(DvrRecordingFile.create(fileId = 1)),
            "not a transport stream" to listOf(file("/rec/a.mkv")),
        ).forEach { (case, files) ->
            for (state in listOf(DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) {
                assertEquals("$state $case", RecordingOpenAction.DETAILS, open(entry(state, 590, files = files)))
            }
        }
    }
    @Test fun growingRecordingWithoutSupportedProgressOpensDetails() {
        listOf(RecordingProgressCapability.UNKNOWN, RecordingProgressCapability.UNSUPPORTED).forEach {
            assertEquals("$it", RecordingOpenAction.DETAILS, open(entry(DvrEntryState.RECORDING, 590, files = listOf(file())), it))
        }
    }
    @Test fun failedRecordingWithAFilePlaysLikeACompletedOne() {
        val failed = DvrEntryState.COMPLETED_ERROR
        assertEquals(RecordingOpenAction.START_OVER, open(entry(failed, files = listOf(file()))))
        assertEquals(RecordingOpenAction.RESUME_CHOICE, open(entry(failed, 300, files = listOf(file()))))
        assertEquals(RecordingOpenAction.RESUME_CHOICE, open(entry(failed, 300, files = listOf(file()), dataSize = 1)))
        assertEquals(RecordingOpenAction.START_OVER, open(entry(failed, 590, files = listOf(file()))))
    }
    @Test fun missedRecordingWithAFileAlwaysStartsOver() {
        listOf(null, 300L).forEach {
            assertEquals(RecordingOpenAction.START_OVER, open(entry(DvrEntryState.MISSED, it, files = listOf(file()))))
        }
    }
    @Test fun scheduledFailedWithoutAFileAndUnavailablePlaybackOpenDetails() {
        listOf(DvrEntryState.SCHEDULED, DvrEntryState.MISSED, DvrEntryState.INVALID,
            DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING).forEach {
            assertEquals("$it", RecordingOpenAction.DETAILS, open(entry(it, 300)))
            assertEquals("$it", RecordingOpenAction.DETAILS, open(entry(it, 300, files = emptyList())))
        }
        for (state in listOf(DvrEntryState.MISSED, DvrEntryState.COMPLETED_ERROR)) {
            assertEquals("$state", RecordingOpenAction.DETAILS, open(entry(state, 300, files = listOf(file()), dataSize = 0)))
        }
        assertEquals(RecordingOpenAction.DETAILS, open(entry(DvrEntryState.FILE_MISSING, 300, files = listOf(file()))))
        assertEquals(RecordingOpenAction.DETAILS, open(entry(DvrEntryState.INVALID, 300, files = listOf(file()))))
    }
    @Test fun noAdmissionOffersNoPlaybackOrResume() {
        assertEquals(RecordingOpenAction.DETAILS, recordingOpenAction(entry(position = 300), null))
        assertNull(offeredResumePosition(null))
    }
    @Test fun timeLeftIsTheRestFromWhereOpeningStartsAndOnlyForAFinishedFile() {
        val observation = SessionObservation.create(recordingProgressCapability = RecordingProgressCapability.SUPPORTED)
        fun left(entry: DvrEntry) = recordingTimeLeft(entry, observation.recordingPlaybackAdmission(entry))
        assertEquals(600.seconds, left(entry()))
        assertEquals(300.seconds, left(entry(position = 300)))
        // Past the completion point opening starts over, so all of it is left.
        assertEquals(600.seconds, left(entry(position = 590)))
        assertNull(left(entry(duration = null)))
        assertNull(left(entry(DvrEntryState.RECORDING, 300, files = listOf(file()))))
        assertNull(left(entry(DvrEntryState.SCHEDULED)))
        assertNull(recordingTimeLeft(entry(position = 300), null))
    }
    @Test fun resumeOfferFollowsTheAdmissionPosition() {
        assertEquals(300.seconds, offeredResumePosition(TvheadendTestResultFactory.completedRecordingAdmission(
            300.seconds, RecordingProgressCapability.SUPPORTED)))
        assertNull(offeredResumePosition(TvheadendTestResultFactory.growingRecordingAdmission(
            300.seconds, RecordingProgressCapability.UNKNOWN)))
    }
}
