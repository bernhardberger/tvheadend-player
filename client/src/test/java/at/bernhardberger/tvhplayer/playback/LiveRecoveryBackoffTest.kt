package at.bernhardberger.tvhplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveRecoveryBackoffTest {
    private var now = 0L
    private val backoff = LiveRecoveryBackoff(
        delaysMillis = listOf(0L, 2_000L, 5_000L),
        resetMillis = 60_000L,
        elapsedRealtimeMillis = { now },
    )

    @Test
    fun `consecutive attempts back off and then exhaust the budget`() {
        assertEquals(LiveRecoveryAttempt(attempt = 1, delayMillis = 0L), backoff.nextAttempt())
        assertEquals(LiveRecoveryAttempt(attempt = 2, delayMillis = 2_000L), backoff.nextAttempt())
        assertEquals(LiveRecoveryAttempt(attempt = 3, delayMillis = 5_000L), backoff.nextAttempt())

        assertNull(backoff.nextAttempt())
        assertNull(backoff.nextAttempt())
    }

    @Test
    fun `a target that survives the reset window regains the full budget`() {
        backoff.nextAttempt()
        backoff.nextAttempt()

        now += 60_000L

        assertEquals(LiveRecoveryAttempt(attempt = 1, delayMillis = 0L), backoff.nextAttempt())
    }

    @Test
    fun `recovery requests inside the reset window keep escalating`() {
        backoff.nextAttempt()
        now += 59_999L
        assertEquals(LiveRecoveryAttempt(attempt = 2, delayMillis = 2_000L), backoff.nextAttempt())
    }

    @Test
    fun `an explicit reset refills an exhausted budget`() {
        repeat(3) { backoff.nextAttempt() }
        assertNull(backoff.nextAttempt())

        backoff.reset()

        assertEquals(LiveRecoveryAttempt(attempt = 1, delayMillis = 0L), backoff.nextAttempt())
    }
}
