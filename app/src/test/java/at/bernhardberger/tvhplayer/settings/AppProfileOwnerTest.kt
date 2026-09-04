package at.bernhardberger.tvhplayer.settings

import android.content.Context
import android.content.ContextWrapper
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
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
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
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
    fun selectedProfileReadSettlesWhenPersistedSelectionUpdateFails() = runTest {
        val selectionUpdateStarted = CompletableDeferred<Unit>()
        val releaseSelectionUpdate = CompletableDeferred<Unit>()
        val observations = FakeSessionObservation(currentObservation("server"))
        val currentSession = checkNotNull(observations.observation.value.currentSession)
        val session = ProfileSession(observations.observation) { originatingSession ->
            available(StreamProfileId("11111111111111111111111111111111"), originatingSession)
        }
        val dataStore = InMemoryPreferencesDataStore(beforeUpdate = {
            selectionUpdateStarted.complete(Unit)
            releaseSelectionUpdate.await()
            error("selection update failed")
        })
        val owner = profileOwner(
            session,
            PlayerSettingsStore(dataStore),
            StandardTestDispatcher(testScheduler),
        )

        backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { owner.run() }
        }
        runCurrent()
        selectionUpdateStarted.await()
        val selected = async { owner.selectedStreamProfileIdFor(currentSession) }
        runCurrent()
        assertFalse(selected.isCompleted)

        releaseSelectionUpdate.complete(Unit)
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
        var settingsUpdates = 0
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
            settingsUpdates += 1
            if (settingsUpdates == 2) {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun supersededGenerationCannotConsumeLegacyEvidenceDuringPersistence() = runTest {
        val legacyKey = stringPreferencesKey("profile")
        val uuidKey = stringPreferencesKey("profileUuid")
        val updateStarted = CompletableDeferred<Unit>()
        val releaseUpdate = CompletableDeferred<Unit>()
        val dataStore = InMemoryPreferencesDataStore(
            initial = preferencesOf(legacyKey to "profile"),
            beforeUpdate = {
                updateStarted.complete(Unit)
                withContext(NonCancellable) { releaseUpdate.await() }
            },
        )
        val observations = FakeSessionObservation(currentObservation("old"))
        val session = ProfileSession(observations.observation) { currentSession ->
            available(StreamProfileId("11111111111111111111111111111111"), currentSession)
        }
        val owner = profileOwner(
            session,
            PlayerSettingsStore(dataStore),
            StandardTestDispatcher(testScheduler),
        )

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { owner.run() }
        runCurrent()
        updateStarted.await()
        observations.retire(SessionObservation.create(sessionState = SessionState.Disconnected))
        releaseUpdate.complete(Unit)
        runCurrent()

        val persisted = dataStore.data.first()
        assertEquals("profile", persisted[legacyKey])
        assertFalse(persisted.contains(uuidKey))
    }

    @Test
    fun credentialMigrationNormalizesOnlyCompleteLegacyEvidence() {
        val normalized = checkNotNull(
            LegacyServerProfile(
                " tvh.example.invalid ",
                9982,
                " viewer ",
                LegacyPassword.Available("secret"),
            ).normalizedForMigration(),
        )

        assertEquals("tvh.example.invalid", normalized.host)
        assertEquals("viewer", normalized.username)
        assertFalse(ServerProfileReadResult.Missing.matchesLegacyProfile(normalized))
        assertNull(normalized.copy(password = LegacyPassword.Unavailable).normalizedForMigration())
    }

    private fun profileOwner(
        session: TvheadendSession,
        settings: PlayerSettingsStore,
        ioDispatcher: CoroutineDispatcher,
    ): AppProfileOwner {
        val context = contextWithoutAndroidRuntime()
        return AppProfileOwner(
            context = context,
            session = session,
            profileStore = TvheadendServerProfileStore(context),
            legacyCredentials = LegacyCredentialSource(context),
            playerSettings = settings,
            ioDispatcher = ioDispatcher,
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

private class LocalTestContext private constructor() : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = File("/tmp/opencode")
}

private fun contextWithoutAndroidRuntime(): Context {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
    val unsafe = field.get(null)
    return unsafeClass.getMethod("allocateInstance", Class::class.java)
        .invoke(unsafe, LocalTestContext::class.java) as Context
}
