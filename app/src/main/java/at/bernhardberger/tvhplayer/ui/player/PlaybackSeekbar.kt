package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ProgrammeAxis
import at.bernhardberger.tvhplayer.core.SeekbarDomain
import at.bernhardberger.tvhplayer.core.SeekbarRange
import at.bernhardberger.tvhplayer.core.TIMESHIFT_LIVE_EDGE_TOLERANCE_MS
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.seekbarScrub
import kotlin.math.abs

/** Focusable seekbar for timeshift and recording playback. */
@Composable
fun PlaybackSeekbar(
    range: SeekbarRange,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    programmeAxis: ProgrammeAxis? = null,
    programmePositionMs: Long? = null,
    programmeDurationMs: Long? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val programmeDuration = programmeDurationMs?.takeIf { it > 0L }
    val programmePosition = programmePositionMs?.coerceIn(0L, programmeDuration ?: 0L)
    val displayedProgress = programmeAxis?.playbackFraction ?: range.progress
    val description = when {
        programmeAxis != null && programmePosition != null && programmeDuration != null ->
            stringResource(
                R.string.player_programme_progress_description,
                formatPlaybackDuration(programmePosition),
                formatPlaybackDuration(programmeDuration),
            )
        range.domain == SeekbarDomain.RECORDING -> stringResource(
            R.string.player_seekbar_recording_description,
            formatPlaybackDuration(range.positionMs),
            formatPlaybackDuration(range.endMs),
        )
        else -> stringResource(
            R.string.player_seekbar_timeshift_description,
            formatPlaybackDuration(abs(range.positionMs)),
        )
    }
    val leadingLabel = when {
        programmePosition != null && programmeDuration != null ->
            formatPlaybackDuration(programmePosition)
        range.domain == SeekbarDomain.RECORDING -> formatPlaybackDuration(range.positionMs)
        range.endMs - range.positionMs > TIMESHIFT_LIVE_EDGE_TOLERANCE_MS ->
            "−${formatPlaybackDuration(abs(range.positionMs))}"
        else -> null
    }
    val trailingLabel = when {
        programmePosition != null && programmeDuration != null ->
            formatPlaybackDuration(programmeDuration)
        range.domain == SeekbarDomain.RECORDING -> formatPlaybackDuration(range.endMs)
        else -> null
    }
    PlayerTimelineBlock(
        progress = displayedProgress,
        tone = if (focused) PlayerTimelineTone.ACTIVE else PlayerTimelineTone.INTERACTIVE,
        leadingLabel = leadingLabel,
        trailingLabel = trailingLabel,
        leadingLabelTestTag = "player-programme-progress".takeIf {
            programmePosition != null && programmeDuration != null
        },
        rewindableStartFraction = programmeAxis?.rewindableStartFraction
            ?: 0f.takeIf { range.domain == SeekbarDomain.TIMESHIFT },
        liveEdgeFraction = programmeAxis?.liveEdgeFraction
            ?: 1f.takeIf { range.domain == SeekbarDomain.TIMESHIFT },
        thumbTestTag = "player-seekbar-thumb",
        progressSemantics = false,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeekTo(seekbarScrub(range, -1, event.nativeKeyEvent.repeatCount))
                        true
                    }
                    Key.DirectionRight -> {
                        onSeekTo(seekbarScrub(range, 1, event.nativeKeyEvent.repeatCount))
                        true
                    }
                    else -> false
                }
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(displayedProgress, 0f..1f)
            }
            .padding(vertical = 8.dp),
    )
}
