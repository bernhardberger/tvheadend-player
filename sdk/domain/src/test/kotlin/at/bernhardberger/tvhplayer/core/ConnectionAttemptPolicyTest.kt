package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionAttemptPolicyTest {
    @Test
    fun supersededAttemptCannotPublishFailureOrSuccess() {
        val first = beginConnectionAttempt(ConnectionAttemptState())
        val second = beginConnectionAttempt(first.state)

        assertEquals(1L, first.attemptId)
        assertEquals(2L, second.attemptId)
        assertFalse(connectionAttemptMayPublish(second.state, first.attemptId))
        assertTrue(connectionAttemptMayPublish(second.state, second.attemptId))
    }

    @Test
    fun invalidatedAttemptCannotPublishWhileConfigurationIsUnavailable() {
        val first = beginConnectionAttempt(ConnectionAttemptState())
        val invalidated = invalidateConnectionAttempts(first.state)

        assertFalse(connectionAttemptMayPublish(invalidated, first.attemptId))
    }

    @Test
    fun retryCoalescesOnlyWhileAnAttemptIsActivelyConnecting() {
        assertFalse(
            shouldRestartConnectionRetry(
                reconnectJobActive = true,
                connectionIsConnecting = true,
            )
        )
        assertTrue(
            shouldRestartConnectionRetry(
                reconnectJobActive = true,
                connectionIsConnecting = false,
            )
        )
        assertTrue(
            shouldRestartConnectionRetry(
                reconnectJobActive = false,
                connectionIsConnecting = true,
            )
        )
    }

    @Test
    fun reconnectAttemptPhaseIgnoresStaleCompletionAndReopensAfterCurrentFailure() {
        val first = beginReconnectAttemptPhase(ReconnectAttemptPhase(), attemptId = 1L)
        assertEquals(1L, first.inFlightAttemptId)

        val second = beginReconnectAttemptPhase(first, attemptId = 2L)
        val afterStaleCompletion = completeReconnectAttemptPhase(second, attemptId = 1L)
        assertEquals(2L, afterStaleCompletion.inFlightAttemptId)

        val completed = completeReconnectAttemptPhase(afterStaleCompletion, attemptId = 2L)
        assertEquals(null, completed.inFlightAttemptId)
        assertEquals(ReconnectAttemptPhase(), invalidateReconnectAttemptPhase(second))
    }
}
