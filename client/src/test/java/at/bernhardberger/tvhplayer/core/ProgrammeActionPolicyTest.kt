package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTvheadendApi::class, TvheadendTestingApi::class)
class ProgrammeActionPolicyTest {
    private val event = EpgEvent.create(
        id = EventId(21), channelId = ChannelId(7),
        start = Instant.fromEpochSeconds(1_800_000_000),
        stop = Instant.fromEpochSeconds(1_800_003_600),
    )
    private val beforeStart = 1_799_999_900L

    @Test
    fun airingProgrammeOffersWatchThenRecord() {
        assertEquals(listOf(ProgrammeAction.WATCH, ProgrammeAction.RECORD),
            programmeActions(event, event.start.epochSeconds, null))
    }

    @Test
    fun airingRecordingOffersWatchThenStopKeepingThePartialRecording() {
        assertEquals(listOf(ProgrammeAction.WATCH, ProgrammeAction.STOP_RECORDING),
            programmeActions(event, event.start.epochSeconds, recording(DvrEntryState.RECORDING)))
    }

    @Test
    fun airingScheduledRecordingOffersWatchThenCancelAtThePaddingEdge() {
        assertEquals(listOf(ProgrammeAction.WATCH, ProgrammeAction.CANCEL_RECORDING),
            programmeActions(event, event.start.epochSeconds, recording(DvrEntryState.SCHEDULED)))
    }

    @Test
    fun readOnlyAiringProgrammeOffersOnlyWatchForEveryRecordingState() {
        listOf(null, recording(DvrEntryState.RECORDING), recording(DvrEntryState.SCHEDULED)).forEach {
            assertEquals(listOf(ProgrammeAction.WATCH),
                programmeActions(event, event.start.epochSeconds, it, canModifyRecordings = false))
        }
    }

    @Test
    fun endedProgrammeKeepsItsWatchFromStartPolicy() {
        assertEquals(emptyList<ProgrammeAction>(), programmeActions(event, event.stop.epochSeconds, null))
        assertEquals(listOf(ProgrammeAction.WATCH_FROM_START),
            ended(recording(DvrEntryState.COMPLETED)))
    }

    @Test
    fun endedProgrammeWithoutAnAdmissionOffersNoPlayback() {
        val completed = recording(DvrEntryState.COMPLETED)
        assertEquals(emptyList<ProgrammeAction>(),
            programmeActions(event, event.stop.epochSeconds, completed, recordingAdmission = null))
    }

    @Test
    fun endedProgrammeWithAFailedRecordingWatchesFromStartOnlyWithAPlayableFile() {
        for (state in listOf(DvrEntryState.COMPLETED_ERROR, DvrEntryState.MISSED)) {
            assertEquals("$state", listOf(ProgrammeAction.WATCH_FROM_START),
                ended(recording(state, files = listOf(file("/rec/a.ts")))))
            assertEquals("$state", emptyList<ProgrammeAction>(), ended(recording(state)))
        }
        assertEquals(emptyList<ProgrammeAction>(),
            ended(recording(DvrEntryState.FILE_MISSING, files = listOf(file("/rec/a.ts")))))
    }

    @Test
    fun endedProgrammeWithAFailingRecordingWatchesFromStartOnlyWithOneTransportStreamFile() {
        assertEquals(listOf(ProgrammeAction.WATCH_FROM_START),
            ended(recording(DvrEntryState.RECORDING_ERROR, files = listOf(file("/rec/a.ts")))))
        mapOf(
            "no files" to null,
            "empty files" to emptyList(),
            "two files" to listOf(file("/rec/a.ts"), file("/rec/b.ts")),
            "blank path" to listOf(file(" ")),
            "no path" to listOf(DvrRecordingFile.create(fileId = 1)),
            "not a transport stream" to listOf(file("/rec/a.mkv")),
        ).forEach { (case, files) ->
            assertEquals(case, emptyList<ProgrammeAction>(),
                ended(recording(DvrEntryState.RECORDING_ERROR, files = files)))
        }
    }

    @Test
    fun endedProgrammeWithAGrowingRecordingNeedsSupportedProgress() {
        val growing = recording(DvrEntryState.RECORDING_ERROR, files = listOf(file("/rec/a.ts")))
        listOf(RecordingProgressCapability.UNKNOWN, RecordingProgressCapability.UNSUPPORTED).forEach {
            assertEquals("$it", emptyList<ProgrammeAction>(), ended(growing, capability = it))
        }
    }

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

    private fun ended(
        recording: DvrEntry,
        capability: RecordingProgressCapability = RecordingProgressCapability.SUPPORTED,
    ) = programmeActions(event, event.stop.epochSeconds, recording,
        recordingAdmission = SessionObservation.create(recordingProgressCapability = capability)
            .recordingPlaybackAdmission(recording))

    private fun file(path: String) = TvheadendTestResultFactory.recordingFile(fileId = 1, serverPath = path)

    private fun recording(state: DvrEntryState, files: List<DvrRecordingFile>? = null) =
        DvrEntry.create(id = DvrEntryId(31), eventId = event.id, state = state, files = files)
}
