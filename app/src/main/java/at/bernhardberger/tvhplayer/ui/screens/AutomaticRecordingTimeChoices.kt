package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Checkbox
import androidx.tv.material3.RadioButton
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.core.AutorecDraft
import at.bernhardberger.tvhplayer.core.RecordingStartWindow
import at.bernhardberger.tvhplayer.core.orderBrowseChannels
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.PiconBox
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
        AutomaticRecordingEditorLevel.KEEP -> AutomaticRecordingKeep(state)
        else -> Unit
    }
}

@Composable
private fun AutomaticRecordingChannelTime(state: AutomaticRecordingEditorState, channels: List<Channel>) {
    GuidedStepAction(
        stringResource(R.string.autorec_editor_channel),
        { state.navigation.open(AutomaticRecordingEditorLevel.CHANNEL, channelRowId(state.draft.channel)) },
        editorRowModifier(state.navigation, "channel"), subtitle = automaticRecordingChannelLabel(state.draft, channels),
        trailingIcon = R.drawable.ic_keyboard_arrow_right,
    )
    GuidedStepAction(
        stringResource(R.string.autorec_editor_days), { state.navigation.open(AutomaticRecordingEditorLevel.DAYS, "done") },
        editorRowModifier(state.navigation, "days"), subtitle = automaticRecordingDaysLabel(state.draft.days),
        trailingIcon = R.drawable.ic_keyboard_arrow_right,
    )
    GuidedStepAction(
        stringResource(R.string.autorec_editor_start_window),
        { state.navigation.open(AutomaticRecordingEditorLevel.START_WINDOW, startWindowRowId(state.draft.startWindow)) },
        editorRowModifier(state.navigation, "start-window"), subtitle = automaticRecordingWindowLabel(state.draft.startWindow),
        trailingIcon = R.drawable.ic_keyboard_arrow_right,
    )
}

private fun channelRowId(channel: RecordingRuleChannel): String =
    "channel-${(channel as? RecordingRuleChannel.SpecificChannel)?.channelId?.value ?: "all"}"

@Composable
private fun AutomaticRecordingChannels(state: AutomaticRecordingEditorState, channels: List<Channel>) {
    val all = RecordingRuleChannel.AllChannels
    AutomaticRecordingRadio(state, channelRowId(all), stringResource(R.string.all_channels), state.draft.channel == all,
        { state.applyChoice { it.copy(channel = all) } })
    val stored = state.draft.channel as? RecordingRuleChannel.SpecificChannel
    if (stored != null && channels.none { it.id == stored.channelId }) AutomaticRecordingRadio(
        state, channelRowId(stored),
        stringResource(R.string.autorec_editor_current, stringResource(R.string.autorec_editor_channel_unavailable)),
        true, state::back,
    )
    for (channel in orderBrowseChannels(channels)) {
        val value = RecordingRuleChannel.SpecificChannel(channel.id)
        AutomaticRecordingRadio(
            state, channelRowId(value), channel.name.orEmpty(), state.draft.channel == value,
            { state.applyChoice { it.copy(channel = value) } },
            leadingIcon = { PiconBox(channel.icon, Modifier.size(40.dp, 28.dp)) },
        )
    }
}

@Composable
private fun AutomaticRecordingDays(state: AutomaticRecordingEditorState) {
    val unrestricted = DayOfWeek.entries.toSet()
    val anyDay = state.draft.days == unrestricted
    GuidedStepAction(stringResource(R.string.autorec_editor_done), state::back, editorRowModifier(state.navigation, "done"))
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
        GuidedStepAction(
            day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            {
                state.updateDraft {
                    val days = it.days.takeUnless { days -> days.size == DayOfWeek.entries.size }.orEmpty()
                    val changed = if (day in days) days - day else days + day
                    it.copy(days = changed.takeUnless { days -> days.isEmpty() || days.size == 7 } ?: unrestricted)
                }
            },
            editorRowModifier(state.navigation, "day-${day.value}").semantics {
                role = Role.Checkbox; toggleableState = ToggleableState(checked)
            },
            trailingContent = {
                Checkbox(checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
            },
        )
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
    if (current is RecordingStartWindow.Stored) AutomaticRecordingRadio(
        state, "current-window", automaticRecordingWindowLabel(current), true, state::back,
    )
    AutomaticRecordingRadio(state, "any-time", stringResource(R.string.autorec_editor_any_time),
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
    for (hour in 0..23) GuidedStepAction(
        formatClock(LocalTime.of(hour, 0)),
        {
            state.selectedHour = hour
            val minute = when (val window = state.draft.startWindow) {
                is RecordingStartWindow.After -> window.time.minute
                is RecordingStartWindow.Before -> window.time.minute
                else -> 0
            }
            state.navigation.open(if (state.navigation.page.level == AutomaticRecordingEditorLevel.AFTER_HOUR)
                AutomaticRecordingEditorLevel.AFTER_MINUTE else AutomaticRecordingEditorLevel.BEFORE_MINUTE,
                "minute-$minute")
        },
        editorRowModifier(state.navigation, "hour-$hour"), trailingIcon = R.drawable.ic_keyboard_arrow_right,
    )
}

@Composable
private fun AutomaticRecordingMinutes(state: AutomaticRecordingEditorState) {
    val after = state.navigation.page.level == AutomaticRecordingEditorLevel.AFTER_MINUTE
    for (minute in 0..55 step 5) {
        val time = LocalTime.of(state.selectedHour, minute)
        val window = if (after) RecordingStartWindow.After(time) else RecordingStartWindow.Before(time)
        AutomaticRecordingRadio(state, "minute-$minute", formatClock(time),
            state.draft.startWindow == window, { state.applyChoice { it.copy(startWindow = window) } })
    }
}

@Composable
private fun AutomaticRecordingKeep(state: AutomaticRecordingEditorState) {
    val default = state.baseline.removal.takeIf { it == DvrRemoval.ServerDefault }
    val values = listOf(default, DvrRemoval.Days(7), DvrRemoval.Days(30), DvrRemoval.Days(90), DvrRemoval.Days(180),
        DvrRemoval.Days(365), DvrRemoval.UntilSpaceNeeded, DvrRemoval.Forever)
    val current = state.draft.removal
    if (current != null && current !in values) AutomaticRecordingRadio(
        state, "keep-$current", stringResource(R.string.autorec_editor_current, automaticRecordingKeepLabel(current)),
        true, state::back,
    )
    for (value in values) AutomaticRecordingRadio(
        state, "keep-$value", automaticRecordingKeepChoiceLabel(value), current == value,
        { state.applyChoice { it.copy(removal = value) } },
    )
}

@Composable
internal fun AutomaticRecordingRadio(
    state: AutomaticRecordingEditorState,
    id: String,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
) {
    GuidedStepAction(
        title, { if (!state.busy) onClick() }, editorRowModifier(state.navigation, id).semantics {
            role = Role.RadioButton; this.selected = selected
        },
        leadingIcon = leadingIcon,
        subtitle = subtitle,
        trailingContent = { RadioButton(selected, onClick = null, modifier = Modifier.clearAndSetSemantics {}) },
    )
}
