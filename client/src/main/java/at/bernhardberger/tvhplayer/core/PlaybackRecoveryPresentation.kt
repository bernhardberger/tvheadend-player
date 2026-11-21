package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvhplayer.data.ConnectionState

enum class PlaybackRetryCommand {
    RECONNECT,
    RETRY_LIVE,
    RESUME_RECORDING,
    NONE,
}

/** What the recording player's Retry does; [retryTargetAvailable] is a recording it can resume. */
fun recordingRetryCommand(
    connectionState: ConnectionState,
    retryTargetAvailable: Boolean,
): PlaybackRetryCommand {
    val connectionAvailable = connectionState is ConnectionState.Connected
    val connectionRetryAvailable = connectionState is ConnectionState.Error &&
        connectionState.recoveryDisposition == SessionRecoveryDisposition.EXPLICIT_RETRY
    return when {
        !retryTargetAvailable -> PlaybackRetryCommand.NONE
        !connectionAvailable && connectionRetryAvailable -> PlaybackRetryCommand.RECONNECT
        !connectionAvailable -> PlaybackRetryCommand.NONE
        else -> PlaybackRetryCommand.RESUME_RECORDING
    }
}
