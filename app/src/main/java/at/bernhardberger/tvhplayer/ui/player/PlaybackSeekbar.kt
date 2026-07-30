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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ProgrammeAxis
import at.bernhardberger.tvhplayer.core.SeekbarDomain
import at.bernhardberger.tvhplayer.core.SeekbarRange
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.seekbarScrub
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation

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
    val timeshiftPosition = if (range.domain == SeekbarDomain.TIMESHIFT) {
        timeshiftPositionPresentation(range.positionMs, range.endMs)
    } else {
        null
    }
    val timeshiftBoundary = if (range.domain == SeekbarDomain.TIMESHIFT) {
        stringResource(
            R.string.timeshift_buffer_start_description,
            formatPlaybackDuration((range.endMs - range.startMs).coerceAtLeast(0L)),
        )
    } else {
        null
    }
    val description = when {
        timeshiftPosition?.atLiveEdge == true -> stringResource(
            R.string.player_seekbar_timeshift_live_description,
            requireNotNull(timeshiftBoundary),
        )
        timeshiftPosition != null -> stringResource(
            R.string.player_seekbar_timeshift_description,
            formatPlaybackDuration(timeshiftPosition.behindLiveMs),
            requireNotNull(timeshiftBoundary),
        )
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
        else -> error("Unsupported seekbar domain")
    }
    val leadingLabel = when {
        timeshiftPosition != null && !timeshiftPosition.atLiveEdge -> stringResource(
            R.string.timeshift_behind_live,
            formatPlaybackDuration(timeshiftPosition.behindLiveMs),
        )
        range.domain == SeekbarDomain.RECORDING -> formatPlaybackDuration(range.positionMs)
        programmePosition != null && programmeDuration != null ->
            formatPlaybackDuration(programmePosition)
        else -> null
    }
    val trailingLabel = when {
        timeshiftPosition != null -> stringResource(R.string.timeshift_live)
        range.domain == SeekbarDomain.RECORDING -> formatPlaybackDuration(range.endMs)
        programmePosition != null && programmeDuration != null ->
            formatPlaybackDuration(programmeDuration)
        else -> null
    }
    val seekBackLabel = stringResource(R.string.seek_back_30)
    val seekForwardLabel = stringResource(R.string.seek_forward_30)
    val seekBackTarget = seekbarScrub(range, -1, 0)
    val seekForwardTarget = seekbarScrub(range, 1, 0)
    val accessibilityProgress = if (range.domain == SeekbarDomain.TIMESHIFT) {
        range.progress
    } else {
        displayedProgress
    }
    val accessibilityActions = buildList {
        if (seekBackTarget < range.positionMs) {
            add(
                CustomAccessibilityAction(seekBackLabel) {
                    onSeekTo(seekBackTarget)
                    true
                }
            )
        }
        if (
            seekForwardTarget > range.positionMs &&
            timeshiftPosition?.atLiveEdge != true
        ) {
            add(
                CustomAccessibilityAction(seekForwardLabel) {
                    onSeekTo(seekForwardTarget)
                    true
                }
            )
        }
    }
    PlayerTimelineBlock(
        progress = displayedProgress,
        tone = if (focused) PlayerTimelineTone.ACTIVE else PlayerTimelineTone.INTERACTIVE,
        leadingLabel = leadingLabel,
        trailingLabel = trailingLabel,
        leadingLabelTestTag = "player-programme-progress".takeIf {
            timeshiftPosition == null && programmePosition != null && programmeDuration != null
        },
        rewindableStartFraction = programmeAxis?.rewindableStartFraction
            ?: 0f.takeIf { range.domain == SeekbarDomain.TIMESHIFT },
        rewindableStartOverflow = programmeAxis?.rewindableStartsBeforeProgramme == true,
        liveEdgeFraction = programmeAxis?.liveEdgeFraction
            ?: 1f.takeIf { range.domain == SeekbarDomain.TIMESHIFT },
        thumbTestTag = "player-seekbar-thumb",
        progressSemantics = false,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
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
                progressBarRangeInfo = ProgressBarRangeInfo(accessibilityProgress, 0f..1f)
                customActions = accessibilityActions
            }
            .focusable()
            .padding(vertical = 8.dp),
    )
}
