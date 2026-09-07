package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.core.formatPlaybackDelta
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.projectedTimeshiftState
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayTextTertiaryAlpha

@Composable
internal fun TimeshiftSeekPreview(
    state: AppTimeshiftState,
    decision: TimeshiftSeekDecision,
    modifier: Modifier = Modifier,
    programmeWindow: ProgrammeWindow? = null,
) {
    val targetState = projectedTimeshiftState(state, decision.targetMs)
    val range = timeshiftSeekbarRange(targetState)
    val displayedProgress = programmeWindow?.positionFraction ?: range.displayProgress
    val clockLabels = programmeWindow?.let { programmeWindowClockLabels(it.event) }
    val positionPresentation = timeshiftPositionPresentation(targetState)
    val liveLabel = stringResource(R.string.timeshift_live)
    val behindLiveLabel = if (positionPresentation.atLiveEdge) {
        liveLabel
    } else {
        stringResource(
            R.string.timeshift_behind_live,
            formatPlaybackDuration(positionPresentation.behindLiveMs),
        )
    }
    val targetLabel = if (
        positionPresentation.atLiveEdge
    ) {
        liveLabel
    } else {
        "−${formatPlaybackDuration(positionPresentation.behindLiveMs)}"
    }
    val deltaLabel = formatPlaybackDelta(decision.deltaMs)
    val bufferStartLabel = stringResource(
        R.string.timeshift_buffer_start,
        formatPlaybackDuration(
            (targetState.liveEdgeMs - targetState.bufferStartMs).coerceAtLeast(0L)
        ),
    )
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

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bottomGradient)
            .padding(
                start = TvOverlaySidePadding,
                end = TvOverlaySidePadding,
                top = TvOverlayFooterGradientRunout,
                bottom = TvOverlayBottomPadding,
            )
            .testTag("timeshift-seek-preview")
            .clearAndSetSemantics {
                contentDescription = programmeWindow?.let {
                    "${it.event.title.orEmpty()}. ${clockLabels?.first} - ${clockLabels?.second}. $description" +
                        if (!it.targetAvailable) " $unavailableTarget" else ""
                } ?: description
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        programmeWindow?.let {
            Text(it.event.title.orEmpty(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("timeshift-preview-programme"))
        }
        TimelineTargetLabel(targetLabel, displayedProgress, available = programmeWindow?.targetAvailable != false)
        PlayerTimelineBlock(
            progress = range.displayProgress,
            tone = PlayerTimelineTone.PREVIEW,
            rewindableStartFraction = range.availableStartFraction,
            rewindableStartOverflow = false,
            liveEdgeFraction = 1f,
            rewindableBoundaryTestTag = "timeshift-preview-rewindable-boundary",
            rewindableOverflowTestTag = "timeshift-preview-rewindable-overflow",
            liveEdgeTestTag = "timeshift-preview-live-edge",
            progressSemantics = false,
            programmeWindow = programmeWindow,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = clockLabels?.first ?: bufferStartLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = TvOverlayTextTertiaryAlpha,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("timeshift-preview-buffer-start"),
            )
            Text(
                text = clockLabels?.second ?: if (targetLabel == liveLabel) "" else liveLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = TvOverlayTextTertiaryAlpha,
                ),
                modifier = Modifier.testTag("timeshift-preview-position"),
            )
        }
        if (programmeWindow?.targetAvailable == false) {
            Text(unavailableTarget, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun TimelineTargetLabel(label: String, progress: Float, available: Boolean = true) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (available) 1f else TvOverlayTextTertiaryAlpha),
            maxLines = 1,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val label = measurable.measure(constraints.copy(minWidth = 0))
                    layout(constraints.maxWidth, label.height) {
                        label.placeRelative(
                            (constraints.maxWidth * progress - label.width / 2f).roundToInt()
                                .coerceIn(0, constraints.maxWidth - label.width),
                            0,
                        )
                    }
                }
                .testTag("timeshift-preview-target"),
        )
}
