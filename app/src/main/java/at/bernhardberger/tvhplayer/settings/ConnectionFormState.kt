package at.bernhardberger.tvhplayer.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal const val DEFAULT_HTSP_PORT = "9982"

internal enum class ConnectionFormFeedback {
    SAVED,
    INVALID,
    SAVE_FAILED,
}

@Stable
internal class ConnectionFormState(
    host: String = "",
    port: String = DEFAULT_HTSP_PORT,
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
    var feedback by mutableStateOf<ConnectionFormFeedback?>(null)
        private set

    private val parsedPort: Int?
        get() = port.toIntOrNull()?.takeIf { it in 1..65535 }

    val canSubmit: Boolean
        get() {
            val credentialsComplete =
                (username.isBlank() && password.isBlank()) ||
                    (username.isNotBlank() && password.isNotBlank())
            return host.isNotBlank() && parsedPort != null && credentialsComplete
        }

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
        this.password = password
    }

    fun clearPassword() {
        password = ""
    }

    fun clearCredentials() {
        username = ""
        password = ""
    }

    fun clearFeedback() {
        feedback = null
    }

    suspend fun loadFrom(settingsStore: ConnectionProfileEditor) {
        settingsStore.loadServerForEditing { host, port, username, password ->
            this.host = host
            this.port = port.toString()
            this.username = username
            this.password = password
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
                    password = password,
                    credentialLease = acquireCredentialLease(),
                )
            }
        }
    }

    suspend fun clearSavedPassword(
        settingsStore: ConnectionProfileEditor,
    ): ConnectionFormFeedback = runProfileOperation {
        val current = settingsStore.serverSettings.first()
        if (current.host.isNotBlank()) {
            settingsStore.saveServer(current.host, current.htspPort)
        } else {
            settingsStore.clearProfile()
        }
        clearCredentials()
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
