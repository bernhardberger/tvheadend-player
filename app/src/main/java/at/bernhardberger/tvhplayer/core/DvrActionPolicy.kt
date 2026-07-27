package at.bernhardberger.tvhplayer.core

enum class DvrActionFailure {
    PERMISSION_DENIED,
    /** Server hit its connection limit; transient, never a statement about rights. */
    CONNECTION_LIMIT,
    CONFLICT,
    REJECTED,
    CONNECTION,
}

sealed interface DvrActionResult {
    data class Accepted(val entryId: Int? = null) : DvrActionResult
    data class Failed(val reason: DvrActionFailure) : DvrActionResult
}

fun dvrActionFailure(fields: Map<String, Any?>): DvrActionFailure? {
    val noAccess = (fields["noaccess"] as? Number)?.toInt() == 1
    if (noAccess) {
        // TVHeadend also answers noaccess=1 when the connection limit is reached,
        // then flagged with connlimit=1. That says nothing about DVR rights.
        val connLimit = (fields["connlimit"] as? Number)?.toInt() == 1
        return if (connLimit) {
            DvrActionFailure.CONNECTION_LIMIT
        } else {
            DvrActionFailure.PERMISSION_DENIED
        }
    }
    val error = fields["error"]?.toString()?.lowercase()?.takeIf { it.isNotBlank() }
        ?: return null
    return when {
        "permission" in error || "access denied" in error || "not allowed" in error ->
            DvrActionFailure.PERMISSION_DENIED
        "conflict" in error || "no free" in error || "tuner" in error ->
            DvrActionFailure.CONFLICT
        else -> DvrActionFailure.REJECTED
    }
}
