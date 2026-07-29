package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.DvrState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingProgressPolicyTest {
    @Test
    fun mediaMillisecondsFloorToWholeServerSeconds() {
        assertEquals(180L, mediaMillisecondsToRecordingSeconds(180_999L))
        assertEquals(0L, mediaMillisecondsToRecordingSeconds(0L))
        assertNull(mediaMillisecondsToRecordingSeconds(-1L))
    }

    @Test
    fun serverSecondsRejectNegativeAndOverflow() {
        assertEquals(180_000L, recordingSecondsToMediaMilliseconds(180L))
        assertNull(recordingSecondsToMediaMilliseconds(-1L))
        assertNull(recordingSecondsToMediaMilliseconds(Long.MAX_VALUE))
    }

    @Test
    fun resumeCandidateHonorsFloorCompletionAndGrowingGate() {
        assertNull(recordingResumeCandidateSeconds(DvrState.COMPLETED, 0L))
        assertNull(recordingResumeCandidateSeconds(DvrState.COMPLETED, -1L))
        assertNull(recordingResumeCandidateSeconds(DvrState.COMPLETED, Long.MAX_VALUE))
        assertNull(recordingResumeCandidateSeconds(DvrState.COMPLETED, 179L))
        assertEquals(180L, recordingResumeCandidateSeconds(DvrState.COMPLETED, 180L))
        assertNull(recordingResumeCandidateSeconds(DvrState.RECORDING, 600L))
    }

    @Test
    fun defaultAndExplicitResumeRequireUsableKnownTimeline() {
        assertEquals(
            RecordingStartDecision.FromBeginning,
            recordingStartDecision(
                intent = RecordingPlaybackIntent.DefaultPolicy,
                state = DvrState.COMPLETED,
                serverPositionSeconds = 600L,
                durationMs = null,
            ),
        )
        assertEquals(
            RecordingStartDecision.ResumeAt(600_000L),
            recordingStartDecision(
                intent = RecordingPlaybackIntent.DefaultPolicy,
                state = DvrState.COMPLETED,
                serverPositionSeconds = 600L,
                durationMs = 3_600_000L,
            ),
        )
        assertEquals(
            RecordingStartDecision.ResumeAt(900_000L),
            recordingStartDecision(
                intent = RecordingPlaybackIntent.Resume(900L),
                state = DvrState.COMPLETED,
                serverPositionSeconds = 600L,
                durationMs = 3_600_000L,
            ),
        )
        assertEquals(
            RecordingStartDecision.FromBeginning,
            recordingStartDecision(
                intent = RecordingPlaybackIntent.FromBeginning,
                state = DvrState.COMPLETED,
                serverPositionSeconds = 600L,
                durationMs = 3_600_000L,
            ),
        )
    }

    @Test
    fun oversizedAndNearEndResumePointsStartFromBeginning() {
        assertEquals(
            RecordingStartDecision.FromBeginning,
            recordingStartDecision(
                intent = RecordingPlaybackIntent.DefaultPolicy,
                state = DvrState.COMPLETED,
                serverPositionSeconds = 3_600L,
                durationMs = 3_600_000L,
            ),
        )
        assertEquals(
            RecordingStartDecision.FromBeginning,
            recordingStartDecision(
                intent = RecordingPlaybackIntent.DefaultPolicy,
                state = DvrState.COMPLETED,
                serverPositionSeconds = 3_450L,
                durationMs = 3_600_000L,
            ),
        )
        assertEquals(
            RecordingStartDecision.ResumeAt(3_200_000L),
            recordingStartDecision(
                intent = RecordingPlaybackIntent.DefaultPolicy,
                state = DvrState.COMPLETED,
                serverPositionSeconds = 3_200L,
                durationMs = 3_600_000L,
            ),
        )
    }

    @Test
    fun watchedRecordingCanResumeLaterPartialRewatch() {
        assertEquals(
            RecordingStartDecision.ResumeAt(600_000L),
            recordingStartDecision(
                intent = RecordingPlaybackIntent.DefaultPolicy,
                state = DvrState.COMPLETED,
                serverPositionSeconds = 600L,
                durationMs = 3_600_000L,
                playCount = 4,
            ),
        )
    }

    @Test
    fun completionRequiresBothThresholdsOnFinalButNaturalEndIsImmediate() {
        val duration = 2 * 60 * 60 * 1_000L
        assertFalse(recordingIsComplete(DvrState.COMPLETED, duration - 300_001L, duration, false))
        assertTrue(recordingIsComplete(DvrState.COMPLETED, duration - 300_000L, duration, false))
        assertFalse(recordingIsComplete(DvrState.COMPLETED, duration * 95 / 100 - 1L, duration, false))
        assertTrue(recordingIsComplete(DvrState.COMPLETED, 0L, duration, true))
        assertFalse(recordingIsComplete(DvrState.RECORDING, duration, duration, true))
        assertFalse(recordingIsComplete(DvrState.COMPLETED, -1L, duration, true))
        assertFalse(recordingIsComplete(DvrState.COMPLETED, duration + 1L, duration, true))
    }

    @Test
    fun terminalErrorNeverCompletes() {
        assertFalse(
            recordingCompletionDecision(
                state = DvrState.COMPLETED,
                trigger = RecordingCompletionTrigger.Final,
                positionMs = 5_900_000L,
                durationMs = 6_000_000L,
                terminalError = true,
            ),
        )
        assertFalse(
            recordingCompletionDecision(
                state = DvrState.COMPLETED,
                trigger = RecordingCompletionTrigger.NaturalEnd,
                positionMs = 6_000_000L,
                durationMs = 6_000_000L,
                terminalError = true,
            ),
        )
    }

    @Test
    fun everyCheckpointTriggerSuppressesBelowFloor() {
        RecordingCheckpointTrigger.entries.forEach { trigger ->
            assertNull(recordingCheckpointSeconds(trigger, 179_999L))
            assertEquals(180L, recordingCheckpointSeconds(trigger, 180_000L))
        }
    }

    @Test
    fun periodicCheckpointRequiresIntervalAndMinimumDelta() {
        assertEquals(2_000L, RECORDING_SEEK_CHECKPOINT_DEBOUNCE_MS)
        assertFalse(
            recordingPeriodicCheckpointDue(
                nowMs = 29_999L,
                lastAttemptMs = 0L,
                positionSeconds = 210L,
                lastAcceptedSeconds = 180L,
            ),
        )
        assertFalse(
            recordingPeriodicCheckpointDue(
                nowMs = 30_000L,
                lastAttemptMs = 0L,
                positionSeconds = 189L,
                lastAcceptedSeconds = 180L,
            ),
        )
        assertTrue(
            recordingPeriodicCheckpointDue(
                nowMs = 30_000L,
                lastAttemptMs = 0L,
                positionSeconds = 190L,
                lastAcceptedSeconds = 180L,
            ),
        )
    }

    @Test
    fun completionLatchEmitsOncePerGeneration() {
        val latch = RecordingCompletionLatch()
        assertTrue(latch.claim(4L))
        assertFalse(latch.claim(4L))
        assertTrue(latch.claim(5L))
        assertFalse(latch.claim(5L))
    }

    @Test
    fun remoteBaselineIsAdoptedOnlyWhileLocallyClean() {
        assertEquals(
            RecordingRemoteUpdateDecision.AdoptRemote,
            recordingRemoteUpdateDecision(localDirty = false, remoteSeconds = 900L),
        )
        assertEquals(
            RecordingRemoteUpdateDecision.KeepLocal,
            recordingRemoteUpdateDecision(localDirty = true, remoteSeconds = 900L),
        )
    }
}
