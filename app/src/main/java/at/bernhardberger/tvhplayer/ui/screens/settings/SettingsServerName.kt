package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text as FieldText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.GuidedStepGuidance
import at.bernhardberger.tvhplayer.ui.components.GuidedStepLayout
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import at.bernhardberger.tvhplayer.ui.components.arrivalFocus
import at.bernhardberger.tvhplayer.ui.components.serverAddress

/** The longest name a server profile stores. */
internal const val ServerNameMaxLength = 64

internal const val SERVER_NAME_FIELD = "server-name-field"
internal const val SERVER_NAME_SAVE = "server-name-save"

/**
 * Names a stored server (frame 08): a guided step with one optional field and Save. An empty name
 * shows the server's account and address instead. Back leaves without saving.
 */
@Composable
internal fun SettingsServerName(
    profile: ServerProfileSummary,
    onSave: (String?) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var name by rememberSaveable(profile.id.value) { mutableStateOf(profile.label.orEmpty()) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val save = { onSave(name.trim().takeIf { it.isNotEmpty() }) }
    val username = profile.username?.takeIf { profile.authenticationMode == ServerProfileAuthenticationMode.PASSWORD }
    val fallback = serverProfileUnnamed(profile)
    val address = serverAddress(profile.host, profile.port.toString())
    val fieldLabel = stringResource(R.string.server_name_field)
    SettingsSetupFrame {
        GuidedStepLayout(guidance = {
            GuidedStepGuidance(
                title = stringResource(R.string.server_name_title),
                body = stringResource(R.string.server_name_body, fallback),
                meta = if (username == null) address else stringResource(R.string.server_detail_separator, username, address),
            )
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = fieldLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                TvOutlinedTextField(
                    id = SERVER_NAME_FIELD,
                    editingId = editingId,
                    setEditingId = { editingId = it },
                    value = name,
                    onValueChange = { value -> name = value.filterNot(Char::isISOControl).take(ServerNameMaxLength) },
                    label = null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    supportingText = { FieldText(stringResource(R.string.server_name_limit)) },
                    onImeAction = { editingId = null },
                    modifier = arrivalFocus()
                        .width(TvBrowseColumnWidth)
                        .testTag(SERVER_NAME_FIELD)
                        .semantics { contentDescription = fieldLabel },
                )
            }
            GuidedStepAction(
                title = stringResource(R.string.server_name_save),
                icon = R.drawable.ic_check,
                onClick = save,
                modifier = Modifier.testTag(SERVER_NAME_SAVE),
            )
        }
    }
}
