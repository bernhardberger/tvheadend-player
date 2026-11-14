package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.size
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
import androidx.tv.material3.Checkbox
import androidx.tv.material3.Switch
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.AutorecEpisodeState
import at.bernhardberger.tvhplayer.core.AutorecPreviewRow
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.core.RecordingDraftField
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import androidx.compose.material3.CircularProgressIndicator

/** Task content only: the caller retains its navigation entry and restores focus when it closes. */
@Composable
internal fun AutomaticRecordingEditor(
    state: AutomaticRecordingEditorState,
    modifier: Modifier = Modifier,
    channels: List<Channel> = emptyList(),
    configurations: List<DvrConfiguration> = emptyList(),
    events: List<EpgEvent> = emptyList(),
    scheduledEpisodeCount: Int = 0,
) {
    DisposableEffect(state) { onDispose(state::dispose) }
    if (state.closed) return
    RecordingEditorSurface(
        state.navigation, state::back, modifier,
        guidance = { AutomaticRecordingGuidance(state, channels, events) },
    ) {
        when (state.navigation.page.level) {
            AutomaticRecordingEditorLevel.FIRST -> AutomaticRecordingFirstLevel(state, channels)
            AutomaticRecordingEditorLevel.UPCOMING -> AutomaticRecordingEpisodes(state)
            AutomaticRecordingEditorLevel.WORDS, AutomaticRecordingEditorLevel.WORDS_ENTRY,
            AutomaticRecordingEditorLevel.SEARCH_IN -> AutomaticRecordingWords(state)
            AutomaticRecordingEditorLevel.CHANNEL_TIME,
            AutomaticRecordingEditorLevel.CHANNEL,
            AutomaticRecordingEditorLevel.DAYS,
            AutomaticRecordingEditorLevel.START_WINDOW,
            AutomaticRecordingEditorLevel.AFTER_HOUR,
            AutomaticRecordingEditorLevel.AFTER_MINUTE,
            AutomaticRecordingEditorLevel.BEFORE_HOUR,
            AutomaticRecordingEditorLevel.BEFORE_MINUTE,
            AutomaticRecordingEditorLevel.KEEP -> AutomaticRecordingTimeChoices(state, channels)
            else -> AutomaticRecordingOptionChoices(state, configurations)
        }
    }
    if (state.navigation.leaveConfirmation) RecordingEditorLeaveDialog(
        state.navigation, automaticRecordingMainLabel(state),
        state::back, state::submit, state::confirmDestructiveAction,
        mainIcon = if (state.editing) R.drawable.ic_save else R.drawable.ic_fiber_smart_record,
        unavailableReason = automaticRecordingUnavailableLabel(state),
    )
    if (state.navigation.confirmation == AutomaticRecordingEditorConfirmation.DELETE)
        AutomaticRecordingDeleteDialog(state, scheduledEpisodeCount, channels)
}

@Composable
private fun AutomaticRecordingFirstLevel(state: AutomaticRecordingEditorState, channels: List<Channel>) {
    GuidedStepAction(
        title = automaticRecordingMainLabel(state), onClick = state::submit,
        modifier = editorRowModifier(state.navigation, "main"),
        icon = if (state.editing) R.drawable.ic_save else R.drawable.ic_fiber_smart_record,
        enabled = !state.busy,
        leadingIcon = if (state.busy) ({
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        }) else null,
    )
    if (state.editing) GuidedStepAction(
        title = stringResource(R.string.autorec_editor_turned_on),
        onClick = { state.updateDraft { it.copy(enabled = !it.enabled) } },
        modifier = editorRowModifier(state.navigation, "enabled").semantics {
            role = Role.Switch
            toggleableState = ToggleableState(state.draft.enabled)
        },
        trailingContent = { Switch(state.draft.enabled, onCheckedChange = null, Modifier.clearAndSetSemantics {}) },
    )
    val series = state.draft.kind == AutomaticRecordingKind.SERIES
    GuidedStepAction(
        title = stringResource(if (series) R.string.autorec_editor_upcoming else R.string.autorec_editor_words),
        subtitleMaxLines = 2,
        subtitle = if (series) automaticRecordingPreviewSummary(state) else automaticRecordingWordsSummary(state),
        modifier = editorRowModifier(state.navigation, "matches"),
        onClick = { state.navigation.open(if (series) AutomaticRecordingEditorLevel.UPCOMING else
            AutomaticRecordingEditorLevel.WORDS, "done") }, trailingIcon = R.drawable.ic_keyboard_arrow_right,
        trailingContent = if (!series) ({
            AutomaticRecordingChanged(state, setOf(RecordingDraftField.WORDS, RecordingDraftField.SEARCH_IN))
        }) else null,
    )
    GuidedStepAction(
        title = stringResource(R.string.autorec_editor_channel_time),
        subtitleMaxLines = 2, subtitle = automaticRecordingChannelTimeLabel(state.draft, channels),
        onClick = { state.navigation.open(AutomaticRecordingEditorLevel.CHANNEL_TIME, "channel") },
        modifier = editorRowModifier(state.navigation, "channel-time"),
        trailingIcon = R.drawable.ic_keyboard_arrow_right,
        trailingContent = { AutomaticRecordingChanged(state, setOf(
            RecordingDraftField.CHANNEL, RecordingDraftField.DAYS, RecordingDraftField.START_WINDOW,
        )) },
    )
    GuidedStepAction(
        title = stringResource(R.string.recording_keep_until_heading),
        subtitleMaxLines = 2, subtitle = automaticRecordingKeepLabel(state.draft.removal),
        onClick = { state.navigation.open(AutomaticRecordingEditorLevel.KEEP, "keep-${state.draft.removal}") },
        modifier = editorRowModifier(state.navigation, "keep"), trailingIcon = R.drawable.ic_keyboard_arrow_right,
        trailingContent = { AutomaticRecordingChanged(state, setOf(RecordingDraftField.KEEP_UNTIL)) },
    )
    GuidedStepAction(
        title = stringResource(R.string.autorec_editor_more),
        subtitleMaxLines = 2, subtitle = recordingEditorMoreSummary(
            state.changedFields.intersect(automaticRecordingMoreFields), state.editing),
        onClick = { state.navigation.open(AutomaticRecordingEditorLevel.MORE, "duplicate") },
        modifier = editorRowModifier(state.navigation, "more"), trailingIcon = R.drawable.ic_keyboard_arrow_right,
        trailingContent = { AutomaticRecordingChanged(state, automaticRecordingMoreFields) },
    )
}

@Composable
internal fun AutomaticRecordingEpisodes(state: AutomaticRecordingEditorState) {
    GuidedStepAction(
        stringResource(R.string.autorec_editor_done), state::back, editorRowModifier(state.navigation, "done"),
    )
    AutomaticRecordingEpisodeRows(state)
}

@Composable
internal fun AutomaticRecordingEpisodeRows(state: AutomaticRecordingEditorState) {
    val rows = (state.preview as? AutorecPreviewState.Matches)?.rows.orEmpty()
    for (row in rows) {
        val toggleable = row.eventId != null && row.state !in
            setOf(AutorecEpisodeState.REMOVED, AutorecEpisodeState.DUPLICATE)
        val checked = state.episodeEnabled(row)
        GuidedStepAction(
            title = automaticRecordingPreviewTitle(state.draft.kind, row), onClick = { state.toggleEpisode(row) },
            subtitleMaxLines = 2, subtitle = automaticRecordingEpisodeMeta(row, state),
            modifier = editorRowModifier(state.navigation, automaticRecordingEpisodeRowId(row)).semantics {
                if (toggleable) {
                    role = Role.Checkbox
                    toggleableState = ToggleableState(checked)
                }
            },
            trailingContent = if (toggleable) ({
                RecordingEditorCheckboxMark(checked)
            }) else null,
        )
    }
}

internal fun automaticRecordingEpisodeRowId(row: AutorecPreviewRow): String =
    "episode-${row.eventId?.value ?: "recording-${row.recordingId?.value}"}"

@Composable
internal fun automaticRecordingMainLabel(state: AutomaticRecordingEditorState): String = stringResource(
    when {
        state.editing -> R.string.autorec_editor_save
        state.draft.kind == AutomaticRecordingKind.SERIES -> R.string.context_record_series
        else -> R.string.context_record
    },
)

@Composable
internal fun automaticRecordingPreviewSummary(state: AutomaticRecordingEditorState): String {
    val preview = state.preview
    return when (preview) {
        AutorecPreviewState.Loading -> stringResource(R.string.autorec_editor_searching)
        is AutorecPreviewState.Failed -> stringResource(R.string.autorec_editor_search_failed)
        AutorecPreviewState.NothingToSearch -> stringResource(R.string.autorec_editor_enter_words)
        is AutorecPreviewState.Matches -> formatAutomaticRecordingPreviewSummary(
            LocalContext.current, state.draft.kind, preview,
        )
    }
}
