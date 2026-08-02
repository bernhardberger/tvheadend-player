package at.bernhardberger.tvhplayer.htsp

internal const val DVR_PROGRESS_MIN_HTSP_VERSION = 27
internal const val DVR_PROGRESS_UPDATE_METHOD = "updateDvrEntry"

enum class RecordingProgressCapability {
    Disconnected,
    Unsupported,
    ReadOnly,
    Full,
}

sealed interface RecordingProgressUpdateResult {
    data object Accepted : RecordingProgressUpdateResult
    data object PermissionDenied : RecordingProgressUpdateResult
    data object Unsupported : RecordingProgressUpdateResult
    data object Timeout : RecordingProgressUpdateResult
    data object Disconnected : RecordingProgressUpdateResult
    data object Rejected : RecordingProgressUpdateResult
}

internal data class RecordingProgressRequest(
    val method: String,
    val fields: Map<String, Any?>,
)

internal fun recordingProgressCapability(state: ConnectionState): RecordingProgressCapability =
    when (state) {
        is ConnectionState.Connected -> when {
            state.htspVersion == null || state.htspVersion < DVR_PROGRESS_MIN_HTSP_VERSION ->
                RecordingProgressCapability.Unsupported
            state.dvrAccess == false -> RecordingProgressCapability.ReadOnly
            else -> RecordingProgressCapability.Full
        }
        ConnectionState.Disconnected,
        is ConnectionState.Connecting,
        is ConnectionState.Error -> RecordingProgressCapability.Disconnected
    }

internal fun recordingProgressRequest(
    entryId: Int,
    playPositionSeconds: Long,
    setWatched: Boolean,
): RecordingProgressRequest? {
    if (entryId < 0 || playPositionSeconds < 0L) return null

    return RecordingProgressRequest(
        method = DVR_PROGRESS_UPDATE_METHOD,
        fields = mapOf(
            "id" to entryId,
            "playposition" to playPositionSeconds,
            "playcount" to if (setWatched) 1 else DVR_PLAY_COUNT_KEEP,
        ),
    )
}

internal fun recordingProgressReplyResult(reply: HtspMessage): RecordingProgressUpdateResult {
    if (reply.int("success") == 1) return RecordingProgressUpdateResult.Accepted
    if (reply.int("noaccess") == 1 && reply.int("connlimit") != 1) {
        return RecordingProgressUpdateResult.PermissionDenied
    }

    val error = reply.str("error").orEmpty().lowercase()
    return when {
        "method not found" in error || "unknown method" in error ->
            RecordingProgressUpdateResult.Unsupported
        "permission" in error || "access denied" in error || "not allowed" in error ->
            RecordingProgressUpdateResult.PermissionDenied
        else -> RecordingProgressUpdateResult.Rejected
    }
}
