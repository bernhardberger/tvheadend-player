package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
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
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayTextTertiaryAlpha

@Composable
internal fun TimeshiftSeekPreview(
    state: AppTimeshiftState,
    decision: TimeshiftSeekDecision,
    modifier: Modifier = Modifier,
    programmeWindow: ProgrammeWindow? = null,
    channelsAvailable: Boolean = true,
) {
    val targetState = projectedTimeshiftState(state, decision.targetMs)
    val range = timeshiftSeekbarRange(targetState)
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
    val targetLineHeight = MaterialTheme.typography.titleMedium.lineHeight

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bottomGradient)
            .padding(
                start = TvOverlaySidePadding,
                end = TvOverlaySidePadding,
                top = TvOverlayFooterGradientRunout,
                bottom = playerSeekPreviewBottomPadding(channelsAvailable),
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
            Text(if (it.targetAvailable) it.event.title.orEmpty() else unavailableTarget,
                color = if (it.targetAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.layout { measurable, constraints ->
                    val title = measurable.measure(constraints.copy(minHeight = 0))
                     // Match the full chrome's centered status row above the target clearance.
                     layout(title.width, 0) {
                         title.placeRelative(0, -8.dp.roundToPx() - targetLineHeight.roundToPx() -
                             (TvOverlayActionButtonSize.roundToPx() + title.height) / 2)
                     }
                }.then(if (!it.targetAvailable) Modifier.background(MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.shapes.small).padding(horizontal = 8.dp) else Modifier)
                    .testTag("timeshift-preview-programme"))
        }
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
            leadingLabel = clockLabels?.first ?: behindLiveLabel.takeUnless { positionPresentation.atLiveEdge },
            trailingLabel = clockLabels?.second,
            leadingLabelTestTag = "timeshift-preview-buffer-start",
            trailingLabelTestTag = "timeshift-preview-position",
            previewLabel = targetLabel,
        )
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
