package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionFailureKind
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.data.ConnectionState

sealed interface ConnectionUiState {
    data object NeedsConfiguration : ConnectionUiState
    data object Connecting : ConnectionUiState
    data object SyncingChannels : ConnectionUiState
    data object Ready : ConnectionUiState
    data object Reconnecting : ConnectionUiState
    data object CredentialUnavailable : ConnectionUiState
    data class Error(
        val kind: ConnectionFailureKind,
        val recoveryDisposition: SessionRecoveryDisposition,
    ) : ConnectionUiState
}

enum class ConnectionRecoveryAction {
    NONE,
    RETRY,
    SETTINGS,
}

/** The notice source owns loss detection; initial connection is not a loss. */
fun ConnectionUiState.isDegraded(lossAnnounced: Boolean): Boolean = when (this) {
    ConnectionUiState.Ready, ConnectionUiState.NeedsConfiguration -> false
    ConnectionUiState.CredentialUnavailable, is ConnectionUiState.Error -> true
    else -> lossAnnounced
}

fun ConnectionUiState.primaryRecoveryAction(): ConnectionRecoveryAction = when (this) {
    is ConnectionUiState.Error -> when {
        recoveryDisposition == SessionRecoveryDisposition.EXPLICIT_RETRY ->
            ConnectionRecoveryAction.RETRY
        recoveryDisposition == SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED ||
            recoveryDisposition == SessionRecoveryDisposition.NO_RETRY ->
            ConnectionRecoveryAction.SETTINGS
        else -> ConnectionRecoveryAction.NONE
    }
    ConnectionUiState.NeedsConfiguration,
    ConnectionUiState.CredentialUnavailable -> ConnectionRecoveryAction.SETTINGS
    ConnectionUiState.Connecting,
    ConnectionUiState.SyncingChannels,
    ConnectionUiState.Ready,
    ConnectionUiState.Reconnecting -> ConnectionRecoveryAction.NONE
}

fun ConnectionUiState.forEmptyChannelPresentation(
    channelCatalogCurrent: Boolean,
): ConnectionUiState = if (this == ConnectionUiState.Ready && !channelCatalogCurrent) {
    ConnectionUiState.SyncingChannels
} else {
    this
}

fun shouldPresentEmptyTag(
    channelCatalogCurrent: Boolean,
    connectionState: ConnectionUiState,
    hasChannelsOutsideActiveTag: Boolean,
    activeTagSelected: Boolean,
): Boolean = channelCatalogCurrent &&
    connectionState == ConnectionUiState.Ready &&
    hasChannelsOutsideActiveTag &&
    activeTagSelected

fun SessionState.toConnectionUiState(): ConnectionUiState = when (this) {
    // The caller has an available profile; transport state cannot establish missing settings.
    SessionState.Disconnected -> ConnectionUiState.Connecting
    SessionState.Connecting -> ConnectionUiState.Connecting
    SessionState.Synchronizing -> ConnectionUiState.SyncingChannels
    is SessionState.Ready -> ConnectionUiState.Ready
    is SessionState.Unavailable -> ConnectionUiState.Error(
        kind = reason.toConnectionFailureKind(),
        recoveryDisposition = reason.recoveryDisposition,
    )
}

fun SessionState.toConnectionState(): ConnectionState = when (this) {
    SessionState.Disconnected -> ConnectionState.Disconnected
    SessionState.Connecting, SessionState.Synchronizing -> ConnectionState.Connecting
    is SessionState.Ready -> ConnectionState.Connected
    is SessionState.Unavailable -> ConnectionState.Error(
        kind = reason.toConnectionFailureKind(),
        recoveryDisposition = reason.recoveryDisposition,
    )
}

private fun SessionFailure.toConnectionFailureKind(): ConnectionFailureKind = when (kind) {
    SessionFailureKind.AUTHENTICATION_REJECTED -> ConnectionFailureKind.AUTHENTICATION
    SessionFailureKind.INCOMPATIBLE_SERVER,
    SessionFailureKind.SERVER_UPGRADE_REQUIRED -> ConnectionFailureKind.INCOMPATIBLE_SERVER
    SessionFailureKind.SERVER_UNREACHABLE,
    SessionFailureKind.NETWORK_UNAVAILABLE,
    SessionFailureKind.TRANSPORT_UNAVAILABLE -> ConnectionFailureKind.UNREACHABLE
    SessionFailureKind.SYNCHRONIZATION_FAILED -> when (operationFailure) {
        TvheadendFailure.ACCESS_DENIED -> ConnectionFailureKind.PERMISSION_DENIED
        TvheadendFailure.TIMEOUT -> ConnectionFailureKind.TIMEOUT
        TvheadendFailure.TRANSPORT_UNAVAILABLE -> ConnectionFailureKind.UNREACHABLE
        TvheadendFailure.NOT_SUPPORTED -> ConnectionFailureKind.INCOMPATIBLE_SERVER
        else -> ConnectionFailureKind.OTHER
    }
    else -> ConnectionFailureKind.OTHER
}
