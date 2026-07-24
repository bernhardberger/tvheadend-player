package at.bernhardberger.tvhplayer.core

object PlaybackRecoveryPolicy {
    private val retryDelaysMillis = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)

    fun retryDelayMillis(consecutiveFailures: Int): Long {
        require(consecutiveFailures > 0)
        return retryDelaysMillis[(consecutiveFailures - 1).coerceAtMost(retryDelaysMillis.lastIndex)]
    }
}
