package at.bernhardberger.tvhplayer.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Checkbox
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.AutorecEpisodeState
import at.bernhardberger.tvhplayer.core.AutorecPreviewRow
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.core.RecordingDraftField
import at.bernhardberger.tvhplayer.ui.components.CentredDialog
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.GuidedStepLayout
import at.bernhardberger.tvhplayer.ui.components.backKeyCycleFocus
import at.bernhardberger.tvhplayer.ui.components.rememberBackKeyCycle
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Button
import androidx.compose.runtime.withFrameNanos

/** Task content only: the caller retains its navigation entry and restores focus when it closes. */
@Composable
internal fun AutomaticRecordingEditor(
    state: AutomaticRecordingEditorState,
    channels: List<Channel> = emptyList(),
    configurations: List<DvrConfiguration> = emptyList(),
    events: List<EpgEvent> = emptyList(),
    modifier: Modifier = Modifier,
    scheduledEpisodeCount: Int = 0,
) {
    DisposableEffect(state) { onDispose(state::dispose) }
    BackHandler(enabled = state.confirmation == null) { state.back() }
    if (state.closed) return
    key(state.page.level) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).editorKeys(state)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                GuidedStepLayout(
                    guidance = { AutomaticRecordingGuidance(state, channels, events) },
                ) {
                    when (state.page.level) {
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
            }
        }
    }
    if (state.leaveConfirmation) AutomaticRecordingLeaveDialog(state)
    if (state.confirmation == AutomaticRecordingEditorConfirmation.DELETE)
        AutomaticRecordingDeleteDialog(state, scheduledEpisodeCount)
}

@Composable
private fun Modifier.editorKeys(state: AutomaticRecordingEditorState): Modifier {
    val back = rememberBackKeyCycle(state.confirmation == null)
    val left = rememberBackKeyCycle(state.confirmation == null)
    var openingRelease by remember { mutableStateOf(true) }
    return backKeyCycleFocus(back, left).onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        val down = key.action == KeyEvent.ACTION_DOWN
        if (down && key.repeatCount == 0) openingRelease = false
        when {
            state.confirmation != null -> false
            state.textEntryEditing && key.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> false
            key.keyCode == KeyEvent.KEYCODE_BACK -> back.handle(
                down, key.repeatCount, key.isCanceled, onBack = state::back,
            )
            key.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && state.pages.size > 1 -> left.handle(
                down, key.repeatCount, key.isCanceled, onBack = state::back,
            )
            openingRelease && key.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER) -> true
            else -> false
        }
    }
}

@Composable
internal fun editorRowModifier(state: AutomaticRecordingEditorState, id: String): Modifier {
    val requester = remember { FocusRequester() }
    val restoreFocus = state.focusedRow == id && state.confirmation == null
    LaunchedEffect(state.focusRevision) {
        if (restoreFocus) {
            withFrameNanos { }
            requester.requestFocus()
        }
    }
    return Modifier.testTag("automatic-recording-$id").focusRequester(requester).onFocusChanged {
        if (it.isFocused) state.focusedRow = id
    }
}

@Composable
private fun AutomaticRecordingFirstLevel(state: AutomaticRecordingEditorState, channels: List<Channel>) {
    GuidedStepAction(
        title = automaticRecordingMainLabel(state), onClick = state::submit,
        modifier = editorRowModifier(state, "main"), icon = R.drawable.ic_fiber_smart_record,
        enabled = !state.busy && state.mainProblem == null,
        leadingIcon = if (state.busy) ({
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        }) else null,
    )
    val series = state.draft.kind == AutomaticRecordingKind.SERIES
    GuidedStepAction(
        title = stringResource(if (series) R.string.autorec_editor_upcoming else R.string.autorec_editor_words),
        subtitle = if (series) automaticRecordingPreviewSummary(state) else automaticRecordingWordsSummary(state),
        modifier = editorRowModifier(state, "matches"),
        onClick = { state.open(if (series) AutomaticRecordingEditorLevel.UPCOMING else
            AutomaticRecordingEditorLevel.WORDS, "done") }, trailingIcon = R.drawable.ic_keyboard_arrow_right,
        trailingContent = if (!series) ({
            AutomaticRecordingChanged(state, setOf(RecordingDraftField.WORDS, RecordingDraftField.SEARCH_IN))
        }) else null,
    )
    GuidedStepAction(
        title = stringResource(R.string.autorec_editor_channel_time),
        subtitle = automaticRecordingChannelTimeLabel(state.draft, channels),
        onClick = { state.open(AutomaticRecordingEditorLevel.CHANNEL_TIME, "channel") },
        modifier = editorRowModifier(state, "channel-time"), trailingIcon = R.drawable.ic_keyboard_arrow_right,
        trailingContent = { AutomaticRecordingChanged(state, setOf(
            RecordingDraftField.CHANNEL, RecordingDraftField.DAYS, RecordingDraftField.START_WINDOW,
        )) },
    )
    GuidedStepAction(
        title = stringResource(R.string.autorec_editor_keep),
        subtitle = automaticRecordingKeepLabel(state.draft.removal),
        onClick = { state.open(AutomaticRecordingEditorLevel.KEEP, "keep-${state.draft.removal}") },
        modifier = editorRowModifier(state, "keep"), trailingIcon = R.drawable.ic_keyboard_arrow_right,
        trailingContent = { AutomaticRecordingChanged(state, setOf(RecordingDraftField.KEEP_UNTIL)) },
    )
    GuidedStepAction(
        title = stringResource(R.string.autorec_editor_more), subtitle = automaticRecordingMoreSummary(state),
        onClick = { state.open(AutomaticRecordingEditorLevel.MORE, "duplicate") },
        modifier = editorRowModifier(state, "more"), trailingIcon = R.drawable.ic_keyboard_arrow_right,
        trailingContent = { AutomaticRecordingChanged(state, automaticRecordingMoreFields) },
    )
    if (state.editing) GuidedStepAction(
        title = stringResource(R.string.autorec_editor_turned_on),
        onClick = { state.updateDraft { it.copy(enabled = !it.enabled) } },
        modifier = editorRowModifier(state, "enabled").semantics {
            role = Role.Switch
            toggleableState = ToggleableState(state.draft.enabled)
        },
        trailingContent = { Switch(state.draft.enabled, onCheckedChange = null, Modifier.clearAndSetSemantics {}) },
    )
    GuidedStepAction(stringResource(R.string.cancel), state::back, editorRowModifier(state, "cancel"))
}

@Composable
internal fun AutomaticRecordingEpisodes(state: AutomaticRecordingEditorState) {
    GuidedStepAction(
        stringResource(R.string.autorec_editor_done), state::back, editorRowModifier(state, "done"),
        subtitle = stringResource(R.string.autorec_editor_choices_stay),
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
            subtitle = automaticRecordingEpisodeMeta(row, state),
            modifier = editorRowModifier(state, automaticRecordingEpisodeRowId(row)).semantics {
                if (toggleable) {
                    role = Role.Checkbox
                    toggleableState = ToggleableState(checked)
                }
            },
            trailingContent = if (toggleable) ({
                Checkbox(checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
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
        state.draft.kind == AutomaticRecordingKind.SERIES -> R.string.autorec_editor_record_series
        else -> R.string.autorec_editor_record
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

@Composable
private fun AutomaticRecordingLeaveDialog(state: AutomaticRecordingEditorState) {
    val keep = remember { FocusRequester() }
    val main = remember { FocusRequester() }
    val discard = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; keep.requestFocus() }
    CentredDialog(
        title = stringResource(R.string.autorec_editor_leave_title),
        text = stringResource(R.string.autorec_editor_leave_body), onDismiss = state::back, onBack = state::back,
        actions = {
            Button(onClick = state::back, modifier = Modifier.testTag("automatic-recording-keep-editing")
                .focusRequester(keep).focusProperties { left = FocusRequester.Cancel; right = main }) {
                Text(stringResource(R.string.autorec_editor_keep_editing))
            }
            OutlinedButton(onClick = state::submit, enabled = state.mainProblem == null,
                modifier = Modifier.focusRequester(main).focusProperties { left = keep; right = discard }) {
                Text(automaticRecordingMainLabel(state))
            }
            OutlinedButton(onClick = state::confirmDestructiveAction,
                modifier = Modifier.testTag("automatic-recording-discard")
                .focusRequester(discard).focusProperties { left = main; right = FocusRequester.Cancel }) {
                Text(stringResource(R.string.autorec_editor_discard))
            }
        },
    )
}
