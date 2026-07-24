package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryPolicyTest {
    @Test
    fun repeatedFailures_backOffToBoundedDelay() {
        assertEquals(1_000L, PlaybackRecoveryPolicy.retryDelayMillis(1))
        assertEquals(2_000L, PlaybackRecoveryPolicy.retryDelayMillis(2))
        assertEquals(5_000L, PlaybackRecoveryPolicy.retryDelayMillis(3))
        assertEquals(10_000L, PlaybackRecoveryPolicy.retryDelayMillis(4))
        assertEquals(30_000L, PlaybackRecoveryPolicy.retryDelayMillis(5))
        assertEquals(30_000L, PlaybackRecoveryPolicy.retryDelayMillis(50))
    }
}
