package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
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
        val currentSession = FakeSessionObservation(
            SessionObservation.create(
                sessionState = SessionState.Ready(
                    ServerCapabilities.create(
                        streaming = CapabilityAccess.ALLOWED,
                        dvrWrite = CapabilityAccess.ALLOWED,
                    )
                ),
                channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
                epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
                dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
            )
        ).captureCurrentSession()
        val discovery = StreamProfileDiscovery(ioDispatcher) {
            assertSame(
                ioDispatcher,
                currentCoroutineContext()[ContinuationInterceptor],
            )
            StreamProfilesResult.NotReady
        }

        assertSame(StreamProfilesResult.NotReady, discovery.discover(currentSession))
    }
}
