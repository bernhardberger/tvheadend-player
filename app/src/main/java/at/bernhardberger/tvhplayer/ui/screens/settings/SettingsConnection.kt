package at.bernhardberger.tvhplayer.ui.screens.settings

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import at.bernhardberger.tvhplayer.ui.components.TvPasswordField
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.WeakHashMap

internal class ConnectionEditCredentials {
    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set

    fun replace(username: String, password: String) {
        this.username = username
        this.password = password
    }

    fun updateUsername(username: String) {
        this.username = username
    }

    fun updatePassword(password: String) {
        this.password = password
    }

    fun clear() {
        username = ""
        password = ""
    }
}

internal suspend fun ConnectionEditCredentials.loadFrom(
    settingsStore: ConnectionProfileEditor,
    applyEndpoint: (host: String, port: Int) -> Unit,
) {
    settingsStore.loadServerForEditing { host, port, username, password ->
        applyEndpoint(host, port)
        replace(username, password)
    }
}

internal suspend fun ConnectionEditCredentials.saveTo(
    settingsStore: ConnectionProfileEditor,
    host: String,
    port: Int,
    acquireCredentialLease: () -> CredentialEditLease,
) {
    if (username.isBlank()) {
        settingsStore.saveServer(host, port)
    } else {
        settingsStore.savePasswordServer(
            host,
            port,
            username,
            password,
            acquireCredentialLease(),
        )
    }
}

internal object ConnectionSecureWindow {
    private data class State(
        var leaseCount: Int,
        val clearOnFinalRelease: Boolean,
    )

    private val states = WeakHashMap<Window, State>()

    fun acquire(window: Window?): CredentialEditLease {
        if (window == null) return CredentialEditLease {}

        synchronized(states) {
            val existing = states[window]
            if (existing == null) {
                val wasSecure = window.hasSecureFlag()
                if (!wasSecure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                states[window] = State(leaseCount = 1, clearOnFinalRelease = !wasSecure)
            } else {
                existing.leaseCount += 1
                if (!window.hasSecureFlag()) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        return object : CredentialEditLease {
            private var active = true

            override fun release() = synchronized(states) {
                if (!active) return@synchronized
                active = false
                val state = states[window] ?: return@synchronized
                state.leaseCount -= 1
                if (state.leaseCount == 0) {
                    states.remove(window)
                    if (state.clearOnFinalRelease) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    private fun Window.hasSecureFlag(): Boolean =
        attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}

@Composable
internal fun SettingsConnection(
    initialFocusRequester: FocusRequester,
    settingsStore: ConnectionProfileEditor = koinInject<AppProfileOwner>(),
) {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val window = activity?.window

    DisposableEffect(window) {
        val lease = ConnectionSecureWindow.acquire(window)
        onDispose(lease::release)
    }

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    var host by rememberSaveable { mutableStateOf("") }
    var htspPort by rememberSaveable { mutableStateOf("9982") }
    val credentials = remember { ConnectionEditCredentials() }
    var credentialError by remember { mutableStateOf(false) }
    val parsedPort = htspPort.toIntOrNull()?.takeIf { it in 1..65535 }
    val credentialsComplete =
        (credentials.username.isBlank() && credentials.password.isBlank()) ||
            (credentials.username.isNotBlank() && credentials.password.isNotBlank())
    val validEndpoint = host.isNotBlank() && parsedPort != null && credentialsComplete

    LaunchedEffect(Unit) {
        credentials.loadFrom(settingsStore) { configuredHost, configuredPort ->
            host = configuredHost
            htspPort = configuredPort.toString()
        }
    }

    DisposableEffect(credentials) {
        onDispose { credentials.clear() }
    }

    SettingsPane(title = stringResource(R.string.settings_server)) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .focusGroup()
        ) {

            TvOutlinedTextField(
                id = "host",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.host)) },
                modifier = Modifier.focusRequester(initialFocusRequester),
            )

            Spacer(Modifier.height(12.dp))

            TvOutlinedTextField(
                id = "port",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = htspPort,
                onValueChange = { htspPort = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.port_htsp)) }
            )

            Spacer(Modifier.height(12.dp))

            TvOutlinedTextField(
                id = "user",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = credentials.username,
                onValueChange = credentials::updateUsername,
                label = { Text(stringResource(R.string.username)) },
                modifier = Modifier.testTag("connection-username"),
            )

            Spacer(Modifier.height(12.dp))

            TvPasswordField(
                id = "pass",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = credentials.password,
                onValueChange = {
                    credentials.updatePassword(it)
                    credentialError = false
                },
                modifier = Modifier.testTag("connection-password"),
            )

            Spacer(Modifier.height(12.dp))
        }

        if (credentialError) {
            Text(
                text = stringResource(R.string.credential_save_failed),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = validEndpoint,
                onClick = {
                val pHtsp = parsedPort ?: return@Button
                scope.launch {
                    try {
                        credentials.saveTo(settingsStore, host.trim(), pHtsp) {
                            ConnectionSecureWindow.acquire(window)
                        }
                        credentialError = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        credentialError = true
                    }
                }
            }) {
                Text(stringResource(R.string.save))
            }

            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        val current = settingsStore.serverSettings.first()
                        if (current.host.isNotBlank()) {
                            settingsStore.saveServer(current.host, current.htspPort)
                        } else {
                            settingsStore.clearProfile()
                        }
                        credentials.clear()
                        credentialError = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        credentialError = true
                    }
                }
            }) {
                Text(stringResource(R.string.clear_saved_password))
            }
        }
    }
}
