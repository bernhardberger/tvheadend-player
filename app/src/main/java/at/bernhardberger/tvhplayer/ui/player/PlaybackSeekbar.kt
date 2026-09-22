package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
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
    recordingMarkerPreviewMs: Long? = null,
    paused: Boolean = false,
    programmeAxis: ProgrammeAxis? = null,
    programmePositionMs: Long? = null,
    programmeDurationMs: Long? = null,
    programmeWindow: ProgrammeWindow? = null,
    previewing: Boolean = false,
    feedback: String? = null,
    feedbackIsError: Boolean = feedback != null,
    statusAction: (@Composable () -> Unit)? = null,
    collapsed: Boolean = false,
    recordingMarkers: List<Long> = emptyList(),
    onOpenRecordingMarkers: (() -> Unit)? = null,
    trackOverlay: (@Composable BoxScope.() -> Unit)? = null,
    /**
     * Enables timeshift labels. Timeline geometry and positional labels use the sampled
     * range; server-shift-aware Live status belongs to the passive player clock only.
     */
    timeshiftPosition: TimeshiftPositionPresentation? = if (range.domain == SeekbarDomain.TIMESHIFT) {
        timeshiftPositionPresentation(range.positionMs, range.endMs)
    } else {
        null
    },
) {
    if (!range.positionKnown) {
        val unavailable = stringResource(R.string.player_timing_unavailable)
        val description = (if (paused) "${stringResource(R.string.player_paused)}. " else "") + unavailable
        PlayerTimelineBlock(
            progress = null,
            collapsed = collapsed,
            tone = PlayerTimelineTone.AMBIENT,
            leadingLabel = unavailable,
            rewindableStartFraction = range.availableStartFraction,
            liveEdgeFraction = 1f,
            progressSemantics = false,
            reserveLabelSpace = true,
            statusAction = statusAction,
            feedback = feedback,
            feedbackIsError = feedbackIsError,
            timelineModifier = modifier.semantics { contentDescription = description },
        )
        return
    }
    var focused by remember { mutableStateOf(false) }
    val programmeDuration = programmeDurationMs?.takeIf { it > 0L }
    val programmePosition = programmePositionMs?.coerceIn(0L, programmeDuration ?: 0L)
    val markerPreview = recordingMarkerPreviewMs?.takeIf {
        range.domain == SeekbarDomain.RECORDING && it >= range.startMs && it < range.endMs
    }
    val sampledPosition = timeshiftPosition?.let {
        timeshiftPositionPresentation(range.positionMs, if (programmeWindow == null) range.displayEndMs else range.endMs)
    }
    val displayedProgress = if (range.domain == SeekbarDomain.TIMESHIFT) {
        range.displayProgress
    } else {
        programmeAxis?.playbackFraction ?: range.displayProgress
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
        sampledPosition?.atLiveEdge == true -> stringResource(
            R.string.player_seekbar_timeshift_live_description,
            requireNotNull(timeshiftBoundary),
        )
        sampledPosition != null -> stringResource(
            R.string.player_seekbar_timeshift_description,
            formatPlaybackDuration(sampledPosition.behindLiveMs),
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
            formatPlaybackDuration(range.displayEndMs),
        )
        else -> error("Unsupported seekbar domain")
    }
    val positionDescription = if (range.positionEstimated || range.displayEndMs > range.endMs) {
        "${stringResource(R.string.player_timing_estimated)}. $description"
    } else description
    val stateDescription = if (paused) "${stringResource(R.string.player_paused)}. $positionDescription" else positionDescription
    val positionLabel = when {
        sampledPosition != null -> timeshiftEndpointLabel(
            false, (range.displayEndMs - range.displayStartMs).coerceAtLeast(0),
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
        sampledPosition != null -> formatPlaybackDuration(0)
        range.domain == SeekbarDomain.RECORDING -> formatPlaybackDuration(range.displayEndMs)
        programmePosition != null && programmeDuration != null ->
            formatPlaybackDuration(programmeDuration)
        else -> null
    }
    val seekBackLabel = stringResource(R.string.seek_back_30)
    val seekForwardLabel = stringResource(R.string.seek_forward_30)
    val openMarkersLabel = stringResource(R.string.recording_markers_open)
    val seekBackTarget = seekbarScrub(range, -1, 0)
    val seekForwardTarget = seekbarScrub(range, 1, 0)
    val accessibilityProgress = programmeWindow?.positionFraction ?: if (range.domain == SeekbarDomain.TIMESHIFT) {
        range.displayProgress
    } else {
        displayedProgress
    }
    val accessibilityActions = buildList {
        if (recordingMarkers.isNotEmpty() && onOpenRecordingMarkers != null) {
            add(CustomAccessibilityAction(openMarkersLabel) { onOpenRecordingMarkers(); true })
        }
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
            sampledPosition?.atLiveEdge != true
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
        PlayerTimelineBlock(
            progress = displayedProgress,
            selectedMarkerFraction = markerPreview?.let { range.copy(positionMs = it).displayProgress },
            markerFractions = recordingMarkers.map { it.toFloat() / range.displayEndMs },
            collapsed = collapsed,
            tone = when {
                focused && !collapsed -> PlayerTimelineTone.ACTIVE
                previewing -> PlayerTimelineTone.PREVIEW
                else -> PlayerTimelineTone.INTERACTIVE
            },
            // The estimate qualifier stays in the accessible description; visibly it is noise.
            leadingLabel = clockLabels?.first ?: positionLabel,
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
            trackOverlay = trackOverlay,
            statusAction = statusAction,
            feedback = windowFeedback,
            feedbackIsError = feedbackIsError,
            previewLabel = if (previewing && sampledPosition != null) {
                if (programmeWindow == null) timeshiftEndpointLabel(sampledPosition.atLiveEdge, sampledPosition.behindLiveMs)
                else if (sampledPosition.atLiveEdge) stringResource(R.string.timeshift_live)
                else "−${formatPlaybackDuration(sampledPosition.behindLiveMs)}"
            } else if (previewing && range.domain == SeekbarDomain.RECORDING) positionLabel else null,
            timelineModifier = modifier
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
                    if (!collapsed) customActions = accessibilityActions
                }
                .then(if (collapsed) Modifier else Modifier.focusable()),
        )
}

/** Numeric fallback when no programme clock axis exists; never announce state here. */
internal fun timeshiftEndpointLabel(atLiveEdge: Boolean, behindLiveMs: Long): String =
    if (atLiveEdge || behindLiveMs < 1_000) formatPlaybackDuration(0)
    else "−${formatPlaybackDuration(behindLiveMs)}"
