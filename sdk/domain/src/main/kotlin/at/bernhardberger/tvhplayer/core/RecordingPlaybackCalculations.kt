package at.bernhardberger.tvhplayer.core

enum class RecordingFinishedAction {
    NONE,
    STOP,
    STOP_AND_CLOSE_PLAYER,
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
