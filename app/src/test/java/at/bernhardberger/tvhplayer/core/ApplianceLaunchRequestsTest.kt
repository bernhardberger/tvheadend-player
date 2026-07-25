package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplianceLaunchRequestsTest {
    private val channels = listOf(10, 20, 30)

    @Test
    fun enabledStartup_hasOnePendingRequest() {
        val requests = ApplianceLaunchRequests()

        requests.requestStartup(autoStartPlayback = true)

        assertNotNull(requests.pending.value)
    }

    @Test
    fun disabledStartup_hasNoPendingRequest() {
        val requests = ApplianceLaunchRequests()

        requests.requestStartup(autoStartPlayback = false)

        assertNull(requests.pending.value)
    }

    @Test
    fun startupDoesNotReplacePendingExplicitRequest() {
        val requests = ApplianceLaunchRequests()
        requests.request()
        val explicitRequest = requests.pending.value

        requests.requestStartup(autoStartPlayback = true)

        assertEquals(explicitRequest, requests.pending.value)
    }

    @Test
    fun emptyChannelList_doesNotConsumePendingRequest() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val pending = requests.pending.value

        assertNull(requests.resolve(emptyList(), persistedId = 20))
        assertEquals(pending, requests.pending.value)
    }

    @Test
    fun loadedChannels_resolvePersistedChannel() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)

        assertEquals(20, requests.resolve(channels, persistedId = 20)?.channelId)
    }

    @Test
    fun stalePersistedChannel_resolvesFirstCurrentChannel() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)

        assertEquals(10, requests.resolve(channels, persistedId = 99)?.channelId)
    }

    @Test
    fun consumedRequest_doesNotResolveAgainOnRecompositionOrResume() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val target = requireNotNull(requests.resolve(channels, persistedId = 20))

        assertTrue(requests.consume(target.request))
        assertNull(requests.resolve(channels, persistedId = 20))
        assertNull(requests.resolve(channels, persistedId = 20))
    }

    @Test
    fun explicitNewRequest_startsPlaybackAgain() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val first = requireNotNull(requests.resolve(channels, persistedId = 20))
        assertTrue(requests.consume(first.request))

        requests.request()

        val second = requireNotNull(requests.resolve(channels, persistedId = 20))
        assertEquals(20, second.channelId)
        assertTrue(second.request.id > first.request.id)
    }

    @Test
    fun backWithoutExplicitLaunch_leavesNoPendingRequest() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val target = requireNotNull(requests.resolve(channels, persistedId = 20))
        assertTrue(requests.consume(target.request))

        // Closing the player has no request-producing policy event.
        assertNull(requests.pending.value)
    }

    @Test
    fun pendingStartup_canBeCancelledForOperatorAccess() {
        val requests = ApplianceLaunchRequests()
        requests.requestStartup(autoStartPlayback = true)
        val pending = requireNotNull(requests.pending.value)

        assertTrue(requests.cancel(pending))
        assertNull(requests.pending.value)
        assertNull(requests.resolve(channels, persistedId = 20))
    }
}
