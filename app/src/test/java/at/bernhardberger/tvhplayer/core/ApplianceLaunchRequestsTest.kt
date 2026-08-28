package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplianceLaunchRequestsTest {
    private val channels = listOf(
        Channel.create(ChannelId(10), name = "Ten"),
        Channel.create(ChannelId(20), name = "Twenty"),
        Channel.create(ChannelId(30), name = "Thirty"),
    )
    private val ready = CurrentChannelReadiness.Ready(channels)

    @Test
    fun request_transitionsIdleToPendingWithMonotonicIds() {
        val requests = ApplianceLaunchRequests()

        assertEquals(ApplianceLaunchState.Idle, requests.state.value)

        requests.request()
        val first = requests.state.value as ApplianceLaunchState.Pending
        assertTrue(requests.cancel(first))

        requests.request()
        val second = requests.state.value as ApplianceLaunchState.Pending

        assertTrue(second.request.id > first.request.id)
    }

    @Test
    fun retainedPending_restoresExactGenerationAndResolvesCurrentSnapshot() {
        var retainedRequestId: Long? = null
        val original = ApplianceLaunchRequests(
            onRetainedRequestIdChanged = { retainedRequestId = it },
        )
        original.request()
        val originalPending = original.state.value as ApplianceLaunchState.Pending

        val restored = ApplianceLaunchRequests(
            restoredRequestId = retainedRequestId,
            onRetainedRequestIdChanged = { retainedRequestId = it },
        )
        val restoredPending = restored.state.value as ApplianceLaunchState.Pending
        val currentReady = CurrentChannelReadiness.Ready(
            listOf(Channel.create(ChannelId(20), name = "Current Twenty")),
        )
        val target = requireNotNull(
            restored.resolve(
                request = restoredPending.request,
                readiness = currentReady,
                persistedId = ChannelId(20),
            )
        )

        assertEquals(originalPending.request, restoredPending.request)
        assertEquals(ChannelId(20), target.channelId)
        assertEquals("Current Twenty", target.channelName)
    }

    @Test
    fun retainedEntering_restoresAsPendingAndDoesNotReuseStaleTarget() {
        var retainedRequestId: Long? = null
        val original = ApplianceLaunchRequests(
            onRetainedRequestIdChanged = { retainedRequestId = it },
        )
        original.request()
        val originalPending = original.state.value as ApplianceLaunchState.Pending
        val staleTarget = requireNotNull(
            original.resolve(
                request = originalPending.request,
                readiness = CurrentChannelReadiness.Ready(
                    listOf(Channel.create(ChannelId(20), name = "Stale Twenty")),
                ),
                persistedId = ChannelId(20),
            )
        )
        assertEquals(ApplianceLaunchState.Entering(staleTarget), original.state.value)

        val restored = ApplianceLaunchRequests(
            restoredRequestId = retainedRequestId,
            onRetainedRequestIdChanged = { retainedRequestId = it },
        )
        val restoredPending = restored.state.value as ApplianceLaunchState.Pending
        val currentTarget = requireNotNull(
            restored.resolve(
                request = restoredPending.request,
                readiness = CurrentChannelReadiness.Ready(
                    listOf(Channel.create(ChannelId(20), name = "Current Twenty")),
                ),
                persistedId = ChannelId(20),
            )
        )

        assertEquals(staleTarget.request, restoredPending.request)
        assertEquals("Current Twenty", currentTarget.channelName)
        assertFalse(currentTarget == staleTarget)
    }

    @Test
    fun requestAfterRestoredGenerationClears_isGreaterAndRejectsStaleOperations() {
        var retainedRequestId: Long? = 41L
        val requests = ApplianceLaunchRequests(
            restoredRequestId = retainedRequestId,
            onRetainedRequestIdChanged = { retainedRequestId = it },
        )
        val restoredPending = requests.state.value as ApplianceLaunchState.Pending
        val restoredTarget = requireNotNull(
            requests.resolve(
                request = restoredPending.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )
        val restoredEntering = ApplianceLaunchState.Entering(restoredTarget)

        assertTrue(requests.cancel(restoredEntering))
        assertNull(retainedRequestId)
        requests.request()
        val nextPending = requests.state.value as ApplianceLaunchState.Pending
        val nextTarget = requireNotNull(
            requests.resolve(
                request = nextPending.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )

        assertTrue(nextPending.request.id > restoredPending.request.id)
        assertFalse(requests.cancel(restoredEntering))
        assertFalse(
            requests.completePlayerVisibility(
                target = restoredTarget,
                channelId = restoredTarget.channelId,
                channelName = restoredTarget.channelName,
            )
        )
        assertEquals(ApplianceLaunchState.Entering(nextTarget), requests.state.value)
    }

    @Test
    fun disabledStartup_staysIdle() {
        val requests = ApplianceLaunchRequests()

        requests.requestStartup(autoStartPlayback = false)

        assertEquals(ApplianceLaunchState.Idle, requests.state.value)
    }

    @Test
    fun enabledStartup_transitionsIdleToPending() {
        val requests = ApplianceLaunchRequests()

        requests.requestStartup(autoStartPlayback = true)

        assertTrue(requests.state.value is ApplianceLaunchState.Pending)
    }

    @Test
    fun requestsCoalesceWhilePending() {
        val requests = ApplianceLaunchRequests()
        requests.request()
        val pending = requests.state.value

        requests.request()
        requests.requestStartup(autoStartPlayback = true)

        assertEquals(pending, requests.state.value)
    }

    @Test
    fun onlyReadyReadiness_resolvesPendingRequest() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val pending = requests.state.value as ApplianceLaunchState.Pending

        assertNull(
            requests.resolve(
                request = pending.request,
                readiness = CurrentChannelReadiness.Waiting,
                persistedId = ChannelId(20),
            )
        )
        assertEquals(pending, requests.state.value)
    }

    @Test
    fun readyEmpty_doesNotConsumePendingRequest() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val pending = requests.state.value as ApplianceLaunchState.Pending

        assertNull(
            requests.resolve(
                request = pending.request,
                readiness = CurrentChannelReadiness.Ready(emptyList()),
                persistedId = ChannelId(20),
            )
        )
        assertEquals(pending, requests.state.value)
    }

    @Test
    fun readyChannels_commitPersistedChannelFromSameSnapshot() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val pending = requests.state.value as ApplianceLaunchState.Pending

        val target = requireNotNull(
            requests.resolve(
                request = pending.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )

        assertEquals(
            ApplianceLaunchTarget(
                request = pending.request,
                channelId = ChannelId(20),
                channelName = "Twenty",
            ),
            target,
        )
        assertEquals(ApplianceLaunchState.Entering(target), requests.state.value)
    }

    @Test
    fun stalePersistedChannel_commitsFirstCurrentChannel() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val pending = requests.state.value as ApplianceLaunchState.Pending

        val target = requests.resolve(
            request = pending.request,
            readiness = ready,
            persistedId = ChannelId(99),
        )

        assertEquals(ChannelId(10), target?.channelId)
        assertEquals("Ten", target?.channelName)
    }

    @Test
    fun pendingCommit_isAtomicAndRequestsCoalesceWhileEntering() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val pending = requests.state.value as ApplianceLaunchState.Pending
        val target = requireNotNull(
            requests.resolve(
                request = pending.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )
        val entering = ApplianceLaunchState.Entering(target)

        requests.request()
        requests.requestStartup(autoStartPlayback = true)

        assertEquals(entering, requests.state.value)
        assertNull(
            requests.resolve(
                request = pending.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )
    }

    @Test
    fun cancellingPending_preventsLateResolutionAndDoesNotTouchNewGeneration() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val cancelled = requests.state.value as ApplianceLaunchState.Pending

        assertTrue(requests.cancel(cancelled))
        assertNull(
            requests.resolve(
                request = cancelled.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )

        requests.request()
        val next = requests.state.value as ApplianceLaunchState.Pending
        assertNull(
            requests.resolve(
                request = cancelled.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )
        assertEquals(next, requests.state.value)
        assertFalse(requests.cancel(cancelled))
    }

    @Test
    fun cancellingUncommittedEntering_preventsLateEntryAndCompletion() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val pending = requests.state.value as ApplianceLaunchState.Pending
        val target = requireNotNull(
            requests.resolve(
                request = pending.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )
        val entering = ApplianceLaunchState.Entering(target)

        assertTrue(requests.isEntering(target))
        assertFalse(
            requests.cancel(
                entering.copy(target = target.copy(channelName = "Wrong target"))
            )
        )
        assertEquals(entering, requests.state.value)
        assertTrue(requests.cancel(entering))
        assertFalse(requests.isEntering(target))
        assertFalse(
            requests.completePlayerVisibility(
                target = target,
                channelId = target.channelId,
                channelName = target.channelName,
            )
        )
        assertNull(
            requests.resolve(
                request = pending.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )
        assertEquals(ApplianceLaunchState.Idle, requests.state.value)
    }

    @Test
    fun mismatchedPlayerArguments_doNotCompleteEntering() {
        val requests = ApplianceLaunchRequests()
        requests.request()
        val pending = requests.state.value as ApplianceLaunchState.Pending
        val target = requireNotNull(
            requests.resolve(
                request = pending.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )

        assertFalse(
            requests.completePlayerVisibility(
                target = target,
                channelId = ChannelId(10),
                channelName = target.channelName,
            )
        )
        assertFalse(
            requests.completePlayerVisibility(
                target = target,
                channelId = target.channelId,
                channelName = "Stale name",
            )
        )
        assertEquals(ApplianceLaunchState.Entering(target), requests.state.value)
    }

    @Test
    fun matchingPlayerVisibility_completesEnteringToIdle() {
        val requests = ApplianceLaunchRequests()
        requests.request()
        val pending = requests.state.value as ApplianceLaunchState.Pending
        val target = requireNotNull(
            requests.resolve(
                request = pending.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )

        assertTrue(
            requests.completePlayerVisibility(
                target = target,
                channelId = target.channelId,
                channelName = target.channelName,
            )
        )
        assertEquals(ApplianceLaunchState.Idle, requests.state.value)
    }

    @Test
    fun staleVisibilityCompletion_cannotCompleteNewEnteringGeneration() {
        val requests = ApplianceLaunchRequests()
        requests.request()
        val firstPending = requests.state.value as ApplianceLaunchState.Pending
        val firstTarget = requireNotNull(
            requests.resolve(
                request = firstPending.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )
        assertTrue(requests.cancel(ApplianceLaunchState.Entering(firstTarget)))

        requests.request()
        val secondPending = requests.state.value as ApplianceLaunchState.Pending
        val secondTarget = requireNotNull(
            requests.resolve(
                request = secondPending.request,
                readiness = ready,
                persistedId = ChannelId(20),
            )
        )

        assertFalse(
            requests.completePlayerVisibility(
                target = firstTarget,
                channelId = firstTarget.channelId,
                channelName = firstTarget.channelName,
            )
        )
        assertEquals(ApplianceLaunchState.Entering(secondTarget), requests.state.value)
    }

}
