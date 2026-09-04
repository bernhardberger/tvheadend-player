package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionOperationFailure
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.data.SubscriptionFailureKind

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
    data class SubscriptionError(val kind: SubscriptionFailureKind) : ConnectionUiState
}

enum class ConnectionRecoveryAction {
    NONE,
    RETRY,
    SETTINGS,
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
    is ConnectionUiState.SubscriptionError -> ConnectionRecoveryAction.RETRY
    ConnectionUiState.NeedsConfiguration,
    ConnectionUiState.CredentialUnavailable -> ConnectionRecoveryAction.SETTINGS
    ConnectionUiState.Connecting,
    ConnectionUiState.SyncingChannels,
    ConnectionUiState.Ready,
    ConnectionUiState.Reconnecting -> ConnectionRecoveryAction.NONE
}

internal fun ConnectionUiState.forEmptyChannelPresentation(
    channelCatalogCurrent: Boolean,
): ConnectionUiState = if (this == ConnectionUiState.Ready && !channelCatalogCurrent) {
    ConnectionUiState.SyncingChannels
} else {
    this
}

internal fun shouldPresentEmptyTag(
    channelCatalogCurrent: Boolean,
    connectionState: ConnectionUiState,
    hasChannelsOutsideActiveTag: Boolean,
    activeTagSelected: Boolean,
): Boolean = channelCatalogCurrent &&
    connectionState == ConnectionUiState.Ready &&
    hasChannelsOutsideActiveTag &&
    activeTagSelected

fun SessionState.toConnectionUiState(): ConnectionUiState = when (this) {
    SessionState.Disconnected -> ConnectionUiState.NeedsConfiguration
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

private fun SessionFailure.toConnectionFailureKind(): ConnectionFailureKind = when (this) {
    SessionFailure.AuthenticationRejected -> ConnectionFailureKind.AUTHENTICATION
    SessionFailure.PermissionDenied -> ConnectionFailureKind.PERMISSION_DENIED
    SessionFailure.IncompatibleServer -> ConnectionFailureKind.INCOMPATIBLE_SERVER
    SessionFailure.NoChannels -> ConnectionFailureKind.ZERO_CHANNELS
    SessionFailure.ServerUnreachable,
    SessionFailure.NetworkUnavailable,
    SessionFailure.TransportUnavailable -> ConnectionFailureKind.UNREACHABLE
    is SessionFailure.SynchronizationFailed -> when {
        failure == SessionOperationFailure.ACCESS_DENIED ->
            ConnectionFailureKind.PERMISSION_DENIED
        failure == SessionOperationFailure.TIMEOUT -> ConnectionFailureKind.TIMEOUT
        failure == SessionOperationFailure.TRANSPORT_UNAVAILABLE ->
            ConnectionFailureKind.UNREACHABLE
        failure == SessionOperationFailure.NOT_SUPPORTED ->
            ConnectionFailureKind.INCOMPATIBLE_SERVER
        else -> ConnectionFailureKind.OTHER
    }
    else -> ConnectionFailureKind.OTHER
}
