package at.bernhardberger.tvhplayer.core

enum class DvrActionFailure {
    PERMISSION_DENIED,
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
    if (noAccess) return DvrActionFailure.PERMISSION_DENIED
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
