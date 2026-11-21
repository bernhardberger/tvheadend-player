package at.bernhardberger.tvhplayer.touch.connect

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R

@Composable
internal fun ConnectionForm(
    state: ConnectionUiState,
    onFieldsChange: (ConnectionFields) -> Unit,
    compact: Boolean,
    showSignInHelp: Boolean,
    modifier: Modifier = Modifier,
    focusPassword: Boolean = false,
) {
    val fields = state.fields
    val enabled = state.phase !is ConnectionPhase.Connecting
    val addressError = state.addressError
    val portError = state.portError
    // The address pair's own errors come first; "not Tvheadend" belongs to both fields at once.
    val pairError = stringResource(R.string.connect_not_tvheadend)
        .takeIf { state.phase == ConnectionPhase.NotTvheadend && !addressError && !portError }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ConnectionField(
                    label = SharedR.string.onboarding_server_address,
                    value = fields.address,
                    onChange = { onFieldsChange(fields.copy(address = it)) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    support = when {
                        pairError != null -> null
                        addressError -> stringResource(SharedR.string.onboarding_server_address_missing)
                        else -> stringResource(SharedR.string.onboarding_server_address_support)
                    },
                    error = pairError
                        ?: stringResource(SharedR.string.onboarding_server_address_missing).takeIf { addressError },
                    keyboardType = KeyboardType.Uri,
                )
                ConnectionField(
                    label = R.string.connect_port,
                    value = fields.port,
                    onChange = { onFieldsChange(fields.copy(port = it)) },
                    enabled = enabled,
                    modifier = Modifier.width(if (compact) 124.dp else 140.dp),
                    support = when {
                        pairError != null -> null
                        portError -> stringResource(SharedR.string.onboarding_port_invalid)
                        else -> stringResource(SharedR.string.onboarding_port_support, USUAL_PORT)
                    },
                    error = pairError ?: stringResource(SharedR.string.onboarding_port_invalid).takeIf { portError },
                    keyboardType = KeyboardType.Number,
                )
            }
            // Both fields carry the sentence as their error, so the shared line is not read again.
            if (pairError != null) {
                PairLine(pairError, MaterialTheme.colorScheme.error, Modifier.semantics { hideFromAccessibility() })
            }
        }
        SignInFields(state, onFieldsChange, compact, showSignInHelp, focusPassword)
        ConnectionField(
            label = SharedR.string.server_name_field,
            value = fields.name,
            onChange = { onFieldsChange(fields.copy(name = it)) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            support = if (fields.address.isBlank()) stringResource(R.string.connect_name_help_no_address)
            else stringResource(SharedR.string.server_name_body, fields.address),
            imeAction = ImeAction.Done,
        )
        SaveFailedLine(state.saveFailed)
    }
}

/** The shared sentence the TV shows when a passed connection couldn't be written. */
@Composable
private fun SaveFailedLine(shown: Boolean) {
    if (!shown) return
    Text(
        stringResource(SharedR.string.connection_save_failed),
        Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SignInFields(
    state: ConnectionUiState,
    onFieldsChange: (ConnectionFields) -> Unit,
    compact: Boolean,
    showHelp: Boolean,
    focusPassword: Boolean,
) {
    val fields = state.fields
    val enabled = state.phase !is ConnectionPhase.Connecting
    val errorMessage = when {
        state.phase == ConnectionPhase.Refused -> stringResource(R.string.connect_refused)
        state.signInError -> stringResource(SharedR.string.onboarding_sign_in_pair)
        else -> null
    }
    // Left empty, Password keeps the saved one; typing replaces it, and the note leaves.
    val saved = stringResource(SharedR.string.onboarding_password_saved)
        .takeIf { state.passwordSaved && fields.password.isEmpty() && errorMessage == null }
    val username: @Composable (Modifier) -> Unit = { modifier ->
        ConnectionField(
            label = SharedR.string.username,
            value = fields.username,
            onChange = { onFieldsChange(fields.copy(username = it)) },
            enabled = enabled,
            modifier = modifier.semantics { contentType = ContentType.Username },
            error = errorMessage,
            trailingIcon = if (errorMessage != null) {
                { Icon(painterResource(R.drawable.connect_error_outline), contentDescription = null) }
            } else null,
        )
    }
    val password: @Composable (Modifier) -> Unit = { modifier ->
        PasswordField(
            value = fields.password,
            onChange = { onFieldsChange(fields.copy(password = it)) },
            enabled = enabled,
            // The sentence is shown once for the pair: under Password when stacked, under both fields side by side.
            support = if (compact) {
                errorMessage ?: saved ?: stringResource(SharedR.string.onboarding_sign_in_body).takeIf { showHelp }
            } else {
                saved
            },
            error = errorMessage,
            modifier = modifier,
            focus = focusPassword,
        )
    }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            username(Modifier.fillMaxWidth())
            password(Modifier.fillMaxWidth())
        }
    } else {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                username(Modifier.weight(1f))
                password(Modifier.weight(1f))
            }
            when {
                // Both fields already carry the sentence as their error, so the shared line is not read again.
                errorMessage != null -> PairLine(
                    errorMessage, MaterialTheme.colorScheme.error, Modifier.semantics { hideFromAccessibility() },
                )
                showHelp -> PairLine(
                    stringResource(SharedR.string.onboarding_sign_in_body), MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Supporting text that belongs to the side-by-side sign-in pair rather than one field. */
@Composable
private fun PairLine(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
        color = color, style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    support: String?,
    error: String?,
    modifier: Modifier,
    focus: Boolean,
) {
    var visible by remember(enabled) { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (focus) LaunchedEffect(Unit) { requester.requestFocus() }
    ConnectionField(
        label = SharedR.string.password,
        value = value,
        onChange = onChange,
        enabled = enabled,
        modifier = modifier.focusRequester(requester).semantics {
            password()
            contentType = ContentType.Password
        },
        support = support,
        error = error,
        keyboardType = KeyboardType.Password,
        transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = if (value.isNotEmpty()) {
            {
                ConnectionIconButton(
                    if (visible) R.drawable.connect_hide_outline else R.drawable.connect_show_outline,
                    if (visible) R.string.connect_hide_password else SharedR.string.show_password,
                    enabled = enabled,
                ) {
                    visible = !visible
                }
            }
        } else null,
    )
}

@Composable
private fun ConnectionField(
    @StringRes label: Int,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    support: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    transformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.semantics { if (error != null) error(error) },
        enabled = enabled,
        isError = error != null,
        singleLine = true,
        label = { Text(stringResource(label)) },
        supportingText = support?.let { text -> { Text(text) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        visualTransformation = transformation,
        trailingIcon = trailingIcon,
    )
}
