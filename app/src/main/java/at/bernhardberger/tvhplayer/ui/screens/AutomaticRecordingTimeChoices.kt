package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.tv.material3.Checkbox
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.core.RecordingStartWindow
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun AutomaticRecordingTimeChoices(state: AutomaticRecordingEditorState, channels: List<Channel>) {
    when (state.navigation.page.level) {
        AutomaticRecordingEditorLevel.CHANNEL_TIME -> AutomaticRecordingChannelTime(state, channels)
        AutomaticRecordingEditorLevel.CHANNEL -> AutomaticRecordingChannels(state, channels)
        AutomaticRecordingEditorLevel.DAYS -> AutomaticRecordingDays(state)
        AutomaticRecordingEditorLevel.START_WINDOW -> AutomaticRecordingStartWindow(state)
        AutomaticRecordingEditorLevel.AFTER_HOUR, AutomaticRecordingEditorLevel.BEFORE_HOUR ->
            AutomaticRecordingHours(state)
        AutomaticRecordingEditorLevel.AFTER_MINUTE, AutomaticRecordingEditorLevel.BEFORE_MINUTE ->
            AutomaticRecordingMinutes(state)
        AutomaticRecordingEditorLevel.KEEP -> RecordingEditorKeep(
            state.navigation, state.draft.removal, state.baseline.removal,
            { value -> state.applyChoice { it.copy(removal = value) } }, state::back,
        )
        else -> Unit
    }
}

@Composable
private fun AutomaticRecordingChannelTime(state: AutomaticRecordingEditorState, channels: List<Channel>) {
    GuidedStepAction(
        stringResource(R.string.autorec_editor_channel),
        { state.navigation.open(AutomaticRecordingEditorLevel.CHANNEL, channelRowId(state.draft.channel)) },
        editorRowModifier(state.navigation, "channel"),
        subtitle = automaticRecordingChannelLabel(state.draft, channels),
        trailingIcon = R.drawable.ic_keyboard_arrow_right,
    )
    GuidedStepAction(
        stringResource(R.string.autorec_editor_days),
        { state.navigation.open(AutomaticRecordingEditorLevel.DAYS, "done") },
        editorRowModifier(state.navigation, "days"), subtitle = automaticRecordingDaysLabel(state.draft.days),
        trailingIcon = R.drawable.ic_keyboard_arrow_right,
    )
    GuidedStepAction(
        stringResource(R.string.autorec_editor_start_window),
        {
            state.navigation.open(AutomaticRecordingEditorLevel.START_WINDOW, startWindowRowId(state.draft.startWindow))
        },
        editorRowModifier(state.navigation, "start-window"),
        subtitle = automaticRecordingWindowLabel(state.draft.startWindow),
        trailingIcon = R.drawable.ic_keyboard_arrow_right,
    )
}

private fun channelRowId(channel: RecordingRuleChannel): String =
    "channel-${(channel as? RecordingRuleChannel.SpecificChannel)?.channelId?.value ?: "all"}"

@Composable
private fun AutomaticRecordingChannels(state: AutomaticRecordingEditorState, channels: List<Channel>) {
    RecordingEditorChannels(
        state.navigation, channels, (state.draft.channel as? RecordingRuleChannel.SpecificChannel)?.channelId,
        { value -> state.applyChoice { it.copy(channel = RecordingRuleChannel.SpecificChannel(value)) } },
        state::back,
        onAll = { state.applyChoice { it.copy(channel = RecordingRuleChannel.AllChannels) } },
    )
}

@Composable
private fun AutomaticRecordingDays(state: AutomaticRecordingEditorState) {
    val unrestricted = DayOfWeek.entries.toSet()
    val anyDay = state.draft.days == unrestricted
    GuidedStepAction(
        stringResource(R.string.autorec_editor_done), state::back, editorRowModifier(state.navigation, "done"),
    )
    GuidedStepAction(
        stringResource(R.string.autorec_editor_any_day),
        { state.updateDraft { it.copy(days = unrestricted) } },
        editorRowModifier(state.navigation, "any-day").semantics {
            role = Role.Checkbox; toggleableState = ToggleableState(anyDay)
        },
        trailingContent = {
            Checkbox(anyDay, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
        },
    )
    for (day in DayOfWeek.entries) {
        val checked = day in state.draft.days && state.draft.days.size != DayOfWeek.entries.size
        RecordingEditorDay(state.navigation, day, checked) {
                state.updateDraft {
                    val days = it.days.takeUnless { days -> days.size == DayOfWeek.entries.size }.orEmpty()
                    val changed = if (day in days) days - day else days + day
                    it.copy(days = changed.takeUnless { days -> days.isEmpty() || days.size == 7 } ?: unrestricted)
                }
        }
    }
}

private fun startWindowRowId(window: RecordingStartWindow): String = when (window) {
    RecordingStartWindow.AnyTime -> "any-time"
    is RecordingStartWindow.After -> "after"
    is RecordingStartWindow.Before -> "before"
    is RecordingStartWindow.Stored -> "current-window"
}

@Composable
private fun AutomaticRecordingStartWindow(state: AutomaticRecordingEditorState) {
    val current = state.draft.startWindow
    if (current is RecordingStartWindow.Stored) RecordingEditorRadio(
        state.navigation, "current-window", automaticRecordingWindowLabel(current), true, state::back,
    )
    RecordingEditorRadio(state.navigation, "any-time", stringResource(R.string.autorec_editor_any_time),
        current == RecordingStartWindow.AnyTime,
        { state.applyChoice { it.copy(startWindow = RecordingStartWindow.AnyTime) } })
    for (after in listOf(true, false)) {
        GuidedStepAction(
            stringResource(if (after) R.string.autorec_editor_after else R.string.autorec_editor_before),
            {
                val time = when (current) {
                    is RecordingStartWindow.After -> current.time
                    is RecordingStartWindow.Before -> current.time
                    else -> LocalTime.MIDNIGHT
                }
                state.selectedHour = time.hour
                state.navigation.open(if (after) AutomaticRecordingEditorLevel.AFTER_HOUR else
                    AutomaticRecordingEditorLevel.BEFORE_HOUR, "hour-${time.hour}")
            },
            editorRowModifier(state.navigation, if (after) "after" else "before"),
            trailingIcon = R.drawable.ic_keyboard_arrow_right,
            subtitle = when {
                after && current is RecordingStartWindow.After -> formatClock(current.time)
                !after && current is RecordingStartWindow.Before -> formatClock(current.time)
                else -> null
            },
        )
    }
}

@Composable
private fun AutomaticRecordingHours(state: AutomaticRecordingEditorState) {
    for (hour in 0..23) RecordingEditorHour(state.navigation, hour, {
            state.selectedHour = hour
            val minute = when (val window = state.draft.startWindow) {
                is RecordingStartWindow.After -> window.time.minute
                is RecordingStartWindow.Before -> window.time.minute
                else -> 0
            }
            state.navigation.open(if (state.navigation.page.level == AutomaticRecordingEditorLevel.AFTER_HOUR)
                AutomaticRecordingEditorLevel.AFTER_MINUTE else AutomaticRecordingEditorLevel.BEFORE_MINUTE,
                "minute-$minute")
        })
}

@Composable
private fun AutomaticRecordingMinutes(state: AutomaticRecordingEditorState) {
    val after = state.navigation.page.level == AutomaticRecordingEditorLevel.AFTER_MINUTE
    for (minute in 0..55 step 5) {
        val time = LocalTime.of(state.selectedHour, minute)
        val window = if (after) RecordingStartWindow.After(time) else RecordingStartWindow.Before(time)
        RecordingEditorRadio(state.navigation, "minute-$minute", formatClock(time),
            state.draft.startWindow == window, { state.applyChoice { it.copy(startWindow = window) } })
    }
}
