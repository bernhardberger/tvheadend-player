package at.bernhardberger.tvhplayer.core

/** Bounded display extrapolation, never a seek grant. Reset for each recording/stream segment. */
class GrowingTimelineDisplayEnd {
    private var verifiedEndMs = 0L
    private var verifiedAtMs = 0L
    private var displayEndMs = 0L

    fun update(verifiedMs: Long, nowMonotonicMs: Long, growing: Boolean): Long {
        if (!growing || verifiedMs <= 0L || verifiedMs < verifiedEndMs) {
            verifiedEndMs = verifiedMs.coerceAtLeast(0L)
            verifiedAtMs = nowMonotonicMs
            displayEndMs = verifiedMs
            return verifiedMs
        }
        if (verifiedMs > verifiedEndMs) {
            verifiedEndMs = verifiedMs
            verifiedAtMs = nowMonotonicMs
        }
        // Presentation staleness budget, not permission to extend the verified seek range.
        val leadMs = (nowMonotonicMs - verifiedAtMs).coerceIn(0L, 5_000L)
        displayEndMs = maxOf(displayEndMs, verifiedMs + minOf(leadMs, Long.MAX_VALUE - verifiedMs))
        return displayEndMs
    }
}
