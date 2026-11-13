package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Switch
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.RecordingDraftField
import at.bernhardberger.tvhplayer.ui.components.CentredDialog
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.StatusBadge
import at.bernhardberger.tvhplayer.ui.components.StatusBadgeTone

@Composable
internal fun TimerRecordingEditor(
    state: TimerRecordingEditorState,
    channels: List<Channel> = emptyList(),
    configurations: List<DvrConfiguration> = emptyList(),
    modifier: Modifier = Modifier,
    scheduledEntries: List<DvrEntry> = emptyList(),
) {
    DisposableEffect(state) { onDispose(state::dispose) }
    if (state.closed) return
    RecordingEditorSurface(
        state.navigation, state::back, modifier,
        guidance = { TimerRecordingGuidance(state, channels, scheduledEntries) },
    ) {
        when (state.navigation.page.level) {
            AutomaticRecordingEditorLevel.FIRST -> TimerRecordingFirstLevel(state, channels)
            AutomaticRecordingEditorLevel.CHANNEL_TIME -> TimerRecordingChannelTime(state, channels)
            AutomaticRecordingEditorLevel.REPEAT -> TimerRecordingRepeat(state)
            AutomaticRecordingEditorLevel.DATE -> TimerRecordingDates(state)
            AutomaticRecordingEditorLevel.START_HOUR, AutomaticRecordingEditorLevel.END_HOUR ->
                TimerRecordingHours(state)
            AutomaticRecordingEditorLevel.START_MINUTE, AutomaticRecordingEditorLevel.END_MINUTE ->
                TimerRecordingMinutes(state)
            AutomaticRecordingEditorLevel.NAME -> RecordingEditorTextEntry(
                state.navigation, state.draft.name, stringResource(R.string.timer_editor_name), "name-input",
                { value -> state.applyChoice { it.copy(name = value) } },
            )
            else -> TimerRecordingOptions(state, channels, configurations)
        }
    }
    if (state.navigation.leaveConfirmation) RecordingEditorLeaveDialog(
        state.navigation, timerMainLabel(state), state.mainProblem == null,
        state::back, state::submit, state::confirmDestructiveAction,
    )
    if (state.navigation.confirmation == AutomaticRecordingEditorConfirmation.DELETE) CentredDialog(
        title = stringResource(R.string.timer_editor_delete),
        message = formatAutomaticRecordingDeleteBody(
            LocalContext.current, AutomaticRecordingKind.TIMER, scheduledEntries.size, state.changed,
        ),
        firstLabel = stringResource(R.string.autorec_editor_keep_recording),
        secondLabel = stringResource(R.string.autorec_editor_delete),
        onFirst = state::back, onSecond = state::confirmDestructiveAction, onDismiss = state::back,
        firstTag = "timer-recording-keep", secondTag = "timer-recording-delete-confirm",
    )
}

@Composable
private fun TimerRecordingFirstLevel(state: TimerRecordingEditorState, channels: List<Channel>) {
    GuidedStepAction(
        timerMainLabel(state), state::submit, editorRowModifier(state.navigation, "main"),
        icon = R.drawable.ic_schedule, enabled = !state.busy && state.mainProblem == null,
        leadingIcon = if (state.busy) ({
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        }) else null,
    )
    TimerRecordingField(state, "channel-time", stringResource(R.string.autorec_editor_channel_time),
        "${timerChannelLabel(state, channels)} · ${timerSpanLabel(state)}", timerChannelTimeFields) {
        state.navigation.open(AutomaticRecordingEditorLevel.CHANNEL_TIME, "channel")
    }
    TimerRecordingField(state, "name", stringResource(R.string.timer_editor_name),
        timerNameLabel(state, channels), setOf(RecordingDraftField.NAME)) {
        state.navigation.open(AutomaticRecordingEditorLevel.NAME, "name-input")
    }
    TimerRecordingField(state, "keep", stringResource(R.string.autorec_editor_keep),
        automaticRecordingKeepLabel(state.draft.removal), setOf(RecordingDraftField.KEEP_UNTIL)) {
        state.navigation.open(AutomaticRecordingEditorLevel.KEEP, "keep-${state.draft.removal}")
    }
    TimerRecordingField(state, "more", stringResource(R.string.autorec_editor_more),
        recordingEditorMoreSummary(state.changedFields.intersect(timerMoreFields), state.editing), timerMoreFields) {
        state.navigation.open(AutomaticRecordingEditorLevel.MORE, "priority")
    }
    if (state.editing) GuidedStepAction(
        stringResource(R.string.autorec_editor_turned_on),
        { state.updateDraft { it.copy(enabled = !it.enabled) } },
        editorRowModifier(state.navigation, "enabled").semantics {
            role = Role.Switch; toggleableState = ToggleableState(state.draft.enabled)
        },
        trailingContent = { Switch(state.draft.enabled, null, Modifier.clearAndSetSemantics {}) },
    )
    GuidedStepAction(stringResource(R.string.cancel), state::back, editorRowModifier(state.navigation, "cancel"))
}

@Composable
internal fun TimerRecordingField(
    state: TimerRecordingEditorState,
    id: String,
    title: String,
    value: String,
    fields: Set<RecordingDraftField>,
    onClick: () -> Unit,
) {
    GuidedStepAction(title, { if (state.navigation.canNavigate()) onClick() },
        editorRowModifier(state.navigation, id), subtitle = value, trailingIcon = R.drawable.ic_keyboard_arrow_right,
        trailingContent = {
            if (state.changedFields.any { it in fields })
                StatusBadge(stringResource(R.string.autorec_editor_changed), StatusBadgeTone.CHANGED)
        })
}
