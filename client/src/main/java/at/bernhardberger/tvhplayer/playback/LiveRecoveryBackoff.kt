package at.bernhardberger.tvhplayer.playback

import android.os.SystemClock

/**
 * Delays applied before consecutive live recovery attempts.
 *
 * The first attempt is immediate because most recoveries succeed at once. Later attempts back off
 * so a target the server cannot deliver is not retuned in a tight loop, which starves the
 * subscription it is trying to restore.
 */
internal val DEFAULT_LIVE_RECOVERY_DELAYS_MS: List<Long> =
    listOf(0L, 2_000L, 5_000L, 10_000L, 20_000L)

/**
 * Quiet time between admitted attempts that refills the budget.
 *
 * This measures time since the previous attempt was admitted, which includes its own backoff and
 * retune. It is not evidence of that much healthy playback, only that recovery stopped asking.
 */
internal const val DEFAULT_LIVE_RECOVERY_RESET_MS = 60_000L

internal data class LiveRecoveryAttempt(
    val attempt: Int,
    val delayMillis: Long,
)

/**
 * Consecutive-attempt budget for SDK-requested live recovery.
 *
 * The SDK reports that a target is stuck; it does not decide how often the application may retune.
 * Without a budget an unrecoverable target produces an unbounded retune loop.
 */
internal class LiveRecoveryBackoff(
    private val delaysMillis: List<Long> = DEFAULT_LIVE_RECOVERY_DELAYS_MS,
    private val resetMillis: Long = DEFAULT_LIVE_RECOVERY_RESET_MS,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private var attempts = 0
    private var lastAttemptAtMillis: Long? = null

    /**
     * Claims the next attempt, or returns null when the budget for this target is exhausted.
     *
     * A target that went [resetMillis] without asking for recovery again is treated as recovered,
     * so occasional unrelated glitches never accumulate towards exhaustion.
     */
    fun nextAttempt(): LiveRecoveryAttempt? {
        val now = elapsedRealtimeMillis()
        lastAttemptAtMillis?.let { last ->
            if (now - last >= resetMillis) attempts = 0
        }
        if (attempts >= delaysMillis.size) return null
        val delayMillis = delaysMillis[attempts]
        attempts += 1
        lastAttemptAtMillis = now
        return LiveRecoveryAttempt(attempt = attempts, delayMillis = delayMillis)
    }

    /** Refills the budget for an explicitly requested target change or teardown. */
    fun reset() {
        attempts = 0
        lastAttemptAtMillis = null
    }
}
