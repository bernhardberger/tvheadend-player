package at.bernhardberger.tvhplayer.ui.screens.settings

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionProbeResult
import at.bernhardberger.tvhplayer.htsp.HtspConnectionProbe
import at.bernhardberger.tvhplayer.settings.SecurePasswordStore
import at.bernhardberger.tvhplayer.settings.ServerSettingsStore
import at.bernhardberger.tvhplayer.settings.StoredPassword
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import at.bernhardberger.tvhplayer.ui.components.TvPasswordField
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.screens.ConnectionProbeUiState
import at.bernhardberger.tvhplayer.ui.screens.connectionProbeMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsConnection(
    initialFocusRequester: FocusRequester,
    settingsStore: ServerSettingsStore = koinInject(),
    passwordStore: SecurePasswordStore = koinInject(),
    connectionProbe: HtspConnectionProbe = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    var host by rememberSaveable { mutableStateOf("") }
    var htspPort by rememberSaveable { mutableStateOf("9982") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordChanged by remember { mutableStateOf(false) }
    var credentialError by remember { mutableStateOf(false) }
    var probeState by remember {
        mutableStateOf<ConnectionProbeUiState>(ConnectionProbeUiState.Idle)
    }
    var auto by rememberSaveable { mutableStateOf(true) }
    val parsedPort = htspPort.toIntOrNull()?.takeIf { it in 1..65535 }
    val validEndpoint = host.isNotBlank() && parsedPort != null
    val probeMessage = connectionProbeMessage(probeState)

    LaunchedEffect(Unit) {
        val s = settingsStore.serverSettings.first()
        host = s.host
        htspPort = s.htspPort.toString()
        user = s.username
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
                onValueChange = {
                    host = it
                    probeState = ConnectionProbeUiState.Idle
                },
                label = { Text(stringResource(R.string.host)) },
                modifier = Modifier.focusRequester(initialFocusRequester),
            )

            Spacer(Modifier.height(12.dp))

            TvOutlinedTextField(
                id = "port",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = htspPort,
                onValueChange = {
                    htspPort = it
                    probeState = ConnectionProbeUiState.Idle
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.port_htsp)) }
            )

            Spacer(Modifier.height(12.dp))

            TvOutlinedTextField(
                id = "user",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = user,
                onValueChange = {
                    user = it
                    probeState = ConnectionProbeUiState.Idle
                },
                label = { Text(stringResource(R.string.username)) }
            )

            Spacer(Modifier.height(12.dp))

            TvPasswordField(
                id = "pass",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = pass,
                onValueChange = {
                    pass = it
                    passwordChanged = true
                    credentialError = false
                    probeState = ConnectionProbeUiState.Idle
                }
            )

            Text(
                text = stringResource(R.string.password_replacement_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
        }

        if (credentialError) {
            Text(
                text = stringResource(R.string.credential_save_failed),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (probeMessage != null) {
            val successful = (probeState as? ConnectionProbeUiState.Complete)
                ?.result is ConnectionProbeResult.Success
            Text(
                text = probeMessage,
                color = if (successful) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = validEndpoint && probeState != ConnectionProbeUiState.Testing,
                onClick = {
                    val endpointPort = parsedPort ?: return@OutlinedButton
                    probeState = ConnectionProbeUiState.Testing
                    scope.launch {
                        val testPassword = if (passwordChanged) {
                            pass
                        } else {
                            when (val stored = passwordStore.passwordState.first()) {
                                StoredPassword.Empty -> ""
                                is StoredPassword.Available -> stored.value
                                StoredPassword.Unavailable -> {
                                    credentialError = true
                                    probeState = ConnectionProbeUiState.Idle
                                    return@launch
                                }
                            }
                        }
                        probeState = ConnectionProbeUiState.Complete(
                            connectionProbe.test(
                                host = host.trim(),
                                port = endpointPort,
                                username = user.trim(),
                                password = testPassword,
                            )
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.test_connection))
            }

            Button(
                enabled = validEndpoint,
                onClick = {
                val pHtsp = parsedPort ?: return@Button
                scope.launch {
                    try {
                        if (passwordChanged) passwordStore.setPassword(pass)
                        settingsStore.saveServer(host.trim(), pHtsp, user.trim(), auto)
                        pass = ""
                        passwordChanged = false
                        credentialError = false
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
                        passwordStore.clear()
                        pass = ""
                        passwordChanged = false
                        credentialError = false
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
