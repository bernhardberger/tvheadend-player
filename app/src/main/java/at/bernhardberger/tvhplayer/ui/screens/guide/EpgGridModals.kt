package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ProgrammeAction

@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
@Composable
internal fun ConfirmProgrammeActionDialog(
    action: ProgrammeAction,
    programmeTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    channelName: String = "",
    dateAndTime: String = "",
    entries: at.bernhardberger.tvheadend.sdk.core.DvrEventEntries? = null,
    configurations: List<DvrConfiguration> = emptyList(),
    visibility: androidx.compose.animation.core.MutableTransitionState<Boolean> = remember {
        androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true }
    },
) {
    val target = programmeConfirmationTarget(entries, configurations,
        stringResource(R.string.recording_profile_default))
    val message = stringResource(if (action == ProgrammeAction.STOP_RECORDING) R.string.stop_recording_confirm_message else R.string.cancel_recording_confirm_message,
        programmeTitle, channelName, dateAndTime)
    at.bernhardberger.tvhplayer.ui.components.CentredDialog(
        visibility = visibility,
        title = stringResource(if (action == ProgrammeAction.STOP_RECORDING) R.string.stop_recording_confirm_title else R.string.cancel_recording_confirm_title),
        message = if (target == null) message else "$message ${stringResource(target.first, target.second)}",
        firstLabel = stringResource(R.string.keep_recording),
        secondLabel = programmeActionLabel(action),
        onFirst = onDismiss,
        onSecond = onConfirm,
        onDismiss = onDismiss,
        onPreviewKeyEvent = onPreviewKeyEvent,
    )
}

@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
internal fun programmeConfirmationTarget(
    entries: at.bernhardberger.tvheadend.sdk.core.DvrEventEntries?,
    configurations: List<DvrConfiguration>,
    defaultProfileLabel: String,
): Pair<Int, String>? {
    if (entries == null || entries.otherActive.isEmpty()) return null
    val selected = entries.selected
    val profileId = selected.configId?.value?.takeIf(String::isNotBlank)
    if (entries.otherActive.any { it.configId?.value?.takeIf(String::isNotBlank) != profileId }) {
        val name = configurations.firstOrNull { it.id == selected.configId }?.name?.takeIf(String::isNotBlank)
            ?: defaultProfileLabel
        return R.string.recording_confirmation_profile to name
    }
    val owner = selected.owner?.takeIf(String::isNotBlank) ?: selected.creator?.takeIf(String::isNotBlank)
        ?: return null
    return R.string.recording_confirmation_owner to owner
}

@Composable
internal fun programmeActionLabel(action: ProgrammeAction): String = stringResource(
    when (action) {
        ProgrammeAction.WATCH -> R.string.watch
        ProgrammeAction.RECORD -> R.string.record
        ProgrammeAction.RECORD_SERIES -> R.string.context_record_series
        ProgrammeAction.RECORD_TITLE -> R.string.context_record_title
        ProgrammeAction.CANCEL_RECORDING -> R.string.cancel_recording
        ProgrammeAction.STOP_RECORDING -> R.string.stop_recording
        ProgrammeAction.WATCH_FROM_START -> R.string.watch_from_start
    }
)
