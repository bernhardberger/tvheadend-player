package at.bernhardberger.tvhplayer.viewmodels

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
import at.bernhardberger.tvheadend.sdk.core.StreamProfile
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class AppConnectionProfileMigrationTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun supersededReadyResultCannotMigrateAndReplacementGenerationStillCan() = runTest {
        val observations = FakeSessionObservation(currentObservation("first"))
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val migrations = mutableListOf<String>()
        var discoveries = 0

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            collectReadyStreamProfileMigrations(
                observations = observations.observation,
                currentObservation = observations.observation::value,
                discover = {
                    discoveries += 1
                    if (discoveries == 1) {
                        withContext(NonCancellable) {
                            firstStarted.complete(Unit)
                            releaseFirst.await()
                            available("11111111111111111111111111111111")
                        }
                    } else {
                        available("22222222222222222222222222222222")
                    }
                },
                migrate = { profiles -> migrations += profiles.single().id },
            )
        }

        firstStarted.await()
        observations.retire(
            SessionObservation.create(sessionState = SessionState.Disconnected)
        )
        releaseFirst.complete(Unit)
        runCurrent()
        observations.publish(currentObservation("second"))
        runCurrent()

        assertEquals(2, discoveries)
        assertEquals(listOf("22222222222222222222222222222222"), migrations)
    }

    private fun currentObservation(serverName: String): SessionObservation =
        SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED,
                    dvrWrite = CapabilityAccess.DENIED,
                    serverName = serverName,
                ),
            ),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )

    private fun available(id: String): StreamProfilesResult.Available =
        StreamProfilesResult.Available.create(
            listOf(StreamProfile(StreamProfileId(id), "profile", "")),
        )
}
