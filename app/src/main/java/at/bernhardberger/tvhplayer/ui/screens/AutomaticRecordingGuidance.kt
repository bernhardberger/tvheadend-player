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
        ?.firstOrNull { automaticRecordingEpisodeRowId(it) == state.navigation.focusedRow }
    GuidedStepGuidance(
        title = if (row != null) automaticRecordingPreviewTitle(state.draft.kind, row)
            else if (state.navigation.page.level in setOf(
                AutomaticRecordingEditorLevel.WORDS, AutomaticRecordingEditorLevel.WORDS_ENTRY,
            )) stringResource(R.string.autorec_editor_words) else state.draft.pattern.words,
        body = row?.title ?: automaticRecordingGuidanceBody(state),
        lead = { Text(automaticRecordingEditorEyebrow(state), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha)) },
    )
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.deleting) {
            RecordingEditorHeading(stringResource(R.string.autorec_editor_deleting))
            return@Column
        }
        if (row != null) {
            AutomaticRecordingFocusedEpisode(row, state, channels, events)
        } else {
            AutomaticRecordingFocusedChoice(state)
            if (state.editing && !state.draft.enabled) {
                RecordingEditorHeading(stringResource(R.string.autorec_editor_off_after_save))
                RecordingEditorSecondary(stringResource(R.string.autorec_editor_off_help))
            }
            AutomaticRecordingPassivePreview(state)
        }
    }
}

@Composable
private fun automaticRecordingGuidanceBody(state: AutomaticRecordingEditorState): String =
    when (state.navigation.page.level) {
        AutomaticRecordingEditorLevel.FIRST -> when (state.navigation.focusedRow) {
            "keep" -> stringResource(R.string.autorec_editor_keep_help)
            "channel-time" -> stringResource(R.string.autorec_editor_channel_time_help)
            "more" -> stringResource(R.string.autorec_editor_more_help)
            else -> automaticRecordingDescription(state)
        }
        AutomaticRecordingEditorLevel.KEEP -> stringResource(R.string.autorec_editor_keep_help)
        AutomaticRecordingEditorLevel.CHANNEL_TIME, AutomaticRecordingEditorLevel.CHANNEL,
        AutomaticRecordingEditorLevel.DAYS, AutomaticRecordingEditorLevel.START_WINDOW,
        AutomaticRecordingEditorLevel.AFTER_HOUR, AutomaticRecordingEditorLevel.BEFORE_HOUR,
        AutomaticRecordingEditorLevel.AFTER_MINUTE, AutomaticRecordingEditorLevel.BEFORE_MINUTE ->
            stringResource(R.string.autorec_editor_channel_time_help)
        AutomaticRecordingEditorLevel.MORE -> stringResource(R.string.autorec_editor_more_help)
        else -> automaticRecordingDescription(state)
    }

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
    RecordingEditorSecondary(
        listOfNotNull(automaticRecordingEpisodeMeta(row, state), channel?.name).joinToString(" · "),
    )
    event?.description?.takeIf(String::isNotBlank)?.let { RecordingEditorSecondary(it) }
}

@Composable
private fun AutomaticRecordingFocusedChoice(state: AutomaticRecordingEditorState) {
    when (state.navigation.page.level) {
        AutomaticRecordingEditorLevel.FIRST -> AutomaticRecordingMainGuidance(state)
        AutomaticRecordingEditorLevel.MORE -> RecordingEditorSecondary(
            recordingEditorMoreSummary(state.changedFields.intersect(automaticRecordingMoreFields), state.editing))
        AutomaticRecordingEditorLevel.DUPLICATE -> {
            val focused = (listOf(DuplicateDetection.DVR_PROFILE) + offeredDuplicateDetection)
                .firstOrNull { "choice-$it" == state.navigation.focusedRow } ?: state.draft.duplicateDetection
            RecordingEditorSecondary(automaticRecordingDuplicateHelp(focused))
        }
        AutomaticRecordingEditorLevel.KEEP -> AutomaticRecordingKeepGuidance(state)
        AutomaticRecordingEditorLevel.MAXIMUM -> state.navigation.focusedRow.removePrefix("choice-").toLongOrNull()
            ?.takeIf { it > 0 }?.let {
                RecordingEditorSecondary(stringResource(R.string.autorec_editor_maximum_help, it))
            }
        else -> Unit
    }
}

@Composable
private fun AutomaticRecordingMainGuidance(state: AutomaticRecordingEditorState) {
    if (state.navigation.focusedRow == "more") RecordingEditorSecondary(
        recordingEditorMoreSummary(state.changedFields.intersect(automaticRecordingMoreFields), state.editing))
    if (state.navigation.focusedRow == "main") {
        val reason = when (state.mainProblem) {
            RecordingSubmitUnavailable.UNCHANGED -> stringResource(R.string.autorec_editor_unchanged)
            RecordingSubmitUnavailable.WORDS_REQUIRED -> stringResource(R.string.autorec_editor_enter_words)
            else -> null
        }
        reason?.let { RecordingEditorSecondary(it) }
    }
}

@Composable
private fun AutomaticRecordingKeepGuidance(state: AutomaticRecordingEditorState) {
    val focused = listOf(null, DvrRemoval.Days(7), DvrRemoval.Days(30), DvrRemoval.Days(90),
        DvrRemoval.Days(180), DvrRemoval.Days(365), DvrRemoval.UntilSpaceNeeded, DvrRemoval.Forever)
        .firstOrNull { "keep-$it" == state.navigation.focusedRow }
    RecordingEditorSecondary(automaticRecordingKeepLabel(focused ?: state.draft.removal.takeIf {
        "keep-$it" == state.navigation.focusedRow
    }))
}

@Composable
private fun AutomaticRecordingPassivePreview(state: AutomaticRecordingEditorState) {
    when (val preview = state.preview) {
        AutorecPreviewState.Loading -> RecordingEditorHeading(stringResource(R.string.autorec_editor_searching))
        is AutorecPreviewState.Failed ->
            RecordingEditorHeading(stringResource(R.string.autorec_editor_search_failed))
        AutorecPreviewState.NothingToSearch ->
            RecordingEditorHeading(stringResource(R.string.autorec_editor_empty_words))
        is AutorecPreviewState.Matches -> {
            if (preview.rows.isEmpty()) RecordingEditorHeading(stringResource(R.string.autorec_editor_no_matches))
            else if (!state.editing || state.draft.enabled) RecordingEditorHeading(pluralStringResource(
                if (state.draft.kind == AutomaticRecordingKind.SERIES) R.plurals.autorec_editor_upcoming_episodes
                    else R.plurals.autorec_editor_upcoming_programs,
                preview.willRecordCount, preview.willRecordCount,
            ))
            for (row in preview.rows.take(3)) {
                Column {
                    Text(automaticRecordingPreviewTitle(state.draft.kind, row),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    RecordingEditorSecondary(automaticRecordingEpisodeMeta(row, state))
                }
            }
            if (preview.rows.size > 3) RecordingEditorSecondary(stringResource(
                R.string.autorec_editor_more_preview, preview.rows.size - 3,
                stringResource(if (state.draft.kind == AutomaticRecordingKind.SERIES)
                    R.string.autorec_editor_upcoming else R.string.autorec_editor_words),
            ))
            if (preview.broadcastFilterUnavailable) RecordingEditorSecondary(
                stringResource(R.string.autorec_editor_broadcast_unavailable),
            )
        }
    }
    RecordingEditorSecondary(stringResource(R.string.autorec_editor_guide_horizon))
}

internal fun automaticRecordingPreviewTitle(kind: AutomaticRecordingKind, row: AutorecPreviewRow): String =
    (if (kind == AutomaticRecordingKind.SERIES) row.subtitle ?: row.title else row.title ?: row.subtitle).orEmpty()

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
