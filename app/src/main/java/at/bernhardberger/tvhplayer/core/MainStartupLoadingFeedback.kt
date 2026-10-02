package at.bernhardberger.tvhplayer.core

enum class MainStartupLoadingFeedback { HIDDEN, WAITING }

internal const val MainStartupBriefWaitMillis = 400L

fun mainStartupLoadingFeedback(
    presentation: MainStartupPresentation,
    elapsedMillis: Long,
): MainStartupLoadingFeedback = when {
    presentation !is MainStartupPresentation.Passive -> MainStartupLoadingFeedback.HIDDEN
    elapsedMillis < MainStartupBriefWaitMillis -> MainStartupLoadingFeedback.HIDDEN
    else -> MainStartupLoadingFeedback.WAITING
}

/** One blocking launch request; connection retries and metadata never restart its grace. */
internal class MainStartupLoadingTiming {
    private var startedAtMillis: Long? = null
    private var requestId: Long? = null

    fun elapsedMillis(
        blocking: Boolean,
        requestId: Long?,
        nowMillis: Long,
    ): Long {
        val start = startedAtMillis ?: return 0L
        if (!blocking || isNewAttempt(requestId)) return 0L
        return (nowMillis - start).coerceAtLeast(0L)
    }

    fun observe(
        blocking: Boolean,
        requestId: Long?,
        nowMillis: Long,
    ): Long {
        if (!blocking) {
            startedAtMillis = null
            this.requestId = null
            return 0L
        }
        if (startedAtMillis == null || isNewAttempt(requestId)) {
            startedAtMillis = nowMillis
        }
        this.requestId = requestId
        return (nowMillis - requireNotNull(startedAtMillis)).coerceAtLeast(0L)
    }

    private fun isNewAttempt(requestId: Long?): Boolean =
        this.requestId != null && this.requestId != requestId
}
