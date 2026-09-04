package at.bernhardberger.tvhplayer.data

import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition

enum class ConnectionFailureKind {
    AUTHENTICATION,
    DNS,
    UNREACHABLE,
    TIMEOUT,
    INCOMPATIBLE_SERVER,
    PERMISSION_DENIED,
    ZERO_CHANNELS,
    OTHER,
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

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(
        val kind: ConnectionFailureKind,
        val recoveryDisposition: SessionRecoveryDisposition,
    ) : ConnectionState
}
