package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
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
import at.bernhardberger.tvhplayer.playback.AppTimeshiftSeekResult
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.core.formatPlaybackDelta
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.programmeAnchoredAxis
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayTextTertiaryAlpha

@Composable
internal fun TimeshiftSeekPreview(
    state: AppTimeshiftState,
    decision: AppTimeshiftSeekResult.Applied,
    nowEpochSec: Long,
    programmeStartSec: Long?,
    programmeStopSec: Long?,
    modifier: Modifier = Modifier,
) {
    val targetState = state.copy(positionMs = decision.targetMs)
    val programmeAxis = programmeAnchoredAxis(
        state = targetState,
        nowEpochSec = nowEpochSec,
        programmeStartSec = programmeStartSec,
        programmeStopSec = programmeStopSec,
    )
    val programmeDurationMs = if (
        programmeStartSec != null &&
        programmeStopSec != null &&
        programmeStopSec > programmeStartSec
    ) {
        (programmeStopSec - programmeStartSec) * 1_000L
    } else {
        null
    }
    val programmeTargetMs = programmeDurationMs?.let { duration ->
        ((nowEpochSec + decision.targetMs / 1_000L - requireNotNull(programmeStartSec)) * 1_000L)
            .takeIf { it in 0L..duration }
    }
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
    val targetLabel = programmeTargetMs?.let(::formatPlaybackDuration) ?: if (
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
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        PlayerTimelineBlock(
            progress = programmeAxis?.playbackFraction
                ?: timeshiftSeekbarRange(targetState).progress,
            tone = PlayerTimelineTone.PREVIEW,
            leadingLabel = targetLabel,
            trailingLabel = liveLabel,
            leadingLabelTestTag = "timeshift-preview-target",
            rewindableStartFraction = programmeAxis?.rewindableStartFraction ?: 0f,
            rewindableStartOverflow = programmeAxis?.rewindableStartsBeforeProgramme == true,
            liveEdgeFraction = programmeAxis?.liveEdgeFraction ?: 1f,
            rewindableBoundaryTestTag = "timeshift-preview-rewindable-boundary",
            rewindableOverflowTestTag = "timeshift-preview-rewindable-overflow",
            liveEdgeTestTag = "timeshift-preview-live-edge",
            progressSemantics = false,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = deltaLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = TvOverlayTextTertiaryAlpha,
                ),
                modifier = Modifier.testTag("timeshift-preview-delta"),
            )
            Text(
                text = bufferStartLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = TvOverlayTextTertiaryAlpha,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = TvOverlaySidePadding / 4)
                    .testTag("timeshift-preview-buffer-start"),
            )
            Text(
                text = behindLiveLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = TvOverlayTextTertiaryAlpha,
                ),
                modifier = Modifier.testTag("timeshift-preview-position"),
            )
        }
    }
}
