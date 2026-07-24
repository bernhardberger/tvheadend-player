package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.isValidSimpleTvPin
import at.bernhardberger.tvhplayer.settings.SimpleTvSettingsStore
import at.bernhardberger.tvhplayer.stores.SimpleTvSession
import at.bernhardberger.tvhplayer.ui.TvScreenPadding
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SimpleTvUnlockScreen(
    store: SimpleTvSettingsStore = koinInject(),
    session: SimpleTvSession = koinInject(),
    onUnlocked: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by store.settings.collectAsStateWithLifecycle(
        initialValue = SimpleTvSettings()
    )
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(TvScreenPadding),
    ) {
        Text(
            stringResource(R.string.simple_tv_unlock_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.simple_tv_unlock_session))
        Text(stringResource(R.string.simple_tv_pin_security_boundary))
        Spacer(Modifier.height(20.dp))
        if (settings.pinConfigured) {
            TvOutlinedTextField(
                id = "unlock-pin",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(4)
                    failed = false
                },
                label = { Text(stringResource(R.string.simple_tv_pin)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        } else {
            Text(stringResource(R.string.simple_tv_no_pin))
        }
        if (failed) {
            Text(
                stringResource(R.string.simple_tv_pin_wrong),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !settings.pinConfigured || isValidSimpleTvPin(pin),
            onClick = {
                scope.launch {
                    if (!settings.pinConfigured || store.verifyPin(pin)) {
                        session.unlock()
                        onUnlocked()
                    } else {
                        failed = true
                    }
                }
            },
        ) {
            Text(stringResource(R.string.simple_tv_unlock))
        }
        OutlinedButton(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
    }
}
