package at.bernhardberger.tvhplayer.player

import at.bernhardberger.tvhplayer.core.RecordingPlaybackIntent
import at.bernhardberger.tvhplayer.core.RecordingStartDecision
import at.bernhardberger.tvhplayer.htsp.DvrState
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingStartCoordinatorTest {
    @Test
    fun delayedTimelineWaitsThenResumesBeforeAutoplay() {
        val coordinator = RecordingStartCoordinator(
            generation = 7L,
            intent = RecordingPlaybackIntent.DefaultPolicy,
            state = DvrState.COMPLETED,
            serverPositionSeconds = 600L,
            playCount = 0,
        )

        assertEquals(
            RecordingPreparationDecision.Wait,
            coordinator.decide(7L, durationMs = null, waitExpired = false),
        )
        assertEquals(
            RecordingPreparationDecision.Start(RecordingStartDecision.ResumeAt(600_000L)),
            coordinator.decide(7L, durationMs = 3_600_000L, waitExpired = false),
        )
    }

    @Test
    fun unknownDurationFallsBackDeterministicallyToBeginning() {
        val coordinator = coordinator()

        assertEquals(
            RecordingPreparationDecision.Start(RecordingStartDecision.FromBeginning),
            coordinator.decide(1L, durationMs = null, waitExpired = true),
        )
    }

    @Test
    fun replacementAndPreparationFailureCannotSeekOrAutoplay() {
        assertEquals(
            RecordingPreparationDecision.Cancel,
            coordinator().decide(2L, durationMs = 3_600_000L, waitExpired = false),
        )
        assertEquals(
            RecordingPreparationDecision.Cancel,
            coordinator().decide(
                currentGeneration = 1L,
                durationMs = 3_600_000L,
                waitExpired = false,
                preparationFailed = true,
            ),
        )
    }

    @Test
    fun explicitStartOverNeverUsesServerPosition() {
        val coordinator = RecordingStartCoordinator(
            generation = 1L,
            intent = RecordingPlaybackIntent.FromBeginning,
            state = DvrState.COMPLETED,
            serverPositionSeconds = 600L,
            playCount = 1,
        )

        assertEquals(
            RecordingPreparationDecision.Start(RecordingStartDecision.FromBeginning),
            coordinator.decide(1L, durationMs = 3_600_000L, waitExpired = false),
        )
    }

    @Test
    fun growingAndExplicitStartOverDoNotWaitForDuration() {
        val growing = RecordingStartCoordinator(
            generation = 1L,
            intent = RecordingPlaybackIntent.DefaultPolicy,
            state = DvrState.RECORDING,
            serverPositionSeconds = 600L,
            playCount = 0,
        )

        assertEquals(false, growing.requiresDuration())
        assertEquals(false, RecordingStartCoordinator(
            generation = 1L,
            intent = RecordingPlaybackIntent.FromBeginning,
            state = DvrState.COMPLETED,
            serverPositionSeconds = 600L,
            playCount = 0,
        ).requiresDuration())
        assertEquals(true, coordinator().requiresDuration())
    }

    @Test
    fun unsupportedLegacyCapabilityForcesBeginning() {
        assertEquals(
            RecordingPlaybackIntent.FromBeginning,
            recordingIntentForResumeSupport(
                RecordingPlaybackIntent.DefaultPolicy,
                resumeSupported = false,
            ),
        )
        assertEquals(
            RecordingPlaybackIntent.Resume(600L),
            recordingIntentForResumeSupport(
                RecordingPlaybackIntent.Resume(600L),
                resumeSupported = true,
            ),
        )
    }

    private fun coordinator() = RecordingStartCoordinator(
        generation = 1L,
        intent = RecordingPlaybackIntent.DefaultPolicy,
        state = DvrState.COMPLETED,
        serverPositionSeconds = 600L,
        playCount = 0,
    )
}
