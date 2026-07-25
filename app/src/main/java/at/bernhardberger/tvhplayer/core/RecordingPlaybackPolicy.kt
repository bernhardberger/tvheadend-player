package at.bernhardberger.tvhplayer.core

import android.view.KeyEvent
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState

sealed interface RecordingPlaybackAvailability {
    data class Ready(
        val path: String,
        val size: Long?,
        val growing: Boolean,
    ) : RecordingPlaybackAvailability

    data object NotReady : RecordingPlaybackAvailability
    data object FileUnavailable : RecordingPlaybackAvailability
}

enum class RecordingPlaybackKeyAction {
    PASS_THROUGH,
    REVEAL_CONTROLS,
    HIDE_CONTROLS,
    CLOSE,
    SEEK_BACK_SHORT,
    SEEK_FORWARD_SHORT,
    SEEK_BACK_LONG,
    SEEK_FORWARD_LONG,
}

fun recordingPlaybackKeyAction(
    controlsVisible: Boolean,
    keyCode: Int,
): RecordingPlaybackKeyAction = when {
    keyCode == KeyEvent.KEYCODE_BACK -> if (controlsVisible) {
        RecordingPlaybackKeyAction.HIDE_CONTROLS
    } else {
        RecordingPlaybackKeyAction.CLOSE
    }
    controlsVisible -> RecordingPlaybackKeyAction.PASS_THROUGH
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER -> RecordingPlaybackKeyAction.REVEAL_CONTROLS
    keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> RecordingPlaybackKeyAction.SEEK_BACK_SHORT
    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> RecordingPlaybackKeyAction.SEEK_FORWARD_SHORT
    keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> RecordingPlaybackKeyAction.SEEK_BACK_LONG
    keyCode == KeyEvent.KEYCODE_DPAD_UP -> RecordingPlaybackKeyAction.SEEK_FORWARD_LONG
    else -> RecordingPlaybackKeyAction.PASS_THROUGH
}

fun recordingPlaybackAvailability(entry: DvrEntry): RecordingPlaybackAvailability {
    if (
        entry.state != DvrState.COMPLETED &&
        entry.state != DvrState.RECORDING &&
        entry.state != DvrState.FAILED
    ) {
        return RecordingPlaybackAvailability.NotReady
    }
    val file = entry.files.firstOrNull { !it.path.isNullOrBlank() }
        ?: return RecordingPlaybackAvailability.FileUnavailable
    return RecordingPlaybackAvailability.Ready(
        path = "/dvrfile/${entry.id}",
        size = file.size,
        growing = entry.state == DvrState.RECORDING,
    )
}

fun recordingReadLength(requested: Int, bytesRemaining: Long?): Int = when {
    requested <= 0 -> 0
    bytesRemaining == null -> requested
    bytesRemaining <= 0 -> 0
    else -> minOf(requested.toLong(), bytesRemaining).toInt()
}

fun recordingSeekTarget(currentMs: Long, durationMs: Long?, deltaMs: Long): Long {
    val upperBound = durationMs?.takeIf { it >= 0L } ?: Long.MAX_VALUE
    return (currentMs + deltaMs).coerceIn(0L, upperBound)
}

fun recordingStackedSeekTarget(
    currentMs: Long,
    pendingTargetMs: Long?,
    durationMs: Long?,
    deltaMs: Long,
): Long = recordingSeekTarget(
    currentMs = pendingTargetMs ?: currentMs,
    durationMs = durationMs,
    deltaMs = deltaMs,
)

fun recordingSeekFeedbackSettled(
    playerReady: Boolean,
    playerEnded: Boolean,
    playWhenReady: Boolean,
    isPlaying: Boolean,
    playbackFailed: Boolean,
): Boolean = playbackFailed ||
    playerEnded ||
    (playerReady && (!playWhenReady || isPlaying))
