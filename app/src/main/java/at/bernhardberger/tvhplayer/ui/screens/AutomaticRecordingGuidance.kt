package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DuplicateDetection
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.AutorecEpisodeState
import at.bernhardberger.tvhplayer.core.AutorecPreviewRow
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.core.RecordingSubmitUnavailable
import at.bernhardberger.tvhplayer.core.offeredDuplicateDetection
import at.bernhardberger.tvhplayer.core.programmeArtwork
import at.bernhardberger.tvhplayer.ui.components.GuidedStepGuidance
import at.bernhardberger.tvhplayer.ui.components.ProgrammeArtwork
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate

@Composable
internal fun AutomaticRecordingGuidance(
    state: AutomaticRecordingEditorState,
    channels: List<Channel>,
    events: List<EpgEvent>,
) {
    val row = (state.preview as? AutorecPreviewState.Matches)?.rows
        ?.firstOrNull { automaticRecordingEpisodeRowId(it) == state.focusedRow }
    val eyebrow = when (state.draft.kind) {
        AutomaticRecordingKind.SERIES ->
            if (state.editing) R.string.autorec_editor_edit_series else R.string.autorec_editor_new_series
        AutomaticRecordingKind.TITLE ->
            if (state.editing) R.string.autorec_editor_edit_title else R.string.autorec_editor_new_title
        else -> if (state.editing) R.string.autorec_editor_edit_keyword else R.string.autorec_editor_new_keyword
    }
    GuidedStepGuidance(
        title = row?.subtitle ?: state.draft.pattern.words,
        body = row?.title ?: automaticRecordingGuidanceBody(state),
        lead = { Text(stringResource(eyebrow), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha)) },
    )
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (row != null) {
            AutomaticRecordingFocusedEpisode(row, state, channels, events)
        } else {
            AutomaticRecordingFocusedChoice(state)
            if (state.editing && !state.draft.enabled) {
                AutomaticRecordingHeading(stringResource(R.string.autorec_editor_off_after_save))
                AutomaticRecordingSecondary(stringResource(R.string.autorec_editor_off_help))
            }
            AutomaticRecordingPassivePreview(state)
        }
    }
}

@Composable
private fun automaticRecordingGuidanceBody(state: AutomaticRecordingEditorState): String =
    stringResource(when (state.page.level) {
        AutomaticRecordingEditorLevel.FIRST -> when (state.focusedRow) {
            "keep" -> R.string.autorec_editor_keep_help
            "channel-time" -> R.string.autorec_editor_channel_time_help
            "more" -> R.string.autorec_editor_more_help
            else -> R.string.autorec_editor_series_description
        }
        AutomaticRecordingEditorLevel.KEEP -> R.string.autorec_editor_keep_help
        AutomaticRecordingEditorLevel.CHANNEL_TIME, AutomaticRecordingEditorLevel.CHANNEL,
        AutomaticRecordingEditorLevel.DAYS, AutomaticRecordingEditorLevel.START_WINDOW,
        AutomaticRecordingEditorLevel.AFTER_HOUR, AutomaticRecordingEditorLevel.BEFORE_HOUR,
        AutomaticRecordingEditorLevel.AFTER_MINUTE, AutomaticRecordingEditorLevel.BEFORE_MINUTE ->
            R.string.autorec_editor_channel_time_help
        AutomaticRecordingEditorLevel.MORE -> R.string.autorec_editor_more_help
        else -> R.string.autorec_editor_series_description
    })

@Composable
private fun AutomaticRecordingFocusedEpisode(
    row: AutorecPreviewRow,
    state: AutomaticRecordingEditorState,
    channels: List<Channel>,
    events: List<EpgEvent>,
) {
    val channel = channels.firstOrNull { it.id == row.channelId }
    val event = events.firstOrNull { it.id == row.eventId }
    if (event?.image != null) ProgrammeArtwork(
        programmeArtwork(event), channel?.icon, null, Modifier.fillMaxWidth().aspectRatio(16f / 9),
    )
    AutomaticRecordingSecondary(
        listOfNotNull(automaticRecordingEpisodeMeta(row, state), channel?.name).joinToString(" · "),
    )
    event?.description?.takeIf(String::isNotBlank)?.let { AutomaticRecordingSecondary(it) }
}

@Composable
private fun AutomaticRecordingFocusedChoice(state: AutomaticRecordingEditorState) {
    when (state.page.level) {
        AutomaticRecordingEditorLevel.FIRST -> AutomaticRecordingMainGuidance(state)
        AutomaticRecordingEditorLevel.MORE -> AutomaticRecordingSecondary(automaticRecordingMoreSummary(state))
        AutomaticRecordingEditorLevel.DUPLICATE -> {
            val focused = (listOf(DuplicateDetection.DVR_PROFILE) + offeredDuplicateDetection)
                .firstOrNull { "choice-$it" == state.focusedRow } ?: state.draft.duplicateDetection
            AutomaticRecordingSecondary(automaticRecordingDuplicateHelp(focused))
        }
        AutomaticRecordingEditorLevel.KEEP -> AutomaticRecordingKeepGuidance(state)
        AutomaticRecordingEditorLevel.MAXIMUM -> state.focusedRow.removePrefix("choice-").toLongOrNull()
            ?.takeIf { it > 0 }?.let {
                AutomaticRecordingSecondary(stringResource(R.string.autorec_editor_maximum_help, it))
            }
        else -> Unit
    }
}

@Composable
private fun AutomaticRecordingMainGuidance(state: AutomaticRecordingEditorState) {
    if (state.focusedRow == "more") AutomaticRecordingSecondary(automaticRecordingMoreSummary(state))
    if (state.focusedRow == "main") {
        val reason = when (state.mainProblem) {
            RecordingSubmitUnavailable.UNCHANGED -> stringResource(R.string.autorec_editor_unchanged)
            RecordingSubmitUnavailable.WORDS_REQUIRED -> stringResource(R.string.autorec_editor_enter_words)
            else -> null
        }
        reason?.let { AutomaticRecordingSecondary(it) }
    }
}

@Composable
private fun AutomaticRecordingKeepGuidance(state: AutomaticRecordingEditorState) {
    val focused = listOf(null, DvrRemoval.Days(7), DvrRemoval.Days(30), DvrRemoval.Days(90),
        DvrRemoval.Days(180), DvrRemoval.Days(365), DvrRemoval.UntilSpaceNeeded, DvrRemoval.Forever)
        .firstOrNull { "keep-$it" == state.focusedRow }
    AutomaticRecordingSecondary(automaticRecordingKeepLabel(focused ?: state.draft.removal.takeIf {
        "keep-$it" == state.focusedRow
    }))
}

@Composable
private fun AutomaticRecordingPassivePreview(state: AutomaticRecordingEditorState) {
    when (val preview = state.preview) {
        AutorecPreviewState.Loading -> AutomaticRecordingHeading(stringResource(R.string.autorec_editor_searching))
        is AutorecPreviewState.Failed ->
            AutomaticRecordingHeading(stringResource(R.string.autorec_editor_search_failed))
        AutorecPreviewState.NothingToSearch ->
            AutomaticRecordingHeading(stringResource(R.string.autorec_editor_empty_words))
        is AutorecPreviewState.Matches -> {
            if (preview.rows.isEmpty()) AutomaticRecordingHeading(stringResource(R.string.autorec_editor_no_matches))
            else if (!state.editing || state.draft.enabled) AutomaticRecordingHeading(pluralStringResource(
                if (state.draft.kind == AutomaticRecordingKind.SERIES) R.plurals.autorec_editor_upcoming_episodes
                    else R.plurals.autorec_editor_upcoming_programs,
                preview.willRecordCount, preview.willRecordCount,
            ))
            for (row in preview.rows.take(3)) {
                Column {
                    Text(row.subtitle ?: row.title.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    AutomaticRecordingSecondary(automaticRecordingEpisodeMeta(row, state))
                }
            }
            if (preview.rows.size > 3) AutomaticRecordingSecondary(stringResource(
                R.string.autorec_editor_more_preview, preview.rows.size - 3,
                stringResource(if (state.draft.kind == AutomaticRecordingKind.SERIES)
                    R.string.autorec_editor_upcoming else R.string.autorec_editor_words),
            ))
            if (preview.broadcastFilterUnavailable) AutomaticRecordingSecondary(
                stringResource(R.string.autorec_editor_broadcast_unavailable),
            )
        }
    }
    AutomaticRecordingSecondary(stringResource(R.string.autorec_editor_guide_horizon))
}

@Composable
private fun AutomaticRecordingHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun AutomaticRecordingSecondary(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha))
}

@Composable
internal fun automaticRecordingEpisodeMeta(row: AutorecPreviewRow, state: AutomaticRecordingEditorState): String {
    val locale = LocalConfiguration.current.locales[0]
    val date = formatRecordingDate(row.start.epochSeconds, dateOnly = true, locale = locale,
        nowSec = state.now().epochSeconds)
    val time = "$date · ${formatClock(java.time.Instant.ofEpochSecond(row.start.epochSeconds)
        .atZone(java.time.ZoneId.systemDefault()), locale)}"
    val status = when (row.state) {
        AutorecEpisodeState.MATCH -> if (state.episodeEnabled(row)) null else R.string.autorec_editor_left_out
        AutorecEpisodeState.SCHEDULED -> if (state.episodeEnabled(row))
            R.string.recording_state_scheduled else R.string.autorec_editor_skipped
        AutorecEpisodeState.DUPLICATE -> R.string.autorec_editor_duplicate_state
        AutorecEpisodeState.SKIPPED -> if (state.episodeEnabled(row))
            R.string.recording_state_scheduled else R.string.autorec_editor_skipped
        AutorecEpisodeState.LEFT_OUT -> if (state.episodeEnabled(row)) null else R.string.autorec_editor_left_out
        AutorecEpisodeState.REMOVED -> R.string.autorec_editor_removed
    }
    return if (status == null) time else "$time · ${stringResource(status)}"
}
