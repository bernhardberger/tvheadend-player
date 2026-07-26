package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
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
    onExited: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by store.settings.collectAsStateWithLifecycle(
        initialValue = SimpleTvSettings()
    )
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var pinAccepted by remember(settings.pinConfigured) {
        mutableStateOf(!settings.pinConfigured)
    }
    BackHandler(onBack = onBack)

    if (pinAccepted) {
        SimpleTvExitConfirmation(
            onCancel = onBack,
            onConfirm = {
                session.exit()
                onExited()
            },
        )
        return
    }

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
        if (failed) {
            Text(
                stringResource(R.string.simple_tv_pin_wrong),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = isValidSimpleTvPin(pin),
            onClick = {
                scope.launch {
                    if (store.verifyPin(pin)) {
                        pinAccepted = true
                    } else {
                        failed = true
                    }
                }
            },
        ) {
            Text(stringResource(R.string.simple_tv_continue_to_exit))
        }
        OutlinedButton(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun SimpleTvExitConfirmation(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val safeFocus = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { safeFocus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.76f))
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.56f),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.simple_tv_exit_confirm_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(R.string.simple_tv_exit_confirm_message))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.focusRequester(safeFocus),
                    ) {
                        Text(stringResource(R.string.simple_tv_keep_watching))
                    }
                    Button(onClick = onConfirm) {
                        Text(stringResource(R.string.simple_tv_exit))
                    }
                }
            }
        }
    }
}
