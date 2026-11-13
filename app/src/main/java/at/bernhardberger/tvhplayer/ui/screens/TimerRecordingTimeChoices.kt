package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Checkbox
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.timerHourChoices
import at.bernhardberger.tvhplayer.core.timerMinuteChoices
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import java.time.DayOfWeek
import java.time.LocalTime

private const val DATE_CHOICES_COUNT = 14

@Composable
internal fun TimerRecordingChannelTime(state: TimerRecordingEditorState, channels: List<Channel>) {
    GuidedStepAction(stringResource(R.string.autorec_editor_channel),
        { state.navigation.open(AutomaticRecordingEditorLevel.CHANNEL,
            "channel-${state.draft.channelId?.value ?: channels.firstOrNull()?.id?.value}") },
        editorRowModifier(state.navigation, "channel"), subtitle = timerChannelLabel(state.draft, channels),
        trailingIcon = R.drawable.ic_keyboard_arrow_right)
    GuidedStepAction(stringResource(R.string.timer_editor_repeat),
        { state.navigation.open(AutomaticRecordingEditorLevel.REPEAT, "done") },
        editorRowModifier(state.navigation, "repeat"), subtitle = timerRepeatLabel(state),
        trailingIcon = R.drawable.ic_keyboard_arrow_right)
    if (!state.draft.repeating) GuidedStepAction(stringResource(R.string.timer_editor_date),
        { state.navigation.open(AutomaticRecordingEditorLevel.DATE, "date-${state.draft.date}") },
        editorRowModifier(state.navigation, "date"),
        subtitle = timerDateLabel(state, state.draft.date, relative = true),
        trailingIcon = R.drawable.ic_keyboard_arrow_right)
    GuidedStepAction(stringResource(R.string.timer_editor_start), { state.openClock(false) },
        editorRowModifier(state.navigation, "start"), subtitle = formatClock(state.draft.start),
        trailingIcon = R.drawable.ic_keyboard_arrow_right)
    GuidedStepAction(stringResource(R.string.timer_editor_end), { state.openClock(true) },
        editorRowModifier(state.navigation, "end"), subtitle = timerEndLabel(state),
        trailingIcon = R.drawable.ic_keyboard_arrow_right)
}

@Composable
internal fun TimerRecordingRepeat(state: TimerRecordingEditorState) {
    GuidedStepAction(stringResource(R.string.autorec_editor_done), state::back,
        editorRowModifier(state.navigation, "done"))
    val never = state.draft.days.isEmpty()
    if (!state.editing) GuidedStepAction(
        stringResource(R.string.timer_editor_never),
        { state.updateDraft { it.copy(days = emptySet()) } },
        editorRowModifier(state.navigation, "never").semantics {
            role = Role.Checkbox; toggleableState = ToggleableState(never)
        },
        trailingContent = {
            RecordingEditorCheckboxMark(never)
        },
    )
    for (day in DayOfWeek.entries) RecordingEditorDay(
        state.navigation, day, day in state.draft.days,
    ) {
        state.updateDraft { it.copy(days = if (day in it.days) it.days - day else it.days + day) }
    }
}

@Composable
internal fun TimerRecordingDates(state: TimerRecordingEditorState) {
    val dates = (0 until DATE_CHOICES_COUNT).map { state.today.plusDays(it.toLong()) }
    if (state.draft.date !in dates) RecordingEditorRadio(
        state.navigation, "date-${state.draft.date}",
        stringResource(R.string.autorec_editor_current, timerDateLabel(state, state.draft.date)),
        true, state::back,
    )
    for (date in dates) RecordingEditorRadio(state.navigation, "date-$date",
        timerDateLabel(state, date, relative = true), state.draft.date == date,
        { state.applyChoice { it.copy(date = date) } })
}

@Composable
internal fun TimerRecordingHours(state: TimerRecordingEditorState) {
    val current = state.currentTime
    if (current.minute % 5 != 0) RecordingEditorRadio(
        state.navigation, "current-time", stringResource(R.string.autorec_editor_current, formatClock(current)),
        true, { state.navigation.returnTo(AutomaticRecordingEditorLevel.CHANNEL_TIME) },
    )
    for (hour in state.draft.timerHourChoices(state.ending)) {
        if (state.ending && hour == 0 && state.draft.start.hour != 0) RecordingEditorSecondary(
            if (state.draft.repeating) stringResource(R.string.timer_editor_next_day)
            else stringResource(R.string.timer_editor_next_day_date,
                timerDateLabel(state, state.draft.date.plusDays(1))),
            Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        )
        RecordingEditorHour(state.navigation, hour, { state.openMinutes(hour) },
            enabled = state.draft.timerMinuteChoices(hour, state.ending).isNotEmpty())
    }
}

@Composable
internal fun TimerRecordingMinutes(state: TimerRecordingEditorState) {
    val current = state.currentTime
    if (current.hour == state.selectedHour && current.minute % 5 != 0) RecordingEditorRadio(
        state.navigation, "current-minute",
        stringResource(R.string.autorec_editor_current, formatClock(current)), true,
        { state.navigation.returnTo(AutomaticRecordingEditorLevel.CHANNEL_TIME) },
    )
    for (time in state.draft.timerMinuteChoices(state.selectedHour, state.ending)) RecordingEditorRadio(
        state.navigation, "minute-${time.minute}", formatClock(time), current == time,
        { state.applyChoice { if (state.ending) it.copy(end = time) else it.copy(start = time) } },
    )
}
