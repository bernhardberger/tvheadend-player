package at.bernhardberger.tvhplayer.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.components.CardPadding
import at.bernhardberger.tvhplayer.ui.components.CentredDialogShell
import at.bernhardberger.tvhplayer.ui.components.GuidedStepLayout
import at.bernhardberger.tvhplayer.ui.components.PlaybackCardWidth
import at.bernhardberger.tvhplayer.ui.components.PlaybackChoiceButton
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
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .recordingEditorKeys(navigation, onBack),
        ) {
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
        // The shell can release focus during its fade, before its dismissal restores the opener.
        if (it.isFocused && navigation.confirmation == null) navigation.focusedRow = id
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
    @DrawableRes mainIcon: Int,
    unavailableReason: String? = null,
) {
    val keep = remember { FocusRequester() }
    val main = remember { FocusRequester() }
    val discard = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; keep.requestFocus() }
    val question = stringResource(R.string.autorec_editor_leave_title)
    CentredDialogShell(paneTitle = question, onDismiss = onBack, verticalKeys = true) {
        Column(Modifier.width(PlaybackCardWidth).clip(RoundedCornerShape(16.dp))
            .background(TvSurfaceColors.container).testTag("centred-dialog-card").padding(CardPadding)
            .onPreviewKeyEvent { it.nativeKeyEvent.keyCode in listOf(
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            ) }) {
            Text(question, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))
            Text(unavailableReason ?: stringResource(R.string.autorec_editor_leave_body),
                style = MaterialTheme.typography.bodyLarge, color = TvTextColors.secondary)
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlaybackChoiceButton(stringResource(R.string.autorec_editor_keep_editing), R.drawable.ic_edit,
                    null, true, onBack, Modifier.testTag("${navigation.tagPrefix}-keep-editing")
                        .focusRequester(keep).focusProperties { up = FocusRequester.Cancel; down = main })
                PlaybackChoiceButton(mainLabel, mainIcon, null, false, onSubmit,
                    Modifier.testTag("${navigation.tagPrefix}-leave-main").focusRequester(main)
                        .focusProperties { up = keep; down = discard }, enabled = canSubmit)
                PlaybackChoiceButton(stringResource(R.string.autorec_editor_discard), R.drawable.ic_close,
                    null, false, onDiscard, Modifier.testTag("${navigation.tagPrefix}-discard")
                        .focusRequester(discard).focusProperties { up = main; down = FocusRequester.Cancel })
            }
        }
    }
}
