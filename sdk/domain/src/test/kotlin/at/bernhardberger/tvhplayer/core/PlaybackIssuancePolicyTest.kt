package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackIssuancePolicyTest {
    @Test
    fun equivalentLiveStartJoinsButLaterTuneInvalidatesItsCommit() {
        val first = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Live(serviceId = 10),
        )
        val duplicate = submitPlaybackIntent(
            first.state,
            PlaybackIntent.Live(serviceId = 10),
        )
        val laterTune = submitPlaybackIntent(
            duplicate.state,
            PlaybackIntent.Live(serviceId = 11),
        )

        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 1L), first.decision)
        assertEquals(PlaybackSubmissionDecision.Join(epoch = 1L), duplicate.decision)
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), laterTune.decision)
        assertFalse(playbackIntentMayCommit(laterTune.state, epoch = 1L))
        assertTrue(playbackIntentMayCommit(laterTune.state, epoch = 2L))
    }

    @Test
    fun liveRetryAdvancesEpochCoalescesDuplicatesAndRemainsSubordinateToTune() {
        val live = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Live(serviceId = 20),
        )
        val retry = submitPlaybackIntent(
            live.state,
            PlaybackIntent.RetryLive(serviceId = 20, expectedEpoch = 1L),
        )
        val duplicateRetry = submitPlaybackIntent(
            retry.state,
            PlaybackIntent.RetryLive(serviceId = 20, expectedEpoch = 1L),
        )
        val newerTune = submitPlaybackIntent(
            duplicateRetry.state,
            PlaybackIntent.Live(serviceId = 21),
        )

        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), retry.decision)
        assertEquals(PlaybackSubmissionDecision.Join(epoch = 2L), duplicateRetry.decision)
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 3L), newerTune.decision)
        assertFalse(playbackIntentMayCommit(newerTune.state, epoch = 2L))
    }

    @Test
    fun completedLiveRetryAllowsTheNextRecoveryAttemptForItsCurrentEpoch() {
        val live = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Live(serviceId = 22),
        )
        val retry = submitPlaybackIntent(
            live.state,
            PlaybackIntent.RetryLive(serviceId = 22, expectedEpoch = 1L),
        )
        val completed = completePlaybackIssuance(retry.state, epoch = 2L)
        val nextRetry = submitPlaybackIntent(
            completed,
            PlaybackIntent.RetryLive(serviceId = 22, expectedEpoch = 2L),
        )

        assertEquals(PlaybackIntent.Live(serviceId = 22), completed.intent)
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 3L), nextRetry.decision)
    }

    @Test
    fun delayedRetryFromOlderEpochCannotJoinCurrentRetry() {
        val live = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Live(serviceId = 23),
        )
        val firstRetry = submitPlaybackIntent(
            live.state,
            PlaybackIntent.RetryLive(serviceId = 23, expectedEpoch = 1L),
        )
        val recovered = completePlaybackIssuance(firstRetry.state, epoch = 2L)
        val currentRetry = submitPlaybackIntent(
            recovered,
            PlaybackIntent.RetryLive(serviceId = 23, expectedEpoch = 2L),
        )
        val staleRetry = submitPlaybackIntent(
            currentRetry.state,
            PlaybackIntent.RetryLive(serviceId = 23, expectedEpoch = 1L),
        )

        assertEquals(
            PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.STALE_RETRY),
            staleRetry.decision,
        )
        assertEquals(currentRetry.state, staleRetry.state)
    }

    @Test
    fun staleLiveRetryCannotReplaceANewerIntent() {
        val first = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Live(serviceId = 30),
        )
        val second = submitPlaybackIntent(
            first.state,
            PlaybackIntent.Live(serviceId = 31),
        )
        val staleRetry = submitPlaybackIntent(
            second.state,
            PlaybackIntent.RetryLive(serviceId = 30, expectedEpoch = 1L),
        )

        assertEquals(
            PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.STALE_RETRY),
            staleRetry.decision,
        )
        assertEquals(second.state, staleRetry.state)
    }

    @Test
    fun equivalentRecordingJoinsWhileExplicitRestartAndLiveRemainLatestIntent() {
        val recording = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Recording(
                entryId = 40,
                path = "/recording/40.ts",
                startIntent = RecordingPlaybackIntent.DefaultPolicy,
            ),
        )
        val duplicate = submitPlaybackIntent(
            recording.state,
            PlaybackIntent.Recording(
                entryId = 40,
                path = "/recording/40.ts",
                startIntent = RecordingPlaybackIntent.DefaultPolicy,
            ),
        )
        val restart = submitPlaybackIntent(
            duplicate.state,
            PlaybackIntent.Recording(
                entryId = 40,
                path = "/recording/40.ts",
                startIntent = RecordingPlaybackIntent.FromBeginning,
            ),
        )
        val live = submitPlaybackIntent(
            restart.state,
            PlaybackIntent.Live(serviceId = 41),
        )

        assertEquals(PlaybackSubmissionDecision.Join(epoch = 1L), duplicate.decision)
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), restart.decision)
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 3L), live.decision)
    }

    @Test
    fun recordingReadRetryReopensSamePathAtLocalPositionAndCoalesces() {
        val recording = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Recording(
                entryId = 50,
                path = "/recording/50.ts",
                startIntent = RecordingPlaybackIntent.DefaultPolicy,
            ),
        )
        val retry = submitPlaybackIntent(
            recording.state,
            PlaybackIntent.RetryRecording(
                entryId = 50,
                path = "/recording/50.ts",
                positionSeconds = 321L,
                expectedEpoch = 1L,
            ),
        )
        val duplicate = submitPlaybackIntent(
            retry.state,
            PlaybackIntent.RetryRecording(
                entryId = 50,
                path = "/recording/50.ts",
                positionSeconds = 322L,
                expectedEpoch = 1L,
            ),
        )

        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), retry.decision)
        assertEquals(PlaybackSubmissionDecision.Join(epoch = 2L), duplicate.decision)
        assertEquals(
            PlaybackIntent.RetryRecording(
                entryId = 50,
                path = "/recording/50.ts",
                positionSeconds = 321L,
                expectedEpoch = 1L,
            ),
            retry.state.intent,
        )
    }

    @Test
    fun completedRecordingRetryKeepsResumeIdentityAndAllowsAnotherReadRetry() {
        val recording = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Recording(
                entryId = 51,
                path = "/recording/51.ts",
                startIntent = RecordingPlaybackIntent.DefaultPolicy,
            ),
        )
        val retry = submitPlaybackIntent(
            recording.state,
            PlaybackIntent.RetryRecording(
                entryId = 51,
                path = "/recording/51.ts",
                positionSeconds = 120L,
                expectedEpoch = 1L,
            ),
        )
        val completed = completePlaybackIssuance(retry.state, epoch = 2L)
        val nextRetry = submitPlaybackIntent(
            completed,
            PlaybackIntent.RetryRecording(
                entryId = 51,
                path = "/recording/51.ts",
                positionSeconds = 180L,
                expectedEpoch = 2L,
            ),
        )

        assertEquals(
            PlaybackIntent.Recording(
                entryId = 51,
                path = "/recording/51.ts",
                startIntent = RecordingPlaybackIntent.Resume(120L),
            ),
            completed.intent,
        )
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 3L), nextRetry.decision)
    }

    @Test
    fun delayedRecordingRetryCannotJoinCurrentResumeAttempt() {
        val recording = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Recording(
                entryId = 52,
                path = "/recording/52.ts",
                startIntent = RecordingPlaybackIntent.DefaultPolicy,
            ),
        )
        val firstRetry = submitPlaybackIntent(
            recording.state,
            PlaybackIntent.RetryRecording(
                entryId = 52,
                path = "/recording/52.ts",
                positionSeconds = 100L,
                expectedEpoch = 1L,
            ),
        )
        val resumed = completePlaybackIssuance(firstRetry.state, epoch = 2L)
        val currentRetry = submitPlaybackIntent(
            resumed,
            PlaybackIntent.RetryRecording(
                entryId = 52,
                path = "/recording/52.ts",
                positionSeconds = 200L,
                expectedEpoch = 2L,
            ),
        )
        val staleRetry = submitPlaybackIntent(
            currentRetry.state,
            PlaybackIntent.RetryRecording(
                entryId = 52,
                path = "/recording/52.ts",
                positionSeconds = 100L,
                expectedEpoch = 1L,
            ),
        )

        assertEquals(
            PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.STALE_RETRY),
            staleRetry.decision,
        )
    }

    @Test
    fun stopIsABarrierAndNewPlaybackWaitsForACompletedStop() {
        val live = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Live(serviceId = 60),
        )
        val stop = submitPlaybackIntent(live.state, PlaybackIntent.Stop)
        val duplicateStop = submitPlaybackIntent(stop.state, PlaybackIntent.Stop)
        val blockedPlay = submitPlaybackIntent(
            duplicateStop.state,
            PlaybackIntent.Live(serviceId = 61),
        )
        val stopped = completePlaybackTeardown(stop.state, epoch = 2L)
        val nextPlay = submitPlaybackIntent(stopped, PlaybackIntent.Live(serviceId = 61))

        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), stop.decision)
        assertEquals(PlaybackSubmissionDecision.Join(epoch = 2L), duplicateStop.decision)
        assertEquals(
            PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.TEARDOWN_IN_PROGRESS),
            blockedPlay.decision,
        )
        assertFalse(playbackIntentMayCommit(stop.state, epoch = 1L))
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 3L), nextPlay.decision)
    }

    @Test
    fun releaseIsTerminalAndCannotBeSuperseded() {
        val live = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Live(serviceId = 70),
        )
        val release = submitPlaybackIntent(live.state, PlaybackIntent.Release)
        val released = completePlaybackTeardown(release.state, epoch = 2L)
        val rejected = submitPlaybackIntent(
            released,
            PlaybackIntent.Live(serviceId = 71),
        )

        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), release.decision)
        assertTrue(released.released)
        assertEquals(
            PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.RELEASED),
            rejected.decision,
        )
        assertFalse(playbackIntentMayCommit(released, epoch = 2L))
    }

    @Test
    fun failedReleaseStaysTerminalButAllowsAReleaseOnlyRetry() {
        val release = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Release,
        )
        val failed = failPlaybackTeardown(release.state, epoch = 1L)
        val blockedPlayback = submitPlaybackIntent(
            failed,
            PlaybackIntent.Live(serviceId = 71),
        )
        val retry = submitPlaybackIntent(failed, PlaybackIntent.Release)
        val released = completePlaybackTeardown(retry.state, epoch = 2L)

        assertFalse(failed.released)
        assertEquals(PlaybackTeardownBarrier.RELEASE, failed.barrier)
        assertEquals(
            PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.TEARDOWN_IN_PROGRESS),
            blockedPlayback.decision,
        )
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), retry.decision)
        assertTrue(released.released)
    }

    @Test
    fun failedStopKeepsPlaybackFencedUntilStopRetriesOrReleaseEscalates() {
        val stop = submitPlaybackIntent(
            PlaybackIssuanceState(),
            PlaybackIntent.Stop,
        )
        val failed = failPlaybackTeardown(stop.state, epoch = 1L)
        val blockedPlayback = submitPlaybackIntent(
            failed,
            PlaybackIntent.Live(serviceId = 72),
        )
        val retry = submitPlaybackIntent(failed, PlaybackIntent.Stop)
        val failedAgain = failPlaybackTeardown(retry.state, epoch = 2L)
        val release = submitPlaybackIntent(failedAgain, PlaybackIntent.Release)

        assertEquals(PlaybackTeardownBarrier.STOP, failed.barrier)
        assertEquals(
            PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.TEARDOWN_IN_PROGRESS),
            blockedPlayback.decision,
        )
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), retry.decision)
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 3L), release.decision)
        assertEquals(PlaybackTeardownBarrier.RELEASE, release.state.barrier)
    }
}
