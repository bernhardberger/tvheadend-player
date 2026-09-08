package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ProgrammeAxis
import at.bernhardberger.tvhplayer.core.SeekbarDomain
import at.bernhardberger.tvhplayer.core.SeekbarRange
import at.bernhardberger.tvhplayer.core.TimeshiftPositionPresentation
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.seekbarScrub
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation

/** Focusable seekbar for timeshift and recording playback. */
@Composable
fun PlaybackSeekbar(
    range: SeekbarRange,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    programmeAxis: ProgrammeAxis? = null,
    programmePositionMs: Long? = null,
    programmeDurationMs: Long? = null,
    programmeWindow: ProgrammeWindow? = null,
    previewing: Boolean = false,
    feedback: String? = null,
    showFeedback: Boolean = true,
    /**
     * Live-edge presentation to label the timeline with. Defaults to the measured
     * distance within [range]; live callers pass the server-shift-aware presentation.
     */
    timeshiftPosition: TimeshiftPositionPresentation? = if (range.domain == SeekbarDomain.TIMESHIFT) {
        timeshiftPositionPresentation(range.positionMs, range.endMs)
    } else {
        null
    },
) {
    if (!range.positionKnown) {
        val unavailable = (if (paused) "${stringResource(R.string.player_paused)}. " else "") +
            stringResource(R.string.player_timing_unavailable)
        PlayerTimelineBlock(
            progress = null,
            tone = PlayerTimelineTone.AMBIENT,
            leadingLabel = unavailable,
            rewindableStartFraction = range.availableStartFraction,
            liveEdgeFraction = 1f,
            progressSemantics = false,
            reserveLabelSpace = true,
            modifier = modifier
                .semantics { contentDescription = unavailable }
                .padding(vertical = 8.dp),
        )
        return
    }
    var focused by remember { mutableStateOf(false) }
    val programmeDuration = programmeDurationMs?.takeIf { it > 0L }
    val programmePosition = programmePositionMs?.coerceIn(0L, programmeDuration ?: 0L)
    val displayedProgress = if (range.domain == SeekbarDomain.TIMESHIFT) {
        range.displayProgress
    } else {
        programmeAxis?.playbackFraction ?: range.progress
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
    val positionDescription = if (range.positionEstimated) {
        "${stringResource(R.string.player_timing_estimated)}. $description"
    } else description
    val stateDescription = if (paused) "${stringResource(R.string.player_paused)}. $positionDescription" else positionDescription
    val positionLabel = when {
        // The status above the track already says Live; a distance adds nothing.
        timeshiftPosition?.atLiveEdge == true -> null
        timeshiftPosition != null -> stringResource(
            R.string.timeshift_behind_live,
            formatPlaybackDuration(timeshiftPosition.behindLiveMs),
        )
        range.domain == SeekbarDomain.RECORDING -> {
            val elapsed = formatPlaybackDuration(range.positionMs)
            if (range.endMs >= 3_600_000L && range.positionMs < 3_600_000L) "0:${elapsed.padStart(5, '0')}" else elapsed
        }
        programmePosition != null && programmeDuration != null ->
            formatPlaybackDuration(programmePosition)
        else -> null
    }
    val trailingLabel = when {
        timeshiftPosition != null -> null
        range.domain == SeekbarDomain.RECORDING -> formatPlaybackDuration(range.endMs)
        programmePosition != null && programmeDuration != null ->
            formatPlaybackDuration(programmeDuration)
        else -> null
    }
    val seekBackLabel = stringResource(R.string.seek_back_30)
    val seekForwardLabel = stringResource(R.string.seek_forward_30)
    val seekBackTarget = seekbarScrub(range, -1, 0)
    val seekForwardTarget = seekbarScrub(range, 1, 0)
    val accessibilityProgress = programmeWindow?.positionFraction ?: if (range.domain == SeekbarDomain.TIMESHIFT) {
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
    val clockLabels = programmeWindow?.let { programmeWindowClockLabels(it.event) }
    val unavailableTarget = stringResource(R.string.timeshift_target_expired)
    val windowFeedback = feedback ?: programmeWindow?.event?.title?.takeIf { previewing && it.isNotBlank() }
    Column {
        if (windowFeedback != null && showFeedback) {
            Row(Modifier.fillMaxWidth().layout { measurable, constraints ->
                val row = measurable.measure(constraints.copy(minHeight = 0))
                // Feedback floats above the target readout; neither creates an empty row.
                layout(row.width, 0) { row.placeRelative(0, -row.height - 24.sp.roundToPx()) }
            }) {
                Text(
                    text = windowFeedback,
                    color = if (feedback != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("player-window-title")
                        .then(if (feedback != null) Modifier.padding(end = 24.dp).wrapContentWidth(Alignment.Start).background(
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.shapes.small,
                        ).padding(horizontal = 8.dp) else Modifier),
                )
            }
        }
        PlayerTimelineBlock(
            progress = displayedProgress,
            tone = if (focused) PlayerTimelineTone.ACTIVE else PlayerTimelineTone.INTERACTIVE,
            // The estimate qualifier stays in the accessible description; visibly it is noise.
            leadingLabel = clockLabels?.first ?: listOfNotNull(
                stringResource(R.string.player_paused).takeIf { paused },
                positionLabel,
            ).joinToString(" / ").takeIf { it.isNotBlank() },
            trailingLabel = clockLabels?.second ?: trailingLabel,
            trailingLabelTestTag = "player-window-end".takeIf { programmeWindow != null },
            leadingLabelTestTag = if (programmeWindow != null) "player-window-start" else "player-programme-progress".takeIf {
                timeshiftPosition == null && programmePosition != null && programmeDuration != null
            },
            rewindableStartFraction = range.availableStartFraction
                .takeIf { range.domain == SeekbarDomain.TIMESHIFT },
            rewindableStartOverflow = false,
            liveEdgeFraction = 1f.takeIf { range.domain == SeekbarDomain.TIMESHIFT },
            thumbTestTag = "player-seekbar-thumb",
            progressSemantics = false,
            programmeWindow = programmeWindow,
            reserveLabelSpace = true,
            previewLabel = if (previewing && timeshiftPosition != null) {
                if (timeshiftPosition.atLiveEdge) stringResource(R.string.timeshift_live)
                else "−${formatPlaybackDuration(timeshiftPosition.behindLiveMs)}"
            } else if (previewing && range.domain == SeekbarDomain.RECORDING) positionLabel else null,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            val target = seekbarScrub(range, -1, event.nativeKeyEvent.repeatCount)
                            if (target < range.positionMs) onSeekTo(target)
                            true
                        }
                        Key.DirectionRight -> {
                            val target = seekbarScrub(range, 1, event.nativeKeyEvent.repeatCount)
                            if (target > range.positionMs) onSeekTo(target)
                            true
                        }
                        else -> false
                    }
                }
                .semantics {
                    contentDescription = programmeWindow?.let {
                        "${it.event.title.orEmpty()}. ${clockLabels?.first} - ${clockLabels?.second}. $stateDescription" +
                            if (!it.targetAvailable) " $unavailableTarget" else ""
                    } ?: stateDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(accessibilityProgress, 0f..1f)
                    customActions = accessibilityActions
                }
                .focusable()
                .padding(vertical = 8.dp),
        )
    }
}
