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

fun playbackRecoveryUiModel(
    surface: PlaybackRecoverySurface,
    connectionAvailable: Boolean,
    retryTargetAvailable: Boolean,
    secondaryAction: PlaybackRecoverySecondaryAction,
): PlaybackRecoveryUiModel {
    val retryCommand = when (surface) {
        PlaybackRecoverySurface.LIVE -> playbackRetryCommand(
            connectionAvailable = connectionAvailable,
            liveRetryAvailable = retryTargetAvailable,
            recordingResumeAvailable = false,
        )
        PlaybackRecoverySurface.RECORDING -> if (retryTargetAvailable) {
            playbackRetryCommand(
                connectionAvailable = connectionAvailable,
                liveRetryAvailable = false,
                recordingResumeAvailable = true,
            )
        } else {
            PlaybackRetryCommand.NONE
        }
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

private fun playbackRetryCommand(
    connectionAvailable: Boolean,
    liveRetryAvailable: Boolean,
    recordingResumeAvailable: Boolean,
): PlaybackRetryCommand = when {
    !connectionAvailable -> PlaybackRetryCommand.RECONNECT
    liveRetryAvailable -> PlaybackRetryCommand.RETRY_LIVE
    recordingResumeAvailable -> PlaybackRetryCommand.RESUME_RECORDING
    else -> PlaybackRetryCommand.NONE
}
