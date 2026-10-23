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
import at.bernhardberger.tvhplayer.core.BrowseContextAction.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTvheadendApi::class, TvheadendTestingApi::class)
class BrowseContextActionsTest {
    private val event = EpgEvent.create(EventId(1), ChannelId(1), Instant.fromEpochSeconds(100), Instant.fromEpochSeconds(200))
    private fun recording(
        state: DvrEntryState,
        position: Long = 0,
        files: List<DvrRecordingFile>? = null,
        dataSize: Long? = null,
        playCount: Long? = 0,
    ) = DvrEntry.create(DvrEntryId(1), state = state, playPosition = position.seconds, files = files,
        dataSizeBytes = dataSize, playCount = playCount)

    @Test fun channelsWithoutProgrammeOrPermissionDoNotOfferRecording() {
        assertEquals(listOf(WATCH, SHOW_GUIDE), channelContextActions(null, 100, null, true))
        assertEquals(listOf(WATCH, DETAILS, SHOW_GUIDE), channelContextActions(event, 100, null, false))
    }

    @Test fun channelsAndGuideReuseProgrammeRecordingPolicy() {
        listOf(null to RECORD, DvrEntryState.RECORDING to STOP, DvrEntryState.RECORDING_ERROR to STOP,
            DvrEntryState.SCHEDULED to CANCEL).forEach { (state, action) ->
            val recording = state?.let { recording(it) }
            assertEquals(listOf(WATCH, DETAILS, action, SHOW_GUIDE), channelContextActions(event, 100, recording, true))
            assertEquals(listOf(DETAILS, action, WATCH), guideContextActions(event, 100, recording, true))
            assertEquals(listOf(DETAILS, WATCH), guideContextActions(event, 100, recording, false))
        }
        assertEquals(listOf(DETAILS, WATCH), guideContextActions(event, 200, null, true))
    }

    @Test fun completedRecordingsOfferResumeOnlyWithProgress() {
        assertEquals(listOf(PLAY, DETAILS, MARK_WATCHED, DELETE), actions(recording(DvrEntryState.COMPLETED)))
        assertEquals(listOf(RESUME, START_OVER, DETAILS, MARK_WATCHED, DELETE), actions(recording(DvrEntryState.COMPLETED, 20)))
    }

    @Test fun completedWatchedRecordingsOfferOnlyMarkUnwatchedAfterDetails() {
        assertEquals(listOf(PLAY, DETAILS, MARK_UNWATCHED, DELETE),
            actions(recording(DvrEntryState.COMPLETED, playCount = 2)))
    }

    @Test fun completedRecordingsWithoutSupportedProgressOfferOnlyPlay() {
        listOf(RecordingProgressCapability.UNKNOWN, RecordingProgressCapability.UNSUPPORTED).forEach {
            assertEquals("$it", listOf(PLAY, DETAILS, MARK_WATCHED, DELETE), actions(recording(DvrEntryState.COMPLETED, 20), capability = it))
        }
    }

    @Test fun recordingAndScheduledActionsMatchLifecycle() {
        assertEquals(listOf(WATCH, START_OVER, DETAILS, STOP), actions(recording(DvrEntryState.RECORDING, 20, listOf(file()))))
        assertEquals(listOf(WATCH, DETAILS, STOP), actions(recording(DvrEntryState.RECORDING, files = listOf(file()))))
        assertEquals(listOf(DETAILS, CANCEL), actions(recording(DvrEntryState.SCHEDULED)))
    }

    @Test fun failedRecordingsWithoutAPlayableFileOnlyOfferDetailsAndDelete() {
        listOf(DvrEntryState.MISSED, DvrEntryState.INVALID,
            DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING).forEach {
            assertEquals("$it", listOf(DETAILS, DELETE), actions(recording(it, 20)))
            assertEquals("$it", listOf(DETAILS, DELETE), actions(recording(it, 20, files = emptyList())))
        }
        listOf(DvrEntryState.MISSED, DvrEntryState.COMPLETED_ERROR).forEach {
            assertEquals("$it", listOf(DETAILS, DELETE), actions(recording(it, 20, listOf(file()), dataSize = 0)))
        }
        listOf(DvrEntryState.INVALID, DvrEntryState.FILE_MISSING).forEach {
            assertEquals("$it", listOf(DETAILS, DELETE), actions(recording(it, 20, listOf(file()))))
        }
    }

    @Test fun failedRecordingWithAFilePlaysLikeACompletedOne() {
        val failed = DvrEntryState.COMPLETED_ERROR
        assertEquals(listOf(PLAY, DETAILS, MARK_WATCHED, DELETE), actions(recording(failed, files = listOf(file()))))
        assertEquals(listOf(RESUME, START_OVER, DETAILS, MARK_WATCHED, DELETE), actions(recording(failed, 20, listOf(file()))))
        assertEquals(listOf(RESUME, START_OVER, DETAILS, MARK_WATCHED, DELETE), actions(recording(failed, 20, listOf(file()), dataSize = 1)))
    }

    @Test fun missedRecordingWithAFileOnlyOffersPlay() {
        listOf(0L, 20L).forEach {
            assertEquals(listOf(PLAY, DETAILS, DELETE), actions(recording(DvrEntryState.MISSED, it, listOf(file()))))
        }
    }

    @Test fun failingRecordingIsWatchedLikeARunningOne() {
        assertEquals(listOf(WATCH, START_OVER, DETAILS, STOP), actions(recording(DvrEntryState.RECORDING_ERROR, 20, listOf(file()))))
        assertEquals(listOf(WATCH, DETAILS, STOP), actions(recording(DvrEntryState.RECORDING_ERROR, files = listOf(file()))))
    }

    @Test fun growingRecordingWithoutOneTransportStreamFileOffersNoPlayback() {
        mapOf(
            "no files" to null,
            "empty files" to emptyList(),
            "two files" to listOf(file(), file("/rec/b.ts")),
            "blank path" to listOf(file(" ")),
            "no path" to listOf(DvrRecordingFile.create(fileId = 1)),
            "not a transport stream" to listOf(file("/rec/a.mkv")),
        ).forEach { (case, files) ->
            assertEquals("RECORDING $case", listOf(DETAILS, STOP), actions(recording(DvrEntryState.RECORDING, 20, files)))
            assertEquals("RECORDING_ERROR $case", listOf(DETAILS, STOP),
                actions(recording(DvrEntryState.RECORDING_ERROR, 20, files)))
        }
    }

    @Test fun growingRecordingWithoutSupportedProgressOffersNoPlayback() {
        listOf(RecordingProgressCapability.UNKNOWN, RecordingProgressCapability.UNSUPPORTED).forEach {
            assertEquals("$it", listOf(DETAILS, STOP),
                actions(recording(DvrEntryState.RECORDING, 20, listOf(file())), capability = it))
        }
    }

    @Test fun noAdmissionRemovesPlaybackAndUnavailableAuthorityRemovesMutations() {
        listOf(DvrEntryState.COMPLETED, DvrEntryState.RECORDING, DvrEntryState.SCHEDULED,
            DvrEntryState.MISSED, DvrEntryState.INVALID, DvrEntryState.RECORDING_ERROR,
            DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING).forEach {
            assertEquals(listOf(DETAILS), recordingContextActions(recording(it, 20, listOf(file())), null, false))
        }
    }

    @Test fun playbackAndMutationAuthorityAreIndependent() {
        assertEquals(listOf(RESUME, START_OVER, DETAILS), actions(recording(DvrEntryState.COMPLETED, 20), canModify = false))
        assertEquals(listOf(DETAILS, DELETE), recordingContextActions(
            recording(DvrEntryState.COMPLETED, 20), admission = null, canModifyRecordings = true))
    }

    private fun file(path: String = "/rec/a.ts") = TvheadendTestResultFactory.recordingFile(fileId = 1, serverPath = path)

    private fun actions(
        entry: DvrEntry,
        canModify: Boolean = true,
        capability: RecordingProgressCapability = RecordingProgressCapability.SUPPORTED,
    ) = recordingContextActions(entry, SessionObservation.create(recordingProgressCapability = capability)
        .recordingPlaybackAdmission(entry), canModify)
}
