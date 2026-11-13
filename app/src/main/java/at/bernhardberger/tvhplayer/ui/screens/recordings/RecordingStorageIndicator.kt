package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.DvrDiskSpaceState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.RecordingSpaceIndicator
import at.bernhardberger.tvhplayer.core.RecordingSpaceLevel
import at.bernhardberger.tvhplayer.core.recordingSpaceIndicator
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.TvWarningColor
import at.bernhardberger.tvhplayer.ui.common.formatFileSize
import kotlin.math.roundToInt

private val StorageRingSize = 20.dp
private val StorageRingStroke = 2.5.dp

/**
 * How much recording storage is left, quiet at the end of the Recordings tab line: a small ring that
 * fills with the share in use, then one muted line with the free space and the total. Only the ring
 * turns amber or red as space runs low. Only a current reading is drawn; anything else says the space
 * is unknown rather than showing old numbers. It has no focus stop and is spoken as one line.
 */
@Composable
internal fun RecordingStorageIndicator(state: DvrDiskSpaceState, modifier: Modifier = Modifier) {
    val indicator = state.recordingSpaceIndicator()
    if (indicator == null) {
        val unknown = stringResource(R.string.recording_space_unknown)
        Text(
            text = unknown,
            style = MaterialTheme.typography.bodySmall,
            color = TvTextColors.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.testTag("recordings-space").semantics { contentDescription = unknown },
        )
        return
    }
    val context = LocalContext.current
    val free = formatFileSize(indicator.freeBytes, context)
    val total = formatFileSize(indicator.totalBytes, context)
    val percent = (indicator.usedFraction * 100).roundToInt()
    val description = stringResource(R.string.recording_space_description, free, total, percent)
    val emphasis = when (indicator.level) {
        RecordingSpaceLevel.NORMAL -> null
        RecordingSpaceLevel.LOW -> TvWarningColor
        RecordingSpaceLevel.CRITICAL -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier.testTag("recordings-space").clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(TvSpacing12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StorageRing(indicator, emphasis ?: TvTextColors.secondary)
        Text(
            text = stringResource(R.string.recording_space_value, free, total),
            style = MaterialTheme.typography.bodySmall,
            color = TvTextColors.secondary,
            maxLines = 1,
            modifier = Modifier.testTag("recordings-space-text"),
        )
    }
}

@Composable
private fun StorageRing(indicator: RecordingSpaceIndicator, arc: Color) {
    CircularProgressIndicator(
        progress = { indicator.usedFraction },
        modifier = Modifier.size(StorageRingSize).testTag("recordings-space-ring"),
        color = arc,
        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        gapSize = 0.dp,
        strokeWidth = StorageRingStroke,
        strokeCap = StrokeCap.Round,
    )
}
