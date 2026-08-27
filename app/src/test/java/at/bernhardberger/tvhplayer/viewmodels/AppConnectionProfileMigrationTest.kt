package at.bernhardberger.tvhplayer.viewmodels

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.StreamProfile
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
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
        val firstReady = ready("first")
        val secondReady = ready("second")
        val states = MutableStateFlow<SessionState>(firstReady)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val migrations = mutableListOf<String>()
        var discoveries = 0

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            collectReadyStreamProfileMigrations(
                states = states,
                currentState = states::value,
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
        states.value = SessionState.Disconnected
        releaseFirst.complete(Unit)
        runCurrent()
        states.value = secondReady
        runCurrent()

        assertEquals(2, discoveries)
        assertEquals(listOf("22222222222222222222222222222222"), migrations)
    }

    private fun ready(serverName: String): SessionState.Ready = SessionState.Ready(
        ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED,
            dvrWrite = CapabilityAccess.DENIED,
            serverName = serverName,
        ),
    )

    private fun available(id: String): StreamProfilesResult.Available =
        StreamProfilesResult.Available.create(
            listOf(StreamProfile(StreamProfileId(id), "profile", "")),
        )
}
