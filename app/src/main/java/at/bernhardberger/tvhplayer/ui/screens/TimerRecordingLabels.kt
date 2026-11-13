package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.RecordingDraftField
import at.bernhardberger.tvhplayer.core.RecordingSubmitUnavailable
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate
import java.time.LocalDate

internal val timerMoreFields = setOf(
    RecordingDraftField.PRIORITY, RecordingDraftField.PROFILE,
    RecordingDraftField.PADDING_BEFORE, RecordingDraftField.PADDING_AFTER,
)
internal val timerChannelTimeFields = setOf(
    RecordingDraftField.CHANNEL, RecordingDraftField.DAYS, RecordingDraftField.DATE,
    RecordingDraftField.START, RecordingDraftField.END,
)

@Composable
internal fun timerMainLabel(state: TimerRecordingEditorState): String = stringResource(when {
    state.editing -> R.string.autorec_editor_save
    state.draft.repeating -> R.string.timer_editor_create
    else -> R.string.autorec_editor_record
})

@Composable
internal fun timerChannelLabel(state: TimerRecordingEditorState, channels: List<Channel>): String =
    channels.firstOrNull { it.id == state.draft.channelId }?.name ?: stringResource(
        if (state.draft.channelId == null) R.string.timer_editor_channel_not_chosen
        else R.string.autorec_editor_channel_unavailable,
    )

@Composable
internal fun timerNameLabel(state: TimerRecordingEditorState, channels: List<Channel>): String =
    state.draft.name.ifBlank {
        channels.firstOrNull { it.id == state.draft.channelId }?.name
            ?: stringResource(R.string.timer_editor_name_default)
    }

@Composable
internal fun timerDateLabel(state: TimerRecordingEditorState, date: LocalDate, relative: Boolean = false): String =
    when {
        relative && date == state.today -> stringResource(R.string.today)
        relative && date == state.today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> formatRecordingDate(date.atTime(12, 0).atZone(state.zone).toEpochSecond(),
            dateOnly = true, nowSec = state.now().epochSeconds)
    }

@Composable
internal fun timerRepeatLabel(state: TimerRecordingEditorState): String =
    if (!state.editing && state.draft.days.isEmpty()) stringResource(R.string.timer_editor_never)
    else automaticRecordingDaysLabel(state.draft.days)

@Composable
internal fun timerSpanLabel(state: TimerRecordingEditorState): String =
    (if (state.draft.repeating) timerRepeatLabel(state) else timerDateLabel(state, state.draft.date)) +
        " · ${formatClock(state.draft.start)} – ${formatClock(state.draft.end)}"

@Composable
internal fun timerEndLabel(state: TimerRecordingEditorState): String =
    if (state.draft.end < state.draft.start) stringResource(
        R.string.timer_editor_next_day_time, formatClock(state.draft.end),
    ) else formatClock(state.draft.end)

@Composable
internal fun timerUnavailableLabel(state: TimerRecordingEditorState): String? = when (state.mainProblem) {
    RecordingSubmitUnavailable.CHANNEL_REQUIRED -> stringResource(R.string.timer_editor_channel_required)
    RecordingSubmitUnavailable.REPEAT_REQUIRED -> stringResource(R.string.timer_editor_repeat_required)
    RecordingSubmitUnavailable.EMPTY_SPAN -> stringResource(R.string.timer_editor_empty_span)
    RecordingSubmitUnavailable.TIME_PASSED -> stringResource(R.string.timer_editor_time_passed)
    RecordingSubmitUnavailable.UNCHANGED -> stringResource(R.string.autorec_editor_unchanged)
    else -> null
}
