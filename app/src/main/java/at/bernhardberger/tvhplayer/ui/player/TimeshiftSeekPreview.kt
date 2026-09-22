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
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.projectedTimeshiftState
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding

@Composable
internal fun TimeshiftSeekPreview(
    state: AppTimeshiftState,
    decision: TimeshiftSeekDecision,
    modifier: Modifier = Modifier,
    programmeWindow: ProgrammeWindow? = null,
    channelsAvailable: Boolean = true,
    feedback: String? = null,
    feedbackIsError: Boolean = feedback != null,
    headerContent: (@Composable (Modifier) -> Unit)? = null,
) {
    val targetState = projectedTimeshiftState(state, decision.targetMs)
    val range = timeshiftSeekbarRange(targetState)
    val clockLabels = programmeWindow?.let { programmeWindowClockLabels(it.event) }
    val positionPresentation = if (programmeWindow == null) timeshiftPositionPresentation(range.positionMs, range.displayEndMs)
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
    val targetLabel = if (programmeWindow == null) {
        timeshiftEndpointLabel(positionPresentation.atLiveEdge, positionPresentation.behindLiveMs)
    } else if (
        positionPresentation.atLiveEdge
    ) {
        liveLabel
    } else {
        "−${formatPlaybackDuration(positionPresentation.behindLiveMs)}"
    }
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

    PlayerOverlayChrome(
        modifier = modifier,
        headerContent = { headerContent?.invoke(it) },
        footerPadding = androidx.compose.foundation.layout.PaddingValues(
                start = TvOverlaySidePadding,
                end = TvOverlaySidePadding,
                top = TvOverlayFooterGradientRunout,
                bottom = playerSeekPreviewBottomPadding(channelsAvailable),
            ),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().testTag("timeshift-seek-preview")
            .clearAndSetSemantics {
                contentDescription = (programmeWindow?.let {
                    "${it.event.title.orEmpty()}. ${clockLabels?.first} - ${clockLabels?.second}. $description" +
                        if (!it.targetAvailable) " $unavailableTarget" else ""
                } ?: description) + feedback?.let { ". $it" }.orEmpty()
                liveRegion = LiveRegionMode.Polite
            }) {
        PlayerTimelineBlock(
            progress = range.displayProgress,
            tone = PlayerTimelineTone.PREVIEW,
            rewindableStartFraction = range.availableStartFraction,
            rewindableStartOverflow = false,
            liveEdgeFraction = 1f,
            rewindableBoundaryTestTag = "timeshift-preview-rewindable-boundary",
            rewindableOverflowTestTag = "timeshift-preview-rewindable-overflow",
            progressSemantics = false,
            programmeWindow = programmeWindow,
            reserveLabelSpace = true,
            leadingLabel = clockLabels?.first ?: timeshiftEndpointLabel(
                false, (range.displayEndMs - range.displayStartMs).coerceAtLeast(0),
            ).takeIf { range.positionKnown },
            trailingLabel = clockLabels?.second ?: formatPlaybackDuration(0).takeIf { range.positionKnown },
            leadingLabelTestTag = "timeshift-preview-buffer-start",
            trailingLabelTestTag = "timeshift-preview-position",
            previewLabel = targetLabel,
            feedback = feedback ?: programmeWindow?.let { if (it.targetAvailable) it.event.title.orEmpty() else unavailableTarget },
            feedbackIsError = if (feedback != null) feedbackIsError else programmeWindow?.targetAvailable == false,
            feedbackTestTag = "timeshift-preview-programme",
        )
        }
    }
}
