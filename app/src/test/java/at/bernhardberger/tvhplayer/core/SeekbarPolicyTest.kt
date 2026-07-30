package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekbarPolicyTest {
    @Test
    fun axisIsNullWithoutAProgramme() {
        val state = TimeshiftState(available = true, bufferStartMs = -60_000L)

        assertEquals(null, programmeAnchoredAxis(state, 5_400L, null, 7_200L))
        assertEquals(null, programmeAnchoredAxis(state, 5_400L, 3_600L, 3_600L))
        assertEquals(
            null,
            programmeAnchoredAxis(state.copy(available = false), 5_400L, 3_600L, 7_200L),
        )
    }

    @Test
    fun playbackFractionTracksPositionWithinProgramme() {
        val axis = programmeAnchoredAxis(
            state = TimeshiftState(
                available = true,
                bufferStartMs = -1_800_000L,
                positionMs = -600_000L,
            ),
            nowEpochSec = 5_400L,
            programmeStartSec = 3_600L,
            programmeStopSec = 7_200L,
        )

        assertEquals(1f / 3f, requireNotNull(axis).playbackFraction, 0.001f)
    }

    @Test
    fun liveEdgeSitsAheadOfPlaybackWhenBehindLive() {
        val axis = requireNotNull(
            programmeAnchoredAxis(
                state = TimeshiftState(
                    available = true,
                    bufferStartMs = -1_800_000L,
                    positionMs = -120_000L,
                ),
                nowEpochSec = 5_400L,
                programmeStartSec = 3_600L,
                programmeStopSec = 7_200L,
            ),
        )

        assertTrue(axis.liveEdgeFraction > axis.playbackFraction)
        assertEquals(0.5f, axis.liveEdgeFraction, 0.001f)
    }

    @Test
    fun rewindableRegionClampsToProgrammeStart() {
        val axis = requireNotNull(
            programmeAnchoredAxis(
                state = TimeshiftState(
                    available = true,
                    bufferStartMs = -3_600_000L,
                ),
                nowEpochSec = 5_400L,
                programmeStartSec = 3_600L,
                programmeStopSec = 7_200L,
            ),
        )

        assertEquals(0f, axis.rewindableStartFraction, 0.001f)
        assertTrue(axis.rewindableStartsBeforeProgramme)
    }

    @Test
    fun rewindableBoundaryAtProgrammeStartIsNotOffAxis() {
        val axis = requireNotNull(
            programmeAnchoredAxis(
                state = TimeshiftState(
                    available = true,
                    bufferStartMs = -1_800_000L,
                ),
                nowEpochSec = 5_400L,
                programmeStartSec = 3_600L,
                programmeStopSec = 7_200L,
            ),
        )

        assertEquals(0f, axis.rewindableStartFraction, 0.001f)
        assertFalse(axis.rewindableStartsBeforeProgramme)
    }

    @Test
    fun axisIsStableAsTheBufferGrows() {
        val shortBuffer = requireNotNull(
            programmeAnchoredAxis(
                state = TimeshiftState(
                    available = true,
                    bufferStartMs = -60_000L,
                    positionMs = -30_000L,
                ),
                nowEpochSec = 5_400L,
                programmeStartSec = 3_600L,
                programmeStopSec = 7_200L,
            ),
        )
        val longBuffer = requireNotNull(
            programmeAnchoredAxis(
                state = TimeshiftState(
                    available = true,
                    bufferStartMs = -1_800_000L,
                    positionMs = -30_000L,
                ),
                nowEpochSec = 5_400L,
                programmeStartSec = 3_600L,
                programmeStopSec = 7_200L,
            ),
        )

        assertEquals(shortBuffer.playbackFraction, longBuffer.playbackFraction, 0.001f)
        assertEquals(shortBuffer.liveEdgeFraction, longBuffer.liveEdgeFraction, 0.001f)
    }

    @Test
    fun recordingDomainIsZeroToDuration() {
        val range = requireNotNull(
            recordingSeekbarRange(positionMs = 45_000, durationMs = 120_000)
        )
        assertEquals(SeekbarDomain.RECORDING, range.domain)
        assertEquals(0L, range.startMs)
        assertEquals(120_000L, range.endMs)
        assertEquals(45_000L, range.positionMs)
        assertEquals(0.375f, range.progress, 0.001f)
    }

    @Test
    fun recordingTimelineRequiresARealPositiveDurationForSeeking() {
        val seekable = recordingTimelinePresentation(
            positionMs = 45_000,
            durationMs = 120_000,
            growing = false,
        )
        assertEquals(
            RecordingTimelinePresentation.Seekable(
                requireNotNull(recordingSeekbarRange(45_000, 120_000))
            ),
            seekable,
        )

        assertEquals(
            RecordingTimelinePresentation.StillRecording(elapsedMs = 45_000),
            recordingTimelinePresentation(
                positionMs = 45_000,
                durationMs = null,
                growing = true,
            ),
        )
        assertEquals(
            RecordingTimelinePresentation.DurationUnavailable(elapsedMs = 45_000),
            recordingTimelinePresentation(
                positionMs = 45_000,
                durationMs = 0,
                growing = false,
            ),
        )
        assertNull(recordingSeekbarRange(positionMs = 45_000, durationMs = null))
        assertNull(recordingSeekbarRange(positionMs = 45_000, durationMs = 0))
    }

    @Test
    fun timeshiftDomainIsBufferToLiveEdge() {
        val range = timeshiftSeekbarRange(
            TimeshiftState(
                available = true,
                bufferStartMs = -600_000,
                positionMs = -120_000,
                liveEdgeMs = 0,
            ),
        )
        assertEquals(SeekbarDomain.TIMESHIFT, range.domain)
        assertEquals(-600_000L, range.startMs)
        assertEquals(0L, range.endMs)
        assertEquals(-120_000L, range.positionMs)
    }

    @Test
    fun repeatAccelerationGrowsFromThirtySecondsToFiveMinutes() {
        assertEquals(30_000L, seekStepMs(0))
        assertEquals(30_000L, seekStepMs(3))
        assertEquals(120_000L, seekStepMs(4))
        assertEquals(120_000L, seekStepMs(11))
        assertEquals(300_000L, seekStepMs(12))
    }

    @Test
    fun scrubStaysInsideRange() {
        val range = requireNotNull(recordingSeekbarRange(positionMs = 10_000, durationMs = 60_000))
        assertEquals(0L, seekbarScrub(range, direction = -1, repeatCount = 0))
        assertEquals(40_000L, seekbarScrub(range, direction = 1, repeatCount = 0))
        val nearEnd = requireNotNull(
            recordingSeekbarRange(positionMs = 55_000, durationMs = 60_000)
        )
        assertEquals(60_000L, seekbarScrub(nearEnd, direction = 1, repeatCount = 0))
    }

    @Test
    fun programmeProgressHiddenWithoutCurrentEvent() {
        assertTrue(shouldShowProgrammeProgress(hasCurrentEpgEvent = true))
        assertFalse(shouldShowProgrammeProgress(hasCurrentEpgEvent = false))
    }
}
