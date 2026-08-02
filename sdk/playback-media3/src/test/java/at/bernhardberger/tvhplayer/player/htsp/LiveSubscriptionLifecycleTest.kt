package at.bernhardberger.tvhplayer.player.htsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSubscriptionLifecycleTest {
    @Test
    fun requestWrittenThenCancelledKeepsAttemptBoundOwnershipUntilUnsubscribeSettles() {
        val lifecycle = LiveSubscriptionLifecycle()

        assertTrue(lifecycle.beginOpen())
        assertTrue(lifecycle.beginSubscriptionRequest(attemptId = 42L))
        assertEquals(42L, lifecycle.ownershipAttemptId())

        // Retirement cannot send unsubscribe ahead of the still-running subscribe request.
        assertFalse(lifecycle.retireRequiresUnsubscribe())
        assertFalse(lifecycle.awaitOwnershipSettlement(timeoutMillis = 1L))

        // The open owner handles the ambiguous request result after cancellation.
        assertTrue(lifecycle.failedOpenRequiresUnsubscribe(attemptId = 42L))
        lifecycle.unsubscribeSettled(attemptId = 42L, success = false)
        lifecycle.openSettled()
        assertFalse(lifecycle.awaitOwnershipSettlement(timeoutMillis = 1L))

        assertTrue(lifecycle.retireRequiresUnsubscribe())
        lifecycle.unsubscribeSettled(attemptId = 42L, success = true)
        assertTrue(lifecycle.awaitOwnershipSettlement(timeoutMillis = 1L))
    }

    @Test
    fun disappearedAttemptSettlesUncertainOwnershipWithoutUnsubscribe() {
        val lifecycle = LiveSubscriptionLifecycle()

        assertTrue(lifecycle.beginOpen())
        assertTrue(lifecycle.beginSubscriptionRequest(attemptId = 7L))
        assertTrue(lifecycle.connectionAttemptUnavailable(attemptId = 7L))
        lifecycle.openSettled()

        assertEquals(null, lifecycle.ownershipAttemptId())
        assertTrue(lifecycle.awaitOwnershipSettlement(timeoutMillis = 1L))
    }

    @Test
    fun reopenedSourceRequiresFreshAcceptanceOnReplacementAttempt() {
        val lifecycle = LiveSubscriptionLifecycle()

        assertTrue(lifecycle.beginOpen())
        assertTrue(lifecycle.beginSubscriptionRequest(attemptId = 10L))
        assertEquals(
            LiveSubscriptionAcceptance.OWNED,
            lifecycle.subscriptionAccepted(attemptId = 10L),
        )
        lifecycle.openSettled()

        assertTrue(lifecycle.beginOpen())
        assertTrue(lifecycle.connectionAttemptUnavailable(attemptId = 10L))
        assertTrue(lifecycle.beginSubscriptionRequest(attemptId = 11L))
        assertFalse(lifecycle.commitIfOwned(attemptId = 10L) {})
        assertEquals(
            LiveSubscriptionAcceptance.OWNED,
            lifecycle.subscriptionAccepted(attemptId = 11L),
        )
        assertTrue(lifecycle.commitIfOwned(attemptId = 11L) {})
        lifecycle.openSettled()
    }

    @Test
    fun acceptedSubscriptionIsUnsubscribedExactlyOnce() {
        val lifecycle = LiveSubscriptionLifecycle()

        assertTrue(lifecycle.beginOpen())
        assertTrue(lifecycle.beginSubscriptionRequest(attemptId = 1L))
        assertEquals(
            LiveSubscriptionAcceptance.OWNED,
            lifecycle.subscriptionAccepted(attemptId = 1L),
        )
        lifecycle.openSettled()
        assertTrue(lifecycle.retireRequiresUnsubscribe())
        lifecycle.unsubscribeSettled(attemptId = 1L, success = true)
        assertFalse(lifecycle.retireRequiresUnsubscribe())
        assertTrue(lifecycle.awaitOwnershipSettlement(timeoutMillis = 1L))
        assertFalse(lifecycle.beginOpen())
    }

    @Test
    fun releaseRacingAheadOfSubscribeReplyStillUnsubscribesTheAcceptedSubscription() {
        val lifecycle = LiveSubscriptionLifecycle()

        assertTrue(lifecycle.beginOpen())
        assertTrue(lifecycle.beginSubscriptionRequest(attemptId = 2L))
        assertFalse(lifecycle.retireRequiresUnsubscribe())
        assertFalse(lifecycle.awaitOwnershipSettlement(timeoutMillis = 1L))
        assertEquals(
            LiveSubscriptionAcceptance.RELEASE_IMMEDIATELY,
            lifecycle.subscriptionAccepted(attemptId = 2L),
        )
        assertTrue(lifecycle.pendingUnsubscribeRequiresUnsubscribe())
        assertFalse(lifecycle.commitIfOwned(attemptId = 2L) {})
        lifecycle.unsubscribeSettled(attemptId = 2L, success = true)
        lifecycle.openSettled()
        assertTrue(lifecycle.awaitOwnershipSettlement(timeoutMillis = 1L))
    }

    @Test
    fun ownedSubscriptionCanStartOnlyUntilReleaseWins() {
        val lifecycle = LiveSubscriptionLifecycle()
        var starts = 0

        lifecycle.beginOpen()
        lifecycle.beginSubscriptionRequest(attemptId = 3L)
        lifecycle.subscriptionAccepted(attemptId = 3L)
        assertTrue(lifecycle.commitIfOwned(attemptId = 3L) { starts++ })
        assertTrue(lifecycle.retireRequiresUnsubscribe())
        assertFalse(lifecycle.commitIfOwned(attemptId = 3L) { starts++ })
        assertEquals(1, starts)
    }

    @Test
    fun terminalStatusBeforeReplyFencesStartAndOwnsTheLateUnsubscribe() {
        val lifecycle = LiveSubscriptionLifecycle()

        assertTrue(lifecycle.beginOpen())
        assertTrue(lifecycle.beginSubscriptionRequest(attemptId = 4L))
        assertFalse(lifecycle.terminalFailureRequiresUnsubscribe())
        assertEquals(
            LiveSubscriptionAcceptance.RELEASE_IMMEDIATELY,
            lifecycle.subscriptionAccepted(attemptId = 4L),
        )
        assertTrue(lifecycle.pendingUnsubscribeRequiresUnsubscribe())
        assertFalse(lifecycle.commitIfOwned(attemptId = 4L) {})
    }

    @Test
    fun terminalStatusAfterAcceptanceClaimsExactlyOneUnsubscribe() {
        val lifecycle = LiveSubscriptionLifecycle()
        lifecycle.beginOpen()
        lifecycle.beginSubscriptionRequest(attemptId = 5L)
        lifecycle.subscriptionAccepted(attemptId = 5L)
        lifecycle.openSettled()

        assertTrue(lifecycle.terminalFailureRequiresUnsubscribe())
        assertFalse(lifecycle.terminalFailureRequiresUnsubscribe())
        assertFalse(lifecycle.retireRequiresUnsubscribe())
        assertFalse(lifecycle.awaitOwnershipSettlement(timeoutMillis = 1L))
        lifecycle.unsubscribeSettled(attemptId = 5L, success = true)
        assertTrue(lifecycle.awaitOwnershipSettlement(timeoutMillis = 1L))
    }

    @Test
    fun failedUnsubscribePreventsReplacementOwnershipFromProceeding() {
        val lifecycle = LiveSubscriptionLifecycle()
        lifecycle.beginOpen()
        lifecycle.beginSubscriptionRequest(attemptId = 6L)
        lifecycle.subscriptionAccepted(attemptId = 6L)
        lifecycle.openSettled()
        lifecycle.retireRequiresUnsubscribe()
        lifecycle.unsubscribeSettled(attemptId = 6L, success = false)

        assertFalse(lifecycle.awaitOwnershipSettlement(timeoutMillis = 1L))
        assertTrue(lifecycle.retireRequiresUnsubscribe())
        lifecycle.unsubscribeSettled(attemptId = 6L, success = true)
        assertTrue(lifecycle.awaitOwnershipSettlement(timeoutMillis = 1L))
    }
}
