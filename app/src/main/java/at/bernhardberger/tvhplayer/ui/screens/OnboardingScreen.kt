package at.bernhardberger.tvhplayer.ui.screens

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.settings.rememberConnectionFormState
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import at.bernhardberger.tvhplayer.ui.components.ActionsTemplate
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import at.bernhardberger.tvhplayer.ui.components.TvPasswordField
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

enum class OnboardingStep {
    INTRODUCTION,
    CONNECTION,
}

@Composable
fun OnboardingScreen(
    settingsStore: AppProfileOwner = koinInject(),
) {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    var step by rememberSaveable { mutableStateOf(OnboardingStep.INTRODUCTION) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
    ) {
        when (step) {
            OnboardingStep.INTRODUCTION -> OnboardingIntroduction(
                onContinue = { step = OnboardingStep.CONNECTION },
            )
            OnboardingStep.CONNECTION -> OnboardingConnection(
                settingsStore = settingsStore,
                onBack = { step = OnboardingStep.INTRODUCTION },
            )
        }
    }
}

@Composable
fun OnboardingIntroduction(onContinue: () -> Unit) {
    val continueFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { continueFocus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(TvFullScreenPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        ActionsTemplate(
            title = stringResource(R.string.onboarding_title),
            subtitle = stringResource(R.string.onboarding_requirement),
            body = {
                Text(
                    text = stringResource(R.string.trusted_network_guidance),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            actions = {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.focusRequester(continueFocus),
                ) {
                    Text(stringResource(R.string.onboarding_continue))
                }
            },
        )
    }
}

@Composable
internal fun OnboardingConnection(
    settingsStore: ConnectionProfileEditor,
    onBack: () -> Unit,
    form: ConnectionFormState = rememberConnectionFormState(),
) {
    val scope = rememberCoroutineScope()
    val hostFocus = remember { FocusRequester() }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { hostFocus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(TvFullScreenPadding)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_connection_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.trusted_network_guidance),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(760.dp),
        )

        TvOutlinedTextField(
            id = "onboarding-host",
            editingId = editingId,
            setEditingId = { editingId = it },
            value = form.host,
            onValueChange = form::updateHost,
            label = { Text(stringResource(R.string.host)) },
            modifier = Modifier
                .width(560.dp)
                .focusRequester(hostFocus),
        )
        TvOutlinedTextField(
            id = "onboarding-port",
            editingId = editingId,
            setEditingId = { editingId = it },
            value = form.port,
            onValueChange = form::updatePort,
            label = { Text(stringResource(R.string.port_htsp)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(560.dp),
        )
        TvOutlinedTextField(
            id = "onboarding-user",
            editingId = editingId,
            setEditingId = { editingId = it },
            value = form.username,
            onValueChange = form::updateUsername,
            label = { Text(stringResource(R.string.username)) },
            modifier = Modifier.width(560.dp),
        )
        TvPasswordField(
            id = "onboarding-password",
            editingId = editingId,
            setEditingId = { editingId = it },
            value = form.password,
            onValueChange = form::updatePassword,
            modifier = Modifier.width(560.dp),
        )

        if (form.feedback == ConnectionFormFeedback.SAVE_FAILED) {
            Text(
                text = stringResource(R.string.credential_save_failed),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
            Button(
                enabled = form.canSubmit,
                onClick = {
                    scope.launch {
                        val result = form.submit(settingsStore) {
                            CredentialEditLease {}
                        }
                        if (result == ConnectionFormFeedback.SAVED) {
                            form.clearPassword()
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.save_and_continue))
            }
        }
    }
}
