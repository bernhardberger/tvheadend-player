package at.bernhardberger.tvhplayer.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvhplayer.ui.components.ConfirmationDialog
import at.bernhardberger.tvhplayer.ui.components.ConfirmationChoice
import at.bernhardberger.tvhplayer.ui.components.ConfirmationSubject

@Composable
internal fun AutomaticRecordingDeleteDialog(
    state: AutomaticRecordingEditorState, scheduledCount: Int, channels: List<Channel>,
) {
    ConfirmationDialog(
        question = stringResource(when (state.baseline.kind) {
            AutomaticRecordingKind.SERIES -> R.string.autorec_editor_delete_series
            AutomaticRecordingKind.TITLE -> R.string.autorec_editor_delete_title
            else -> R.string.autorec_editor_delete_keyword
        }),
        subject = ConfirmationSubject.Rule(R.drawable.ic_fiber_smart_record, state.baseline.pattern.words,
            listOf(stringResource(when (state.baseline.kind) {
                AutomaticRecordingKind.SERIES -> R.string.automatic_recording_kind_series
                AutomaticRecordingKind.TITLE -> R.string.automatic_recording_kind_title
                else -> R.string.automatic_recording_kind_keyword
            }), automaticRecordingChannelLabel(state.baseline, channels)).joinToString(" • ")),
        sentence = formatAutomaticRecordingDeleteBody(
            LocalContext.current, state.draft.kind, scheduledCount, state.changed,
        ),
        safe = ConfirmationChoice(stringResource(R.string.keep),
            R.drawable.ic_check, "automatic-recording-keep"),
        action = ConfirmationChoice(stringResource(R.string.context_delete),
            R.drawable.ic_delete_outlined, "automatic-recording-delete-confirm"),
        onDismiss = state::back, onConfirm = state::confirmDestructiveAction,
    )
}

internal fun formatAutomaticRecordingDeleteBody(
    context: Context,
    kind: AutomaticRecordingKind,
    scheduledCount: Int,
    changed: Boolean,
): String {
    val scheduled = if (scheduledCount == 0) "" else context.resources.getQuantityString(
        when (kind) {
            AutomaticRecordingKind.SERIES -> R.plurals.autorec_editor_delete_episodes
            AutomaticRecordingKind.TIMER -> R.plurals.timer_editor_delete_scheduled
            else -> R.plurals.autorec_editor_delete_programs
        },
        scheduledCount, scheduledCount,
    ) + " "
    return scheduled + context.getString(R.string.autorec_editor_delete_finished) +
        if (changed) " " + context.getString(R.string.autorec_editor_delete_unsaved) else ""
}
