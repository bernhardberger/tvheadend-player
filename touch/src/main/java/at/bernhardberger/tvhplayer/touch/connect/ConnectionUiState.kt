package at.bernhardberger.tvhplayer.touch.connect

internal const val USUAL_PORT = 9982

internal data class FoundServer(val name: String, val address: String, val port: Int)

internal data class ActiveConnectionUiState(val name: String, val address: String, val port: Int, val username: String)

internal data class ServerSearchUiState(
    val servers: List<FoundServer>,
    val searching: Boolean = false,
)

internal data class ConnectionFields(
    val address: String = "",
    val port: String = USUAL_PORT.toString(),
    val username: String = "",
    val password: String = "",
    val name: String = "",
) {
    val addressMissing: Boolean get() = address.isBlank()
    val portInvalid: Boolean get() = port.toIntOrNull() !in 1..65535
    val signInIncomplete: Boolean get() = username.isBlank() != password.isBlank()
    val canSubmit: Boolean get() = !addressMissing && !portInvalid && !signInIncomplete

    override fun toString(): String = "ConnectionFields(<redacted>)"
}

internal sealed interface ConnectionPhase {
    data object Editing : ConnectionPhase
    data class Connecting(val address: String) : ConnectionPhase
    data object Refused : ConnectionPhase
}

internal data class ConnectionUiState(
    val fields: ConnectionFields,
    val phase: ConnectionPhase = ConnectionPhase.Editing,
)
