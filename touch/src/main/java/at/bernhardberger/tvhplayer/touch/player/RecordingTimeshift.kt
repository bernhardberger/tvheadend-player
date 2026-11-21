package at.bernhardberger.tvhplayer.touch.player

import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.SeekbarRange
import at.bernhardberger.tvhplayer.core.StepReadout
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.playbackSeekStepMs
import at.bernhardberger.tvhplayer.core.recordingBarEnd
import at.bernhardberger.tvhplayer.core.recordingElapsedLabel
import at.bernhardberger.tvhplayer.core.recordingStackedSeekTarget
import at.bernhardberger.tvhplayer.core.recordingStepReadout
import at.bernhardberger.tvhplayer.playback.RecordingSkipAction

internal sealed interface RecordingMove {
    data class Seek(val positionMs: Long) : RecordingMove
    data class Marker(
        val positionMs: Long,
        val expectedRevision: Long,
        val programMarks: List<RecordingMark>,
    ) : RecordingMove, PlayerChoice
}

internal sealed interface RecordingSkipChoice : PlayerChoice {
    data class Accept(val action: RecordingSkipAction, val fromControls: Boolean) : RecordingSkipChoice
    data class Dismiss(val action: RecordingSkipAction) : RecordingSkipChoice
}

internal fun recordingDragMove(range: SeekbarRange?, targetMs: Long): RecordingMove.Seek? =
    range?.let { RecordingMove.Seek(targetMs.coerceIn(it.startMs, it.endMs)) }

internal fun recordingSkipMove(
    range: SeekbarRange?,
    action: PlayerAction,
    pendingTargetMs: Long? = null,
): RecordingMove.Seek? {
    val direction = when (action) {
        PlayerAction.BackTen -> -1
        PlayerAction.ForwardThirty -> 1
        else -> return null
    }
    return range?.let {
        RecordingMove.Seek(recordingStackedSeekTarget(
            it.positionMs, pendingTargetMs, it.endMs, playbackSeekStepMs(direction, repeatCount = 0),
        ))
    }
}

internal fun recordingMarkerMove(
    state: RecordingPlayerState,
    positionMs: Long,
): RecordingMove.Marker? {
    val revision = state.markerRevision ?: return null
    return RecordingMove.Marker(positionMs, revision, state.programMarks)
        .takeIf { state.seekable && state.hasMarkers && state.stops.any { it.positionMs == positionMs } }
}

internal fun recordingMarkerTap(
    state: RecordingPlayerState,
    onSeek: (Long) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
): (Long) -> Unit = { position ->
    if (state.markerRevision == null) onSeek(position)
    else recordingMarkerMove(state, position)?.let(onChoice)
}

internal fun RecordingMove.Seek.stepReadout(
    range: SeekbarRange,
    originMs: Long,
    growing: Boolean,
    liveLabel: String,
): StepReadout = recordingStepReadout(positionMs - originMs, positionMs, range.endMs, growing, liveLabel)

internal fun recordingPositionLabel(
    positionMs: Long, durationMs: Long, displayDurationMs: Long, growing: Boolean, liveLabel: String,
): String = when {
    recordingBarEnd(positionMs, durationMs, growing)?.atLive == true -> liveLabel
    growing -> recordingElapsedLabel(positionMs, displayDurationMs)
    // The touch face deliberately uses compact elapsed time, e.g. "52:30 / 1:45:00".
    else -> formatPlaybackDuration(positionMs)
}
