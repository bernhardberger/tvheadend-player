package at.bernhardberger.tvhplayer.settings

import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.ArtworkLoader
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrRepository
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepository
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.PlaybackBinding
import at.bernhardberger.tvheadend.sdk.core.PlaybackBindingResult
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.ServerProfile
import at.bernhardberger.tvheadend.sdk.core.ServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.SessionCommandResult
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.StreamProfile
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProfileOwnerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun freshSetupReadsOnlyTheCurrentStoreAndRemainsDisconnected() = runTest {
        val store = at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore()
        val session = at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession()
        val events = mutableListOf<String>()
        val owner = AppProfileOwner(
            session = session,
            profileStore = store,
            playerSettings = PlayerSettingsStore(InMemoryPreferencesDataStore()),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            readProfileForEditing = {
                events += "edit"
                at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult.Missing
            },
        )
        val job = backgroundScope.launch { owner.run() }
        runCurrent()
        assertEquals(ServerProfileReadResult.Missing, owner.serverProfile.value)
        assertEquals(listOf(
            at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStoreCall.LOAD_PROFILE,
        ), store.calls)
        assertFalse(session.calls.contains(at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall.CONNECT))
        owner.loadServerForEditing { _, _, _, _ -> error("Missing profile must not expose editing values") }
        assertEquals(listOf("edit"), events)
        job.cancelAndJoin()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun currentProfileIsLoadedAndConnectedWithoutRewritingIt() = runTest {
        val session = at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession()
        val profile = ServerProfileReadResult.anonymous("offline.invalid")
        val store = at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore(
            profile,
        )
        val owner = AppProfileOwner(
            session = session, profileStore = store,
            playerSettings = PlayerSettingsStore(InMemoryPreferencesDataStore()),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            readProfileForEditing = { error("Unexpected editing read") },
        )
        val job = backgroundScope.launch { owner.run() }
        runCurrent()
        assertSame(profile, owner.serverProfile.value)
        assertEquals(listOf(
            at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStoreCall.LOAD_PROFILE,
        ), store.calls)
        assertTrue(session.calls.contains(at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall.CONNECT))
        job.cancelAndJoin()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancellationStillWaitsForNonCancellableProfileInitialization() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val stored = at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore()
        val profileStore = object : ServerProfileStore by stored {
            override suspend fun loadProfile(): ServerProfileReadResult {
                entered.complete(Unit)
                release.await()
                return stored.loadProfile()
            }
        }
        val owner = AppProfileOwner(
            session = at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession(),
            profileStore = profileStore,
            playerSettings = PlayerSettingsStore(InMemoryPreferencesDataStore()),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            readProfileForEditing = { error("Unexpected editing read") },
        )
        val job = backgroundScope.launch { owner.run() }
        runCurrent()
        assertTrue(entered.isCompleted)
        job.cancel()
        runCurrent()
        assertFalse(job.isCompleted)
        release.complete(Unit)
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(ServerProfileReadResult.Missing, owner.serverProfile.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancelledPendingPasswordSaveReleasesCredentialLease() = runTest {
        val session = ProfileSession(
            FakeSessionObservation(currentObservation("server")).observation,
        ) { currentSession ->
            available(StreamProfileId("11111111111111111111111111111111"), currentSession)
        }
        val owner = profileOwner(
            session,
            PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler),
        )
        var leaseReleases = 0

        val save = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            owner.savePasswordServer(
                host = "tvheadend.invalid",
                htspPort = 9982,
                username = "viewer",
                password = "fake password",
                credentialLease = CredentialEditLease { leaseReleases += 1 },
            )
        }
        runCurrent()

        assertFalse(save.isCompleted)
        save.cancelAndJoin()
        assertEquals(1, leaseReleases)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun failedAcceptedPasswordSaveReleasesCredentialLease() = runTest {
        val session = ProfileSession(
            FakeSessionObservation(currentObservation("server")).observation,
        ) { currentSession ->
            available(StreamProfileId("11111111111111111111111111111111"), currentSession)
        }
        val owner = profileOwner(
            session,
            PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler),
        )
        var leaseReleases = 0

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { owner.run() }
        runCurrent()
        val result = runCatching {
            owner.savePasswordServer(
                host = "",
                htspPort = 9982,
                username = "viewer",
                password = "fake password",
                credentialLease = CredentialEditLease { leaseReleases += 1 },
            )
        }

        assertTrue(result.isFailure)
        assertEquals(1, leaseReleases)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun selectedProfileReadWaitsForCurrentDiscoveryToSettle() = runTest {
        val selectedId = StreamProfileId("11111111111111111111111111111111")
        val discoveryStarted = CompletableDeferred<Unit>()
        val releaseDiscovery = CompletableDeferred<Unit>()
        val observations = FakeSessionObservation(currentObservation("server"))
        val session = ProfileSession(observations.observation) { currentSession ->
            discoveryStarted.complete(Unit)
            releaseDiscovery.await()
            available(selectedId, currentSession)
        }
        val dataStore = InMemoryPreferencesDataStore(
            initial = preferencesOf(stringPreferencesKey("profileUuid") to selectedId.value),
        )
        val owner = profileOwner(
            session,
            PlayerSettingsStore(dataStore),
            StandardTestDispatcher(testScheduler),
        )

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { owner.run() }
        runCurrent()
        discoveryStarted.await()
        val selected = async {
            owner.selectedStreamProfileIdFor(
                checkNotNull(observations.observation.value.currentSession),
            )
        }
        runCurrent()

        assertFalse(selected.isCompleted)
        releaseDiscovery.complete(Unit)
        runCurrent()
        assertEquals(selectedId, selected.await())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun selectedProfileReadSettlesWhenPersistedSelectionReadFails() = runTest {
        val selectionReadStarted = CompletableDeferred<Unit>()
        val releaseSelectionRead = CompletableDeferred<Unit>()
        val observations = FakeSessionObservation(currentObservation("server"))
        val currentSession = checkNotNull(observations.observation.value.currentSession)
        val session = ProfileSession(observations.observation) { originatingSession ->
            available(StreamProfileId("11111111111111111111111111111111"), originatingSession)
        }
        val dataStore = object : DataStore<Preferences> by InMemoryPreferencesDataStore() {
            override val data = flow<Preferences> {
                selectionReadStarted.complete(Unit)
                releaseSelectionRead.await()
                throw java.io.IOException("selection read failed")
            }
        }
        val owner = profileOwner(
            session,
            PlayerSettingsStore(dataStore),
            StandardTestDispatcher(testScheduler),
        )

        backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { owner.run() }
        }
        runCurrent()
        selectionReadStarted.await()
        val selected = async { owner.selectedStreamProfileIdFor(currentSession) }
        runCurrent()
        assertFalse(selected.isCompleted)

        releaseSelectionRead.complete(Unit)
        runCurrent()

        assertTrue(selected.isCompleted)
        assertNull(selected.await())
        assertTrue(owner.streamProfiles.value is StreamProfilesResult.Available)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun selectedProfileReadReturnsWhenObservationExpiresDuringDiscovery() = runTest {
        val discoveryStarted = CompletableDeferred<Unit>()
        val releaseDiscovery = CompletableDeferred<Unit>()
        val observations = FakeSessionObservation(currentObservation("server"))
        val currentSession = checkNotNull(observations.observation.value.currentSession)
        val session = ProfileSession(observations.observation) { originatingSession ->
            discoveryStarted.complete(Unit)
            releaseDiscovery.await()
            available(StreamProfileId("11111111111111111111111111111111"), originatingSession)
        }
        val owner = profileOwner(
            session,
            PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler),
        )

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { owner.run() }
        runCurrent()
        discoveryStarted.await()
        val selected = async { owner.selectedStreamProfileIdFor(currentSession) }
        runCurrent()

        assertFalse(selected.isCompleted)
        observations.retire(SessionObservation.create(sessionState = SessionState.Disconnected))
        runCurrent()
        assertNull(selected.await())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun staleGenerationIsDiscardedAndReconnectDiscoversOnceOnOwnedIoDispatcher() = runTest {
        val observations = FakeSessionObservation(currentObservation("first"))
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val selectionStarted = CompletableDeferred<Unit>()
        val releaseSelection = CompletableDeferred<Unit>()
        val ioDispatcher = StandardTestDispatcher(testScheduler, name = "profile-owner-io")
        val secondId = StreamProfileId("22222222222222222222222222222222")
        var discoveries = 0
        var pauseSelectionUpdate = false
        val session = ProfileSession(observations.observation) { originatingSession ->
            assertSame(ioDispatcher, currentCoroutineContext()[ContinuationInterceptor])
            discoveries += 1
            if (discoveries == 1) {
                withContext(NonCancellable) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    available(
                        StreamProfileId("11111111111111111111111111111111"),
                        originatingSession,
                    )
                }
            } else {
                available(secondId, originatingSession)
            }
        }
        val dataStore = InMemoryPreferencesDataStore(beforeUpdate = {
            if (pauseSelectionUpdate) {
                pauseSelectionUpdate = false
                selectionStarted.complete(Unit)
                withContext(NonCancellable) { releaseSelection.await() }
            }
        })
        val settings = PlayerSettingsStore(dataStore)
        val owner = profileOwner(session, settings, ioDispatcher)

        val ownerJob = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            owner.run()
        }
        runCurrent()
        firstStarted.await()
        observations.retire(SessionObservation.create(sessionState = SessionState.Disconnected))
        releaseFirst.complete(Unit)
        runCurrent()
        observations.publish(currentObservation("second"))
        runCurrent()

        assertEquals(2, discoveries)
        val result = owner.streamProfiles.value as StreamProfilesResult.Available
        assertEquals(secondId, result.profiles.single().id)
        assertNull(owner.selectedStreamProfileId.value)

        val failedMutation = async { runCatching { owner.saveServer("", 9982) } }
        runCurrent()
        assertTrue(failedMutation.await().isFailure)
        assertTrue(owner.serverProfile.value != null)

        pauseSelectionUpdate = true
        val selection = async { owner.selectStreamProfile(secondId) }
        runCurrent()
        selectionStarted.await()
        val close = async { ownerJob.cancelAndJoin() }
        runCurrent()
        assertFalse(close.isCompleted)
        releaseSelection.complete(Unit)
        runCurrent()
        selection.await()
        close.await()
        assertEquals(secondId, owner.selectedStreamProfileId.value)
        assertEquals(secondId, settings.resolveStreamProfileSelection(result.profiles) { true })
    }

    private fun profileOwner(
        session: TvheadendSession,
        settings: PlayerSettingsStore,
        ioDispatcher: CoroutineDispatcher,
    ): AppProfileOwner {
        return AppProfileOwner(
            session = session,
            profileStore = at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore(),
            playerSettings = settings,
            ioDispatcher = ioDispatcher,
            readProfileForEditing = { error("Unexpected editing read") },
        )
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

    private fun available(
        id: StreamProfileId,
        originatingSession: CurrentSessionObservation,
    ): StreamProfilesResult.Available = StreamProfilesResult.Available.create(
        profiles = listOf(StreamProfile(id, "profile", "")),
        originatingSession = originatingSession,
    )
}

private class ProfileSession(
    override val observation: StateFlow<SessionObservation>,
    private val discover: suspend (CurrentSessionObservation) -> StreamProfilesResult,
) : TvheadendSession {
    override val cache: at.bernhardberger.tvheadend.sdk.core.SessionCache get() = error("unused")
    override val epgRepository: EpgRepository get() = error("unused")
    override val dvrRepository: DvrRepository get() = error("unused")
    override val artwork: ArtworkLoader get() = error("unused")

    override suspend fun getStreamProfiles(
        currentSession: CurrentSessionObservation,
    ): StreamProfilesResult = discover(currentSession)

    override fun bindLivePlayback(
        currentSession: CurrentSessionObservation,
        channelId: ChannelId,
    ): PlaybackBindingResult<PlaybackBinding.Live> = error("unused")

    override fun bindRecordingPlayback(
        currentSession: CurrentSessionObservation,
        recordingId: DvrEntryId,
    ): PlaybackBindingResult<PlaybackBinding.Recording> = error("unused")

    override suspend fun connect(profile: ServerProfile): SessionCommandResult =
        SessionCommandResult.STARTED

    override suspend fun retry(): SessionCommandResult = SessionCommandResult.STARTED
    override suspend fun disconnect() = Unit
    override suspend fun shutdown() = Unit
}
