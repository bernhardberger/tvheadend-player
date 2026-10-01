package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Column
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
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import at.bernhardberger.tvhplayer.core.RecordingTimelinePresentation
import at.bernhardberger.tvhplayer.core.StepReadout
import at.bernhardberger.tvhplayer.core.recordingBarEnd
import at.bernhardberger.tvhplayer.core.recordingElapsedLabel
import at.bernhardberger.tvhplayer.core.recordingStepReadout
import at.bernhardberger.tvhplayer.core.SeekbarRange
import at.bernhardberger.tvhplayer.core.StepDirection
import at.bernhardberger.tvhplayer.core.stepDirection
import at.bernhardberger.tvhplayer.core.formatPlaybackDelta
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.recordingTimelinePresentation

/** The recording's bar at the step's target, or at the playback position; null while the length is unknown. */
internal fun recordingChromeRange(timeline: PlayerChromeTimeline.Recording): SeekbarRange? =
    (recordingTimelinePresentation(
        positionMs = timeline.targetMs ?: timeline.positionMs,
        durationMs = timeline.durationMs,
        growing = timeline.growing,
        displayDurationMs = timeline.displayDurationMs,
    ) as? RecordingTimelinePresentation.Seekable)?.range

/** A pending or dispatched step's net movement since the sequence began; null without a step. */
internal val PlayerChromeTimeline.Recording.stepDeltaMs: Long?
    get() = targetMs?.let { it - (originMs ?: positionMs) }

/**
 * The recording's bar row: the state cell and the length after the bar, a growing recording's its
 * current length; no length while it is unknown. The remaining time, or the distance to a growing
 * recording's head, is not drawn in the row.
 */
@Composable
internal fun recordingBarStatus(
    timeline: PlayerChromeTimeline.Recording,
    state: PlayerStateCell,
    range: SeekbarRange?,
): PlayerBarStatus = rememberPlayerBarStatus(
    state = state,
    end = range?.let { recordingBarEnd(timeline.positionMs, timeline.durationMs, timeline.growing, it.displayEndMs) },
)

/** The length status the recording's timeline announces. */
@Composable
private fun recordingLengthStatus(timeline: PlayerChromeTimeline.Recording, range: SeekbarRange?): String = when {
    range != null -> stringResource(R.string.recording_known_duration, formatPlaybackDuration(range.displayEndMs))
    timeline.growing -> stringResource(R.string.recording_still_recording)
    else -> stringResource(R.string.recording_duration_unavailable)
}

/**
 * The Banner's recording timeline: the elapsed playback position, the bar with its markers and the
 * length. With [step] a quick step's target reads out at its place on the bar.
 */
@Composable
internal fun RecordingBannerTimeline(timeline: PlayerChromeTimeline.Recording, step: Boolean, state: PlayerStateCell) {
    val range = recordingChromeRange(timeline)
    val target = timeline.targetMs?.takeIf { step }
    if (target == null) {
        RecordingTimelineBlock(timeline, range, tone = PlayerTimelineTone.INTERACTIVE, status = recordingBarStatus(timeline, state, range))
        return
    }
    val readout = range?.let { recordingStepReadout(target, it.endMs, timeline.growing, stringResource(R.string.timeshift_live)) }
        ?: StepReadout(recordingElapsedLabel(target, null))
    val targetLabel = readout.text
    val description = stringResource(
        R.string.recording_seek_preview_description,
        targetLabel,
        formatPlaybackDelta(requireNotNull(timeline.stepDeltaMs)),
        recordingLengthStatus(timeline, range),
    )
    Column(
        Modifier.fillMaxWidth().testTag("recording-seek-preview").clearAndSetSemantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        RecordingTimelineBlock(timeline, range, tone = PlayerTimelineTone.PREVIEW, previewLabel = targetLabel,
            previewDirection = timeline.stepDeltaMs?.let(::stepDirection), previewAtLive = readout.atLive,
            status = recordingBarStatus(timeline, state, range))
    }
}

/**
 * The controls' recording timeline: the focusable seekbar with the markers while seekable, otherwise
 * the passive bar or the elapsed position alone.
 */
@Composable
internal fun RecordingControlsTimeline(
    timeline: PlayerChromeTimeline.Recording,
    /** The seekable bar; null keeps the timeline passive. */
    range: SeekbarRange?,
    previewing: Boolean,
    paused: Boolean,
    state: PlayerStateCell,
    markerNavigation: RecordingMarkerNavigation,
    onSeekMarker: (Long) -> Unit,
    onOpenMarkers: () -> Unit,
    onSeekTo: (Long) -> Unit,
    /** The focusable seekbar's key handling and focus; unused while the timeline is passive. */
    modifier: Modifier,
) {
    if (range == null) {
        val passiveRange = recordingChromeRange(timeline)
        val description = stringResource(
            R.string.recording_timeline_status_description,
            formatPlaybackDuration(timeline.positionMs),
            recordingLengthStatus(timeline, passiveRange),
        )
        RecordingTimelineBlock(
            timeline, passiveRange, tone = PlayerTimelineTone.AMBIENT, status = recordingBarStatus(timeline, state, passiveRange),
            modifier = Modifier.testTag("recording-duration-status").clearAndSetSemantics { contentDescription = description },
        )
        return
    }
    PlaybackSeekbar(
        range = range,
        playbackPositionMs = timeline.positionMs,
        stepDeltaMs = timeline.stepDeltaMs,
        paused = paused,
        previewing = previewing,
        recordingMarkers = timeline.markers,
        recordingMarkerPreviewMs = markerNavigation.selectedMs?.takeIf { it in timeline.markers },
        onOpenRecordingMarkers = onOpenMarkers,
        trackOverlay = {
            RecordingMarkerOverlay(
                navigation = markerNavigation, markers = timeline.markers, onSeek = onSeekMarker,
                displayDurationMs = range.displayEndMs,
                modifier = Modifier.matchParentSize(),
            )
        },
        onSeekTo = onSeekTo,
        barStatus = recordingBarStatus(timeline, state, range),
        growing = timeline.growing,
        motionKey = timeline.motionKey,
        modifier = modifier,
    )
}

/** The recording's bar without focus: elapsed playback position, bar at [range]'s position and length. */
@Composable
private fun RecordingTimelineBlock(
    timeline: PlayerChromeTimeline.Recording,
    range: SeekbarRange?,
    tone: PlayerTimelineTone,
    modifier: Modifier = Modifier,
    previewLabel: String? = null,
    previewDirection: StepDirection? = null,
    previewAtLive: Boolean = false,
    status: PlayerBarStatus,
) {
    PlayerTimelineBlock(
        progress = range?.displayProgress,
        kind = TimelineKind.RECORDING,
        tone = tone,
        // Previewing a step, the playback position stays on the bar as its origin.
        ghostProgress = range?.takeIf { previewLabel != null }?.copy(positionMs = timeline.positionMs)?.displayProgress,
        leadingLabel = recordingElapsedLabel(timeline.positionMs, range?.endMs),
        status = status,
        previewLabel = previewLabel,
        previewDirection = previewDirection,
        previewAtLive = previewAtLive,
        markerFractions = range?.let { r -> timeline.markers.map { it.toFloat() / r.displayEndMs } }.orEmpty(),
        reserveLabelSpace = true,
        progressSemantics = false,
        motionKey = timeline.motionKey,
        timelineModifier = modifier,
    )
}
