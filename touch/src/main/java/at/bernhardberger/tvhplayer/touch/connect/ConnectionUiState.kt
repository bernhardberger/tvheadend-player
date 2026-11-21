package at.bernhardberger.tvhplayer.touch.connect

import androidx.annotation.StringRes
import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvhplayer.presentation.R as SharedR

internal const val USUAL_PORT = 9982

internal data class ActiveConnectionUiState(
    val name: String,
    val address: String,
    val port: Int,
    val username: String,
    val status: ConnectionStatus = ConnectionStatus.Connected,
)

/** The card's state line: what the connection in use is doing, in the shared words. */
internal data class ConnectionStatus(val kind: Kind, @param:StringRes val text: Int) {
    enum class Kind { Connected, Working, Failed }

    companion object {
        val Connected = ConnectionStatus(Kind.Connected, SharedR.string.connection_overview_connected)
    }
}

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

    /** Something answered at the address, but not Tvheadend: the address pair is in error. */
    data object NotTvheadend : ConnectionPhase
}

/**
 * The errors are the shared setup state's, shown once an attempt found them. [passwordSaved] is the
 * shared form's saved password, kept while Password stays empty.
 */
internal data class ConnectionUiState(
    val fields: ConnectionFields,
    val phase: ConnectionPhase = ConnectionPhase.Editing,
    val addressError: Boolean = false,
    val portError: Boolean = false,
    val signInError: Boolean = false,
    val passwordSaved: Boolean = false,
    /** A save is being written: nothing may close or discard the form until it ends. */
    val saving: Boolean = false,
    /** The connection passed but couldn't be saved; Connect or Save writes it again. */
    val saveFailed: Boolean = false,
)
