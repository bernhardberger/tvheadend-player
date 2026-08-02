package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingPlaybackCalculationsTest {
    @Test
    fun boundedReadsNeverPassKnownEndOfFile() {
        assertEquals(4, recordingReadLength(requested = 64, bytesRemaining = 4))
        assertEquals(64, recordingReadLength(requested = 64, bytesRemaining = null))
        assertEquals(0, recordingReadLength(requested = 64, bytesRemaining = 0))
    }

    @Test
    fun fixedSeekStepClampsAtRecordingBounds() {
        assertEquals(0L, recordingSeekTarget(10_000L, 120_000L, -30_000L))
        assertEquals(120_000L, recordingSeekTarget(110_000L, 120_000L, 30_000L))
        assertEquals(70_000L, recordingSeekTarget(40_000L, null, 30_000L))
    }

    @Test
    fun rapidSeekStepsStackFromThePendingTarget() {
        assertEquals(
            80_000L,
            recordingStackedSeekTarget(
                currentMs = 10_000L,
                pendingTargetMs = 50_000L,
                durationMs = 120_000L,
                deltaMs = 30_000L,
            ),
        )
        assertEquals(
            40_000L,
            recordingStackedSeekTarget(
                currentMs = 10_000L,
                pendingTargetMs = null,
                durationMs = 120_000L,
                deltaMs = 30_000L,
            ),
        )
    }

    @Test
    fun seekFeedbackWaitsForPlaybackToResumeOrPausedMediaToBecomeReady() {
        assertEquals(
            false,
            recordingSeekFeedbackSettled(
                playerReady = false,
                playerEnded = false,
                playWhenReady = true,
                isPlaying = false,
                playbackFailed = false,
            ),
        )
        assertEquals(
            false,
            recordingSeekFeedbackSettled(
                playerReady = true,
                playerEnded = false,
                playWhenReady = true,
                isPlaying = false,
                playbackFailed = false,
            ),
        )
        assertEquals(
            true,
            recordingSeekFeedbackSettled(
                playerReady = true,
                playerEnded = false,
                playWhenReady = true,
                isPlaying = true,
                playbackFailed = false,
            ),
        )
        assertEquals(
            true,
            recordingSeekFeedbackSettled(
                playerReady = true,
                playerEnded = false,
                playWhenReady = false,
                isPlaying = false,
                playbackFailed = false,
            ),
        )
        assertEquals(
            true,
            recordingSeekFeedbackSettled(
                playerReady = false,
                playerEnded = true,
                playWhenReady = true,
                isPlaying = false,
                playbackFailed = false,
            ),
        )
        assertEquals(
            true,
            recordingSeekFeedbackSettled(
                playerReady = false,
                playerEnded = false,
                playWhenReady = true,
                isPlaying = false,
                playbackFailed = true,
            ),
        )
    }

    @Test
    fun finishedRecordingAlwaysStopsAndOnlyClosesAVisiblePlayer() {
        assertEquals(
            RecordingFinishedAction.STOP_AND_CLOSE_PLAYER,
            recordingFinishedAction(
                recordingFinished = true,
                activeRecordingId = 7,
                recordingPlayerVisible = true,
            ),
        )
        assertEquals(
            RecordingFinishedAction.STOP,
            recordingFinishedAction(
                recordingFinished = true,
                activeRecordingId = 7,
                recordingPlayerVisible = false,
            ),
        )
        assertEquals(
            RecordingFinishedAction.NONE,
            recordingFinishedAction(
                recordingFinished = false,
                activeRecordingId = 7,
                recordingPlayerVisible = false,
            ),
        )
        assertEquals(
            RecordingFinishedAction.NONE,
            recordingFinishedAction(
                recordingFinished = true,
                activeRecordingId = null,
                recordingPlayerVisible = false,
            ),
        )
    }
}
