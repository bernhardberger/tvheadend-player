package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.core.ClientState
import at.bernhardberger.tvheadend.core.ConnectionFailureKind
import at.bernhardberger.tvheadend.core.SubscriptionFailureKind

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

fun ClientState.toConnectionUiState(): ConnectionUiState = when (this) {
    ClientState.Connecting -> ConnectionUiState.Connecting
    ClientState.SyncingChannels -> ConnectionUiState.SyncingChannels
    ClientState.Ready -> ConnectionUiState.Ready
    ClientState.Reconnecting -> ConnectionUiState.Reconnecting
    is ClientState.Error -> ConnectionUiState.Error(kind)
    is ClientState.SubscriptionError -> ConnectionUiState.SubscriptionError(kind)
}
