package at.bernhardberger.tvhplayer.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.components.CentredDialog
import at.bernhardberger.tvhplayer.ui.components.GuidedStepLayout
import at.bernhardberger.tvhplayer.ui.components.backKeyCycleFocus
import at.bernhardberger.tvhplayer.ui.components.rememberBackKeyCycle

@Composable
internal fun RecordingEditorSurface(
    navigation: RecordingEditorNavigation,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    guidance: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = navigation.confirmation == null, onBack = onBack)
    key(navigation.page.level) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .recordingEditorKeys(navigation, onBack)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                GuidedStepLayout(guidance = guidance, actions = actions)
            }
        }
    }
}

@Composable
private fun Modifier.recordingEditorKeys(navigation: RecordingEditorNavigation, onBack: () -> Unit): Modifier {
    val back = rememberBackKeyCycle(navigation.confirmation == null)
    val left = rememberBackKeyCycle(navigation.confirmation == null)
    var openingRelease by remember { mutableStateOf(true) }
    return backKeyCycleFocus(back, left).onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        val down = key.action == KeyEvent.ACTION_DOWN
        if (down && key.repeatCount == 0) openingRelease = false
        when {
            navigation.confirmation != null -> false
            navigation.textEntryEditing && key.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> false
            key.keyCode == KeyEvent.KEYCODE_BACK -> back.handle(
                down, key.repeatCount, key.isCanceled, onBack = onBack,
            )
            key.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && navigation.pages.size > 1 -> left.handle(
                down, key.repeatCount, key.isCanceled, onBack = onBack,
            )
            openingRelease && key.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER) -> true
            else -> false
        }
    }
}

@Composable
internal fun editorRowModifier(navigation: RecordingEditorNavigation, id: String): Modifier {
    val requester = remember { FocusRequester() }
    val restoreFocus = navigation.focusedRow == id && navigation.confirmation == null
    LaunchedEffect(navigation.focusRevision) {
        if (restoreFocus) {
            withFrameNanos { }
            requester.requestFocus()
        }
    }
    return Modifier.testTag("${navigation.tagPrefix}-$id").focusRequester(requester).onFocusChanged {
        if (it.isFocused) navigation.focusedRow = id
    }
}

@Composable
internal fun RecordingEditorLeaveDialog(
    navigation: RecordingEditorNavigation,
    mainLabel: String,
    canSubmit: Boolean,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onDiscard: () -> Unit,
) {
    val keep = remember { FocusRequester() }
    val main = remember { FocusRequester() }
    val discard = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; keep.requestFocus() }
    CentredDialog(
        title = stringResource(R.string.autorec_editor_leave_title),
        text = stringResource(R.string.autorec_editor_leave_body), onDismiss = onBack, onBack = onBack,
        actions = {
            Button(onClick = onBack, modifier = Modifier.testTag("${navigation.tagPrefix}-keep-editing")
                .focusRequester(keep).focusProperties { left = FocusRequester.Cancel; right = main }) {
                Text(stringResource(R.string.autorec_editor_keep_editing))
            }
            OutlinedButton(onClick = onSubmit, enabled = canSubmit,
                modifier = Modifier.focusRequester(main).focusProperties { left = keep; right = discard }) {
                Text(mainLabel)
            }
            OutlinedButton(onClick = onDiscard,
                modifier = Modifier.testTag("${navigation.tagPrefix}-discard")
                .focusRequester(discard).focusProperties { left = main; right = FocusRequester.Cancel }) {
                Text(stringResource(R.string.autorec_editor_discard))
            }
        },
    )
}
