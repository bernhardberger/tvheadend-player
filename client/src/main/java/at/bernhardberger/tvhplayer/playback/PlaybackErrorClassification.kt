package at.bernhardberger.tvhplayer.playback

/** Retains no throwable, message, file name, package name or full stack trace. */
internal fun classifyPlaybackError(error: Throwable?): String? {
    var root = error ?: return null
    val seen = mutableListOf(root)
    // Malformed cause cycles and unusually deep chains must not stall the player callback.
    while (seen.size < 32) {
        val cause = root.cause ?: break
        if (seen.any { it === cause }) break
        seen.add(cause)
        root = cause
    }
    val exception = diagnosticIdentifier(root.javaClass.simpleName)
    val stack = root.stackTrace
    // Media3 runtime checks throw from precondition helpers; name their caller instead.
    val frame = stack.take(4).firstOrNull { it.className !in preconditionHelpers }
        ?: stack.firstOrNull()
        ?: return exception
    val owner = frame.className
        .takeIf { it.length <= 200 && it.matches(qualifiedClassName) }
        ?.substringAfterLast('.')
        ?.substringAfterLast('$')
        ?.let(::diagnosticIdentifier)
        ?: "?"
    val method = diagnosticIdentifier(frame.methodName)
    return "$exception · $owner.$method:${frame.lineNumber}"
}

private val preconditionHelpers = setOf(
    "androidx.media3.common.util.Assertions",
    "com.google.common.base.Preconditions",
)

private val qualifiedClassName = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")

// StackTraceElement can be supplied by an exception implementation; reject non-symbol text.
private fun diagnosticIdentifier(value: String): String =
    value.takeIf { it.length in 1..80 && it.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*|<init>|<clinit>")) }
        ?: "?"
