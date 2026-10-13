package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R

internal enum class PendingRecordingAction { STOP, CANCEL, DELETE }

internal enum class RecordingDetailsAction { RESUME, BEGINNING, PLAY, STOP, CANCEL, DELETE }

@Composable
internal fun RecordingConfirmationDialog(
    action: PendingRecordingAction,
    title: String,
    backEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    channelName: String = "",
    dateAndTime: String = "",
) {
    at.bernhardberger.tvhplayer.ui.components.TwoColumnDialog(
        title = stringResource(when (action) {
            PendingRecordingAction.STOP -> R.string.stop_recording_confirm_title
            PendingRecordingAction.CANCEL -> R.string.cancel_recording_confirm_title
            PendingRecordingAction.DELETE -> R.string.delete_recording_confirm_title
        }),
        message = stringResource(when (action) {
            PendingRecordingAction.STOP -> R.string.stop_recording_confirm_message
            PendingRecordingAction.CANCEL -> R.string.cancel_recording_confirm_message
            PendingRecordingAction.DELETE -> R.string.delete_recording_confirm_message
        }, title, channelName, dateAndTime),
        firstLabel = stringResource(if (action == PendingRecordingAction.DELETE) R.string.keep else R.string.keep_recording),
        secondLabel = stringResource(when (action) {
            PendingRecordingAction.STOP -> R.string.stop_recording
            PendingRecordingAction.CANCEL -> R.string.cancel_recording
            PendingRecordingAction.DELETE -> R.string.delete_recording
        }),
        onFirst = onDismiss, onSecond = onConfirm, onDismiss = onDismiss,
        backEnabled = backEnabled,
        firstTag = "recording-confirmation-back", secondTag = "recording-confirmation-confirm",
    )
}
