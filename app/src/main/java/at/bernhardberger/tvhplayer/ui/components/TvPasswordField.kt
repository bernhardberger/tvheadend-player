package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R

/**
 * Password counterpart of [TvOutlinedTextField]: keeps the soft keyboard hidden
 * while navigating with the D-pad (the field is [readOnly] until confirmed with
 * OK) and exposes the visibility toggle as a D-pad reachable trailing icon.
 */
@Composable
fun TvPasswordField(
    id: String,
    editingId: String?,
    setEditingId: (String?) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    presentationValue: String? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val textFocus = remember { FocusRequester() }
    val eyeFocus = remember { FocusRequester() }

    var passwordVisible by remember { mutableStateOf(false) }
    var consumeBackKeyUp by remember { mutableStateOf(false) }

    val isEditing = editingId == id
    val displayedPresentation = presentationValue.takeIf { !isEditing && value.isEmpty() }
    val displayedValue = displayedPresentation ?: value

    LaunchedEffect(isEditing) {
        if (isEditing) {
            textFocus.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    val icon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
    val desc =
        stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password)

    OutlinedTextField(
        value = displayedValue,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.password)) },
        singleLine = true,
        readOnly = !isEditing,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { setEditingId(null) }),

        visualTransformation = if (displayedPresentation != null || passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },

        modifier = modifier
            .focusRequester(textFocus)
            .onFocusChanged { state ->
                if (!state.isFocused && isEditing) setEditingId(null)
            }
            .onPreviewKeyEvent { ev ->
                if (
                    ev.key == Key.Back &&
                    ev.type == KeyEventType.KeyUp &&
                    consumeBackKeyUp
                ) {
                    consumeBackKeyUp = false
                    return@onPreviewKeyEvent true
                }
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (!isEditing) {
                            setEditingId(id)
                            true
                        } else false
                    }

                    Key.Back -> {
                        if (isEditing) {
                            consumeBackKeyUp = true
                            setEditingId(null)
                            true
                        } else false
                    }

                    Key.DirectionRight -> {
                        if (!isEditing) {
                            eyeFocus.requestFocus()
                            true
                        } else false
                    }

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

        trailingIcon = {
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                interactionSource = remember { MutableInteractionSource() },
                modifier = Modifier
                    .focusRequester(eyeFocus)
                    .onKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false

                        when (ev.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                passwordVisible = !passwordVisible
                                true
                            }

                            Key.DirectionLeft -> {
                                textFocus.requestFocus()
                                true
                            }

                            Key.Back -> {
                                if (isEditing) {
                                    setEditingId(null)
                                    true
                                } else false
                            }

                            else -> false
                        }
                    }
            ) {
                Icon(imageVector = icon, contentDescription = desc)
            }
        }
    )
}
