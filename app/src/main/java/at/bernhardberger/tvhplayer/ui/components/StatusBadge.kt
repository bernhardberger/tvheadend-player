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
enum class StatusBadgeTone { NEUTRAL, RECORDING, ERROR }

private val StatusBadgeHeight = 20.dp
private val StatusBadgeShape = RoundedCornerShape(4.dp)
private val StatusBadgeGlyphSize = 12.dp

/**
 * The one status tag, in one size, on dark surfaces only: eyebrows, info headers, menus and image
 * corners. Rows and grid cells use the dot of [RecordingStatusIndicator] instead.
 */
@Composable
fun StatusBadge(
    text: String?,
    tone: StatusBadgeTone,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    contentDescription: String? = text,
) {
    val (container, content) = when (tone) {
        StatusBadgeTone.NEUTRAL -> TvSurfaceColors.containerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        StatusBadgeTone.RECORDING -> TvRecordingContainerColor to TvOnRecordingContainerColor
        // Material's badge role: the pink error fill under its dark text.
        StatusBadgeTone.ERROR -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
    }
    Row(
        modifier.background(container, StatusBadgeShape).height(StatusBadgeHeight).padding(horizontal = 6.dp)
            .clearAndSetSemantics { contentDescription?.let { this.contentDescription = it } },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(StatusBadgeGlyphSize), tint = content)
        } else if (tone == StatusBadgeTone.RECORDING) {
            Box(Modifier.size(6.dp).background(content, CircleShape))
        }
        if (text != null) {
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, color = content)
        }
    }
}

/**
 * A DVR state as badges: `● REC` while recording, followed by "Errors" when the recording has them;
 * a clock for scheduled; "Failed" for a recording that failed. A completed or cancelled recording
 * has none. [label] false keeps the scheduled clock without its word, for small image corners.
 */
@Composable
fun RecordingBadge(state: DvrEntryState?, modifier: Modifier = Modifier, label: Boolean = true) {
    when (state) {
        DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR -> {
            val description = stringResource(
                if (state == DvrEntryState.RECORDING) R.string.player_recording else R.string.recording_state_recording_errors
            )
            // One announcement for the pair; a caller's tag wins over the default one.
            Row(modifier.testTag("recording-badge").clearAndSetSemantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(stringResource(R.string.player_status_rec), StatusBadgeTone.RECORDING)
                if (state == DvrEntryState.RECORDING_ERROR) {
                    StatusBadge(stringResource(R.string.recording_badge_errors), StatusBadgeTone.ERROR,
                        icon = R.drawable.ic_error_outlined)
                }
            }
        }
        DvrEntryState.SCHEDULED -> {
            StatusBadge(stringResource(R.string.recording_state_scheduled).takeIf { label }, StatusBadgeTone.NEUTRAL,
                modifier.testTag("recording-badge-scheduled"), icon = R.drawable.ic_schedule,
                contentDescription = stringResource(R.string.details_status_scheduled))
        }
        DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING ->
            StatusBadge(stringResource(R.string.recording_state_failed), StatusBadgeTone.ERROR,
                modifier.testTag("recording-badge-failed"), icon = R.drawable.ic_error_outlined,
                contentDescription = stringResource(R.string.details_status_failed))
        else -> Unit
    }
}
