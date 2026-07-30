package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.ChannelUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplianceLaunchRequestsTest {
    private val channels = listOf(
        channel(id = 10, name = "Ten"),
        channel(id = 20, name = "Twenty"),
        channel(id = 30, name = "Thirty"),
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
                persistedId = 20,
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
                persistedId = 20,
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
                persistedId = 20,
            )
        )

        assertEquals(
            ApplianceLaunchTarget(
                request = pending.request,
                channelId = 20,
                serviceId = 20,
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
            persistedId = 99,
        )

        assertEquals(10, target?.channelId)
        assertEquals(10, target?.serviceId)
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
                persistedId = 20,
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
                persistedId = 20,
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
                persistedId = 20,
            )
        )

        requests.request()
        val next = requests.state.value as ApplianceLaunchState.Pending
        assertNull(
            requests.resolve(
                request = cancelled.request,
                readiness = ready,
                persistedId = 20,
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
                persistedId = 20,
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
                serviceId = target.serviceId,
                channelName = target.channelName,
            )
        )
        assertNull(
            requests.resolve(
                request = pending.request,
                readiness = ready,
                persistedId = 20,
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
                persistedId = 20,
            )
        )

        assertFalse(
            requests.completePlayerVisibility(
                target = target,
                channelId = 10,
                serviceId = target.serviceId,
                channelName = target.channelName,
            )
        )
        assertFalse(
            requests.completePlayerVisibility(
                target = target,
                channelId = target.channelId,
                serviceId = 10,
                channelName = target.channelName,
            )
        )
        assertFalse(
            requests.completePlayerVisibility(
                target = target,
                channelId = target.channelId,
                serviceId = target.serviceId,
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
                persistedId = 20,
            )
        )

        assertTrue(
            requests.completePlayerVisibility(
                target = target,
                channelId = target.channelId,
                serviceId = target.serviceId,
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
                persistedId = 20,
            )
        )
        assertTrue(requests.cancel(ApplianceLaunchState.Entering(firstTarget)))

        requests.request()
        val secondPending = requests.state.value as ApplianceLaunchState.Pending
        val secondTarget = requireNotNull(
            requests.resolve(
                request = secondPending.request,
                readiness = ready,
                persistedId = 20,
            )
        )

        assertFalse(
            requests.completePlayerVisibility(
                target = firstTarget,
                channelId = firstTarget.channelId,
                serviceId = firstTarget.serviceId,
                channelName = firstTarget.channelName,
            )
        )
        assertEquals(ApplianceLaunchState.Entering(secondTarget), requests.state.value)
    }

    private fun channel(id: Int, name: String) = ChannelUi(
        id = id,
        name = name,
        number = null,
        icon = null,
    )
}
