package at.bernhardberger.tvhplayer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** The existing discovery/observation fences must survive switching the profile store. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppProfileDiscoveryTest {
    private val streamId = StreamProfileId("11111111111111111111111111111111")

    @Test fun targetDiscoveryDuringPersistenceResolvesTheTargetsPreferenceAfterCommit() = runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val target = (stored.addAnonymous("target.invalid") as ServerProfileWriteResult.Applied).id
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        settings.useProfile(target); settings.setStreamProfile(streamId); settings.useProfile(first)
        val gate = CompletableDeferred<Unit>()
        var writing = false
        var discovered = false
        val store = object : ServerProfileStore by stored {
            override suspend fun setActive(id: ServerProfileId): ServerProfileWriteResult {
                writing = true; gate.await(); return stored.setActive(id)
            }
        }
        val observations = FakeSessionObservation(SessionObservation.create())
        val session = object : TvheadendSession by FakeTvheadendSession() {
            override val observation = observations.observation
            override suspend fun getStreamProfiles(currentSession: CurrentSessionObservation): StreamProfilesResult {
                discovered = true
                return available(streamId, currentSession)
            }
        }
        val owner = AppProfileOwner(session, store, settings, StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }; runCurrent()
        val attempt = backgroundScope.async { owner.switchActive(target) }; runCurrent()
        observations.publish(currentObservation("target")); runCurrent()
        assertTrue(writing); assertTrue(discovered)
        assertNull(owner.selectedStreamProfileId.value)
        gate.complete(Unit); runCurrent()
        assertEquals(ServerProfileWriteResult.Applied(target), attempt.await())
        assertEquals(streamId, owner.selectedStreamProfileId.value)
    }

    @Test fun selectedProfileReadWaitsForCurrentDiscoveryToSettle() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val observations = FakeSessionObservation(currentObservation("server"))
        val fake = FakeTvheadendSession()
        val session = object : TvheadendSession by fake {
            override val observation = observations.observation
            override suspend fun getStreamProfiles(currentSession: CurrentSessionObservation): StreamProfilesResult {
                started.complete(Unit)
                release.await()
                return available(streamId, currentSession)
            }
        }
        val stored = FakeServerProfileStore()
        val id = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        settings.useProfile(id)
        settings.setStreamProfile(streamId)
        val owner = AppProfileOwner(session, stored, settings, StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        started.await()
        val selected = async { owner.selectedStreamProfileIdFor(checkNotNull(observations.observation.value.currentSession)) }
        runCurrent()
        assertFalse(selected.isCompleted)
        release.complete(Unit)
        runCurrent()
        assertEquals(streamId, selected.await())
    }

    @Test fun selectedProfileReadSettlesWhenPersistedSelectionReadFails() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val observations = FakeSessionObservation(currentObservation("server"))
        val current = checkNotNull(observations.observation.value.currentSession)
        val session = object : TvheadendSession by FakeTvheadendSession() {
            override val observation = observations.observation
            override suspend fun getStreamProfiles(currentSession: CurrentSessionObservation): StreamProfilesResult = available(streamId, currentSession)
        }
        val data = object : DataStore<Preferences> by InMemoryPreferencesDataStore() {
            override val data = flow<Preferences> {
                started.complete(Unit)
                release.await()
                throw java.io.IOException("selection read failed")
            }
        }
        val stored = FakeServerProfileStore()
        stored.addAnonymous("first.invalid", activate = true)
        val owner = AppProfileOwner(session, stored, PlayerSettingsStore(data), StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        started.await()
        val selected = async { owner.selectedStreamProfileIdFor(current) }
        runCurrent()
        assertFalse(selected.isCompleted)
        release.complete(Unit)
        runCurrent()
        assertTrue(selected.isCompleted)
        assertNull(selected.await())
        assertTrue(owner.streamProfiles.value is StreamProfilesResult.Available)
    }

    @Test fun selectedProfileReadReturnsWhenObservationExpiresDuringDiscovery() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val observations = FakeSessionObservation(currentObservation("server"))
        val current = checkNotNull(observations.observation.value.currentSession)
        val session = object : TvheadendSession by FakeTvheadendSession() {
            override val observation = observations.observation
            override suspend fun getStreamProfiles(currentSession: CurrentSessionObservation): StreamProfilesResult {
                started.complete(Unit)
                release.await()
                return available(streamId, currentSession)
            }
        }
        val stored = FakeServerProfileStore()
        stored.addAnonymous("first.invalid", activate = true)
        val owner = AppProfileOwner(session, stored, PlayerSettingsStore(InMemoryPreferencesDataStore()), StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        started.await()
        val selected = async { owner.selectedStreamProfileIdFor(current) }
        runCurrent()
        assertFalse(selected.isCompleted)
        observations.retire(SessionObservation.create(sessionState = SessionState.Disconnected))
        runCurrent()
        assertNull(selected.await())
    }

    @Test fun staleGenerationIsDiscardedAndSelectionFinishesBeforeOwnerCancellation() = runTest {
        val observations = FakeSessionObservation(currentObservation("first"))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val selectionStarted = CompletableDeferred<Unit>()
        val releaseSelection = CompletableDeferred<Unit>()
        val io = StandardTestDispatcher(testScheduler, name = "profile-owner-io")
        val secondId = StreamProfileId("22222222222222222222222222222222")
        var discoveries = 0
        var pauseSelection = false
        val session = object : TvheadendSession by FakeTvheadendSession() {
            override val observation = observations.observation
            override suspend fun getStreamProfiles(currentSession: CurrentSessionObservation): StreamProfilesResult {
                assertSame(io, currentCoroutineContext()[ContinuationInterceptor])
                discoveries++
                return if (discoveries == 1) withContext(NonCancellable) {
                    started.complete(Unit)
                    release.await()
                    available(streamId, currentSession)
                } else available(secondId, currentSession)
            }
        }
        val data = InMemoryPreferencesDataStore(beforeUpdate = {
            if (pauseSelection) {
                pauseSelection = false
                selectionStarted.complete(Unit)
                withContext(NonCancellable) { releaseSelection.await() }
            }
        })
        val stored = FakeServerProfileStore()
        stored.addAnonymous("first.invalid", activate = true)
        val settings = PlayerSettingsStore(data)
        val owner = AppProfileOwner(session, stored, settings, io, { ServerProfileEditReadResult.Missing })
        val job = backgroundScope.launch { owner.run() }
        runCurrent()
        started.await()
        observations.retire(SessionObservation.create(sessionState = SessionState.Disconnected))
        release.complete(Unit)
        runCurrent()
        observations.publish(currentObservation("second"))
        runCurrent()
        assertEquals(2, discoveries)
        val profiles = owner.streamProfiles.value as StreamProfilesResult.Available
        assertEquals(secondId, profiles.profiles.single().id)
        assertNull(owner.selectedStreamProfileId.value)
        val failed = async { runCatching { owner.saveServer("", 9982) } }
        runCurrent()
        assertTrue(failed.await().isFailure)
        assertNotNull(owner.serverProfile.value)
        pauseSelection = true
        val selection = async { owner.selectStreamProfile(secondId) }
        runCurrent()
        selectionStarted.await()
        val close = async { job.cancelAndJoin() }
        runCurrent()
        assertFalse(close.isCompleted)
        releaseSelection.complete(Unit)
        runCurrent()
        selection.await()
        close.await()
        assertEquals(secondId, owner.selectedStreamProfileId.value)
        assertEquals(secondId, settings.resolveStreamProfileSelection(profiles.profiles) { true })
    }

    private fun currentObservation(name: String): SessionObservation = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.DENIED, serverName = name)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )

    private fun available(id: StreamProfileId, currentSession: CurrentSessionObservation): StreamProfilesResult.Available =
        StreamProfilesResult.Available.create(profiles = listOf(StreamProfile(id, "profile", "")), originatingSession = currentSession)
}
