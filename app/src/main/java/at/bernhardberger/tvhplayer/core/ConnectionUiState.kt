package at.bernhardberger.tvhplayer.core

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
    data class Error(val kind: ConnectionFailureKind) : ConnectionUiState
    data class SubscriptionError(val kind: SubscriptionFailureKind) : ConnectionUiState
}

fun SessionState.toConnectionUiState(): ConnectionUiState = when (this) {
    SessionState.Disconnected -> ConnectionUiState.NeedsConfiguration
    SessionState.Connecting -> ConnectionUiState.Connecting
    SessionState.Synchronizing -> ConnectionUiState.SyncingChannels
    is SessionState.Ready -> ConnectionUiState.Ready
    is SessionState.Unavailable -> ConnectionUiState.Error(
        when (reason) {
            at.bernhardberger.tvheadend.sdk.core.SessionFailure.AuthenticationRejected -> ConnectionFailureKind.AUTHENTICATION
            at.bernhardberger.tvheadend.sdk.core.SessionFailure.PermissionDenied -> ConnectionFailureKind.PERMISSION_DENIED
            at.bernhardberger.tvheadend.sdk.core.SessionFailure.IncompatibleServer -> ConnectionFailureKind.INCOMPATIBLE_SERVER
            at.bernhardberger.tvheadend.sdk.core.SessionFailure.NoChannels -> ConnectionFailureKind.ZERO_CHANNELS
            at.bernhardberger.tvheadend.sdk.core.SessionFailure.ServerUnreachable,
            at.bernhardberger.tvheadend.sdk.core.SessionFailure.NetworkUnavailable,
            at.bernhardberger.tvheadend.sdk.core.SessionFailure.TransportUnavailable -> ConnectionFailureKind.UNREACHABLE
            else -> ConnectionFailureKind.OTHER
        },
    )
}

fun SessionState.toConnectionState(): ConnectionState = when (this) {
    SessionState.Disconnected -> ConnectionState.Disconnected
    SessionState.Connecting, SessionState.Synchronizing -> ConnectionState.Connecting
    is SessionState.Ready -> ConnectionState.Connected
    is SessionState.Unavailable -> ConnectionState.Error(toConnectionUiState().let {
        (it as ConnectionUiState.Error).kind
    })
}
