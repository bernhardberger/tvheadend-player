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
    REVEAL_AND_TOGGLE_PAUSE,
    HIDE_CONTROLS,
    CLOSE,
    OPEN_INFO,
    SEEK_BACK,
    SEEK_FORWARD,
}

enum class RecordingFinishedAction {
    NONE,
    STOP,
    STOP_AND_CLOSE_PLAYER,
}

/**
 * Recording player keys follow the shared player contract: vertical D-pad
 * reveals controls and Left/Right seek. The old hidden Up/Down ten-minute seek
 * mapping is intentionally retired; programme info remains an explicit action.
 */
fun recordingPlaybackKeyAction(
    controlsVisible: Boolean,
    keyCode: Int,
    simpleTvActive: Boolean = false,
    seekbarFocused: Boolean = false,
): RecordingPlaybackKeyAction {
    val action = playerKeyAction(
        PlayerKeyContext(
            surface = PlayerSurface.RECORDING,
            controlsVisible = controlsVisible,
            seekbarFocused = seekbarFocused,
            timeshiftAvailable = false,
            simpleTvActive = simpleTvActive,
        ),
        keyCode,
    )
    return when (action) {
        PlayerKeyAction.PASS_THROUGH -> RecordingPlaybackKeyAction.PASS_THROUGH
        PlayerKeyAction.REVEAL_CONTROLS -> RecordingPlaybackKeyAction.REVEAL_CONTROLS
        PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE ->
            RecordingPlaybackKeyAction.REVEAL_AND_TOGGLE_PAUSE
        PlayerKeyAction.HIDE_CONTROLS -> RecordingPlaybackKeyAction.HIDE_CONTROLS
        PlayerKeyAction.CLOSE_PLAYER,
        PlayerKeyAction.DISMISS_OVERLAY_ONLY -> RecordingPlaybackKeyAction.CLOSE
        PlayerKeyAction.OPEN_INFO -> RecordingPlaybackKeyAction.OPEN_INFO
        PlayerKeyAction.SEEK_BACK -> RecordingPlaybackKeyAction.SEEK_BACK
        PlayerKeyAction.SEEK_FORWARD -> RecordingPlaybackKeyAction.SEEK_FORWARD
        PlayerKeyAction.OPEN_CHANNELS -> RecordingPlaybackKeyAction.PASS_THROUGH
    }
}

fun recordingPlaybackSuppressesRevealingKey(
    revealingKeyCode: Int?,
    keyCode: Int,
): Boolean = revealingKeyCode == keyCode

fun recordingKeyActionStartsOpeningCycle(action: RecordingPlaybackKeyAction): Boolean =
    when (action) {
        RecordingPlaybackKeyAction.REVEAL_CONTROLS,
        RecordingPlaybackKeyAction.REVEAL_AND_TOGGLE_PAUSE,
        RecordingPlaybackKeyAction.OPEN_INFO -> true
        RecordingPlaybackKeyAction.PASS_THROUGH,
        RecordingPlaybackKeyAction.HIDE_CONTROLS,
        RecordingPlaybackKeyAction.CLOSE,
        RecordingPlaybackKeyAction.SEEK_BACK,
        RecordingPlaybackKeyAction.SEEK_FORWARD -> false
    }

fun recordingFinishedAction(
    recordingFinished: Boolean,
    activeRecordingId: Int?,
    recordingPlayerVisible: Boolean,
): RecordingFinishedAction = when {
    !recordingFinished || activeRecordingId == null -> RecordingFinishedAction.NONE
    recordingPlayerVisible -> RecordingFinishedAction.STOP_AND_CLOSE_PLAYER
    else -> RecordingFinishedAction.STOP
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
