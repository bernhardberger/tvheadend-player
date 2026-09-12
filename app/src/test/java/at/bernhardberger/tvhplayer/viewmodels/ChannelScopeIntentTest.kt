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

    @Test fun selectionAndReversalAreImmediateWhilePersistenceIsBlocked() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val writeGate = CompletableDeferred<Unit>()
        var blockWrites = false
        val data = InMemoryPreferencesDataStore(beforeUpdate = {
            if (blockWrites) writeGate.await()
        })
        val settings = ChannelTagSettingsStore(data)
        settings.save(ChannelTagPreferences(activeTagId = a.id))
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), settings)
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
        val settings = ChannelTagSettingsStore(InMemoryPreferencesDataStore(beforeUpdate = {
            check(++writes <= 32) { "Tag write-back did not settle after one selection" }
        }))
        settings.save(ChannelTagPreferences(activeTagId = a.id))
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), settings)
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
        val base = InMemoryPreferencesDataStore()
        ChannelTagSettingsStore(base).save(ChannelTagPreferences(activeTagId = a.id))
        val readGate = CompletableDeferred<Unit>()
        val delayed = object : DataStore<Preferences> by base {
            override val data = flow { readGate.await(); emitAll(base.data) }
        }
        val settings = ChannelTagSettingsStore(delayed)
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), settings)
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
        val settings = ChannelTagSettingsStore(InMemoryPreferencesDataStore(beforeUpdate = {
            if (fail) throw IOException("test write failure")
        }))
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), settings)
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
        val settings = ChannelTagSettingsStore(InMemoryPreferencesDataStore())
        settings.save(ChannelTagPreferences(activeTagId = b.id))
        val missingB = ChannelCatalog.create(channels = catalog.channels.take(1), tags = listOf(a))
        val session = session(ChannelRepositoryState.Stale(missingB))
        val model = ChannelsViewModel(session, settings)
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

    @Test fun delayedObsoleteWriteCannotReplaceNewerVisibilityAndTag() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val settings = ChannelTagSettingsStore(InMemoryPreferencesDataStore(beforeUpdate = { gate.await() }))
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), settings)
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
        val base = InMemoryPreferencesDataStore()
        ChannelTagSettingsStore(base).save(ChannelTagPreferences(activeTagId = a.id))
        var fail = true
        val failing = object : DataStore<Preferences> by base {
            override val data = flow {
                if (fail) throw IOException("test read failure")
                emitAll(base.data)
            }
        }
        val model = ChannelsViewModel(session(ChannelRepositoryState.Current(catalog)), ChannelTagSettingsStore(failing))
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
            ChannelTagSettingsStore(InMemoryPreferencesDataStore()))
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
        val settings = ChannelTagSettingsStore(InMemoryPreferencesDataStore())
        settings.save(ChannelTagPreferences(visibility = at.bernhardberger.tvhplayer.core.ChannelScopeVisibility(
            configured = true, allChannelsVisible = false, visibleTagIds = setOf(a.id, b.id),
        )))
        val source = session(ChannelRepositoryState.Stale(ChannelCatalog.create(
            channels = catalog.channels.take(1), tags = listOf(a),
        )))
        val model = ChannelsViewModel(source, settings)
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
