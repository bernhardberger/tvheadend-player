package at.bernhardberger.tvhplayer.settings

import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppProfileOwnerTest {
    @Test fun unavailableSwitchListAndUnreadableTargetPostWriteFailureWithoutConnecting() = runTest {
        val store = FakeServerProfileStore()
        val id = (store.addPassword("second.invalid", username = "viewer", password = "synthetic") as ServerProfileWriteResult.Applied).id
        store.scriptCredentialsUnavailable(id)
        val session = FakeTvheadendSession()
        val notices = NoticeCenter({ 0 }, { NoticeContext(0, null) })
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Unavailable }, { notices })
        assertEquals(ServerProfileWriteResult.Unavailable, owner.switchActive(id))
        assertEquals(Notice.ServerProfileWriteFailed, notices.state.value.candidate?.notice)
        backgroundScope.launch { owner.run() }; runCurrent()
        val connects = session.calls.count { it == FakeSessionCall.CONNECT }
        assertEquals(ServerProfileWriteResult.Unavailable, owner.switchActive(id))
        assertEquals(Notice.ServerProfileWriteFailed, notices.state.value.candidate?.notice)
        assertEquals(connects, session.calls.count { it == FakeSessionCall.CONNECT })
        assertFalse(owner.connectionChanges.isLocked)
    }
    @Test fun cancellationDuringPersistenceRestoresTheOriginalBeforeAReplacementTrials() = runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val second = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val third = (stored.addAnonymous("third.invalid") as ServerProfileWriteResult.Applied).id
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<ServerProfileId>()
        val store = object : ServerProfileStore by stored {
            override suspend fun setActive(id: ServerProfileId): ServerProfileWriteResult {
                writes += id
                if (id == second) gate.await()
                return stored.setActive(id)
            }
        }
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }; runCurrent()
        val old = backgroundScope.launch { owner.switchActive(second) }; runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED))))
        runCurrent()
        assertEquals(listOf(second), writes)
        old.cancel()
        val replacement = backgroundScope.launch { owner.switchActive(third) }; runCurrent()
        assertFalse(old.isCompleted)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf(second, first), writes)
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        assertEquals(first, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        replacement.cancelAndJoin()
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        assertFalse(owner.connectionChanges.isLocked)
    }

    @Test fun cancellationDuringPersistenceRestoresAnUnreadablePreviousActiveId() = runTest {
        val stored = FakeServerProfileStore()
        val previous = (stored.addPassword("first.invalid", username = "viewer", password = "synthetic", activate = true) as ServerProfileWriteResult.Applied).id
        val next = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        stored.scriptCredentialsUnavailable(previous)
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<ServerProfileId>()
        val store = object : ServerProfileStore by stored {
            override suspend fun setActive(id: ServerProfileId): ServerProfileWriteResult {
                writes += id
                if (id == next) gate.await()
                return stored.setActive(id)
            }
        }
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Unavailable })
        backgroundScope.launch { owner.run() }; runCurrent()
        assertTrue(owner.serverProfile.value is ServerProfileReadResult.Unavailable)
        val attempt = backgroundScope.launch { owner.switchActive(next) }; runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED))))
        runCurrent(); assertEquals(listOf(next), writes)
        attempt.cancel(); gate.complete(Unit); runCurrent()
        assertTrue(attempt.isCompleted)
        assertEquals(listOf(next, previous), writes)
        assertEquals(previous, (stored.profiles() as ServerProfileListResult.Available).activeId)
        assertEquals(previous, (owner.profiles.value as ServerProfileListResult.Available).activeId)
        assertTrue(owner.serverProfile.value is ServerProfileReadResult.Unavailable)
        assertFalse(owner.connectionChanges.isLocked)
    }

    @Test fun cancellationDuringPersistenceWithNothingToReturnToCompletesTheSelection() = runTest {
        val stored = FakeServerProfileStore()
        val next = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val gate = CompletableDeferred<Unit>()
        var writing = false
        val store = object : ServerProfileStore by stored {
            override suspend fun setActive(id: ServerProfileId): ServerProfileWriteResult {
                writing = true; gate.await()
                return stored.setActive(id)
            }
        }
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }; runCurrent()
        val attempt = backgroundScope.launch { owner.switchActive(next) }; runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED))))
        runCurrent(); assertTrue(writing)
        attempt.cancel(); gate.complete(Unit); runCurrent()
        assertEquals(next, (stored.profiles() as ServerProfileListResult.Available).activeId)
        assertEquals(next, (owner.profiles.value as ServerProfileListResult.Available).activeId)
        assertEquals(next, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertFalse(owner.connectionChanges.isLocked)
    }

    @Test fun aNewUserChoiceWaitsForTheCancelledChoicesRestore() = runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val second = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val third = (stored.addAnonymous("third.invalid") as ServerProfileWriteResult.Applied).id
        val fake = FakeTvheadendSession()
        val restore = CompletableDeferred<Unit>()
        var disconnects = 0
        val session = object : TvheadendSession by fake {
            override suspend fun disconnect() {
                if (++disconnects == 3) restore.await()
                fake.disconnect()
            }
        }
        val owner = AppProfileOwner(session, stored, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val old = backgroundScope.launch { owner.switchActive(second) }
        runCurrent()
        old.cancel()
        runCurrent()
        assertEquals(3, disconnects)
        val replacement = backgroundScope.async { owner.switchActive(third) }
        runCurrent()
        assertFalse(old.isCompleted)
        assertEquals(3, disconnects)
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        restore.complete(Unit)
        runCurrent()
        assertTrue(old.isCompleted)
        assertEquals(4, disconnects)
        fake.publish(SessionObservation.create(sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED))))
        runCurrent()
        assertEquals(ServerProfileWriteResult.Applied(third), replacement.await())
        assertEquals(third, (stored.profiles() as ServerProfileListResult.Available).activeId)
    }
    @Test fun userSwitchCommitsOnlyAfterSignInAndCancellationRestoresThePreviousProfile() = runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val next = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, stored, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val cancelled = backgroundScope.launch { owner.switchActive(next) }
        runCurrent()
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        cancelled.cancelAndJoin()
        assertEquals(first, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertFalse(owner.connectionChanges.isLocked)
        val attempt = backgroundScope.async { owner.switchActive(next) }
        runCurrent()
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        session.publish(SessionObservation.create(sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED))))
        runCurrent()
        assertEquals(ServerProfileWriteResult.Applied(next), attempt.await())
        assertEquals(next, (stored.profiles() as ServerProfileListResult.Available).activeId)
    }

    @Test fun rejectedUserSwitchKeepsThePreviousProfileAndRestoresItsConnection() = runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val next = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, stored, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val attempt = backgroundScope.async { owner.switchActive(next) }
        runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.AUTHENTICATION_REJECTED)))
        runCurrent()
        assertEquals(ServerProfileWriteResult.Unavailable, attempt.await())
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        assertEquals(first, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertFalse(owner.connectionChanges.isLocked)
    }

    @Test fun cancellationAtReceiveBeforeTheSubmitterResumesKeepsTheConnectionLock() = runTest {
        val stored = FakeServerProfileStore()
        stored.addAnonymous("first.invalid", activate = true)
        val next = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val gate = CompletableDeferred<Unit>()
        lateinit var submitter: Job
        var received = false
        val store = object : ServerProfileStore by stored {
            override suspend fun setActive(id: ServerProfileId): ServerProfileWriteResult {
                received = true
                // The unconfined actor runs as admission delivers to its waiting receive,
                // before that admission call returns to the standard-dispatched sender.
                submitter.cancel()
                gate.await()
                return stored.setActive(id)
            }
        }
        val owner = AppProfileOwner(FakeTvheadendSession(), store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { owner.run() }
        runCurrent()
        submitter = backgroundScope.launch { owner.setActive(next) }
        runCurrent()
        assertTrue(received)
        assertTrue(submitter.isCancelled)
        assertFalse(submitter.isCompleted)
        var entered = false
        backgroundScope.launch { owner.connectionChanges.withLock { entered = true } }
        runCurrent()
        assertFalse(entered)
        gate.complete(Unit)
        runCurrent()
        assertTrue(submitter.isCompleted)
        assertTrue(entered)
        assertEquals(next, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
    }

    @Test fun cancellationOfTheReceiverBeforeDispatchReleasesTheUndeliveredCommandAndItsWaiter() = runTest {
        val store = FakeServerProfileStore()
        val active = (store.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val next = (store.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val owner = AppProfileOwner(FakeTvheadendSession(), store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        val actor = backgroundScope.launch { owner.run() }
        runCurrent()
        val calls = store.calls.toList()
        // Admission wakes the suspended receive, but its dispatched continuation has not run.
        val submitter = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { owner.setActive(next) }
        actor.cancel()
        runCurrent()
        assertTrue(submitter.isCompleted)
        assertTrue(runCatching { submitter.await() }.exceptionOrNull() is CancellationException)
        assertEquals(calls, store.calls)
        assertEquals(active, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertTrue(owner.connectionChanges.tryLock())
        owner.connectionChanges.unlock()
    }

    @Test fun switchingWaitsForSetupCleanupAndCannotBeUndoneByItsRestore() = runTest {
        val store = FakeServerProfileStore()
        store.addAnonymous("first.invalid", activate = true)
        val next = (store.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val previous = (store.loadActive() as ServerProfileReadResult.Available).profile
        val restore = CompletableDeferred<Unit>()
        val cleanup = backgroundScope.launch {
            owner.connectionChanges.withLock {
                session.disconnect()
                restore.await()
                session.connect(previous)
            }
        }
        runCurrent()
        val calls = session.calls.toList()
        val switch = backgroundScope.async { owner.setActive(next) }
        runCurrent()
        assertFalse(switch.isCompleted)
        assertEquals(calls, session.calls)
        restore.complete(Unit)
        runCurrent()
        assertTrue(cleanup.isCompleted)
        assertEquals(ServerProfileWriteResult.Applied(next), switch.await())
        assertEquals(next, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertEquals(calls + listOf(FakeSessionCall.CONNECT, FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
    }

    @Test fun anAdmittedCancelledCommandKeepsTheConnectionLockUntilItFinishes() = runTest {
        val stored = FakeServerProfileStore()
        stored.addAnonymous("first.invalid", activate = true)
        val next = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val gate = CompletableDeferred<Unit>()
        val store = object : ServerProfileStore by stored {
            override suspend fun setActive(id: ServerProfileId): ServerProfileWriteResult {
                gate.await()
                return stored.setActive(id)
            }
        }
        val owner = AppProfileOwner(FakeTvheadendSession(), store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val command = backgroundScope.launch { owner.setActive(next) }
        runCurrent()
        command.cancel()
        var entered = false
        backgroundScope.launch { owner.connectionChanges.withLock { entered = true } }
        runCurrent()
        assertFalse(entered)
        gate.complete(Unit)
        runCurrent()
        assertTrue(entered)
        assertEquals(next, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
    }

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
        assertEquals(Notice.ServerProfileRemoved(removed, "second.invalid"), notices.state.value.candidate?.notice)
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
        events.clear()
        assertEquals(ServerProfileWriteResult.Applied(id), owner.remove(id))
        assertTrue(events.indexOf("disconnect") < events.indexOf("profiles"))
        assertTrue(events.indexOf("profiles") < events.indexOf("summary-cache"))
        assertFalse(FakeServerProfileStoreCall.LOAD in stored.calls)
        assertTrue(events.indexOf("summary-cache") < events.indexOf("remove"))
        assertTrue(events.lastIndexOf("app-data") > events.indexOf("remove"))
        assertTrue((owner.profiles.value as ServerProfileListResult.Available).profiles.isEmpty())
        assertEquals(ServerProfileReadResult.Missing, owner.serverProfile.value)
        assertEquals(Notice.ServerProfileRemoved(id, "viewer on first.invalid"), notices.state.value.candidate?.notice)
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

    @Test fun passwordSaveCancelledBeforeAdmissionLeavesConfigurationAlone() = runTest {
        val stored = FakeServerProfileStore()
        val owner = AppProfileOwner(FakeTvheadendSession(), stored, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        owner.connectionChanges.lock()
        val save = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            owner.savePasswordServer("first.invalid", 9982, "viewer", "synthetic")
        }
        runCurrent()
        assertFalse(save.isCompleted)
        save.cancelAndJoin()
        owner.connectionChanges.unlock()
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
