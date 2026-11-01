package at.bernhardberger.tvhplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation

/**
 * OK opens a text field when it is released, and the whole press stays with the field. A field
 * that opened on the key down left the release to the keyboard it had just brought up, and Gboard
 * for TV took that release as its own action key and moved on to the next field.
 */
internal class FieldOpenPress {
    private var pressedKey: Key? = null

    /** Whether this event belongs to a press that opens the field. */
    fun handle(event: KeyEvent, isEditing: Boolean, open: () -> Unit): Boolean {
        if (isEditing || event.key !in FieldOpenKeys) return false
        return when (event.type) {
            KeyEventType.KeyDown -> {
                pressedKey = event.key
                true
            }

            KeyEventType.KeyUp -> {
                val pressedHere = pressedKey == event.key
                pressedKey = null
                if (pressedHere) open()
                pressedHere
            }

            else -> false
        }
    }

    fun cancel() {
        pressedKey = null
    }
}

private val FieldOpenKeys = setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

/**
 * A D-pad friendly text field for Android TV.
 *
 * The soft keyboard is intentionally NOT opened on focus: while navigating the
 * settings form with the D-pad the field is [readOnly] so the IME stays hidden.
 * Only when the user explicitly confirms a field (OK / center) does it enter the
 * editing state (editingId == id), become editable and show the keyboard.
 * Pressing Back, moving focus away or completing the IME action leaves editing. With
 * [onImeAction] the field keeps [keyboardOptions]' IME action and the caller decides where
 * editing goes next instead.
 */
@Composable
fun TvOutlinedTextField(
    id: String,
    editingId: String?,
    setEditingId: (String?) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    /** Inside the outline; null where the caller shows its label above the field. */
    label: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    presentationValue: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    onImeAction: (() -> Unit)? = null,
) {
    val isEditing = editingId == id
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val openPress = remember { FieldOpenPress() }
    val displayedValue = presentationValue.takeIf { !isEditing && value.isEmpty() } ?: value

    BackHandler(enabled = isEditing) { setEditingId(null) }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    OutlinedTextField(
        value = displayedValue,
        onValueChange = onValueChange,
        label = label,
        readOnly = !isEditing,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        singleLine = singleLine,
        keyboardOptions = if (onImeAction == null) keyboardOptions.copy(imeAction = ImeAction.Done) else keyboardOptions,
        visualTransformation = visualTransformation,
        keyboardActions = if (onImeAction == null) {
            KeyboardActions(onDone = { setEditingId(null) })
        } else {
            KeyboardActions(onAny = { onImeAction() })
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (!state.isFocused) openPress.cancel()
                // If we lose focus while editing (e.g. user navigates away), stop editing.
                if (!state.isFocused && isEditing) setEditingId(null)
            }
            .onPreviewKeyEvent { ev ->
                if (openPress.handle(ev, isEditing) { setEditingId(id) }) return@onPreviewKeyEvent true
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    // While not editing the read-only field would otherwise swallow
                    // vertical D-pad events, so drive focus navigation explicitly.
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
            }
    )
}
