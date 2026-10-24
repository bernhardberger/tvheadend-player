package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RecordingMetadataPolicyTest {
    @Test fun removalUsesStopAndOnlyFutureFiniteDays() {
        fun entry(days: Long?, stop: Instant? = Instant.fromEpochSeconds(100)) =
            DvrEntry.create(DvrEntryId(1), stop = stop, removalDays = days)
        assertEquals(86500L, recordingRemovalTime(entry(1), 100))
        for (days in listOf(null, 0L, 2147483646L, 2147483647L))
            assertNull(recordingRemovalTime(entry(days), 100))
        assertNull(recordingRemovalTime(entry(1), 86500))
        assertNull(recordingRemovalTime(entry(1), 86501))
        assertNull(recordingRemovalTime(entry(1, null), 100))
        assertEquals(100L + 2147483645L * 86400L, recordingRemovalTime(entry(2147483645L), 100))
    }

    @Test fun sizePrefersTotalAndFallsBackToKnownFileSizes() {
        val files = listOf(DvrRecordingFile.create(sizeBytes = 100), DvrRecordingFile.create(sizeBytes = 200),
            DvrRecordingFile.create())
        assertEquals(500L, recordingSizeBytes(DvrEntry.create(DvrEntryId(1), dataSizeBytes = 500, files = files)))
        assertEquals(300L, recordingSizeBytes(DvrEntry.create(DvrEntryId(1), files = files)))
        assertEquals(0L, recordingSizeBytes(DvrEntry.create(DvrEntryId(1), dataSizeBytes = 0, files = files)))
        assertNull(recordingSizeBytes(DvrEntry.create(DvrEntryId(1), files = listOf(DvrRecordingFile.create()))))
    }

    private fun at(seconds: Long) = Instant.fromEpochSeconds(seconds)

    private fun timed(state: DvrEntryState, vararg files: DvrRecordingFile) = DvrEntry.create(DvrEntryId(1), state = state,
        start = at(1_000), stop = at(1_000 + 61 * 60), files = files.toList().takeIf { it.isNotEmpty() })

    @Test fun lengthSumsTheFilesTimesRatherThanTheSchedule() {
        assertEquals(5L, recordingLengthSeconds(timed(DvrEntryState.COMPLETED,
            DvrRecordingFile.create(start = at(1_010), stop = at(1_015))), nowSec = 99_999))
        assertEquals(46L * 60, recordingLengthSeconds(timed(DvrEntryState.COMPLETED,
            DvrRecordingFile.create(start = at(1_000), stop = at(1_000 + 30 * 60)),
            DvrRecordingFile.create(start = at(5_000), stop = at(5_000 + 16 * 60))), nowSec = 99_999))
    }

    @Test fun runningLengthMergesClosedAndOpenFilesWithoutCountingGaps() {
        for (state in listOf(DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) {
            assertEquals(600L, recordingLengthSeconds(timed(state,
                DvrRecordingFile.create(start = at(1_300), stop = at(1_400)),
                DvrRecordingFile.create(start = at(1_200))), nowSec = 1_800))
            val closed = DvrRecordingFile.create(start = at(1_000), stop = at(1_100))
            assertEquals(400L, recordingLengthSeconds(timed(state, closed,
                DvrRecordingFile.create(start = at(1_500))), nowSec = 1_800))
            assertEquals(400L, recordingLengthSeconds(timed(DvrEntryState.COMPLETED, closed,
                DvrRecordingFile.create(start = at(1_500), stop = at(1_800))), nowSec = 1_800))
            assertEquals(100L, recordingLengthSeconds(timed(state, closed), nowSec = 1_800))
        }
        assertTrue(recordingIsRunning(timed(DvrEntryState.RECORDING_ERROR)))
        assertFalse(recordingIsRunning(timed(DvrEntryState.COMPLETED_ERROR)))
    }

    @Test fun recordedSpanRunsFromTheFirstFileToTheLastStopOrNowWhileRecording() {
        assertEquals(at(1_000) to at(5_960), recordingRecordedSpan(timed(DvrEntryState.COMPLETED,
            DvrRecordingFile.create(start = at(5_000), stop = at(5_960)),
            DvrRecordingFile.create(start = at(1_000), stop = at(2_800))), nowSec = 99_999))
        assertEquals(at(1_200) to at(1_800), recordingRecordedSpan(timed(DvrEntryState.RECORDING,
            DvrRecordingFile.create(start = at(1_200))), nowSec = 1_800))
        assertNull(recordingRecordedSpan(timed(DvrEntryState.COMPLETED), nowSec = 99_999))
        assertNull(recordingRecordedSpan(timed(DvrEntryState.COMPLETED, DvrRecordingFile.create()), nowSec = 99_999))
    }

    @Test fun onlyMissingFileTimesFallBackToThePlannedLength() {
        assertEquals(61L * 60, recordingLengthSeconds(timed(DvrEntryState.COMPLETED), nowSec = 99_999))
        assertEquals(61L * 60, recordingLengthSeconds(timed(DvrEntryState.COMPLETED,
            DvrRecordingFile.create(sizeBytes = 7), DvrRecordingFile.create()), nowSec = 99_999))
        assertNull(recordingLengthSeconds(timed(DvrEntryState.RECORDING,
            DvrRecordingFile.create(sizeBytes = 7)), nowSec = 1_800))
        assertNull(recordingLengthSeconds(timed(DvrEntryState.RECORDING_ERROR), nowSec = 1_800))
        assertNull(recordingLengthSeconds(DvrEntry.create(DvrEntryId(1), start = at(1_000)), nowSec = 1_800))
    }

    @Test fun anEntryThatNeverRecordedHasNoLength() {
        for (state in listOf(DvrEntryState.MISSED, DvrEntryState.INVALID)) {
            assertNull(recordingLengthSeconds(timed(state), nowSec = 99_999))
        }
    }

    @Test fun incompleteOrMixedTimelessFilesMakeTheTotalUnknown() {
        val valid = DvrRecordingFile.create(start = at(1_000), stop = at(1_300))
        for (state in listOf(DvrEntryState.COMPLETED, DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING)) {
            for (invalid in listOf(
                DvrRecordingFile.create(start = at(1_400)),
                DvrRecordingFile.create(stop = at(1_500)),
            )) {
                for (files in listOf(listOf(invalid), listOf(valid, invalid))) {
                    val entry = timed(state, *files.toTypedArray())
                    assertNull(recordingLengthSeconds(entry, nowSec = 1_800))
                    assertNull(recordingStoppedEarly(entry))
                }
            }
            val mixed = timed(state, valid, DvrRecordingFile.create(sizeBytes = 1))
            assertNull(recordingLengthSeconds(mixed, nowSec = 1_800))
            assertNull(recordingStoppedEarly(mixed))
        }
        for (state in listOf(DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) {
            assertNull(recordingLengthSeconds(timed(state, valid,
                DvrRecordingFile.create(stop = at(1_500))), nowSec = 1_800))
            assertNull(recordingLengthSeconds(timed(state, valid,
                DvrRecordingFile.create(sizeBytes = 1)), nowSec = 1_800))
        }
    }

    @Test fun zeroRecordedLengthDoesNotProduceAStoppedEarlyHint() {
        assertNull(recordingStoppedEarly(timed(DvrEntryState.COMPLETED,
            DvrRecordingFile.create(start = at(1_000), stop = at(1_000)))))
    }

    @Test fun overlappingFilesCountEachRecordedSecondOnlyOnce() {
        val files = arrayOf(
            DvrRecordingFile.create(start = at(1_200), stop = at(1_400)),
            DvrRecordingFile.create(start = at(1_000), stop = at(1_300)),
            DvrRecordingFile.create(start = at(1_100), stop = at(1_200)),
            DvrRecordingFile.create(start = at(1_400), stop = at(1_500)),
        )
        for (state in listOf(DvrEntryState.COMPLETED, DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) {
            assertEquals(500L, recordingLengthSeconds(timed(state, *files), nowSec = 1_800))
        }
        assertEquals(RecordingShortfall(500, 61 * 60), recordingStoppedEarly(timed(DvrEntryState.COMPLETED, *files)))
    }

    @Test fun paddingComesFromTheEntrysEffectiveExtraMinutes() {
        val padded = DvrEntry.create(DvrEntryId(1), start = at(10_000), stop = at(12_000),
            startExtraMinutes = 2.minutes, stopExtraMinutes = 120.minutes)
        assertEquals(at(10_000 - 120), recordingPaddedStart(padded))
        assertEquals(at(12_000 + 7_200), recordingPaddedStop(padded))
        val unpadded = DvrEntry.create(DvrEntryId(1), start = at(10_000), stop = at(12_000))
        assertEquals(at(10_000), recordingPaddedStart(unpadded))
        assertEquals(at(12_000), recordingPaddedStop(unpadded))
        assertNull(recordingPaddedStop(DvrEntry.create(DvrEntryId(1), start = at(10_000))))
    }

    @Test fun stoppedEarlyNeedsAFinishedFileUnderTheThreshold() {
        val planned = 61L * 60
        val threshold = (planned * RECORDING_STOPPED_EARLY_FRACTION).toLong()
        fun file(seconds: Long) = DvrRecordingFile.create(start = at(1_000), stop = at(1_000 + seconds))
        assertEquals(RecordingShortfall(5, planned), recordingStoppedEarly(timed(DvrEntryState.COMPLETED, file(5))))
        assertEquals(RecordingShortfall(5, planned), recordingStoppedEarly(timed(DvrEntryState.COMPLETED_ERROR, file(5))))
        assertNotNull(recordingStoppedEarly(timed(DvrEntryState.FILE_MISSING, file(threshold - 1))))
        assertNull(recordingStoppedEarly(timed(DvrEntryState.COMPLETED, file(threshold))))
        assertNull(recordingStoppedEarly(timed(DvrEntryState.COMPLETED, file(planned + 300))))
        // A short programme under the share but missing less than two minutes is not flagged.
        fun tenMinutes(recorded: Long) = DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED,
            start = at(1_000), stop = at(1_000 + 600), files = listOf(file(recorded)))
        assertNull(recordingStoppedEarly(tenMinutes(600 - 119)))
        assertNotNull(recordingStoppedEarly(tenMinutes(600 - 120)))
        // Running, scheduled and fallback lengths are not evidence of an early stop.
        assertNull(recordingStoppedEarly(timed(DvrEntryState.RECORDING, file(5))))
        assertNull(recordingStoppedEarly(timed(DvrEntryState.SCHEDULED)))
        assertNull(recordingStoppedEarly(timed(DvrEntryState.COMPLETED)))
        assertNull(recordingStoppedEarly(timed(DvrEntryState.COMPLETED, DvrRecordingFile.create(sizeBytes = 7))))
    }

    @Test fun stoppedEarlyRequiresAnEarlyFinalStopNotALateStartOrGap() {
        val stop = 1_000 + 61L * 60
        fun file(start: Long, end: Long) = DvrRecordingFile.create(start = at(start), stop = at(end))
        for (state in listOf(DvrEntryState.COMPLETED, DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING)) {
            assertNull(recordingStoppedEarly(timed(state, file(1_600, stop))))
            assertNull(recordingStoppedEarly(timed(state, file(2_000, stop), file(1_000, 1_300))))
            assertNull(recordingStoppedEarly(timed(state, file(1_600, stop - 119))))
            assertEquals(RecordingShortfall(stop - 120 - 1_600, 61 * 60),
                recordingStoppedEarly(timed(state, file(1_600, stop - 120))))
        }
        // Post-padding must not turn a normal programme end into an early stop.
        assertNull(recordingStoppedEarly(DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED,
            start = at(1_000), stop = at(stop), stopExtraMinutes = 5.minutes,
            files = listOf(file(1_600, stop)))))
    }

    @Test fun errorsKeepUnknownDistinctFromZero() {
        fun errors(stream: Long?, data: Long?) = recordingErrorCount(
            DvrEntry.create(DvrEntryId(1), streamErrors = stream, dataErrors = data))
        assertNull(errors(null, null))
        assertEquals(0L, errors(0, null))
        assertEquals(3L, errors(1, 2))
        assertEquals(2L, errors(null, 2))
    }

    @OptIn(ExperimentalTvheadendApi::class)
    @Test fun pictureUsesHighestKnownVideoHeight() {
        fun picture(vararg heights: Int?) = recordingPicture(DvrEntry.create(DvrEntryId(1), files = listOf(
            DvrRecordingFile.create(streams = heights.map { DvrRecordingStream(SubscriptionStreamType.H264, height = it) }))))
        assertNull(picture(null, 0))
        assertEquals("SD 576", picture(576))
        assertEquals("HD 720", picture(720))
        assertEquals("HD 1080", picture(720, 1080))
        assertEquals("UHD 2160", picture(1080, 2160))
    }

    @Test fun watchStateIsUnwatchedWatchedOrPartlyWatchedButNeverACount() {
        fun entry(playCount: Long?, state: DvrEntryState = DvrEntryState.COMPLETED) = DvrEntry.create(DvrEntryId(1),
            state = state, start = at(0), stop = at(100 * 60), playCount = playCount)
        assertEquals(RecordingWatchState.Unwatched, recordingWatchState(entry(null), null, 0))
        assertEquals(RecordingWatchState.Unwatched, recordingWatchState(entry(0), null, 0))
        assertEquals(RecordingWatchState.Watched, recordingWatchState(entry(147), null, 0))
        assertEquals(RecordingWatchState.Unwatched, recordingWatchState(entry(3, DvrEntryState.MISSED), null, 0))
        // A saved position wins over the open count, and its share uses the recorded length.
        assertEquals(RecordingWatchState.PartlyWatched(0.25), recordingWatchState(entry(2), 25.minutes, 0))
        // The SDK clears the position when it marks a recording watched, so a late one is still partly watched.
        assertEquals(RecordingWatchState.PartlyWatched(0.99), recordingWatchState(entry(0), 99.minutes, 0))
        assertEquals(RecordingWatchState.Unwatched, recordingWatchState(entry(0), 0.minutes, 0))
        assertEquals(RecordingWatchState.PartlyWatched(null), recordingWatchState(
            DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED), 5.minutes, 0))
    }
}
