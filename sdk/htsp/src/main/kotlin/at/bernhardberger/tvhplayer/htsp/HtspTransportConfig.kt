package at.bernhardberger.tvhplayer.htsp

data class HtspClientIdentity(
    val clientName: String,
    val clientVersion: String,
) {
    companion object {
        val Default = HtspClientIdentity(
            clientName = "Kotlin HTSP client",
            clientVersion = "unknown",
        )
    }
}

enum class HtspLogLevel {
    WARNING,
    ERROR,
}

fun interface HtspLogger {
    fun log(level: HtspLogLevel, message: String, cause: Throwable?)

    companion object {
        val None = HtspLogger { _, _, _ -> }
    }
}
