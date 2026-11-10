package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModelStore
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.ChannelTagPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import java.io.IOException
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelScopeIntentTest {
    private val profileId = ServerProfileId.generate()
    private val profilePreferences = androidx.datastore.preferences.core.preferencesOf(
        androidx.datastore.preferences.core.stringPreferencesKey("activeServerProfileId") to
            profileId.value,
    )
    private val models = ViewModelStore()
    private val a = ChannelTag.create(ChannelTagId(1), name = "A")
    private val b = ChannelTag.create(ChannelTagId(2), name = "B")
    private val catalog = ChannelCatalog.create(
        channels = listOf(
            Channel.create(ChannelId(1), name = "One", tagIds = listOf(a.id)),
            Channel.create(ChannelId(2), name = "Two", tagIds = listOf(b.id)),
        ),
        tags = listOf(a, b),
    )

    @After fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test fun switchingProfilesReloadsTheirOwnTagPreferencesAndStampsLaterWrites() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val data = InMemoryPreferencesDataStore()
        suspend fun selectProfile(id: ServerProfileId) {
            data.updateData { current -> current.toMutablePreferences().apply {
                this[androidx.datastore.preferences.core.stringPreferencesKey("activeServerProfileId")] = id.value
            } }
        }
        val settings = ChannelTagSettingsStore(data)
        val first = ServerProfileId.generate()
        val second = ServerProfileId.generate()
        selectProfile(first)
        settings.save(settings.settings.first().copy(activeTagId = a.id))
        selectProfile(second)
        settings.save(settings.settings.first().copy(activeTagId = b.id))
        selectProfile(first)
        val firstCatalog = ChannelCatalog.create(channels = catalog.channels.take(1), tags = listOf(a))
        val secondCatalog = ChannelCatalog.create(channels = catalog.channels.drop(1), tags = listOf(b))
        val source = session(ChannelRepositoryState.Current(firstCatalog))
        val liveProfile = kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(first)
        val model = ChannelsViewModel(source, settings, liveProfile)
        models.put("scope", model)
        runCurrent()
        assertEquals(first, model.scope.value.profileId)
        assertEquals(a.id, model.scope.value.scope.activeTagId)
        // A successful setup trial has B's catalog before the user saves it. Cancelling it
        // must not normalize A's selected tag or visible scopes against B's unrelated tags.
        liveProfile.value = null
        source.publish(SessionObservation.create(channelState = ChannelRepositoryState.Current(secondCatalog)))
        runCurrent()
        assertEquals("A trial must not clear the saved connection's tag", a.id, settings.settings.first().activeTagId)
        source.publish(SessionObservation.create(channelState = ChannelRepositoryState.Current(firstCatalog)))
        liveProfile.value = first
        runCurrent()
        liveProfile.value = null
        source.publish(SessionObservation.create(channelState = ChannelRepositoryState.Current(secondCatalog)))
        runCurrent()
        // The session publication can arrive before the preferences collector receives B.
        liveProfile.value = second
        runCurrent()
        assertEquals("Publishing B must not normalize A's preferences", a.id, settings.settings.first().activeTagId)
        source.publish(SessionObservation.create(channelState = ChannelRepositoryState.Current(
            ChannelCatalog.create(channels = secondCatalog.channels),
        )))
        runCurrent()
        selectProfile(second)
        runCurrent()
        assertEquals(second, model.scope.value.profileId)
        assertNull(settings.settings.first().activeTagId)
        source.publish(SessionObservation.create(channelState = ChannelRepositoryState.Current(secondCatalog)))
        model.selectTag(null)
        runCurrent()
        selectProfile(first)
        liveProfile.value = first
        source.publish(SessionObservation.create(channelState = ChannelRepositoryState.Current(firstCatalog)))
        runCurrent()
        assertEquals(a.id, model.scope.value.scope.activeTagId)
        selectProfile(second)
        liveProfile.value = second
        source.publish(SessionObservation.create(channelState = ChannelRepositoryState.Current(secondCatalog)))
        runCurrent()
        assertNull(model.scope.value.scope.activeTagId)
    }

    @Test fun retryAfterCollectorFailureRestoresProfileTracking() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val base = InMemoryPreferencesDataStore(profilePreferences)
        val first = ServerProfileId.from(requireNotNull(profilePreferences[
            androidx.datastore.preferences.core.stringPreferencesKey("activeServerProfileId")]))
        val second = ServerProfileId.generate()
        val seed = ChannelTagSettingsStore(base)
        seed.save(seed.settings.first().copy(activeTagId = a.id))
        val fail = CompletableDeferred<Unit>()
        var collections = 0
        val data = object : DataStore<Preferences> by base {
            override val data = flow {
                if (++collections == 1) {
                    emit(base.data.first())
                    fail.await()
                    throw IOException("Synthetic collector failure")
                }
                emitAll(base.data)
            }
        }
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), ChannelTagSettingsStore(data), kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        assertEquals(first, model.scope.value.profileId)
        fail.complete(Unit)
        runCurrent()
        assertTrue(model.settingsFailure.value)
        model.retrySettings()
        runCurrent()
        assertFalse(model.settingsFailure.value)
        base.updateData { current -> current.toMutablePreferences().apply {
            this[androidx.datastore.preferences.core.stringPreferencesKey("activeServerProfileId")] = second.value
        } }
        seed.save(seed.settings.first().copy(activeTagId = b.id))
        runCurrent()
        assertEquals(second, model.scope.value.profileId)
        assertEquals(b.id, model.scope.value.scope.activeTagId)
        assertTrue(collections >= 2)
    }

    @Test fun selectionAndReversalAreImmediateWhilePersistenceIsBlocked() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val writeGate = CompletableDeferred<Unit>()
        var blockWrites = false
        val data = InMemoryPreferencesDataStore(profilePreferences, beforeUpdate = {
            if (blockWrites) writeGate.await()
        })
        val settings = ChannelTagSettingsStore(data)
        settings.save(settings.settings.first().copy(activeTagId = a.id))
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), settings, kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        assertEquals(a.id, model.scope.value.scope.activeTagId)
        blockWrites = true
        model.selectTag(b.id)
        assertEquals("Tag input must not wait for DataStore", b.id, model.scope.value.scope.activeTagId)
        runCurrent()
        model.selectTag(a.id)
        assertEquals(a.id, model.scope.value.scope.activeTagId)
        writeGate.complete(Unit)
        runCurrent()
        assertEquals(a.id, model.scope.value.scope.activeTagId)
        assertEquals(a.id, settings.settings.first().activeTagId)
    }

    @Test fun currentCatalogDoesNotWriteAnOldScopeOverAValidSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var writes = 0
        val settings = ChannelTagSettingsStore(InMemoryPreferencesDataStore(profilePreferences, beforeUpdate = {
            check(++writes <= 32) { "Tag write-back did not settle after one selection" }
        }))
        settings.save(settings.settings.first().copy(activeTagId = a.id))
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), settings, kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        model.selectTag(b.id)
        runCurrent()
        assertEquals("An older derived scope must not undo B", b.id, model.scope.value.scope.activeTagId)
        assertEquals(b.id, settings.settings.first().activeTagId)
        assertEquals("Only the seed and the new selection should be written", 2, writes)
    }

    @Test fun lateHydrationCannotUndoNewInputOrPublishDefaultAllChannels() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val base = InMemoryPreferencesDataStore(profilePreferences)
        ChannelTagSettingsStore(base).let { it.save(it.settings.first().copy(activeTagId = a.id)) }
        val readGate = CompletableDeferred<Unit>()
        val delayed = object : DataStore<Preferences> by base {
            override val data = flow { readGate.await(); emitAll(base.data) }
        }
        val settings = ChannelTagSettingsStore(delayed)
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), settings, kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        assertFalse(model.scope.value.settingsLoaded)
        model.selectTag(b.id)
        readGate.complete(Unit)
        runCurrent()
        assertTrue(model.scope.value.settingsLoaded)
        assertEquals(b.id, model.scope.value.scope.activeTagId)
        assertEquals(b.id, settings.settings.first().activeTagId)
    }

    @Test fun failureKeepsLatestScopeAndRetryPersistsIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var fail = true
        val settings = ChannelTagSettingsStore(InMemoryPreferencesDataStore(profilePreferences, beforeUpdate = {
            if (fail) throw IOException("test write failure")
        }))
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), settings, kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        model.selectTag(b.id)
        runCurrent()
        assertEquals(b.id, model.scope.value.scope.activeTagId)
        assertTrue(model.settingsFailure.value)
        fail = false
        model.retrySettings()
        runCurrent()
        assertFalse(model.settingsFailure.value)
        assertEquals(b.id, settings.settings.first().activeTagId)
    }

    @Test fun staleCatalogPreservesPreferenceAndCurrentRemovalNormalizesOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = ChannelTagSettingsStore(InMemoryPreferencesDataStore(profilePreferences))
        settings.save(settings.settings.first().copy(activeTagId = b.id))
        val missingB = ChannelCatalog.create(channels = catalog.channels.take(1), tags = listOf(a))
        val session = session(ChannelRepositoryState.Stale(missingB))
        val model = ChannelsViewModel(session, settings, kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        assertEquals(b.id, settings.settings.first().activeTagId)
        session.publish(session.observation.value.let {
            SessionObservation.create(it.sessionState, ChannelRepositoryState.Current(missingB), it.epgState, it.dvrState)
        })
        runCurrent()
        assertNull(settings.settings.first().activeTagId)
        assertTrue(model.unavailableTagNotice.value)
        model.selectTag(a.id)
        runCurrent()
        assertEquals(a.id, settings.settings.first().activeTagId)
        assertFalse(model.unavailableTagNotice.value)
    }

    @Test fun aScopeKeepsTheObservationItWasResolvedFrom() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = session(ChannelRepositoryState.Stale(catalog))
        val model = ChannelsViewModel(session, ChannelTagSettingsStore(InMemoryPreferencesDataStore(profilePreferences)), kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        assertSame(session.observation.value, model.scope.value.observation)
        session.publish(session.observation.value.let {
            SessionObservation.create(it.sessionState, ChannelRepositoryState.Current(catalog), it.epgState, it.dvrState)
        })
        runCurrent()
        assertSame(session.observation.value, model.scope.value.observation)
    }

    @Test fun newSessionWithEqualChannelsRepublishesTheScopeAndAlignsHomeInput() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val channels = ChannelRepositoryState.Current(catalog)
        val session = session(channels)
        val model = ChannelsViewModel(session, ChannelTagSettingsStore(InMemoryPreferencesDataStore(profilePreferences)), kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        val before = model.scope.value
        // Reconnect without changing the retained catalog; StateFlow may conflate the gap.
        session.publish(SessionObservation.create(sessionState = SessionState.Disconnected, channelState = channels))
        session.publish(session(channels).observation.value)
        runCurrent()
        val latest = session.observation.value
        assertEquals(before.observation!!.channelState, latest.channelState)
        assertNotNull(latest.currentSession)
        assertNotSame(before.observation.currentSession, latest.currentSession)
        assertSame("Equal channels must not retain the previous session's scope authority", latest, model.scope.value.observation)
        assertNotNull(at.bernhardberger.tvhplayer.ui.screens.home.homeInput(
            model.scope.value, latest, emptyList(), null, 0L))
    }

    @Test fun epgUpdatesWithinTheSameSessionDoNotRepublishTheScope() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = session(ChannelRepositoryState.Current(catalog))
        val model = ChannelsViewModel(session, ChannelTagSettingsStore(InMemoryPreferencesDataStore(profilePreferences)), kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        val before = model.scope.value
        val source = session.observation.value
        session.publish(SessionObservation.create(source.sessionState, source.channelState,
            EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(EpgEvent.create(
                EventId(1), ChannelId(1), kotlin.time.Instant.fromEpochSeconds(0),
                kotlin.time.Instant.fromEpochSeconds(600), title = "Updated programme")))), source.dvrState))
        runCurrent()
        assertSame(source.currentSession, session.observation.value.currentSession)
        assertNotEquals(source.epgState, session.observation.value.epgState)
        assertSame("An EPG tick must not rebuild channel scope", before, model.scope.value)
    }

    @Test fun delayedObsoleteWriteCannotReplaceNewerVisibilityAndTag() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val settings = ChannelTagSettingsStore(InMemoryPreferencesDataStore(profilePreferences, beforeUpdate = { gate.await() }))
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), settings, kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        model.selectTag(b.id)
        runCurrent()
        model.setScopeVisible(b.id, false)
        model.selectTag(a.id)
        assertEquals(a.id, model.scope.value.scope.activeTagId)
        gate.complete(Unit)
        runCurrent()
        val saved = settings.settings.first()
        assertEquals(a.id, saved.activeTagId)
        assertFalse(saved.visibility.isTagVisible(b.id))
    }

    @Test fun readFailureRetriesWithoutReplacingThePendingSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val base = InMemoryPreferencesDataStore(profilePreferences)
        ChannelTagSettingsStore(base).let { it.save(it.settings.first().copy(activeTagId = a.id)) }
        var fail = true
        val failing = object : DataStore<Preferences> by base {
            override val data = flow {
                if (fail) throw IOException("test read failure")
                emitAll(base.data)
            }
        }
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), ChannelTagSettingsStore(failing), kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        assertTrue(model.settingsFailure.value)
        assertFalse(model.scope.value.settingsLoaded)
        model.selectTag(b.id)
        fail = false
        model.retrySettings()
        runCurrent()
        assertTrue(model.scope.value.settingsLoaded)
        assertFalse(model.settingsFailure.value)
        assertEquals(b.id, model.scope.value.scope.activeTagId)
    }

    @Test fun visibilityReversalsUseCurrentIntentAndPreserveTheLastScope() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)),
            ChannelTagSettingsStore(InMemoryPreferencesDataStore(profilePreferences)), kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        model.toggleScopeVisibility(b.id)
        model.toggleScopeVisibility(b.id)
        assertTrue(model.scope.value.visibility.isTagVisible(b.id))
        model.setScopeVisible(a.id, false)
        model.setScopeVisible(b.id, false)
        model.setScopeVisible(null, false)
        assertTrue(model.scope.value.visibility.isAllChannelsVisible())
    }

    @Test fun staleVisibilityEditPreservesTagsAbsentFromTheRetainedCatalog() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = ChannelTagSettingsStore(InMemoryPreferencesDataStore(profilePreferences))
        settings.save(settings.settings.first().copy(visibility = at.bernhardberger.tvhplayer.core.ChannelScopeVisibility(
            configured = true, allChannelsVisible = false, visibleTagIds = setOf(a.id, b.id),
        )))
        val source = session(ChannelRepositoryState.Stale(ChannelCatalog.create(
            channels = catalog.channels.take(1), tags = listOf(a),
        )))
        val model = ChannelsViewModel(source, settings, kotlinx.coroutines.flow.MutableStateFlow<ServerProfileId?>(profileId))
        models.put("scope", model)
        runCurrent()
        model.toggleScopeVisibility(null)
        runCurrent()
        assertEquals(setOf(a.id, b.id), settings.settings.first().visibility.visibleTagIds)
        source.publish(source.observation.value.let {
            SessionObservation.create(it.sessionState, ChannelRepositoryState.Current(catalog), it.epgState, it.dvrState)
        })
        runCurrent()
        assertTrue(model.scope.value.visibility.isTagVisible(b.id))
    }

    private fun session(channels: ChannelRepositoryState) = FakeTvheadendSession(
        SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
            )),
            channelState = channels,
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        ),
    )
}
