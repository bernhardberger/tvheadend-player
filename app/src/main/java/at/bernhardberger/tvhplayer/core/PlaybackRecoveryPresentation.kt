package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvhplayer.data.ConnectionState

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

enum class PlaybackRecoveryInitialAction {
    RETRY,
    CLOSE,
}

data class PlaybackRecoveryUiModel(
    val retryCommand: PlaybackRetryCommand,
    val initialAction: PlaybackRecoveryInitialAction,
)

fun playbackRecoveryUiModel(
    surface: PlaybackRecoverySurface,
    connectionState: ConnectionState,
    retryTargetAvailable: Boolean,
): PlaybackRecoveryUiModel {
    val connectionAvailable = connectionState is ConnectionState.Connected
    val connectionRetryAvailable = connectionState is ConnectionState.Error &&
        connectionState.recoveryDisposition == SessionRecoveryDisposition.EXPLICIT_RETRY
    val retryCommand = when (surface) {
        PlaybackRecoverySurface.LIVE -> playbackRetryCommand(
            connectionAvailable = connectionAvailable,
            connectionRetryAvailable = connectionRetryAvailable,
            liveRetryAvailable = retryTargetAvailable,
            recordingResumeAvailable = false,
        )
        PlaybackRecoverySurface.RECORDING -> if (retryTargetAvailable) {
            playbackRetryCommand(
                connectionAvailable = connectionAvailable,
                connectionRetryAvailable = connectionRetryAvailable,
                liveRetryAvailable = false,
                recordingResumeAvailable = true,
            )
        } else {
            PlaybackRetryCommand.NONE
        }
    }
    return PlaybackRecoveryUiModel(
        retryCommand = retryCommand,
        initialAction = if (retryCommand == PlaybackRetryCommand.NONE) {
            PlaybackRecoveryInitialAction.CLOSE
        } else {
            PlaybackRecoveryInitialAction.RETRY
        },
    )
}

private fun playbackRetryCommand(
    connectionAvailable: Boolean,
    connectionRetryAvailable: Boolean,
    liveRetryAvailable: Boolean,
    recordingResumeAvailable: Boolean,
): PlaybackRetryCommand = when {
    !connectionAvailable && connectionRetryAvailable -> PlaybackRetryCommand.RECONNECT
    !connectionAvailable -> PlaybackRetryCommand.NONE
    liveRetryAvailable -> PlaybackRetryCommand.RETRY_LIVE
    recordingResumeAvailable -> PlaybackRetryCommand.RESUME_RECORDING
    else -> PlaybackRetryCommand.NONE
}
