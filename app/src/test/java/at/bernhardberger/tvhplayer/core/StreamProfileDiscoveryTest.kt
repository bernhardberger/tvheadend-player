package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

class StreamProfileDiscoveryTest {
    @Test
    fun requestRunsOnTheOwnedIoDispatcher() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler, name = "profile-discovery-io")
        val discovery = StreamProfileDiscovery(ioDispatcher) {
            assertSame(
                ioDispatcher,
                currentCoroutineContext()[ContinuationInterceptor],
            )
            StreamProfilesResult.NotReady
        }

        assertSame(StreamProfilesResult.NotReady, discovery.discover())
    }
}
