package at.bernhardberger.tvhplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.tv.material3.Checkbox
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSpacing8

/**
 * Password counterpart of [TvOutlinedTextField]: keeps the soft keyboard hidden
 * while navigating with the D-pad (the field is [readOnly] until confirmed with
 * OK) and asks the keyboard for a password field, without suggestions, whether
 * the text is shown or not. A "Show password" checkbox row under the field, the
 * field's own width, reveals the text; the row stays but is disabled and
 * skipped by the D-pad while [presentationValue] stands in for a saved password.
 */
@Composable
fun TvPasswordField(
    id: String,
    editingId: String?,
    setEditingId: (String?) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Inside the outline, as in [TvOutlinedTextField]; null where the caller shows it above. */
    label: (@Composable () -> Unit)? = { Text(stringResource(R.string.password)) },
    presentationValue: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    /** Replaces leaving the edit on Done, as in [TvOutlinedTextField]. */
    onImeAction: (() -> Unit)? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val textFocus = remember { FocusRequester() }

    var passwordVisible by remember { mutableStateOf(false) }
    val openPress = remember { FieldOpenPress() }

    val isEditing = editingId == id
    val displayedPresentation = presentationValue.takeIf { !isEditing && value.isEmpty() }
    val displayedValue = displayedPresentation ?: value
    // The saved-password placeholder has nothing to reveal.
    val canReveal = presentationValue == null || value.isNotEmpty()
    val revealed = canReveal && passwordVisible

    BackHandler(enabled = isEditing) { setEditingId(null) }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            textFocus.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    // The row takes the field's width: the caller sizes the field, not this column.
    Column(Modifier.width(IntrinsicSize.Min)) {
        OutlinedTextField(
            value = displayedValue,
            onValueChange = onValueChange,
            label = label,
            singleLine = true,
            readOnly = !isEditing,
            enabled = enabled,
            isError = isError,
            supportingText = supportingText,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() ?: setEditingId(null) }),

            visualTransformation = if (displayedPresentation != null || revealed) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },

            modifier = modifier
                .focusRequester(textFocus)
                .onFocusChanged { state ->
                    if (!state.isFocused) openPress.cancel()
                    if (!state.isFocused && isEditing) setEditingId(null)
                }
                .onPreviewKeyEvent { ev ->
                    if (openPress.handle(ev, isEditing) { setEditingId(id) }) return@onPreviewKeyEvent true
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when (ev.key) {
                        Key.DirectionDown -> {
                            if (!isEditing) {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            } else false
                        }

                        Key.DirectionUp -> {
                            if (!isEditing) {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            } else false
                        }

                        else -> false
                    }
                },
        )
        ShowPasswordRow(
            checked = revealed,
            onToggle = { passwordVisible = !passwordVisible },
            enabled = enabled && canReveal,
        )
    }
}

/**
 * The row is one checkbox target: OK anywhere on it toggles. While disabled it keeps its place
 * so the actions below do not move, and the D-pad passes over it.
 */
@Composable
private fun ShowPasswordRow(checked: Boolean, onToggle: () -> Unit, enabled: Boolean) {
    ListItem(
        selected = checked,
        colors = ListItemDefaults.colors(
            // Checked is semantic state shown by the checkbox, not the selected-container treatment.
            selectedContainerColor = Color.Transparent,
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        // Keeps the field's width on focus, so the row does not cover the help text above it.
        scale = ListItemDefaults.scale(
            focusedScale = 1f, pressedScale = 1f, focusedSelectedScale = 1f, pressedSelectedScale = 1f,
        ),
        enabled = enabled,
        onClick = onToggle,
        headlineContent = { Text(stringResource(R.string.show_password)) },
        leadingContent = {
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                // The row owns the toggle action and accessibility state.
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
        modifier = Modifier
            .padding(top = TvSpacing8)
            .fillMaxWidth()
            // A disabled tv ListItem still takes focus; skipping it keeps Down on the way to Continue.
            .focusProperties { canFocus = enabled }
            .semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(checked)
            },
    )
}
