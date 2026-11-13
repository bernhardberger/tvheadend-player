package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.nextOccurrences
import at.bernhardberger.tvhplayer.core.resolveOccurrenceOn
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate
import at.bernhardberger.tvhplayer.ui.components.GuidedStepGuidance
import at.bernhardberger.tvhplayer.ui.components.StatusBadge
import at.bernhardberger.tvhplayer.ui.components.StatusBadgeTone

@Composable
internal fun TimerRecordingGuidance(
    state: TimerRecordingEditorState,
    channels: List<Channel>,
    scheduledEntries: List<DvrEntry>,
) {
    GuidedStepGuidance(
        title = stringResource(R.string.timer_editor_title),
        body = timerGuidanceBody(state, channels),
        lead = {
            Text(stringResource(if (state.editing) R.string.timer_editor_edit else R.string.timer_editor_new),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha))
        },
    )
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.deleting -> RecordingEditorHeading(stringResource(R.string.timer_editor_deleting))
            state.editing && !state.draft.enabled -> TimerRecordingRemoved(state, scheduledEntries)
            else -> {
                RecordingEditorHeading(if (state.navigation.page.level == AutomaticRecordingEditorLevel.FIRST)
                    timerUnavailableLabel(state) ?: timerSpanLabel(state) else timerSpanLabel(state))
                if (state.draft.name.isNotBlank() || channels.any { it.id == state.draft.channelId }) {
                    Text(timerNameLabel(state, channels), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                if (state.draft.repeating) TimerRecordingUpcoming(state) else TimerRecordingOneOff(state)
            }
        }
    }
}

@Composable
private fun timerGuidanceBody(state: TimerRecordingEditorState, channels: List<Channel>): String =
    when (state.navigation.page.level) {
        AutomaticRecordingEditorLevel.REPEAT -> stringResource(R.string.timer_editor_repeat_help)
        AutomaticRecordingEditorLevel.NAME -> stringResource(R.string.timer_editor_name_help)
        AutomaticRecordingEditorLevel.KEEP -> stringResource(R.string.timer_editor_keep_help)
        AutomaticRecordingEditorLevel.MORE -> stringResource(R.string.timer_editor_more_help)
        AutomaticRecordingEditorLevel.START_HOUR, AutomaticRecordingEditorLevel.END_HOUR ->
            stringResource(R.string.timer_editor_clock_help)
        AutomaticRecordingEditorLevel.START_MINUTE, AutomaticRecordingEditorLevel.END_MINUTE ->
            stringResource(R.string.timer_editor_minute_help)
        else -> if (state.draft.channelId == null) stringResource(R.string.timer_editor_no_channel_description)
            else stringResource(R.string.timer_editor_description, timerChannelLabel(state, channels),
                formatClock(state.draft.start), formatClock(state.draft.end))
    }

@Composable
private fun TimerRecordingOneOff(state: TimerRecordingEditorState) {
    if (state.draft.end < state.draft.start) {
        StatusBadge(stringResource(R.string.timer_editor_next_day), StatusBadgeTone.NEUTRAL,
            icon = R.drawable.ic_event)
        RecordingEditorSecondary(stringResource(R.string.timer_editor_ends,
            timerDateLabel(state, state.draft.date.plusDays(1)), formatClock(state.draft.end)))
    }
    val occurrence = state.draft.resolveOccurrenceOn(state.draft.date, state.zone)
    if (occurrence.start < state.now() && occurrence.stop > state.now())
        RecordingEditorSecondary(stringResource(R.string.timer_editor_starts_now))
}

@Composable
private fun TimerRecordingUpcoming(state: TimerRecordingEditorState) {
    RecordingEditorSecondary(stringResource(R.string.timer_editor_next_recordings))
    val occurrences = state.draft.nextOccurrences(state.now(), state.zone, 3)
    if (occurrences.isEmpty()) RecordingEditorSecondary(stringResource(R.string.timer_editor_none_upcoming))
    for (occurrence in occurrences) RecordingEditorSecondary(
        formatRecordingDate(occurrence.start.epochSeconds, dateOnly = true, nowSec = state.now().epochSeconds) +
            " · ${formatClock(state.draft.start)} – ${formatClock(state.draft.end)}",
    )
    RecordingEditorSecondary(stringResource(R.string.timer_editor_no_padding))
}

@Composable
private fun TimerRecordingRemoved(state: TimerRecordingEditorState, entries: List<DvrEntry>) {
    RecordingEditorHeading(stringResource(R.string.autorec_editor_off_after_save))
    RecordingEditorSecondary(stringResource(R.string.autorec_editor_off_help))
    for (entry in entries.take(3)) {
        Text(entry.title.orEmpty(), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        RecordingEditorSecondary(listOfNotNull(
            entry.start?.let { formatRecordingDate(it.epochSeconds, nowSec = state.now().epochSeconds) },
            stringResource(R.string.autorec_editor_removed),
        ).joinToString(" · "))
    }
    if (entries.size > 3) RecordingEditorSecondary(
        stringResource(R.string.timer_editor_more_removed, entries.size - 3),
    )
}
