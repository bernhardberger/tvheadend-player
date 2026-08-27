package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "tvhplayer_settings")

data class ServerSettings(
    val host: String = "",
    val htspPort: Int = 9982,
    val username: String = ""
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

class ServerSettingsStore(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT_HTSP = intPreferencesKey("htspPort")
        val USER = stringPreferencesKey("user")
        val AUTO = booleanPreferencesKey("auto")
    }

    val serverSettings: Flow<ServerSettings> =
        context.dataStore.data.map { p ->
            ServerSettings(
                host = p[Keys.HOST] ?: "",
                htspPort = p[Keys.PORT_HTSP] ?: 9982,
                username = p[Keys.USER] ?: ""
            )
        }

    suspend fun saveServer(
        host: String,
        htspPort: Int,
        username: String,
        autoConnect: Boolean
    ) {
        context.dataStore.edit { p ->
            p[Keys.HOST] = host
            p[Keys.PORT_HTSP] = htspPort
            p[Keys.USER] = username
            p[Keys.AUTO] = autoConnect
        }
    }
}
