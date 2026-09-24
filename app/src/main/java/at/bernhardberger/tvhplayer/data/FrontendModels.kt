package at.bernhardberger.tvhplayer.data

import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition

enum class ConnectionFailureKind {
    AUTHENTICATION,
    UNREACHABLE,
    TIMEOUT,
    INCOMPATIBLE_SERVER,
    PERMISSION_DENIED,
    ZERO_CHANNELS,
    OTHER,
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
