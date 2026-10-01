package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.core.formatPlaybackDelta
import at.bernhardberger.tvhplayer.core.stepDirection
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.liveStepReadout
import at.bernhardberger.tvhplayer.core.projectedTimeshiftState
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange

/**
 * The seek preview's timeline: target, delta and programme of a pending or dispatched step.
 * [titleAsFeedback] repeats the target programme's title in the status slot; hosts that show
 * the programme in an info bar pass false.
 */
@Composable
internal fun TimeshiftSeekPreviewTimeline(
    state: AppTimeshiftState,
    decision: TimeshiftSeekDecision,
    programmeWindow: ProgrammeWindow? = null,
    feedback: String? = null,
    feedbackIsError: Boolean = feedback != null,
    titleAsFeedback: Boolean = true,
    barStatus: PlayerBarStatus? = null,
    /** The wall-clock second now; with [barStatus] the buffer's start clock is the leading label without programme clocks. */
    nowSec: Long? = null,
) {
    val targetState = projectedTimeshiftState(state, decision.targetMs)
    val range = timeshiftSeekbarRange(targetState)
    val clockLabels = programmeWindow?.let { programmeWindowClockLabels(it) }
    val positionPresentation = if (programmeWindow == null) timeshiftPositionPresentation(range.positionMs, range.endMs)
        else timeshiftPositionPresentation(targetState)
    val liveLabel = stringResource(R.string.timeshift_live)
    val behindLiveLabel = if (positionPresentation.atLiveEdge) {
        liveLabel
    } else {
        stringResource(
            R.string.timeshift_behind_live,
            formatPlaybackDuration(positionPresentation.behindLiveMs),
        )
    }
    val readout = liveStepReadout(positionPresentation.behindLiveMs, liveLabel)
    val targetLabel = readout.text
    val deltaLabel = formatPlaybackDelta(decision.deltaMs)
    val bufferStartDescription = stringResource(
        R.string.timeshift_buffer_start_description,
        formatPlaybackDuration(
            (targetState.liveEdgeMs - targetState.bufferStartMs).coerceAtLeast(0L)
        ),
    )
    val description = stringResource(
        R.string.timeshift_seek_preview_description,
        targetLabel,
        deltaLabel,
        behindLiveLabel,
        bufferStartDescription,
    )
    val unavailableTarget = stringResource(R.string.timeshift_target_expired)

    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().testTag("timeshift-seek-preview")
        .clearAndSetSemantics {
            contentDescription = (programmeWindow?.let {
                "${it.event?.title?.let { title -> "$title. " }.orEmpty()}${clockLabels?.first} - ${clockLabels?.second}. $description" +
                    if (!it.targetAvailable) " $unavailableTarget" else ""
            } ?: description) + feedback?.let { ". $it" }.orEmpty()
            liveRegion = LiveRegionMode.Polite
        }) {
    PlayerTimelineBlock(
        progress = liveTimelineProgress(range, positionPresentation, paused = false, previewing = true),
        tone = PlayerTimelineTone.PREVIEW,
        rewindableStartFraction = range.availableStartFraction,
        rewindableStartOverflow = false,
        liveEdgeFraction = 1f,
        rewindableBoundaryTestTag = "timeshift-preview-rewindable-boundary",
        rewindableOverflowTestTag = "timeshift-preview-rewindable-overflow",
        progressSemantics = false,
        programmeWindow = programmeWindow,
        reserveLabelSpace = true,
        leadingLabel = clockLabels?.first ?: if (barStatus != null) {
            nowSec?.takeIf { targetState.available && targetState.timingKnown }?.let { timeshiftStartClock(targetState, it) }
        } else timeshiftEndpointLabel(
            false, (range.displayEndMs - range.displayStartMs).coerceAtLeast(0),
        ).takeIf { range.positionKnown },
        trailingLabel = if (barStatus?.end != null) null else clockLabels?.second ?: formatPlaybackDuration(0).takeIf { range.positionKnown },
        status = barStatus,
        leadingLabelTestTag = "timeshift-preview-buffer-start",
        trailingLabelTestTag = "timeshift-preview-position",
        previewLabel = targetLabel,
        previewAtLive = readout.atLive,
        previewDirection = stepDirection(decision.deltaMs),
        feedback = feedback ?: programmeWindow?.let { if (!it.targetAvailable) unavailableTarget else it.event?.title?.takeIf { titleAsFeedback } },
        feedbackIsError = if (feedback != null) feedbackIsError else programmeWindow?.targetAvailable == false,
        feedbackTestTag = "timeshift-preview-programme",
    )
    }
}
