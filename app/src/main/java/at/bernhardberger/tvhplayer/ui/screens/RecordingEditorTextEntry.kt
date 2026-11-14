package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField

@Composable
internal fun RecordingEditorTextEntry(
    navigation: RecordingEditorNavigation,
    initialValue: String,
    label: String,
    id: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(initialValue) }
    val keyboard = LocalSoftwareKeyboardController.current
    val commit = {
        keyboard?.hide()
        onCommit(value)
    }
    TvOutlinedTextField(
        id = id, editingId = id.takeIf { navigation.textEntryEditing },
        setEditingId = { navigation.textEntryEditing = it != null },
        value = value, onValueChange = { value = it },
        label = { Text(label) },
        modifier = modifier.then(editorRowModifier(navigation, id)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), onImeAction = commit,
    )
    GuidedStepAction(
        stringResource(R.string.autorec_editor_done), commit, editorRowModifier(navigation, "done"),
    )
}
