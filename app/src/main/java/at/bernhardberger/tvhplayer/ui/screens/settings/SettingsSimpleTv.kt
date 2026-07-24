package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.isValidSimpleTvPin
import at.bernhardberger.tvhplayer.settings.SimpleTvSettingsStore
import at.bernhardberger.tvhplayer.stores.SimpleTvSession
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsSimpleTv(
    store: SimpleTvSettingsStore = koinInject(),
    session: SimpleTvSession = koinInject(),
) {
    val settings by store.settings.collectAsStateWithLifecycle(
        initialValue = SimpleTvSettings()
    )
    val unlocked by session.unlocked.collectAsStateWithLifecycle()
    val ownerAccess = !settings.enabled || unlocked
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var pinResult by remember { mutableStateOf<Int?>(null) }

    SettingsPane(title = stringResource(R.string.settings_simple_tv)) {
        if (!ownerAccess) {
            Text(stringResource(R.string.simple_tv_unlock_required))
            return@SettingsPane
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            SimpleTvToggle(
                label = stringResource(R.string.simple_tv_enabled),
                checked = settings.enabled,
                onClick = {
                    scope.launch {
                        if (settings.enabled) {
                            store.setEnabled(false)
                            session.lock()
                        } else {
                            store.setEnabled(true)
                            session.unlock()
                        }
                    }
                },
            )
            SimpleTvToggle(
                label = stringResource(R.string.simple_tv_epg),
                checked = settings.epg,
                onClick = { scope.launch { store.setEpg(!settings.epg) } },
            )
            SimpleTvToggle(
                label = stringResource(R.string.simple_tv_recordings),
                checked = settings.recordings,
                onClick = { scope.launch { store.setRecordings(!settings.recordings) } },
            )
            SimpleTvToggle(
                label = stringResource(R.string.simple_tv_timeshift),
                checked = settings.timeshift,
                onClick = { scope.launch { store.setTimeshift(!settings.timeshift) } },
            )
            SimpleTvToggle(
                label = stringResource(R.string.simple_tv_stop),
                checked = settings.stop,
                onClick = { scope.launch { store.setStop(!settings.stop) } },
            )
            SimpleTvToggle(
                label = stringResource(R.string.simple_tv_settings_access),
                checked = settings.settings,
                onClick = { scope.launch { store.setSettings(!settings.settings) } },
            )
            SimpleTvToggle(
                label = stringResource(R.string.simple_tv_app_exit),
                checked = settings.appExit,
                onClick = { scope.launch { store.setAppExit(!settings.appExit) } },
            )
            Text(stringResource(R.string.simple_tv_pin_explanation))
            Text(stringResource(R.string.simple_tv_pin_recovery))
            TvOutlinedTextField(
                id = "simple-tv-pin",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = pin,
                onValueChange = { value ->
                    pin = value.filter(Char::isDigit).take(4)
                    pinResult = null
                },
                label = { Text(stringResource(R.string.simple_tv_pin)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = isValidSimpleTvPin(pin),
                onClick = {
                    scope.launch {
                        pinResult = if (store.setPin(pin)) {
                            pin = ""
                            R.string.simple_tv_pin_saved
                        } else {
                            R.string.simple_tv_pin_invalid
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.simple_tv_set_pin))
            }
            if (settings.pinConfigured) {
                Button(
                    onClick = {
                        scope.launch {
                            store.clearPin()
                            pin = ""
                            pinResult = R.string.simple_tv_pin_cleared
                        }
                    },
                ) {
                    Text(stringResource(R.string.simple_tv_clear_pin))
                }
            }
            pinResult?.let { Text(stringResource(it)) }
        }
    }
}

@Composable
private fun SimpleTvToggle(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        selected = checked,
        onClick = onClick,
        headlineContent = { Text(label) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null)
        },
        scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    )
}
