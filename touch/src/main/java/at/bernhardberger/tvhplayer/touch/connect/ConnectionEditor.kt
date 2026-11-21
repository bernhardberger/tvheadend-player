package at.bernhardberger.tvhplayer.touch.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.window.core.layout.WindowSizeClass
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionEditor(
    state: ConnectionUiState,
    original: ConnectionFields,
    onFieldsChange: (ConnectionFields) -> Unit,
    onSave: (ConnectionFields) -> Unit,
    onCancel: () -> Unit,
    focusPassword: Boolean = false,
) {
    val compact = !currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // A save being written can't be left: the connection may already be stored.
    val close = {
        when {
            state.saving -> Unit
            state.fields == original -> onCancel()
            else -> confirmDiscard = true
        }
    }
    BasicAlertDialog(
        onDismissRequest = close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = !compact,
            dismissOnClickOutside = false,
        ),
    ) {
        ConnectionEditorContent(state, compact, onFieldsChange, onSave, close, focusPassword)
    }
    if (confirmDiscard && !state.saving) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(SharedR.string.autorec_editor_discard)) },
            text = { Text(stringResource(SharedR.string.autorec_editor_leave_body)) },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(SharedR.string.cancel)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onCancel()
                }) { Text(stringResource(SharedR.string.autorec_editor_discard)) }
            },
        )
    }
}

@Composable
private fun ConnectionEditorContent(
    state: ConnectionUiState,
    compact: Boolean,
    onFieldsChange: (ConnectionFields) -> Unit,
    onSave: (ConnectionFields) -> Unit,
    onClose: () -> Unit,
    focusPassword: Boolean,
) {
    val focus = LocalFocusManager.current
    val save = {
        focus.clearFocus()
        onSave(state.fields)
    }
    Surface(
        modifier = if (compact) Modifier.fillMaxSize() else Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        shape = if (compact) RectangleShape else MaterialTheme.shapes.extraLarge,
        color = if (compact) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            if (compact) Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            else Modifier.padding(24.dp),
        ) {
            if (compact) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConnectionIconButton(
                        R.drawable.connect_close_outline,
                        SharedR.string.close,
                        enabled = !state.saving,
                        onClick = onClose,
                    )
                    EditorTitle(Modifier.weight(1f).padding(start = 4.dp), compact = true)
                    EditorSave(state.phase, save, compact = true)
                }
            } else {
                EditorTitle(Modifier, compact = false)
            }
            ConnectionForm(
                state,
                onFieldsChange,
                compact,
                showSignInHelp = true,
                focusPassword = focusPassword,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = if (compact) 24.dp else 0.dp, vertical = if (compact) 12.dp else 8.dp),
            )
            if (!compact) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onClose, enabled = !state.saving) {
                        Text(stringResource(SharedR.string.cancel))
                    }
                    EditorSave(state.phase, save, compact = false)
                }
            }
        }
    }
}

@Composable
private fun EditorTitle(modifier: Modifier, compact: Boolean) {
    Text(
        stringResource(SharedR.string.setup_connection),
        modifier.semantics { heading() },
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
    )
}

@Composable
private fun EditorSave(phase: ConnectionPhase, onSave: () -> Unit, compact: Boolean) {
    if (phase is ConnectionPhase.Connecting) {
        ConnectAction(phase, onSave)
    } else {
        TextButton(onClick = onSave) {
            Text(
                stringResource(
                    if (compact) SharedR.string.server_name_save else SharedR.string.onboarding_save_connection,
                ),
            )
        }
    }
}
