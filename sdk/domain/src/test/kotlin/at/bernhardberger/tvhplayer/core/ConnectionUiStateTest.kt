package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionUiStateTest {

    @Test
    fun connectionAttempt_withoutPublishedChannels_isColdLoading() {
        assertEquals(
            ConnectionUiState.Connecting,
            connectionAttemptState(hasPublishedChannels = false),
        )
    }

    @Test
    fun connectionAttempt_withPublishedChannels_isNonBlockingRecovery() {
        assertEquals(
            ConnectionUiState.Reconnecting,
            connectionAttemptState(hasPublishedChannels = true),
        )
    }

    @Test
    fun connectionError_retainsActionableFailureKind() {
        assertEquals(
            ConnectionFailureKind.AUTHENTICATION,
            ConnectionUiState.Error(ConnectionFailureKind.AUTHENTICATION).kind,
        )
    }

    @Test
    fun subscriptionFailureParserRecognizesTerminalTunerStatuses() {
        assertEquals(
            SubscriptionFailureKind.NO_FREE_ADAPTER,
            subscriptionFailureKind(
                subscriptionError = "No free adapter",
                state = null,
            ),
        )
        assertEquals(
            SubscriptionFailureKind.TUNING_FAILED,
            subscriptionFailureKind(
                subscriptionError = null,
                state = "Tuning failed",
            ),
        )
        assertEquals(null, subscriptionFailureKind("OK", "Running"))
    }

    @Test
    fun subscriptionFailureTrackerNeverResurrectsAnOlderZapFailure() {
        var state = updateSubscriptionFailure(
            state = SubscriptionFailureTrackerState(),
            subscriptionId = 10,
            subscriptionError = "No free adapter",
            status = null,
        )
        assertEquals(SubscriptionFailureKind.NO_FREE_ADAPTER, state.currentFailure)

        state = updateSubscriptionFailure(
            state = state,
            subscriptionId = 11,
            subscriptionError = null,
            status = "Running",
        )
        assertEquals(null, state.currentFailure)

        state = removeSubscriptionFailure(state, subscriptionId = 11)
        state = updateSubscriptionFailure(
            state = state,
            subscriptionId = 10,
            subscriptionError = "No free adapter",
            status = null,
        )
        assertEquals(11, state.newestSeenSubscriptionId)
        assertEquals(null, state.currentFailure)
    }

    @Test
    fun subscriptionFailureTrackerTombstonesStoppedSubscriptionBeforeReplacementAppears() {
        var state = updateSubscriptionFailure(
            state = SubscriptionFailureTrackerState(),
            subscriptionId = 20,
            subscriptionError = "No free adapter",
            status = null,
        )

        state = removeSubscriptionFailure(state, subscriptionId = 20)
        state = updateSubscriptionFailure(
            state = state,
            subscriptionId = 20,
            subscriptionError = "No free adapter",
            status = null,
        )

        assertEquals(20, state.stoppedThroughSubscriptionId)
        assertEquals(null, state.currentFailure)

        state = updateSubscriptionFailure(
            state = state,
            subscriptionId = 21,
            subscriptionError = "Tuning failed",
            status = null,
        )
        assertEquals(SubscriptionFailureKind.TUNING_FAILED, state.currentFailure)
    }
}
