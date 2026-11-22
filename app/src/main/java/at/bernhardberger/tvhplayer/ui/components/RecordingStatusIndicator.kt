package at.bernhardberger.tvhplayer.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.R as AppR
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvRecordingFocusedColor
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha

/** Icon size for status marks set next to small text: chips, guide cells, list rows. */
val StatusIconSize: Dp = 16.dp

@Composable
internal fun recordingMarkSize(style: TextStyle = LocalTextStyle.current): Dp {
    val fontSize = style.fontSize.takeIf { it.isSpecified } ?: MaterialTheme.typography.labelMedium.fontSize
    return with(LocalDensity.current) { fontSize.toDp() } * (16f / 14f)
}

/** The kind of rule a recording comes from, when the server says it has one. */
enum class RecordingRule(@StringRes val label: Int) {
    SERIES(R.string.recording_from_series_rule),
    TIMER(R.string.recording_from_timer_rule),
    AUTOMATIC(R.string.recording_from_automatic),
}

/** ID-only fallback for callers such as Guide that cannot resolve the automatic recording's kind. */
val DvrEntry.rule: RecordingRule?
    get() = when {
        autorecRuleId != null -> RecordingRule.AUTOMATIC
        timerecRuleId != null -> RecordingRule.TIMER
        else -> null
    }

fun recordingRule(kind: AutomaticRecordingKind?): RecordingRule? = when (kind) {
    AutomaticRecordingKind.SERIES -> RecordingRule.SERIES
    AutomaticRecordingKind.TIMER -> RecordingRule.TIMER
    AutomaticRecordingKind.TITLE, AutomaticRecordingKind.KEYWORD -> RecordingRule.AUTOMATIC
    null -> null
}

/**
 * Recording now uses a red dot, with a half ring when a [rule] scheduled it; scheduled uses a clock,
 * stacked for a rule. A finished recording uses the muted library mark. Pass [focused] on a light row:
 * the recording mark turns deeper red.
 */
@Composable
fun RecordingStatusIndicator(
    state: DvrEntryState?,
    modifier: Modifier = Modifier,
    rule: RecordingRule? = null,
    announceState: Boolean = true,
    focused: Boolean = false,
    skipped: Boolean = false,
    descriptionOverride: String? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    size: Dp = recordingMarkSize(textStyle),
    scheduledTint: Color = LocalContentColor.current,
) {
    val content = LocalContentColor.current
    val (@DrawableRes icon, tint) = when (state) {
        DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR ->
            (if (rule != null) AppR.drawable.ic_fiber_smart_record else AppR.drawable.ic_fiber_manual_record) to
                recordingMarkColor(focused)
        DvrEntryState.COMPLETED -> AppR.drawable.ic_video_library to content.copy(alpha = TvTextSecondaryAlpha)
        DvrEntryState.COMPLETED_ERROR, DvrEntryState.MISSED ->
            AppR.drawable.ic_error_outlined to errorMarkColor(focused)
        DvrEntryState.SCHEDULED ->
            (when {
                skipped -> AppR.drawable.ic_event_busy
                rule != null -> AppR.drawable.ic_schedule_rule
                else -> AppR.drawable.ic_schedule
            }) to scheduledTint
        else -> return
    }
    Icon(
        painter = painterResource(icon),
        contentDescription = if (announceState) {
            descriptionOverride ?: recordingStatusSpoken(state, rule, skipped)
        } else null,
        modifier = modifier.size(size),
        tint = tint,
    )
}

@Composable
internal fun recordingStatusSpoken(state: DvrEntryState?, rule: RecordingRule?, skipped: Boolean = false): String? {
    if (state == null) return null
    val stateDescription = stringResource(recordingStatusDescription(state, rule, skipped))
    return if (rule != null && (state == DvrEntryState.RECORDING || state == DvrEntryState.RECORDING_ERROR)) {
        stringResource(R.string.recording_state_and_origin, stateDescription, stringResource(rule.label))
    } else stateDescription
}

@StringRes
private fun recordingStatusDescription(
    state: DvrEntryState,
    rule: RecordingRule?,
    skipped: Boolean,
): Int = when (state) {
    DvrEntryState.RECORDING_ERROR -> R.string.recording_state_recording_errors
    DvrEntryState.RECORDING -> R.string.recording_state_recording
    DvrEntryState.COMPLETED -> R.string.details_recorded
    DvrEntryState.COMPLETED_ERROR -> R.string.recording_state_failed
    DvrEntryState.MISSED -> R.string.recording_notice_missed
    DvrEntryState.SCHEDULED -> if (skipped) R.string.autorec_editor_skipped
        else rule?.label ?: R.string.recording_state_scheduled
    else -> rule?.label ?: R.string.recording_state_scheduled
}

/** The red of a recording mark drawn outside this indicator: deeper on a focused, light row. */
fun recordingMarkColor(focused: Boolean): Color = if (focused) TvRecordingFocusedColor else TvRecordingColor

/** The error colour of a mark: pink on the dark surface, dark red on a focused, light row. */
@Composable
internal fun errorMarkColor(focused: Boolean): Color =
    if (focused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.error
