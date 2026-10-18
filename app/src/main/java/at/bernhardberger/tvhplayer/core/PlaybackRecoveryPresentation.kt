package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvhplayer.data.ConnectionState

enum class PlaybackRetryCommand {
    RECONNECT,
    RETRY_LIVE,
    RESUME_RECORDING,
    NONE,
}

enum class PlaybackRecoveryInitialAction {
    RETRY,
    CLOSE,
}

data class PlaybackRecoveryUiModel(
    val retryCommand: PlaybackRetryCommand,
    val initialAction: PlaybackRecoveryInitialAction,
)

/** The recording player's recovery; [retryTargetAvailable] is a recording it can resume. */
fun playbackRecoveryUiModel(
    connectionState: ConnectionState,
    retryTargetAvailable: Boolean,
): PlaybackRecoveryUiModel {
    val connectionAvailable = connectionState is ConnectionState.Connected
    val connectionRetryAvailable = connectionState is ConnectionState.Error &&
        connectionState.recoveryDisposition == SessionRecoveryDisposition.EXPLICIT_RETRY
    val retryCommand = when {
        !retryTargetAvailable -> PlaybackRetryCommand.NONE
        !connectionAvailable && connectionRetryAvailable -> PlaybackRetryCommand.RECONNECT
        !connectionAvailable -> PlaybackRetryCommand.NONE
        else -> PlaybackRetryCommand.RESUME_RECORDING
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
