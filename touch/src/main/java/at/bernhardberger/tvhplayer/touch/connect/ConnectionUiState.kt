package at.bernhardberger.tvhplayer.touch.connect

import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer

internal const val USUAL_PORT = 9982

internal data class ActiveConnectionUiState(val name: String, val address: String, val port: Int, val username: String)

internal data class ServerSearchUiState(
    val servers: List<DiscoveredTvheadendServer>,
    val searching: Boolean = false,
)

/** What the connection form shows; validation and saving belong to the shared setup state. */
internal data class ConnectionFields(
    val address: String = "",
    val port: String = USUAL_PORT.toString(),
    val username: String = "",
    val password: String = "",
    val name: String = "",
) {
    override fun toString(): String = "ConnectionFields(<redacted>)"
}

internal sealed interface ConnectionPhase {
    data object Editing : ConnectionPhase
    data class Connecting(val address: String) : ConnectionPhase
    data object Refused : ConnectionPhase
}

/** The errors are the shared setup state's, shown once an attempt found them. */
internal data class ConnectionUiState(
    val fields: ConnectionFields,
    val phase: ConnectionPhase = ConnectionPhase.Editing,
    val addressError: Boolean = false,
    val portError: Boolean = false,
    val signInError: Boolean = false,
)
