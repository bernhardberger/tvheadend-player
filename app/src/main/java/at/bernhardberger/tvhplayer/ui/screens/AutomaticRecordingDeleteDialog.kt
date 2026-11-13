package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.ui.components.CentredDialog

@Composable
internal fun AutomaticRecordingDeleteDialog(state: AutomaticRecordingEditorState, scheduledCount: Int) {
    CentredDialog(
        title = stringResource(R.string.autorec_editor_delete_title),
        message = pluralStringResource(
            if (state.draft.kind == AutomaticRecordingKind.SERIES) R.plurals.autorec_editor_delete_episodes
                else R.plurals.autorec_editor_delete_programs,
            scheduledCount, scheduledCount,
        ),
        secondaryText = if (state.changed) stringResource(R.string.autorec_editor_delete_unsaved) else null,
        firstLabel = stringResource(R.string.autorec_editor_keep_recording),
        secondLabel = stringResource(R.string.autorec_editor_delete),
        onFirst = state::back, onSecond = state::confirmDestructiveAction, onDismiss = state::back,
        firstTag = "automatic-recording-keep", secondTag = "automatic-recording-delete-confirm",
    )
}
