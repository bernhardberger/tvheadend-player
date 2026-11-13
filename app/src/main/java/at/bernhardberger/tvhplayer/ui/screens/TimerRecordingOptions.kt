package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.RecordingDraftField
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction

@Composable
internal fun TimerRecordingOptions(
    state: TimerRecordingEditorState,
    channels: List<Channel>,
    configurations: List<DvrConfiguration>,
) {
    val draft = state.draft
    when (state.navigation.page.level) {
        AutomaticRecordingEditorLevel.CHANNEL -> RecordingEditorChannels(
            state.navigation, channels, draft.channelId,
            { value -> state.applyChoice { it.copy(channelId = value) } }, state::back,
        )
        AutomaticRecordingEditorLevel.KEEP -> RecordingEditorKeep(
            state.navigation, draft.removal, state.baseline.removal,
            { value -> state.applyChoice { it.copy(removal = value) } }, state::back,
        )
        AutomaticRecordingEditorLevel.MORE -> TimerRecordingMore(state, configurations)
        AutomaticRecordingEditorLevel.PRIORITY -> RecordingEditorPriorities(
            state.navigation, draft.priority, state.baseline.priority,
            { value -> state.applyChoice { it.copy(priority = value) } }, state::back,
        )
        AutomaticRecordingEditorLevel.PADDING_BEFORE -> RecordingEditorPadding(
            state.navigation, draft.paddingBefore, state.baseline.paddingBefore,
            { value -> state.applyChoice { it.copy(paddingBefore = value) } }, state::back,
        )
        AutomaticRecordingEditorLevel.PADDING_AFTER -> RecordingEditorPadding(
            state.navigation, draft.paddingAfter, state.baseline.paddingAfter,
            { value -> state.applyChoice { it.copy(paddingAfter = value) } }, state::back,
        )
        AutomaticRecordingEditorLevel.PROFILE -> RecordingEditorProfiles(
            state.navigation, configurations, draft.configId,
            { value -> state.applyChoice { it.copy(configId = value) } }, state::back,
        )
        else -> Unit
    }
}

@Composable
private fun TimerRecordingMore(state: TimerRecordingEditorState, configurations: List<DvrConfiguration>) {
    val draft = state.draft
    val fields = timerMoreFields.filter {
        when (it) {
            RecordingDraftField.PROFILE -> configurations.size > 1
            RecordingDraftField.PADDING_BEFORE, RecordingDraftField.PADDING_AFTER -> !draft.repeating
            else -> true
        }
    }
    for (field in fields) {
        val value = timerOptionValue(state, field)
        val level = automaticRecordingOptionLevel(field)
        TimerRecordingField(state, level.name.lowercase(), automaticRecordingFieldLabel(field),
            timerOptionLabel(state, field, configurations), setOf(field)) {
            state.navigation.open(level, "choice-$value")
        }
    }
    if (draft.repeating) RecordingEditorSecondary(stringResource(R.string.timer_editor_no_padding))
    if (state.changedFields.any { it in timerMoreFields }) GuidedStepAction(
        stringResource(R.string.timer_editor_undo), state::resetMore,
        editorRowModifier(state.navigation, "reset-options"),
    )
    if (state.editing) GuidedStepAction(
        stringResource(R.string.autorec_editor_delete),
        { state.navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE },
        editorRowModifier(state.navigation, "delete"),
    )
}

private fun timerOptionValue(state: TimerRecordingEditorState, field: RecordingDraftField): Any? = when (field) {
    RecordingDraftField.PRIORITY -> state.draft.priority
    RecordingDraftField.PROFILE -> state.draft.configId?.value
    RecordingDraftField.PADDING_BEFORE -> state.draft.paddingBefore
    else -> state.draft.paddingAfter
}

@Composable
private fun timerOptionLabel(
    state: TimerRecordingEditorState,
    field: RecordingDraftField,
    configurations: List<DvrConfiguration>,
): String = when (field) {
    RecordingDraftField.PRIORITY -> automaticRecordingPriorityLabel(state.draft.priority)
    RecordingDraftField.PROFILE -> configurations.firstOrNull { it.id == state.draft.configId }?.name
        ?: stringResource(R.string.profile_server_default)
    RecordingDraftField.PADDING_BEFORE -> automaticRecordingPaddingLabel(state.draft.paddingBefore)
    else -> automaticRecordingPaddingLabel(state.draft.paddingAfter)
}
