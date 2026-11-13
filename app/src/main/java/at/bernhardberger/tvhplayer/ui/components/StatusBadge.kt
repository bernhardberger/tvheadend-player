package at.bernhardberger.tvhplayer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvOnRecordingContainerColor
import at.bernhardberger.tvhplayer.ui.TvRecordingContainerColor
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

/** The fill of a [StatusBadge]: neutral facts, recording now, or a failure. */
enum class StatusBadgeTone { NEUTRAL, RECORDING, ERROR, CHANGED }

private val StatusBadgeHeight = 20.dp
private val StatusBadgeShape = RoundedCornerShape(4.dp)
private val StatusBadgeGlyphSize = 12.dp
private val CompactStatusBadgeHeight = 16.dp

/**
 * The one status tag, in one size, on dark surfaces only: eyebrows, info headers, menus and image
 * corners; [compact] steps it down for the corner of a small card. Rows and grid cells use the dot
 * of [RecordingStatusIndicator] instead.
 */
@Composable
fun StatusBadge(
    text: String?,
    tone: StatusBadgeTone,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    contentDescription: String? = text,
    compact: Boolean = false,
) {
    val (container, content) = when (tone) {
        StatusBadgeTone.NEUTRAL -> TvSurfaceColors.containerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        StatusBadgeTone.RECORDING -> TvRecordingContainerColor to TvOnRecordingContainerColor
        // Material's badge role: the pink error fill under its dark text.
        StatusBadgeTone.ERROR -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        StatusBadgeTone.CHANGED ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Row(
        modifier.background(container, StatusBadgeShape)
            .height(if (compact) CompactStatusBadgeHeight else StatusBadgeHeight).padding(horizontal = if (compact) 4.dp else 6.dp)
            .clearAndSetSemantics { contentDescription?.let { this.contentDescription = it } },
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(StatusBadgeGlyphSize), tint = content)
        } else if (tone == StatusBadgeTone.RECORDING) {
            Box(Modifier.size(if (compact) 5.dp else 6.dp).background(content, CircleShape))
        }
        if (text != null) {
            Text(text, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, color = content)
        }
    }
}

/**
 * A DVR state as badges: `● REC` while recording, followed by "Errors" when the recording has them;
 * a clock for scheduled, stacked clocks when a [rule] scheduled it; "Failed" for a recording that
 * failed. A completed or cancelled recording has none. [label] false keeps the scheduled clock
 * without its word, for small image corners; [compact] steps the recording badges down for the
 * corner of a small card.
 */
@Composable
fun RecordingBadge(
    state: DvrEntryState?,
    modifier: Modifier = Modifier,
    label: Boolean = true,
    compact: Boolean = false,
    rule: RecordingRule? = null,
    skipped: Boolean = false,
) {
    when (state) {
        DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR -> {
            val stateDescription = stringResource(
                if (state == DvrEntryState.RECORDING) R.string.player_recording else R.string.recording_state_recording_errors
            )
            val description = if (rule == null) stateDescription else stringResource(
                R.string.recording_state_and_origin, stateDescription, stringResource(rule.label))
            // One announcement for the pair; a caller's tag wins over the default one.
            Row(modifier.testTag("recording-badge").clearAndSetSemantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(stringResource(R.string.player_status_rec), StatusBadgeTone.RECORDING, compact = compact,
                    icon = R.drawable.ic_fiber_smart_record.takeIf { rule != null })
                if (state == DvrEntryState.RECORDING_ERROR) {
                    StatusBadge(stringResource(R.string.recording_badge_errors), StatusBadgeTone.ERROR,
                        icon = R.drawable.ic_error_outlined, compact = compact)
                }
            }
        }
        DvrEntryState.SCHEDULED -> {
            val text = stringResource(
                if (skipped) R.string.autorec_editor_skipped else R.string.recording_state_scheduled)
            StatusBadge(text.takeIf { label }, StatusBadgeTone.NEUTRAL,
                modifier.testTag("recording-badge-scheduled"),
                icon = if (rule != null) R.drawable.ic_schedule_rule else R.drawable.ic_schedule,
                contentDescription = if (skipped) text else stringResource(
                    rule?.label ?: R.string.details_status_scheduled))
        }
        DvrEntryState.COMPLETED_ERROR ->
            StatusBadge(stringResource(R.string.recording_state_failed), StatusBadgeTone.ERROR,
                modifier.testTag("recording-badge-failed"), icon = R.drawable.ic_error_outlined,
                contentDescription = stringResource(R.string.details_status_failed))
        DvrEntryState.MISSED ->
            StatusBadge(stringResource(R.string.recording_state_missed), StatusBadgeTone.ERROR,
                modifier.testTag("recording-badge-missed"), icon = R.drawable.ic_error_outlined,
                contentDescription = stringResource(R.string.recording_notice_missed))
        DvrEntryState.INVALID ->
            StatusBadge(stringResource(R.string.recording_state_cancelled), StatusBadgeTone.NEUTRAL,
                modifier.testTag("recording-badge-cancelled"), icon = R.drawable.ic_cancel_outlined)
        else -> Unit
    }
}
