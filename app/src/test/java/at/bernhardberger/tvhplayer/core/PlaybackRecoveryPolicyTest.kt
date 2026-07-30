package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryPolicyTest {
    @Test
    fun repeatedFailures_backOffToBoundedDelay() {
        assertEquals(1_000L, PlaybackRecoveryPolicy.retryDelayMillis(1))
        assertEquals(2_000L, PlaybackRecoveryPolicy.retryDelayMillis(2))
        assertEquals(5_000L, PlaybackRecoveryPolicy.retryDelayMillis(3))
        assertEquals(10_000L, PlaybackRecoveryPolicy.retryDelayMillis(4))
        assertEquals(30_000L, PlaybackRecoveryPolicy.retryDelayMillis(5))
        assertEquals(30_000L, PlaybackRecoveryPolicy.retryDelayMillis(50))
    }

    @Test
    fun connectionRecoveryOwnsTheSingleRetryCommandBeforeLivePlayback() {
        assertEquals(
            PlaybackRetryCommand.RECONNECT,
            PlaybackRecoveryPolicy.retryCommand(
                connectionAvailable = false,
                liveRetryAvailable = true,
                recordingResumeAvailable = false,
            ),
        )
        assertEquals(
            PlaybackRetryCommand.RETRY_LIVE,
            PlaybackRecoveryPolicy.retryCommand(
                connectionAvailable = true,
                liveRetryAvailable = true,
                recordingResumeAvailable = false,
            ),
        )
    }

    @Test
    fun recordingResumeIsUsedOnlyWhenARealReadFailureTargetExists() {
        assertEquals(
            PlaybackRetryCommand.RESUME_RECORDING,
            PlaybackRecoveryPolicy.retryCommand(
                connectionAvailable = true,
                liveRetryAvailable = false,
                recordingResumeAvailable = true,
            ),
        )
        assertEquals(
            PlaybackRetryCommand.NONE,
            PlaybackRecoveryPolicy.retryCommand(
                connectionAvailable = true,
                liveRetryAvailable = false,
                recordingResumeAvailable = false,
            ),
        )
    }

    @Test
    fun liveRecoveryShowsOneAuthoritativeRetryAndTheCorrectSecondaryExit() {
        assertEquals(
            PlaybackRecoveryUiModel(
                retryCommand = PlaybackRetryCommand.RECONNECT,
                secondaryAction = PlaybackRecoverySecondaryAction.CLOSE,
                initialAction = PlaybackRecoveryInitialAction.RETRY,
            ),
            playbackRecoveryUiModel(
                surface = PlaybackRecoverySurface.LIVE,
                connectionAvailable = false,
                retryTargetAvailable = true,
                simpleTvActive = false,
            ),
        )
        assertEquals(
            PlaybackRecoveryUiModel(
                retryCommand = PlaybackRetryCommand.RETRY_LIVE,
                secondaryAction = PlaybackRecoverySecondaryAction.EXIT_SIMPLE_TV,
                initialAction = PlaybackRecoveryInitialAction.RETRY,
            ),
            playbackRecoveryUiModel(
                surface = PlaybackRecoverySurface.LIVE,
                connectionAvailable = true,
                retryTargetAvailable = true,
                simpleTvActive = true,
            ),
        )
    }

    @Test
    fun recordingReadFailureRetriesButUnavailableRecordingIsCloseOnly() {
        assertEquals(
            PlaybackRecoveryUiModel(
                retryCommand = PlaybackRetryCommand.RESUME_RECORDING,
                secondaryAction = PlaybackRecoverySecondaryAction.CLOSE,
                initialAction = PlaybackRecoveryInitialAction.RETRY,
            ),
            playbackRecoveryUiModel(
                surface = PlaybackRecoverySurface.RECORDING,
                connectionAvailable = true,
                retryTargetAvailable = true,
                simpleTvActive = false,
            ),
        )
        assertEquals(
            PlaybackRecoveryUiModel(
                retryCommand = PlaybackRetryCommand.NONE,
                secondaryAction = PlaybackRecoverySecondaryAction.CLOSE,
                initialAction = PlaybackRecoveryInitialAction.CLOSE,
            ),
            playbackRecoveryUiModel(
                surface = PlaybackRecoverySurface.RECORDING,
                connectionAvailable = false,
                retryTargetAvailable = false,
                simpleTvActive = false,
            ),
        )
        assertEquals(
            PlaybackRetryCommand.RECONNECT,
            playbackRecoveryUiModel(
                surface = PlaybackRecoverySurface.RECORDING,
                connectionAvailable = false,
                retryTargetAvailable = true,
                simpleTvActive = false,
            ).retryCommand,
        )
    }
}
