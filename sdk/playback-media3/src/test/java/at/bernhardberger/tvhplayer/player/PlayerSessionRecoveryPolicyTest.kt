package at.bernhardberger.tvhplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSessionRecoveryPolicyTest {
    @Test
    fun manualLiveRetryAcceptsStartingOrRecoveringButNotStablePlayback() {
        assertTrue(
            liveManualRetryEligible(
                state = PlaybackSessionState.Starting,
                connectionAvailable = true,
            )
        )
        assertTrue(
            liveManualRetryEligible(
                state = PlaybackSessionState.Recovering(1_000L),
                connectionAvailable = true,
            )
        )
        assertFalse(
            liveManualRetryEligible(
                state = PlaybackSessionState.Playing,
                connectionAvailable = true,
            )
        )
        assertFalse(
            liveManualRetryEligible(
                state = PlaybackSessionState.Starting,
                connectionAvailable = false,
            )
        )
    }

    @Test
    fun manualRetryRequestGateCoalescesUntilTheOwnerCompletes() {
        val gate = ManualPlaybackRetryGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun errorBeforeRecordingPlaybackBeginsIsCloseOnly() {
        val decision = recordingPlayerErrorDecision(
            playbackStarted = false,
            positionMs = 42_000L,
        )

        assertEquals(PlaybackFailureReason.RECORDING_UNAVAILABLE, decision.reason)
        assertFalse(decision.retryAvailable)
        assertEquals(null, decision.resumePositionSeconds)
    }

    @Test
    fun errorAfterRecordingPlaybackBeginsCanResumeAtLocalPosition() {
        val decision = recordingPlayerErrorDecision(
            playbackStarted = true,
            positionMs = 321_999L,
        )

        assertEquals(PlaybackFailureReason.RECORDING_READ_FAILED, decision.reason)
        assertTrue(decision.retryAvailable)
        assertEquals(321L, decision.resumePositionSeconds)
    }

    @Test
    fun transportLossDuringResumeKeepsTheSameRetryTarget() {
        val decision = recordingStartFailureDecision(
            resumePositionSeconds = 321L,
            connectionAvailable = false,
        )

        assertEquals(PlaybackFailureReason.RECORDING_READ_FAILED, decision.reason)
        assertEquals(321L, decision.resumePositionSeconds)
    }

    @Test
    fun genuineResumeOpenFailureRemainsCloseOnly() {
        val decision = recordingStartFailureDecision(
            resumePositionSeconds = 321L,
            connectionAvailable = true,
        )

        assertEquals(PlaybackFailureReason.RECORDING_UNAVAILABLE, decision.reason)
        assertFalse(decision.retryAvailable)
    }

    @Test
    fun preReadyPlayerErrorDuringDisconnectedResumeKeepsRetryTarget() {
        val decision = recordingPlayerErrorDecision(
            playbackStarted = false,
            positionMs = 0L,
            existingResumePositionSeconds = 321L,
            connectionAvailable = false,
        )

        assertEquals(PlaybackFailureReason.RECORDING_READ_FAILED, decision.reason)
        assertEquals(321L, decision.resumePositionSeconds)
    }

    @Test
    fun staleHandleFailureKeepsResumeAfterReplacementIsAlreadyConnected() {
        val decision = recordingPlayerErrorDecision(
            playbackStarted = false,
            positionMs = 0L,
            existingResumePositionSeconds = 321L,
            connectionAvailable = true,
            connectionAttemptChanged = true,
        )

        assertEquals(PlaybackFailureReason.RECORDING_READ_FAILED, decision.reason)
        assertEquals(321L, decision.resumePositionSeconds)
    }
}
