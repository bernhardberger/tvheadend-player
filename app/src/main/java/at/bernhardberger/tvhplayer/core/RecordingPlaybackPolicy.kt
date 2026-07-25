package at.bernhardberger.tvhplayer.core

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
        path = requireNotNull(file.path),
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
