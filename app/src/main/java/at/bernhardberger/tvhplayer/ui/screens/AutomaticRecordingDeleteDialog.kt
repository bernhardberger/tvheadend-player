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
    RecordingRuleDeleteDialog(
        kind = state.baseline.kind,
        subject = ConfirmationSubject.Rule(R.drawable.ic_fiber_smart_record, state.baseline.pattern.words,
            listOf(stringResource(when (state.baseline.kind) {
                AutomaticRecordingKind.SERIES -> R.string.automatic_recording_kind_series
                AutomaticRecordingKind.TITLE -> R.string.automatic_recording_kind_title
                else -> R.string.automatic_recording_kind_keyword
            }), automaticRecordingChannelLabel(state.baseline, channels)).joinToString(" • ")),
        scheduledCount = scheduledCount, changed = state.changed,
        onDismiss = state::back, onConfirm = state::confirmDestructiveAction,
    )
}

@Composable
internal fun RecordingRuleDeleteDialog(
    kind: AutomaticRecordingKind,
    subject: ConfirmationSubject.Rule,
    scheduledCount: Int,
    changed: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val prefix = if (kind == AutomaticRecordingKind.TIMER) "timer-recording" else "automatic-recording"
    ConfirmationDialog(
        question = stringResource(when (kind) {
            AutomaticRecordingKind.SERIES -> R.string.autorec_editor_delete_series
            AutomaticRecordingKind.TITLE -> R.string.autorec_editor_delete_title
            AutomaticRecordingKind.KEYWORD -> R.string.autorec_editor_delete_keyword
            AutomaticRecordingKind.TIMER -> R.string.timer_editor_delete
        }),
        subject = subject,
        sentence = formatAutomaticRecordingDeleteBody(LocalContext.current, kind, scheduledCount, changed),
        safe = ConfirmationChoice(stringResource(R.string.keep),
            R.drawable.ic_check, "$prefix-keep"),
        action = ConfirmationChoice(stringResource(R.string.context_delete),
            R.drawable.ic_delete_outlined, "$prefix-delete-confirm"),
        onDismiss = onDismiss, onConfirm = onConfirm,
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
