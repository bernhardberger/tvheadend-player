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
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.settings.rememberConnectionFormState
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import at.bernhardberger.tvhplayer.ui.components.TvPasswordField
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.WeakHashMap

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
    form: ConnectionFormState = rememberConnectionFormState(),
) {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val window = activity?.window

    DisposableEffect(window) {
        val lease = ConnectionSecureWindow.acquire(window)
        onDispose(lease::release)
    }

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(settingsStore, form) {
        form.loadFrom(settingsStore)
    }

    DisposableEffect(form) {
        onDispose(form::clearCredentials)
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
                value = form.host,
                onValueChange = form::updateHost,
                label = { Text(stringResource(R.string.host)) },
                modifier = Modifier.focusRequester(initialFocusRequester),
            )

            Spacer(Modifier.height(12.dp))

            TvOutlinedTextField(
                id = "port",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = form.port,
                onValueChange = form::updatePort,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.port_htsp)) }
            )

            Spacer(Modifier.height(12.dp))

            TvOutlinedTextField(
                id = "user",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = form.username,
                onValueChange = form::updateUsername,
                label = { Text(stringResource(R.string.username)) },
                modifier = Modifier.testTag("connection-username"),
            )

            Spacer(Modifier.height(12.dp))

            TvPasswordField(
                id = "pass",
                editingId = editingId,
                setEditingId = { editingId = it },
                value = form.password,
                onValueChange = {
                    form.updatePassword(it)
                    form.clearFeedback()
                },
                modifier = Modifier.testTag("connection-password"),
            )

            Spacer(Modifier.height(12.dp))
        }

        if (form.feedback == ConnectionFormFeedback.SAVE_FAILED) {
            Text(
                text = stringResource(R.string.credential_save_failed),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = form.canSubmit,
                onClick = {
                    scope.launch {
                        form.submit(settingsStore) {
                            ConnectionSecureWindow.acquire(window)
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.save))
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        form.clearSavedPassword(settingsStore)
                    }
                },
            ) {
                Text(stringResource(R.string.clear_saved_password))
            }
        }
    }
}
