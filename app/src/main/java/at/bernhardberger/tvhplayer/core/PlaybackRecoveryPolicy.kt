package at.bernhardberger.tvhplayer.core

enum class PlaybackRetryCommand {
    RECONNECT,
    RETRY_LIVE,
    RESUME_RECORDING,
    NONE,
}

enum class PlaybackRecoverySurface {
    LIVE,
    RECORDING,
}

enum class PlaybackRecoverySecondaryAction {
    CLOSE,
    EXIT_SIMPLE_TV,
}

enum class PlaybackRecoveryInitialAction {
    RETRY,
    CLOSE,
}

data class PlaybackRecoveryUiModel(
    val retryCommand: PlaybackRetryCommand,
    val secondaryAction: PlaybackRecoverySecondaryAction,
    val initialAction: PlaybackRecoveryInitialAction,
)

object PlaybackRecoveryPolicy {
    private val retryDelaysMillis = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)

    fun retryDelayMillis(consecutiveFailures: Int): Long {
        require(consecutiveFailures > 0)
        return retryDelaysMillis[(consecutiveFailures - 1).coerceAtMost(retryDelaysMillis.lastIndex)]
    }

    fun retryCommand(
        connectionAvailable: Boolean,
        liveRetryAvailable: Boolean,
        recordingResumeAvailable: Boolean,
    ): PlaybackRetryCommand = when {
        !connectionAvailable -> PlaybackRetryCommand.RECONNECT
        liveRetryAvailable -> PlaybackRetryCommand.RETRY_LIVE
        recordingResumeAvailable -> PlaybackRetryCommand.RESUME_RECORDING
        else -> PlaybackRetryCommand.NONE
    }
}

fun playbackRecoveryUiModel(
    surface: PlaybackRecoverySurface,
    connectionAvailable: Boolean,
    retryTargetAvailable: Boolean,
    simpleTvActive: Boolean,
): PlaybackRecoveryUiModel {
    val retryCommand = when (surface) {
        PlaybackRecoverySurface.LIVE -> PlaybackRecoveryPolicy.retryCommand(
            connectionAvailable = connectionAvailable,
            liveRetryAvailable = retryTargetAvailable,
            recordingResumeAvailable = false,
        )
        PlaybackRecoverySurface.RECORDING -> if (retryTargetAvailable) {
            PlaybackRecoveryPolicy.retryCommand(
                connectionAvailable = connectionAvailable,
                liveRetryAvailable = false,
                recordingResumeAvailable = true,
            )
        } else {
            PlaybackRetryCommand.NONE
        }
    }
    val secondaryAction = if (
        surface == PlaybackRecoverySurface.LIVE && simpleTvActive
    ) {
        PlaybackRecoverySecondaryAction.EXIT_SIMPLE_TV
    } else {
        PlaybackRecoverySecondaryAction.CLOSE
    }
    return PlaybackRecoveryUiModel(
        retryCommand = retryCommand,
        secondaryAction = secondaryAction,
        initialAction = if (retryCommand == PlaybackRetryCommand.NONE) {
            PlaybackRecoveryInitialAction.CLOSE
        } else {
            PlaybackRecoveryInitialAction.RETRY
        },
    )
}
