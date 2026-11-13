package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.BroadcastType
import at.bernhardberger.tvheadend.sdk.core.DuplicateDetection
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.RecordingDraftField
import at.bernhardberger.tvhplayer.core.offeredDuplicateDetection
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.StatusBadge
import at.bernhardberger.tvhplayer.ui.components.StatusBadgeTone

@Composable
internal fun AutomaticRecordingOptionChoices(
    state: AutomaticRecordingEditorState,
    configurations: List<DvrConfiguration>,
) {
    when (state.navigation.page.level) {
        AutomaticRecordingEditorLevel.MORE -> AutomaticRecordingMore(state, configurations)
        AutomaticRecordingEditorLevel.DUPLICATE -> AutomaticRecordingDuplicates(state)
        AutomaticRecordingEditorLevel.PRIORITY -> RecordingEditorPriorities(
            state.navigation, state.draft.priority, state.baseline.priority,
            { value -> state.applyChoice { it.copy(priority = value) } }, state::back,
        )
        AutomaticRecordingEditorLevel.PADDING_BEFORE, AutomaticRecordingEditorLevel.PADDING_AFTER ->
            AutomaticRecordingPadding(state)
        AutomaticRecordingEditorLevel.BROADCAST -> AutomaticRecordingBroadcast(state)
        AutomaticRecordingEditorLevel.MAXIMUM -> AutomaticRecordingMaximum(state)
        AutomaticRecordingEditorLevel.PROFILE -> RecordingEditorProfiles(
            state.navigation, configurations, state.draft.configId,
            { value -> state.applyChoice { it.copy(configId = value) } }, state::back,
        )
        else -> Unit
    }
}

@Composable
private fun AutomaticRecordingMore(state: AutomaticRecordingEditorState, configurations: List<DvrConfiguration>) {
    for (field in automaticRecordingMoreFields) {
        if (field == RecordingDraftField.PROFILE && configurations.size <= 1) continue
        val level = automaticRecordingOptionLevel(field)
        GuidedStepAction(
            title = automaticRecordingFieldLabel(field),
            subtitle = automaticRecordingOptionValue(state, field, configurations),
            modifier = editorRowModifier(state.navigation, level.name.lowercase()),
            onClick = { state.navigation.open(level, automaticRecordingOptionRowId(state, field)) },
            trailingIcon = R.drawable.ic_keyboard_arrow_right,
            trailingContent = { AutomaticRecordingChanged(state, setOf(field)) },
        )
    }
    val changed = state.changedFields.intersect(automaticRecordingMoreFields)
    if (changed.isNotEmpty()) GuidedStepAction(
        title = stringResource(R.string.autorec_editor_reset_options, changed.size),
        subtitle = changed.map { automaticRecordingFieldLabel(it) }.joinToString(", "),
        modifier = editorRowModifier(state.navigation, "reset-options"),
        onClick = {
            state.updateDraft(restoreFocusTo = "duplicate") { resetAutomaticRecordingMoreOptions(it, state.baseline) }
        },
    )
    if (state.editing) GuidedStepAction(
        stringResource(R.string.autorec_editor_delete),
        { state.navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE },
        editorRowModifier(state.navigation, "delete"),
    )
}

internal fun automaticRecordingOptionLevel(field: RecordingDraftField): AutomaticRecordingEditorLevel = when (field) {
    RecordingDraftField.DUPLICATE_DETECTION -> AutomaticRecordingEditorLevel.DUPLICATE
    RecordingDraftField.PRIORITY -> AutomaticRecordingEditorLevel.PRIORITY
    RecordingDraftField.PADDING_BEFORE -> AutomaticRecordingEditorLevel.PADDING_BEFORE
    RecordingDraftField.PADDING_AFTER -> AutomaticRecordingEditorLevel.PADDING_AFTER
    RecordingDraftField.BROADCAST_TYPE -> AutomaticRecordingEditorLevel.BROADCAST
    RecordingDraftField.MAXIMUM_RECORDINGS -> AutomaticRecordingEditorLevel.MAXIMUM
    RecordingDraftField.PROFILE -> AutomaticRecordingEditorLevel.PROFILE
    else -> error("Not an additional recording option")
}

private fun automaticRecordingOptionRowId(state: AutomaticRecordingEditorState, field: RecordingDraftField): String {
    val draft = state.draft
    val value = when (field) {
        RecordingDraftField.DUPLICATE_DETECTION -> draft.duplicateDetection
        RecordingDraftField.PRIORITY -> draft.priority
        RecordingDraftField.PADDING_BEFORE -> draft.paddingBefore
        RecordingDraftField.PADDING_AFTER -> draft.paddingAfter
        RecordingDraftField.BROADCAST_TYPE -> draft.broadcastType
        RecordingDraftField.MAXIMUM_RECORDINGS -> draft.maximumRecordings
        RecordingDraftField.PROFILE -> draft.configId?.value
        else -> null
    }
    return "choice-$value"
}

@Composable
private fun AutomaticRecordingDuplicates(state: AutomaticRecordingEditorState) {
    val current = state.draft.duplicateDetection
    val values = listOf(DuplicateDetection.DVR_PROFILE) + offeredDuplicateDetection
    if (current !in values) RecordingEditorRadio(
        state.navigation, "choice-$current", stringResource(R.string.autorec_editor_current_setting), true, state::back,
    )
    for (value in values) RecordingEditorRadio(
        state.navigation, "choice-$value", automaticRecordingDuplicateLabel(value),
        current == value, { state.applyChoice { it.copy(duplicateDetection = value) } })
}

@Composable
private fun AutomaticRecordingPadding(state: AutomaticRecordingEditorState) {
    val before = state.navigation.page.level == AutomaticRecordingEditorLevel.PADDING_BEFORE
    val current = if (before) state.draft.paddingBefore else state.draft.paddingAfter
    val baseline = if (before) state.baseline.paddingBefore else state.baseline.paddingAfter
    RecordingEditorPadding(
        state.navigation, current, baseline,
        { value ->
            state.applyChoice { if (before) it.copy(paddingBefore = value) else it.copy(paddingAfter = value) }
        },
        state::back,
    )
}

@Composable
private fun AutomaticRecordingBroadcast(state: AutomaticRecordingEditorState) {
    val current = state.draft.broadcastType
    val values = listOf(BroadcastType.ALL, BroadcastType.NEW, BroadcastType.REPEAT)
    if (current !in values) RecordingEditorRadio(
        state.navigation, "choice-$current", stringResource(R.string.autorec_editor_current,
            automaticRecordingBroadcastLabel(current)), true, state::back,
    )
    for (value in values) RecordingEditorRadio(
        state.navigation, "choice-$value", automaticRecordingBroadcastLabel(value),
        current == value, { state.applyChoice { it.copy(broadcastType = value) } })
}

@Composable
private fun AutomaticRecordingMaximum(state: AutomaticRecordingEditorState) {
    val current = state.draft.maximumRecordings
    val values = listOf(0L, 1L, 2L, 3L, 5L, 10L)
    if (current !in values) RecordingEditorRadio(state.navigation, "choice-$current",
        stringResource(R.string.autorec_editor_current, current.toString()), true, state::back)
    for (value in values) RecordingEditorRadio(
        state.navigation, "choice-$value", automaticRecordingMaximumLabel(value),
        current == value, { state.applyChoice { it.copy(maximumRecordings = value) } })
}

@Composable
internal fun AutomaticRecordingChanged(state: AutomaticRecordingEditorState, fields: Set<RecordingDraftField>) {
    if (state.changedFields.any { it in fields }) StatusBadge(
        stringResource(R.string.autorec_editor_changed), StatusBadgeTone.CHANGED,
    )
}
