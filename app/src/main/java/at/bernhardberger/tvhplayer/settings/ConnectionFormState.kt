package at.bernhardberger.tvhplayer.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import at.bernhardberger.tvheadend.sdk.core.DEFAULT_HTSP_PORT
import at.bernhardberger.tvheadend.sdk.core.ServerAuthentication
import at.bernhardberger.tvheadend.sdk.core.ServerProfile
import kotlinx.coroutines.CancellationException

internal enum class ConnectionFormFeedback {
    SAVED,
    INVALID,
    SAVE_FAILED,
}

@Stable
internal class ConnectionFormState(
    host: String = "",
    port: String = DEFAULT_HTSP_PORT.toString(),
    username: String = "",
    password: String = "",
) {
    var host by mutableStateOf(host)
        private set
    var port by mutableStateOf(port)
        private set
    var username by mutableStateOf(username)
        private set
    var password by mutableStateOf(password)
        private set
    // Kept only in memory for testing/saving, never passed to a text field or its visibility toggle.
    private var savedPassword by mutableStateOf("")
    val hasSavedPassword: Boolean get() = password.isEmpty() && savedPassword.isNotEmpty()
    private val effectivePassword: String get() = password.ifEmpty { savedPassword }
    var feedback by mutableStateOf<ConnectionFormFeedback?>(null)
        private set

    private val parsedPort: Int?
        get() = port.toIntOrNull()?.takeIf { it in 1..65535 }

    val hostMissing: Boolean
        get() = host.isBlank()

    val portInvalid: Boolean
        get() = parsedPort == null

    /** A username without a password, or the other way round; both or neither are complete. */
    val credentialsIncomplete: Boolean
        get() = username.isBlank() != effectivePassword.isBlank()

    val canSubmit: Boolean
        get() = !hostMissing && !portInvalid && !credentialsIncomplete

    fun updateHost(host: String) {
        this.host = host
    }

    fun updatePort(port: String) {
        this.port = port
    }

    fun updateUsername(username: String) {
        this.username = username
    }

    fun updatePassword(password: String) {
        // An emptied replacement falls back to the saved sign-in; only Clear discards it.
        this.password = password
    }

    fun clearPassword() {
        password = ""
        savedPassword = ""
    }

    fun clearCredentials() {
        username = ""
        clearPassword()
    }

    fun clearFeedback() {
        feedback = null
    }

    /** The profile these values describe, or null while [canSubmit] is false. */
    fun serverProfile(): ServerProfile? {
        val endpointPort = parsedPort
        if (!canSubmit || endpointPort == null) return null
        val authentication = if (username.isBlank()) {
            ServerAuthentication.Anonymous
        } else {
            ServerAuthentication.Password(username, effectivePassword)
        }
        return ServerProfile(host, endpointPort, authentication)
    }

    suspend fun loadFrom(settingsStore: ConnectionProfileEditor) {
        settingsStore.loadServerForEditing { host, port, username, password ->
            this.host = host
            this.port = port.toString()
            this.username = username
            this.password = ""
            this.savedPassword = password
        }
    }

    /** Only the saved sign-in, which [ConnectionFormStateSaver] leaves out; a restored address stays. */
    suspend fun loadSignInFrom(settingsStore: ConnectionProfileEditor) {
        settingsStore.loadServerForEditing { _, _, username, password ->
            this.username = username
            this.password = ""
            this.savedPassword = password
        }
    }

    suspend fun submit(
        settingsStore: ConnectionProfileEditor,
        acquireCredentialLease: () -> CredentialEditLease,
    ): ConnectionFormFeedback {
        val endpointPort = parsedPort
        if (!canSubmit || endpointPort == null) {
            return setFeedback(ConnectionFormFeedback.INVALID)
        }

        return runProfileOperation {
            if (username.isBlank()) {
                settingsStore.saveServer(host.trim(), endpointPort)
            } else {
                settingsStore.savePasswordServer(
                    host = host.trim(),
                    htspPort = endpointPort,
                    username = username,
                    password = effectivePassword,
                    credentialLease = acquireCredentialLease(),
                )
            }
        }
    }

    private suspend fun runProfileOperation(
        operation: suspend () -> Unit,
    ): ConnectionFormFeedback {
        val result = try {
            operation()
            ConnectionFormFeedback.SAVED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ConnectionFormFeedback.SAVE_FAILED
        }
        return setFeedback(result)
    }

    private fun setFeedback(value: ConnectionFormFeedback): ConnectionFormFeedback {
        feedback = value
        return value
    }
}

internal val ConnectionFormStateSaver = listSaver<ConnectionFormState, String>(
    save = { listOf(it.host, it.port) },
    restore = { ConnectionFormState(host = it[0], port = it[1]) },
)

@Composable
internal fun rememberConnectionFormState(): ConnectionFormState = rememberSaveable(
    saver = ConnectionFormStateSaver,
) {
    ConnectionFormState()
}
