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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.SecurePasswordStore
import at.bernhardberger.tvhplayer.settings.ServerSettingsStore
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import at.bernhardberger.tvhplayer.ui.components.TvPasswordField
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsConnection(
    settingsStore: ServerSettingsStore = koinInject(),
    passwordStore: SecurePasswordStore = koinInject()
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
    var auto by rememberSaveable { mutableStateOf(true) }

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
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.host)) }
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
                value = user,
                onValueChange = { user = it },
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val pHtsp = htspPort.toIntOrNull() ?: 9982
                scope.launch {
                    try {
                        if (passwordChanged) passwordStore.setPassword(pass)
                        settingsStore.saveServer(host, pHtsp, user, auto)
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
