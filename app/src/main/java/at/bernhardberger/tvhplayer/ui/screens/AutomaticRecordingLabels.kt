package at.bernhardberger.tvhplayer.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import at.bernhardberger.tvheadend.sdk.core.BroadcastType
import at.bernhardberger.tvheadend.sdk.core.DuplicateDetection
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrPriority
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.core.RecordingDraftField
import kotlin.time.Duration

internal fun formatAutomaticRecordingPreviewSummary(
    context: Context,
    kind: AutomaticRecordingKind,
    preview: AutorecPreviewState.Matches,
): String {
    if (preview.matchCount == 0) return context.getString(R.string.autorec_editor_no_matches)
    val episodes = kind == AutomaticRecordingKind.SERIES
    val all = preview.matchCount == preview.willRecordCount
    val resource = when {
        episodes && all -> R.plurals.autorec_editor_episodes_all
        episodes -> R.plurals.autorec_editor_episodes_selected
        all -> R.plurals.autorec_editor_programs_all
        else -> R.plurals.autorec_editor_programs_selected
    }
    return context.resources.getQuantityString(
        resource, preview.matchCount, preview.matchCount, preview.willRecordCount,
    )
}

@Composable
internal fun automaticRecordingEditorEyebrow(state: AutomaticRecordingEditorState): String = stringResource(
    when (state.draft.kind) {
        AutomaticRecordingKind.SERIES ->
            if (state.editing) R.string.context_edit_series else R.string.autorec_editor_new_series
        AutomaticRecordingKind.TITLE ->
            if (state.editing) R.string.context_edit_title else R.string.autorec_editor_new_title
        else -> if (state.editing) R.string.context_edit_keyword else R.string.autorec_editor_new_keyword
    },
)

@Composable
internal fun automaticRecordingFieldLabel(field: RecordingDraftField): String = stringResource(when (field) {
    RecordingDraftField.DUPLICATE_DETECTION -> R.string.autorec_editor_duplicate
    RecordingDraftField.PRIORITY -> R.string.autorec_editor_priority
    RecordingDraftField.PADDING_BEFORE -> R.string.autorec_editor_padding_before
    RecordingDraftField.PADDING_AFTER -> R.string.autorec_editor_padding_after
    RecordingDraftField.BROADCAST_TYPE -> R.string.autorec_editor_broadcast
    RecordingDraftField.MAXIMUM_RECORDINGS -> R.string.autorec_editor_maximum
    RecordingDraftField.PROFILE -> R.string.details_recording_profile
    else -> R.string.autorec_editor_more
})

@Composable
internal fun automaticRecordingOptionValue(
    state: AutomaticRecordingEditorState,
    field: RecordingDraftField,
    configurations: List<DvrConfiguration>,
): String = when (field) {
    RecordingDraftField.DUPLICATE_DETECTION -> automaticRecordingDuplicateLabel(state.draft.duplicateDetection)
    RecordingDraftField.PRIORITY -> automaticRecordingPriorityLabel(state.draft.priority)
    RecordingDraftField.PADDING_BEFORE -> automaticRecordingPaddingLabel(state.draft.paddingBefore)
    RecordingDraftField.PADDING_AFTER -> automaticRecordingPaddingLabel(state.draft.paddingAfter)
    RecordingDraftField.BROADCAST_TYPE -> automaticRecordingBroadcastLabel(state.draft.broadcastType)
    RecordingDraftField.MAXIMUM_RECORDINGS -> automaticRecordingMaximumLabel(state.draft.maximumRecordings)
    RecordingDraftField.PROFILE -> configurations.firstOrNull { it.id == state.draft.configId }?.name
        ?: stringResource(if (state.draft.configId == null)
            R.string.profile_server_default else R.string.autorec_editor_current_setting)
    else -> ""
}

@Composable
internal fun automaticRecordingDuplicateLabel(value: DuplicateDetection): String = stringResource(when (value) {
    DuplicateDetection.DVR_PROFILE -> R.string.profile_server_default
    DuplicateDetection.ALL -> R.string.autorec_editor_duplicate_all
    DuplicateDetection.DIFFERENT_EPISODE_NUMBER -> R.string.autorec_editor_duplicate_episode
    DuplicateDetection.DIFFERENT_SUBTITLE -> R.string.autorec_editor_duplicate_subtitle
    DuplicateDetection.DIFFERENT_DESCRIPTION -> R.string.autorec_editor_duplicate_description
    DuplicateDetection.ONCE_PER_DAY -> R.string.autorec_editor_duplicate_day
    DuplicateDetection.ONCE_PER_WEEK -> R.string.autorec_editor_duplicate_week
    else -> R.string.autorec_editor_current_setting
})

@Composable
internal fun automaticRecordingDuplicateHelp(value: DuplicateDetection): String = stringResource(when (value) {
    DuplicateDetection.DVR_PROFILE -> R.string.autorec_editor_duplicate_default_help
    DuplicateDetection.ALL -> R.string.autorec_editor_duplicate_all_help
    DuplicateDetection.DIFFERENT_EPISODE_NUMBER -> R.string.autorec_editor_duplicate_episode_help
    DuplicateDetection.DIFFERENT_SUBTITLE -> R.string.autorec_editor_duplicate_subtitle_help
    DuplicateDetection.DIFFERENT_DESCRIPTION -> R.string.autorec_editor_duplicate_description_help
    DuplicateDetection.ONCE_PER_DAY -> R.string.autorec_editor_duplicate_day_help
    DuplicateDetection.ONCE_PER_WEEK -> R.string.autorec_editor_duplicate_week_help
    else -> R.string.autorec_editor_current_help
})

@Composable
internal fun automaticRecordingPriorityLabel(value: DvrPriority?): String = stringResource(when (value) {
    null, DvrPriority.DEFAULT -> R.string.autorec_editor_default
    DvrPriority.IMPORTANT -> R.string.autorec_editor_priority_important
    DvrPriority.HIGH -> R.string.autorec_editor_priority_high
    DvrPriority.NORMAL -> R.string.autorec_editor_priority_normal
    DvrPriority.LOW -> R.string.autorec_editor_priority_low
    DvrPriority.UNIMPORTANT -> R.string.autorec_editor_priority_unimportant
    DvrPriority.NOT_SET -> R.string.autorec_editor_priority_unset
    else -> R.string.autorec_editor_current_setting
})

@Composable
internal fun automaticRecordingPaddingLabel(value: Duration?): String =
    if (value == null || value == Duration.ZERO) stringResource(R.string.profile_server_default)
    else pluralStringResource(R.plurals.autorec_editor_minutes, value.inWholeMinutes.toInt(), value.inWholeMinutes)

@Composable
internal fun automaticRecordingBroadcastLabel(value: BroadcastType): String = stringResource(when (value) {
    BroadcastType.ALL -> R.string.autorec_editor_any
    BroadcastType.NEW -> R.string.autorec_editor_new_only
    BroadcastType.REPEAT -> R.string.autorec_editor_repeats_only
    BroadcastType.NEW_OR_UNKNOWN -> R.string.autorec_editor_new_unknown
    else -> R.string.autorec_editor_current_setting
})

@Composable
internal fun automaticRecordingMaximumLabel(value: Long): String =
    if (value == 0L) stringResource(R.string.autorec_editor_no_limit)
    else pluralStringResource(R.plurals.autorec_editor_maximum_choice, value.toInt(), value)
