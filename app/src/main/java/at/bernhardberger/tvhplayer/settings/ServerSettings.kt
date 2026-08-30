package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.dataStore by preferencesDataStore(
    name = "tvhplayer_settings",
    produceMigrations = { listOf(activeTagIdMigration()) },
)

data class ServerSettings(
    val host: String = "",
    val htspPort: Int = 9982,
    val username: String = "",
    val passwordConfigured: Boolean = false,
)

enum class ServerAuthenticationMode {
    ANONYMOUS,
    PASSWORD,
}

data class ServerConnectionPresentation(
    val endpoint: String,
    val authenticationMode: ServerAuthenticationMode,
)

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

fun serverConnectionPresentation(
    host: String,
    htspPort: Int,
    passwordConfigured: Boolean,
): ServerConnectionPresentation {
    val readableHost = host.trim()
    val endpointHost = if (
        readableHost.contains(':') &&
        !(readableHost.startsWith('[') && readableHost.endsWith(']'))
    ) {
        "[$readableHost]"
    } else {
        readableHost
    }
    return ServerConnectionPresentation(
        endpoint = "$endpointHost:$htspPort",
        authenticationMode = if (passwordConfigured) {
            ServerAuthenticationMode.PASSWORD
        } else {
            ServerAuthenticationMode.ANONYMOUS
        },
    )
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

data class LegacyServerProfile(
    val host: String,
    val port: Int,
    val username: String,
    val password: LegacyPassword,
) {
    override fun toString(): String = "LegacyServerProfile(<redacted>)"
}

sealed interface LegacyPassword {
    data object Empty : LegacyPassword
    data class Available(val value: String) : LegacyPassword {
        override fun toString(): String = "LegacyPassword.Available(<redacted>)"
    }
    data object Unavailable : LegacyPassword
}

private object LegacyServerSettingsKeys {
    val host = stringPreferencesKey("host")
    val port = intPreferencesKey("htspPort")
    val username = stringPreferencesKey("user")
}

internal suspend fun Context.loadLegacyServerProfile(
    loadPassword: suspend () -> LegacyPassword,
): LegacyServerProfile? {
    val preferences = dataStore.data.first()
    val host = preferences[LegacyServerSettingsKeys.host]?.trim().orEmpty()
    val port = preferences[LegacyServerSettingsKeys.port]
    val username = preferences[LegacyServerSettingsKeys.username]?.trim().orEmpty()
    if (host.isEmpty() || port == null || port !in 1..65_535) return null
    return LegacyServerProfile(host, port, username, loadPassword())
}

internal suspend fun Context.clearLegacyServerEndpoint() {
    dataStore.edit { preferences ->
        preferences.remove(LegacyServerSettingsKeys.host)
        preferences.remove(LegacyServerSettingsKeys.port)
        preferences.remove(LegacyServerSettingsKeys.username)
    }
}
