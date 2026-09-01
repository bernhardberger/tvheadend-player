package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.isValidSimpleTvPin
import at.bernhardberger.tvhplayer.settings.SimpleTvSettingsStore
import at.bernhardberger.tvhplayer.ui.components.SettingsSectionTitle
import at.bernhardberger.tvhplayer.ui.components.ActionsTemplate
import at.bernhardberger.tvhplayer.ui.components.SettingsSwitchRow
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class PinFeedbackKind { SUCCESS, ERROR }

@Composable
internal fun SettingsSimpleTvContent(
    onStartSimpleTv: (SimpleTvSettings) -> Unit,
    modifier: Modifier = Modifier,
    store: SimpleTvSettingsStore = koinInject(),
) {
    val settings by store.settings.collectAsStateWithLifecycle(
        initialValue = SimpleTvSettings()
    )
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var pinResult by remember { mutableStateOf<Int?>(null) }
    var pinResultKind by remember { mutableStateOf(PinFeedbackKind.SUCCESS) }
    var confirmStart by remember { mutableStateOf(false) }
    val pinValid = isValidSimpleTvPin(pin)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.simple_tv_mode_disclosure),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.simple_tv_home_disclosure),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.simple_tv_guide_return_disclosure),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        SettingsSwitchRow(
            label = stringResource(R.string.simple_tv_startup),
            checked = settings.enabled,
            supportingText = stringResource(R.string.simple_tv_startup_explanation),
            onClick = {
                scope.launch { store.setEnabled(!settings.enabled) }
            },
        )
        SettingsSwitchRow(
            label = stringResource(R.string.simple_tv_timeshift),
            checked = settings.timeshift,
            supportingText = stringResource(R.string.simple_tv_timeshift_explanation),
            onClick = { scope.launch { store.setTimeshift(!settings.timeshift) } },
        )

        SettingsSectionTitle(stringResource(R.string.simple_tv_pin_section))
        Text(
            text = stringResource(R.string.simple_tv_pin_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.simple_tv_pin_recovery),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            enabled = pinValid,
            onClick = {
                scope.launch {
                    if (store.setPin(pin)) {
                        pin = ""
                        pinResult = R.string.simple_tv_pin_saved
                        pinResultKind = PinFeedbackKind.SUCCESS
                    } else {
                        pinResult = R.string.simple_tv_pin_invalid
                        pinResultKind = PinFeedbackKind.ERROR
                    }
                }
            },
        ) {
            Text(stringResource(R.string.simple_tv_set_pin))
        }
        if (!pinValid) {
            Text(
                text = stringResource(R.string.simple_tv_pin_enter_four),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (settings.pinConfigured) {
            Button(
                onClick = {
                    scope.launch {
                        store.clearPin()
                        pin = ""
                        pinResult = R.string.simple_tv_pin_cleared
                        pinResultKind = PinFeedbackKind.SUCCESS
                    }
                },
            ) {
                Text(stringResource(R.string.simple_tv_clear_pin))
            }
        }
        pinResult?.let { messageRes ->
            PinFeedbackRow(
                message = stringResource(messageRes),
                kind = pinResultKind,
            )
        }
        Button(onClick = { confirmStart = true }) {
            Text(stringResource(R.string.simple_tv_start_now))
        }
    }
    if (confirmStart) {
        SimpleTvStartConfirmation(
            onCancel = { confirmStart = false },
            onConfirm = {
                confirmStart = false
                onStartSimpleTv(settings)
            },
        )
    }
}

@Composable
internal fun SimpleTvStartConfirmation(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancelFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 96.dp, vertical = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
                ActionsTemplate(
                    title = stringResource(R.string.simple_tv_start_confirm_title),
                    subtitle = stringResource(R.string.simple_tv_start_confirm_message),
                    actions = {
                        androidx.tv.material3.OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.focusRequester(cancelFocus),
                        ) {
                            Text(stringResource(R.string.back))
                        }
                        Button(onClick = onConfirm) {
                            Text(stringResource(R.string.simple_tv_start_now))
                        }
                    },
                )
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { cancelFocus.requestFocus() }
    }
}

@Composable
private fun PinFeedbackRow(
    message: String,
    kind: PinFeedbackKind,
) {
    val color = when (kind) {
        PinFeedbackKind.SUCCESS -> MaterialTheme.colorScheme.onSurface
        PinFeedbackKind.ERROR -> MaterialTheme.colorScheme.error
    }
    val icon = when (kind) {
        PinFeedbackKind.SUCCESS -> Icons.Filled.CheckCircle
        PinFeedbackKind.ERROR -> Icons.Filled.Error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = message,
            color = color,
        )
    }
}
