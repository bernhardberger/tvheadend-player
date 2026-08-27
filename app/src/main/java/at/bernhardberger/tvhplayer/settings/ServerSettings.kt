package at.bernhardberger.tvhplayer.settings

import android.content.Context
import at.bernhardberger.tvheadend.sdk.android.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.android.ServerProfileOperationResult
import at.bernhardberger.tvheadend.sdk.android.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val Context.dataStore by preferencesDataStore(name = "tvhplayer_settings")

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

class ServerSettingsStore(
    private val context: Context,
    private val profileStore: TvheadendServerProfileStore,
) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT_HTSP = intPreferencesKey("htspPort")
        val USER = stringPreferencesKey("user")
        val AUTO = booleanPreferencesKey("auto")
    }

    private val mutableProfileRevision = MutableStateFlow(0L)
    internal val profileRevision: StateFlow<Long> = mutableProfileRevision.asStateFlow()

    val serverSettings: Flow<ServerSettings> = profileRevision
        .map {
            when (val result = profileStore.loadProfile()) {
                is ServerProfileReadResult.Available -> serverSettingsForEditing(
                    host = result.host,
                    htspPort = result.port,
                    passwordConfigured =
                        result.authenticationMode == ServerProfileAuthenticationMode.PASSWORD,
                )
                ServerProfileReadResult.Missing,
                ServerProfileReadResult.Unavailable -> ServerSettings()
            }
        }

    suspend fun saveServer(
        host: String,
        htspPort: Int,
        username: String,
        autoConnect: Boolean,
    ) {
        require(username.isBlank()) { "Password profiles must be stored atomically" }
        check(profileStore.storeAnonymous(host, htspPort) == ServerProfileOperationResult.SUCCESS)
        mutableProfileRevision.update(Long::inc)
        context.dataStore.edit { it[Keys.AUTO] = autoConnect }
    }

    suspend fun savePasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
        autoConnect: Boolean,
    ) {
        check(
            profileStore.storePassword(host, htspPort, username, password) ==
                ServerProfileOperationResult.SUCCESS,
        )
        mutableProfileRevision.update(Long::inc)
        context.dataStore.edit { it[Keys.AUTO] = autoConnect }
    }

    suspend fun clearProfile() {
        check(profileStore.clearProfile() == ServerProfileOperationResult.SUCCESS)
        mutableProfileRevision.update(Long::inc)
    }

    internal suspend fun loadLegacyProfile(loadPassword: suspend () -> LegacyPassword): LegacyServerProfile? {
        val preferences = context.dataStore.data.first()
        val host = preferences[Keys.HOST]?.trim().orEmpty()
        val port = preferences[Keys.PORT_HTSP]
        val username = preferences[Keys.USER]?.trim().orEmpty()
        if (host.isEmpty() || port == null || port !in 1..65_535) return null
        return LegacyServerProfile(host, port, username, loadPassword())
    }

    internal suspend fun clearLegacyEndpoint() {
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.HOST)
            preferences.remove(Keys.PORT_HTSP)
            preferences.remove(Keys.USER)
        }
    }
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

internal fun replacementCredentialsComplete(
    passwordConfigured: Boolean,
    username: String,
    password: String,
    passwordChanged: Boolean,
): Boolean = when {
    username.isBlank() && password.isBlank() -> !passwordConfigured
    else -> username.isNotBlank() && password.isNotBlank() && passwordChanged
}

data class LegacyServerProfile(
    val host: String,
    val port: Int,
    val username: String,
    val password: LegacyPassword,
)

sealed interface LegacyPassword {
    data object Empty : LegacyPassword
    data class Available(val value: String) : LegacyPassword
    data object Unavailable : LegacyPassword
}

sealed interface SdkProfileState {
    data object Missing : SdkProfileState
    data object Unavailable : SdkProfileState
    data class Available(val host: String, val port: Int, val password: Boolean) : SdkProfileState
}

enum class SdkProfileWriteResult { Stored, Unavailable }
enum class LegacyProfileMigrationResult { Ready, RequiresEntry, RetryableUnavailable }

internal class LegacyProfileMigrationFence(
    private val migrate: suspend () -> LegacyProfileMigrationResult,
) {
    private val mutex = Mutex()
    private var completed: LegacyProfileMigrationResult? = null

    suspend fun await(): LegacyProfileMigrationResult = mutex.withLock {
        completed ?: migrate().also { result ->
            if (result != LegacyProfileMigrationResult.RetryableUnavailable) completed = result
        }
    }
}

/** Process-scoped fence shared by startup presentation and session connection. */
class ServerProfileMigration(
    profileStore: TvheadendServerProfileStore,
    settings: ServerSettingsStore,
    legacyCredentials: LegacyCredentialSource,
) {
    private val fence = LegacyProfileMigrationFence {
        migrateLegacyServerProfile(
            loadSdkProfile = { profileStore.loadProfile().toMigrationState() },
            loadLegacyProfile = { settings.loadLegacyProfile(legacyCredentials::loadPassword) },
            storeSdkProfile = { profileStore.storeLegacy(it) },
            clearLegacyPassword = legacyCredentials::clearCiphertext,
            clearLegacyEndpoint = settings::clearLegacyEndpoint,
            deleteLegacyKey = legacyCredentials::deleteObsoleteKey,
        )
    }

    suspend fun await(): LegacyProfileMigrationResult = fence.await()
}

suspend fun migrateLegacyServerProfile(
    loadSdkProfile: suspend () -> SdkProfileState,
    loadLegacyProfile: suspend () -> LegacyServerProfile?,
    storeSdkProfile: suspend (LegacyServerProfile) -> SdkProfileWriteResult,
    clearLegacyPassword: suspend () -> Unit,
    clearLegacyEndpoint: suspend () -> Unit,
    deleteLegacyKey: suspend () -> Unit,
): LegacyProfileMigrationResult {
    val initial = loadSdkProfile()
    val verified = when (initial) {
        is SdkProfileState.Available -> true
        SdkProfileState.Unavailable -> return LegacyProfileMigrationResult.RetryableUnavailable
        SdkProfileState.Missing -> {
            val legacy = loadLegacyProfile() ?: return LegacyProfileMigrationResult.RequiresEntry
            val normalized = legacy.copy(host = legacy.host.trim(), username = legacy.username.trim())
            val complete = normalized.host.isNotEmpty() && normalized.port in 1..65_535 && when {
                normalized.username.isEmpty() -> normalized.password == LegacyPassword.Empty
                else -> normalized.password is LegacyPassword.Available && normalized.password.value.isNotBlank()
            }
            if (!complete || storeSdkProfile(normalized) != SdkProfileWriteResult.Stored) {
                return LegacyProfileMigrationResult.RequiresEntry
            }
            val readback = loadSdkProfile()
            readback is SdkProfileState.Available &&
                readback.host == normalized.host &&
                readback.port == normalized.port &&
                readback.password == (normalized.password is LegacyPassword.Available)
        }
    }
    if (!verified) return LegacyProfileMigrationResult.RequiresEntry
    clearLegacyPassword()
    clearLegacyEndpoint()
    deleteLegacyKey()
    return LegacyProfileMigrationResult.Ready
}

private fun ServerProfileReadResult.toMigrationState(): SdkProfileState = when (this) {
    ServerProfileReadResult.Missing -> SdkProfileState.Missing
    ServerProfileReadResult.Unavailable -> SdkProfileState.Unavailable
    is ServerProfileReadResult.Available -> SdkProfileState.Available(
        host = host,
        port = port,
        password = authenticationMode == ServerProfileAuthenticationMode.PASSWORD,
    )
}

private suspend fun TvheadendServerProfileStore.storeLegacy(
    legacy: LegacyServerProfile,
): SdkProfileWriteResult {
    val result = when (val password = legacy.password) {
        LegacyPassword.Empty -> storeAnonymous(legacy.host, legacy.port)
        is LegacyPassword.Available -> storePassword(
            legacy.host,
            legacy.port,
            legacy.username,
            password.value,
        )
        LegacyPassword.Unavailable -> return SdkProfileWriteResult.Unavailable
    }
    return if (result == ServerProfileOperationResult.SUCCESS) {
        SdkProfileWriteResult.Stored
    } else {
        SdkProfileWriteResult.Unavailable
    }
}
