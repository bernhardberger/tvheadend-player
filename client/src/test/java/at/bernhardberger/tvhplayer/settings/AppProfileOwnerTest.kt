package at.bernhardberger.tvhplayer.settings

import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppProfileOwnerTest {
    @Test fun rejectedLabelsAndFailedRenameLeaveTheActiveSessionUntouched() = runTest {
        val stored = FakeServerProfileStore()
        val active = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val store = object : ServerProfileStore by stored {
            override suspend fun updateLabel(id: ServerProfileId, label: String?): ServerProfileWriteResult =
                throw IllegalStateException("Synthetic store failure")
        }
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val calls = session.calls.toList()
        val generation = owner.configurationGeneration.value
        assertTrue(runCatching { owner.rename(active, "x".repeat(65)) }.isFailure)
        assertTrue(runCatching { owner.addAnonymous("second.invalid", 9982, "x".repeat(65)) }.isFailure)
        assertTrue(runCatching { owner.rename(active, "Changed") }.isFailure)
        assertEquals(calls, session.calls)
        assertEquals(generation, owner.configurationGeneration.value)
        assertEquals(active, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
    }

    @Test fun editingIntoAnotherProfileIsRejectedByEveryUpdatePath() = runTest {
        val store = FakeServerProfileStore()
        val active = (store.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val anonymous = (store.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val account = (store.addPassword("third.invalid", username = "viewer", password = "synthetic") as ServerProfileWriteResult.Applied).id
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val calls = session.calls.toList()
        assertEquals(SaveServerProfileResult.AlreadyExists(anonymous), owner.updateAnonymous(active, "second.invalid", 9982, null))
        assertEquals(SaveServerProfileResult.AlreadyExists(account), owner.updatePassword(active, "third.invalid", 9982, "viewer", "synthetic", null))
        owner.saveServer("second.invalid", 9982)
        owner.savePasswordServer("third.invalid", 9982, "viewer", "synthetic")
        assertFalse(FakeServerProfileStoreCall.UPDATE_ANONYMOUS in store.calls)
        assertFalse(FakeServerProfileStoreCall.UPDATE_PASSWORD in store.calls)
        assertEquals(calls, session.calls)
        assertEquals("first.invalid", (owner.serverProfile.value as ServerProfileReadResult.Available).host)
    }

    @Test fun duplicateLegacyCommitsWithNoneActiveActivateTheExistingProfile() = runTest {
        val store = FakeServerProfileStore()
        val anonymous = (store.addAnonymous("first.invalid") as ServerProfileWriteResult.Applied).id
        val account = (store.addPassword("second.invalid", username = "viewer", password = "synthetic") as ServerProfileWriteResult.Applied).id
        val owner = AppProfileOwner(FakeTvheadendSession(), store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        owner.saveServer("first.invalid", 9982)
        assertEquals(anonymous, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        owner.remove(anonymous)
        owner.savePasswordServer("second.invalid", 9982, "viewer", "synthetic")
        assertEquals(account, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertEquals(1, store.calls.count { it == FakeServerProfileStoreCall.ADD_ANONYMOUS })
        assertEquals(1, store.calls.count { it == FakeServerProfileStoreCall.ADD_PASSWORD })
    }

    @Test fun successfulRemovalStillPostsNoticeWhenAppDataCleanupFails() = runTest {
        val stored = FakeServerProfileStore()
        stored.addAnonymous("first.invalid", activate = true)
        val removed = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        var failCleanup = false
        val data = InMemoryPreferencesDataStore(beforeUpdate = { if (failCleanup) throw java.io.IOException("Synthetic cleanup failure") })
        val store = object : ServerProfileStore by stored {
            override suspend fun remove(id: ServerProfileId): ServerProfileWriteResult = stored.remove(id).also { failCleanup = true }
        }
        lateinit var owner: AppProfileOwner
        val notices = NoticeCenter({ 0 }, { NoticeContext(owner.configurationGeneration.value, null) })
        owner = AppProfileOwner(FakeTvheadendSession(), store, PlayerSettingsStore(data),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing }, { notices })
        backgroundScope.launch { owner.run() }
        runCurrent()
        assertEquals(ServerProfileWriteResult.Applied(removed), owner.remove(removed))
        assertFalse((owner.profiles.value as ServerProfileListResult.Available).profiles.any { it.id == removed })
        assertEquals(Notice.ServerProfileRemoved, notices.state.value.candidate?.notice)
    }

    @Test fun freshSetupReadsCurrentStoreAndRemainsDisconnected() = runTest {
        val store = FakeServerProfileStore()
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { error("No profile to edit") })
        backgroundScope.launch { owner.run() }
        runCurrent()
        assertEquals(ServerProfileReadResult.Missing, owner.serverProfile.value)
        assertEquals(listOf(FakeServerProfileStoreCall.PROFILES, FakeServerProfileStoreCall.LOAD_ACTIVE), store.calls)
        assertFalse(FakeSessionCall.CONNECT in session.calls)
        owner.loadServerForEditing { _, _, _, _ -> error("No editing values") }
    }

    @Test fun switchingKeepsOrderAndSelectingActiveDoesNotReconnect() = runTest {
        val store = FakeServerProfileStore()
        val first = (store.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val second = (store.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val session = FakeTvheadendSession()
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        val owner = AppProfileOwner(session, store, settings, StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val before = session.calls.count { it == FakeSessionCall.CONNECT }
        assertEquals(ServerProfileWriteResult.Applied(first), owner.setActive(first))
        assertEquals(before, session.calls.count { it == FakeSessionCall.CONNECT })
        owner.setActive(second)
        assertEquals(second, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertEquals(listOf(first, second), (owner.profiles.value as ServerProfileListResult.Available).profiles.map { it.id })
        assertEquals(second.value, owner.audioProfileId)
        assertEquals(before + 1, session.calls.count { it == FakeSessionCall.CONNECT })
    }

    @Test fun storedProfilesWithNoneActiveDoNotAutoplayOnStartup() = runTest {
        val store = FakeServerProfileStore()
        store.addAnonymous("first.invalid")
        store.addAnonymous("second.invalid")
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        assertEquals(ServerProfileReadResult.Missing, owner.serverProfile.value)
        val list = owner.profiles.value as ServerProfileListResult.Available
        assertNull(list.activeId)
        assertEquals(2, list.profiles.size)
        assertFalse(FakeSessionCall.CONNECT in session.calls)
        assertNull(owner.audioProfileId)
    }

    @Test fun duplicateAnonymousAndPasswordAddsReturnExistingIdWithoutAddingOrSwitching() = runTest {
        val store = FakeServerProfileStore()
        val anonymous = (store.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val account = (store.addPassword("first.invalid", username = "viewer", password = "synthetic") as ServerProfileWriteResult.Applied).id
        store.scriptCredentialsUnavailable(account)
        val session = FakeTvheadendSession()
        lateinit var owner: AppProfileOwner
        val notices = NoticeCenter({ 0 }, { NoticeContext(owner.configurationGeneration.value, null) })
        owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Unavailable }, { notices })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val adds = store.calls.count { it == FakeServerProfileStoreCall.ADD_ANONYMOUS || it == FakeServerProfileStoreCall.ADD_PASSWORD }
        assertEquals(SaveServerProfileResult.AlreadyExists(anonymous), owner.addAnonymous("first.invalid", 9982))
        assertEquals(SaveServerProfileResult.AlreadyExists(account), owner.addPassword("first.invalid", 9982, "viewer", "replacement"))
        assertEquals(adds, store.calls.count { it == FakeServerProfileStoreCall.ADD_ANONYMOUS || it == FakeServerProfileStoreCall.ADD_PASSWORD })
        assertEquals(anonymous, (owner.profiles.value as ServerProfileListResult.Available).activeId)
        assertEquals(Notice.ServerProfileAlreadyExists, notices.state.value.candidate?.notice)
        var prefilled = false
        owner.loadServerForEditing(account) { host, port, username, password ->
            assertEquals("first.invalid", host)
            assertEquals(9982, port)
            assertEquals("viewer", username)
            assertEquals("", password)
            prefilled = true
        }
        assertTrue(prefilled)
    }

    @Test fun removalDisconnectsClearsSummaryCacheRemovesAndLeavesNoneActive() = runTest {
        val stored = FakeServerProfileStore()
        val active = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val other = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val events = mutableListOf<String>()
        val data = InMemoryPreferencesDataStore(beforeUpdate = { events += "app-data" })
        val settings = PlayerSettingsStore(data)
        val fakeSession = FakeTvheadendSession()
        val session = object : TvheadendSession by fakeSession {
            override suspend fun disconnect() { events += "disconnect"; fakeSession.disconnect() }
            override val cache = object : SessionCache by fakeSession.cache {
                override suspend fun clear(profile: ServerProfileSummary) { events += "cache"; fakeSession.cache.clear(profile) }
            }
        }
        val store = object : ServerProfileStore by stored {
            override suspend fun profiles(): ServerProfileListResult { events += "profiles"; return stored.profiles() }
            override suspend fun load(id: ServerProfileId): ServerProfileReadResult { events += "load"; return stored.load(id) }
            override suspend fun remove(id: ServerProfileId): ServerProfileWriteResult { events += "remove"; return stored.remove(id) }
        }
        val owner = AppProfileOwner(session, store, settings, StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        settings.setShowInSwitcher(active, false)
        settings.setShowInSwitcher(other, false)
        events.clear()
        assertEquals(ServerProfileWriteResult.Applied(active), owner.remove(active))
        assertTrue(events.indexOf("disconnect") < events.indexOf("profiles"))
        assertTrue(events.indexOf("profiles") < events.indexOf("cache"))
        assertTrue(events.indexOf("cache") < events.indexOf("remove"))
        assertTrue(events.lastIndexOf("app-data") > events.indexOf("remove"))
        assertFalse(FakeServerProfileStoreCall.LOAD in stored.calls)
        assertTrue(FakeSessionCall.CLEAR_PROFILE_CACHE in fakeSession.calls)
        assertEquals(ServerProfileReadResult.Missing, owner.serverProfile.value)
        assertNull((owner.profiles.value as ServerProfileListResult.Available).activeId)
        assertNull(owner.audioProfileId)
        assertEquals(listOf(other), (owner.profiles.value as ServerProfileListResult.Available).profiles.map { it.id })
    }

    @Test fun editingRetainsProfileIdAndItsAppData() = runTest {
        val stored = FakeServerProfileStore()
        val id = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val data = InMemoryPreferencesDataStore()
        val settings = PlayerSettingsStore(data)
        val owner = AppProfileOwner(FakeTvheadendSession(), stored, settings, StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        settings.channelAspects.set(id.value, ChannelId(1), AspectRatioMode.FORCE_4_3)
        at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore(data).setChannelId(ChannelId(1))
        assertEquals(SaveServerProfileResult.Applied(id), owner.updateAnonymous(id, "edited.invalid", 9983, "Edited"))
        assertEquals(id, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertEquals(AspectRatioMode.FORCE_4_3, settings.channelAspects.choices.first().modeFor(ChannelId(1)))
        assertEquals(ChannelId(1), at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore(data).channelId.first())
    }

    @Test fun unreadableActiveProfileCanBeRemovedAfterClearingItsSummaryCache() = runTest {
        val stored = FakeServerProfileStore()
        val id = (stored.addPassword("first.invalid", username = "viewer", password = "synthetic", activate = true) as ServerProfileWriteResult.Applied).id
        stored.scriptCredentialsUnavailable(id)
        val events = mutableListOf<String>()
        val fake = FakeTvheadendSession()
        val session = object : TvheadendSession by fake {
            override suspend fun disconnect() { events += "disconnect"; fake.disconnect() }
            override val cache = object : SessionCache by fake.cache {
                override suspend fun clear(profile: ServerProfileSummary) { events += "summary-cache"; fake.cache.clear(profile) }
            }
        }
        val store = object : ServerProfileStore by stored {
            override suspend fun profiles(): ServerProfileListResult { events += "profiles"; return stored.profiles() }
            override suspend fun load(id: ServerProfileId): ServerProfileReadResult { events += "load"; return stored.load(id) }
            override suspend fun remove(id: ServerProfileId): ServerProfileWriteResult { events += "remove"; return stored.remove(id) }
        }
        val data = InMemoryPreferencesDataStore(beforeUpdate = { events += "app-data" })
        val settings = PlayerSettingsStore(data)
        lateinit var owner: AppProfileOwner
        val notices = NoticeCenter({ 0 }, { NoticeContext(owner.configurationGeneration.value, null) })
        owner = AppProfileOwner(session, store, settings, StandardTestDispatcher(testScheduler),
            { ServerProfileEditReadResult.Unavailable }, { notices })
        backgroundScope.launch { owner.run() }
        runCurrent()
        assertEquals(ServerProfileReadResult.Unavailable, owner.serverProfile.value)
        settings.setShowInSwitcher(id, false)
        events.clear()
        assertEquals(ServerProfileWriteResult.Applied(id), owner.remove(id))
        assertTrue(events.indexOf("disconnect") < events.indexOf("profiles"))
        assertTrue(events.indexOf("profiles") < events.indexOf("summary-cache"))
        assertFalse(FakeServerProfileStoreCall.LOAD in stored.calls)
        assertTrue(events.indexOf("summary-cache") < events.indexOf("remove"))
        assertTrue(events.lastIndexOf("app-data") > events.indexOf("remove"))
        assertTrue((owner.profiles.value as ServerProfileListResult.Available).profiles.isEmpty())
        assertEquals(ServerProfileReadResult.Missing, owner.serverProfile.value)
        assertTrue(settings.hiddenServerProfileIds.first().isEmpty())
        assertEquals(Notice.ServerProfileRemoved, notices.state.value.candidate?.notice)
    }

    @Test fun renamePreservesUnreadableCredentialsAndDoesNotReconnect() = runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val unreadable = (stored.addPassword("second.invalid", username = "viewer", password = "synthetic") as ServerProfileWriteResult.Applied).id
        stored.scriptCredentialsUnavailable(unreadable)
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, stored, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Unavailable })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val connections = session.calls.count { it == FakeSessionCall.CONNECT }
        assertEquals(ServerProfileWriteResult.Applied(unreadable), owner.rename(unreadable, "Lounge"))
        assertEquals(ServerProfileWriteResult.Applied(first), owner.rename(first, "In use"))
        val list = owner.profiles.value as ServerProfileListResult.Available
        assertEquals(listOf("In use", "Lounge"), list.profiles.map { it.label })
        assertEquals(first, list.activeId)
        assertFalse(list.profiles.last().credentialsAvailable)
        assertEquals("viewer", list.profiles.last().username)
        assertEquals(connections, session.calls.count { it == FakeSessionCall.CONNECT })
    }

    @Test fun cancellationStillWaitsForNonCancellableProfileInitialization() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val stored = FakeServerProfileStore()
        val store = object : ServerProfileStore by stored {
            override suspend fun loadActive(): ServerProfileReadResult {
                entered.complete(Unit)
                release.await()
                return stored.loadActive()
            }
        }
        val owner = AppProfileOwner(FakeTvheadendSession(), store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
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

    @Test fun cancelledPendingPasswordSaveLeavesConfigurationAlone() = runTest {
        val stored = FakeServerProfileStore()
        val owner = AppProfileOwner(FakeTvheadendSession(), stored, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        val save = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            owner.savePasswordServer("first.invalid", 9982, "viewer", "synthetic")
        }
        runCurrent()
        assertFalse(save.isCompleted)
        save.cancelAndJoin()
        assertEquals(0L, owner.configurationGeneration.value)
        assertTrue(stored.calls.isEmpty())
    }

    @Test fun failedConnectionAfterPersistenceRecoversWithoutLosingProfileData() = runTest {
        val stored = FakeServerProfileStore()
        val fake = FakeTvheadendSession()
        var connects = 0
        val session = object : TvheadendSession by fake {
            override suspend fun connect(profile: ServerProfile): SessionCommandResult {
                if (++connects == 1) error("Synthetic connect failure")
                return fake.connect(profile)
            }
        }
        val owner = AppProfileOwner(session, stored, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        assertTrue(runCatching { owner.saveServer("first.invalid", 9982) }.isFailure)
        assertEquals(2, connects)
        assertTrue(owner.serverProfile.value is ServerProfileReadResult.Available)
    }

    @Test fun rejectedAddressDoesNotChangeConfiguration() = runTest {
        val owner = AppProfileOwner(FakeTvheadendSession(), FakeServerProfileStore(),
            PlayerSettingsStore(InMemoryPreferencesDataStore()), StandardTestDispatcher(testScheduler),
            { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        assertTrue(runCatching { owner.savePasswordServer("", 9982, "viewer", "synthetic") }.isFailure)
        assertEquals(0L, owner.configurationGeneration.value)
    }
}
