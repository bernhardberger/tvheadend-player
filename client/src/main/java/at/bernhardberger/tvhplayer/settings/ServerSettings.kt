package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import at.bernhardberger.tvheadend.sdk.core.DEFAULT_HTSP_PORT

val Context.dataStore by preferencesDataStore(
    name = "tvhplayer_settings",
)

data class ServerSettings(
    val host: String = "",
    val htspPort: Int = DEFAULT_HTSP_PORT,
    val username: String = "",
    val passwordConfigured: Boolean = false,
)

enum class ServerAuthenticationMode {
    ANONYMOUS,
    PASSWORD,
}

/**
 * Complete in-memory connection values for replacement decisions.
 *
 * Diagnostics intentionally expose neither the endpoint nor credentials.
 */
class ServerConnectionConfiguration(
    val host: String,
    val htspPort: Int,
    val username: String,
    val password: String,
) {
    val authenticationMode: ServerAuthenticationMode
        get() = if (username.isEmpty() && password.isEmpty()) {
            ServerAuthenticationMode.ANONYMOUS
        } else {
            ServerAuthenticationMode.PASSWORD
        }

    override fun equals(other: Any?): Boolean =
        other is ServerConnectionConfiguration &&
            host == other.host &&
            htspPort == other.htspPort &&
            username == other.username &&
            password == other.password

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + htspPort
        result = 31 * result + username.hashCode()
        result = 31 * result + password.hashCode()
        return result
    }

    override fun toString(): String =
        "ServerConnectionConfiguration(" +
            "host=<redacted>, " +
            "htspPort=$htspPort, " +
            "username=<redacted>, " +
            "password=<redacted>, " +
            "authenticationMode=$authenticationMode)"
}

internal fun serverSettingsForEditing(
    host: String,
    htspPort: Int,
    passwordConfigured: Boolean,
): ServerSettings = ServerSettings(
    host = host,
    htspPort = htspPort,
    username = "",
    passwordConfigured = passwordConfigured,
)
