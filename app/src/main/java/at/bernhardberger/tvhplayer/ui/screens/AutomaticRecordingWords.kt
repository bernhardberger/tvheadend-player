package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction

@Composable
internal fun AutomaticRecordingWords(state: AutomaticRecordingEditorState) {
    when (state.navigation.page.level) {
        AutomaticRecordingEditorLevel.WORDS_ENTRY -> RecordingEditorTextEntry(
            state.navigation, state.draft.pattern.words, stringResource(R.string.autorec_editor_words), "words-input",
            { value -> state.applyChoice { it.copy(pattern = it.pattern.copy(words = value)) } },
        )
        AutomaticRecordingEditorLevel.SEARCH_IN -> {
            for (fullText in listOf(false, true)) RecordingEditorRadio(
                state.navigation, "search-$fullText",
                automaticRecordingSearchInLabel(fullText), state.draft.fullText == fullText,
                { state.applyChoice { it.copy(fullText = fullText) } },
            )
        }
        else -> {
            GuidedStepAction(
                stringResource(R.string.autorec_editor_done), state::back, editorRowModifier(state.navigation, "done"),
            )
            GuidedStepAction(
                stringResource(R.string.autorec_editor_words),
                { state.navigation.open(AutomaticRecordingEditorLevel.WORDS_ENTRY, "words-input") },
                editorRowModifier(state.navigation, "words"),
                subtitle = if (state.draft.pattern.rawExpression)
                    stringResource(R.string.autorec_editor_raw_value, state.draft.pattern.words)
                else state.draft.pattern.words.ifBlank { stringResource(R.string.autorec_editor_enter_words) },
                trailingIcon = R.drawable.ic_edit,
            )
            if (state.draft.kind == AutomaticRecordingKind.KEYWORD) GuidedStepAction(
                stringResource(R.string.autorec_editor_search_in),
                { state.navigation.open(AutomaticRecordingEditorLevel.SEARCH_IN, "search-${state.draft.fullText}") },
                editorRowModifier(state.navigation, "search-in"),
                subtitle = automaticRecordingSearchInLabel(state.draft.fullText),
                trailingIcon = R.drawable.ic_keyboard_arrow_right,
            )
            AutomaticRecordingEpisodeRows(state)
        }
    }
}

@Composable
internal fun automaticRecordingSearchInLabel(fullText: Boolean): String = stringResource(
    if (fullText) R.string.autorec_editor_full_text else R.string.autorec_editor_titles_only,
)

@Composable
internal fun automaticRecordingWordsSummary(state: AutomaticRecordingEditorState): String {
    val pattern = state.draft.pattern
    val preview = state.preview as? AutorecPreviewState.Matches
    return when {
        pattern.words.isBlank() -> stringResource(R.string.autorec_editor_enter_words)
        pattern.rawExpression -> stringResource(R.string.autorec_editor_raw_value, pattern.words)
        preview == null -> pattern.words
        preview.matchCount == 0 -> stringResource(R.string.autorec_editor_words_empty_summary, pattern.words)
        else -> pluralStringResource(
            if (state.draft.fullText) R.plurals.autorec_editor_words_full_summary
                else R.plurals.autorec_editor_words_titles_summary,
            preview.matchCount, pattern.words, preview.matchCount,
        )
    }
}

@Composable
internal fun automaticRecordingDescription(state: AutomaticRecordingEditorState): String {
    val resource = when {
        state.draft.kind == AutomaticRecordingKind.SERIES -> R.string.autorec_editor_series_description
        state.draft.pattern.rawExpression -> R.string.autorec_editor_raw_help
        state.draft.kind == AutomaticRecordingKind.TITLE && state.draft.fullText ->
            R.string.autorec_editor_title_full_description
        state.draft.kind == AutomaticRecordingKind.TITLE -> R.string.autorec_editor_title_description
        state.draft.fullText -> R.string.autorec_editor_keyword_full_description
        else -> R.string.autorec_editor_keyword_description
    }
    return stringResource(resource, state.draft.pattern.words)
}
