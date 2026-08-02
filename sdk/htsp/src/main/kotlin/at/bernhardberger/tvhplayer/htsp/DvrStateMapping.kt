package at.bernhardberger.tvhplayer.htsp

fun dvrState(value: String?, error: String? = null): DvrState {
    val normalized = value?.lowercase().orEmpty()
    val normalizedError = error?.lowercase().orEmpty()
    return when {
        "cancel" in normalized || "user" in normalizedError && "cancel" in normalizedError ->
            DvrState.CANCELLED
        "recording" in normalized || "running" in normalized -> DvrState.RECORDING
        "scheduled" in normalized || "pending" in normalized || "waiting" in normalized ->
            DvrState.SCHEDULED
        "completed" in normalized || "finished" in normalized ->
            if (error.isNullOrBlank()) DvrState.COMPLETED else DvrState.FAILED
        "failed" in normalized || "missed" in normalized || "invalid" in normalized ||
            error?.isNotBlank() == true -> DvrState.FAILED
        else -> DvrState.UNKNOWN
    }
}
