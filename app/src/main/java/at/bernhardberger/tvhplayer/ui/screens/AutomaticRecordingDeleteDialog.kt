package at.bernhardberger.tvhplayer.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.ui.components.CentredDialog

@Composable
internal fun AutomaticRecordingDeleteDialog(state: AutomaticRecordingEditorState, scheduledCount: Int) {
    CentredDialog(
        title = stringResource(when (state.draft.kind) {
            AutomaticRecordingKind.SERIES -> R.string.autorec_editor_delete_series
            AutomaticRecordingKind.TITLE -> R.string.autorec_editor_delete_title
            else -> R.string.autorec_editor_delete_keyword
        }),
        message = formatAutomaticRecordingDeleteBody(
            LocalContext.current, state.draft.kind, scheduledCount, state.changed,
        ),
        firstLabel = stringResource(R.string.autorec_editor_keep_recording),
        secondLabel = stringResource(R.string.autorec_editor_delete),
        onFirst = state::back, onSecond = state::confirmDestructiveAction, onDismiss = state::back,
        firstTag = "automatic-recording-keep", secondTag = "automatic-recording-delete-confirm",
    )
}

internal fun formatAutomaticRecordingDeleteBody(
    context: Context,
    kind: AutomaticRecordingKind,
    scheduledCount: Int,
    changed: Boolean,
): String {
    val scheduled = if (scheduledCount == 0) "" else context.resources.getQuantityString(
        if (kind == AutomaticRecordingKind.SERIES) R.plurals.autorec_editor_delete_episodes
            else R.plurals.autorec_editor_delete_programs,
        scheduledCount, scheduledCount,
    ) + " "
    return scheduled + context.getString(R.string.autorec_editor_delete_finished) +
        if (changed) " " + context.getString(R.string.autorec_editor_delete_unsaved) else ""
}
