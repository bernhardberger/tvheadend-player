package at.bernhardberger.tvhplayer.core

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

enum class SubscriptionFailureKind {
    INVALID_TARGET,
    NO_FREE_ADAPTER,
    MUX_NOT_ENABLED,
    TUNING_FAILED,
    BAD_SIGNAL,
    SCRAMBLED,
    OVERRIDDEN,
    NO_INPUT,
}

fun connectionAttemptState(hasPublishedChannels: Boolean): ConnectionUiState =
    if (hasPublishedChannels) ConnectionUiState.Reconnecting else ConnectionUiState.Connecting
