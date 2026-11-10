package at.bernhardberger.tvhplayer.settings

import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.notices.readyObservation
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppProfileOwnerTest {
    @Test fun closingTheOwnerCancelsAndJoinsTheCallersPendingSwitch() = runTest {
        val store = FakeServerProfileStore()
        val first = (store.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val second = (store.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val fake = FakeTvheadendSession()
        val restore = CompletableDeferred<Unit>()
        var disconnects = 0
        val session = object : TvheadendSession by fake {
            override suspend fun disconnect() {
                if (++disconnects == 3) restore.await()
                fake.disconnect()
            }
        }
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        val ownerRun = backgroundScope.launch { owner.run() }
        runCurrent()
        val caller = backgroundScope.launch { owner.switchActive(second) }
        runCurrent()
        assertTrue(owner.switchInProgress.value)
        ownerRun.cancel(); runCurrent()
        assertFalse(ownerRun.isCompleted)
        assertTrue(owner.switchInProgress.value)
        restore.complete(Unit); runCurrent()
        assertTrue(caller.isCancelled)
        assertTrue(ownerRun.isCompleted)
        assertFalse(owner.switchInProgress.value)
        assertFalse(owner.connectionChanges.isLocked)
        assertEquals(first, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        // The caller's enclosing scope remains usable, independently of the owner's lifetime.
        assertEquals(42, backgroundScope.async { 42 }.await())
    }

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
    @Test fun cancellationDuringPersistenceRestoresTheOriginalBeforeAReplacementTrials() {
        listOf(false, true).forEach { unreadable -> runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addPassword("first.invalid", username = "viewer", password = "synthetic", activate = true) as ServerProfileWriteResult.Applied).id
        if (unreadable) stored.scriptCredentialsUnavailable(first)
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
        val owner = owner(session, store)
        val old = backgroundScope.launch { owner.switchActive(second) }; runCurrent()
        if (unreadable) assertEquals(ServerProfileReadResult.Unavailable, owner.serverProfile.value)
        session.publish(readyObservation())
        runCurrent()
        assertEquals(listOf(second), writes)
        old.cancel()
        val replacement = backgroundScope.launch { owner.switchActive(third) }; runCurrent()
        assertFalse(old.isCompleted)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf(second, first), writes)
        assertTrue(old.isCompleted)
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        assertEquals(first, (owner.profiles.value as ServerProfileListResult.Available).activeId)
        if (unreadable) assertEquals(ServerProfileReadResult.Unavailable, owner.serverProfile.value)
        else assertEquals(first, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        replacement.cancelAndJoin()
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        assertFalse(owner.connectionChanges.isLocked)
        } }
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
        val owner = owner(session, store)
        val attempt = backgroundScope.launch { owner.switchActive(next) }; runCurrent()
        session.publish(readyObservation())
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
        val owner = owner(session, stored)
        val old = backgroundScope.launch { owner.switchActive(second) }
        runCurrent()
        old.cancel()
        runCurrent()
        assertEquals(3, disconnects)
        assertTrue(owner.switchInProgress.value) // Cancellation still owns the fallback reconnect.
        val replacement = backgroundScope.async { owner.switchActive(third) }
        runCurrent()
        assertFalse(old.isCompleted)
        assertEquals(3, disconnects)
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        restore.complete(Unit)
        runCurrent()
        assertTrue(old.isCompleted)
        assertEquals(4, disconnects)
        fake.publish(readyObservation())
        runCurrent()
        assertEquals(ServerProfileWriteResult.Applied(third), replacement.await())
        assertEquals(third, (stored.profiles() as ServerProfileListResult.Available).activeId)
        assertFalse(owner.switchInProgress.value)
    }
    @Test fun userSwitchCommitsOnlyAfterSignInAndCancellationRestoresThePreviousProfile() = runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val next = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val session = FakeTvheadendSession()
        val owner = owner(session, stored)
        val cancelled = backgroundScope.launch { owner.switchActive(next) }
        runCurrent()
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        cancelled.cancelAndJoin()
        assertEquals(first, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertFalse(owner.connectionChanges.isLocked)
        val attempt = backgroundScope.async { owner.switchActive(next) }
        runCurrent()
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        session.publish(readyObservation())
        runCurrent()
        assertEquals(ServerProfileWriteResult.Applied(next), attempt.await())
        assertEquals(next, (stored.profiles() as ServerProfileListResult.Available).activeId)
    }

    @Test fun successfulUserSwitchPostsOneSwitchedNoticeAndSwitchingToTheActiveConnectionPostsNone() = runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val next = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val session = FakeTvheadendSession()
        val notices = NoticeCenter({ 0L }, { NoticeContext(0, null) })
        val owner = AppProfileOwner(session, stored, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing }, { notices })
        backgroundScope.launch { owner.run() }
        runCurrent()
        assertEquals(ServerProfileWriteResult.Applied(first), owner.switchActive(first))
        assertEquals(null, notices.state.value.candidate)
        val attempt = backgroundScope.async { owner.switchActive(next) }
        runCurrent()
        assertEquals(null, notices.state.value.candidate)
        session.publish(readyObservation())
        runCurrent()
        assertEquals(ServerProfileWriteResult.Applied(next), attempt.await())
        assertEquals(Notice.ServerSwitched(next, "second.invalid"), notices.state.value.candidate?.notice)
        assertEquals(1, notices.state.value.pending.size)
    }

    @Test fun rejectedUserSwitchKeepsThePreviousProfileAndRestoresItsConnection() = runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val next = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val session = FakeTvheadendSession()
        lateinit var owner: AppProfileOwner
        var noticePosts = 0
        val notices = NoticeCenter({
            assertTrue("The typed outcome is posted before suppression ends", owner.switchInProgress.value)
            noticePosts++
            0L
        }, { NoticeContext(0, null) })
        owner = AppProfileOwner(session, stored, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing }, { notices })
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
        assertTrue(noticePosts > 0)
        assertTrue(notices.state.value.candidate!!.notice is Notice.ServerSignInRejected)
        assertNoSwitchedNotice(notices)
        assertFalse(owner.switchInProgress.value)
    }

    @Test fun unreachableUserSwitchPostsTheFailureAndNoSwitchedNotice() = runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val next = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val fake = FakeTvheadendSession()
        var unreachable = false
        val session = object : TvheadendSession by fake {
            override suspend fun connect(profile: ServerProfile): SessionCommandResult =
                if (unreachable) SessionCommandResult.NO_CHANGE else fake.connect(profile)
        }
        val notices = NoticeCenter({ 0L }, { NoticeContext(0, null) })
        val owner = AppProfileOwner(session, stored, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing }, { notices })
        backgroundScope.launch { owner.run() }
        runCurrent()
        unreachable = true
        assertEquals(ServerProfileWriteResult.Unavailable, owner.switchActive(next))
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        assertTrue(notices.state.value.candidate!!.notice is Notice.ServerConnectionFailed)
        assertNoSwitchedNotice(notices)
    }

    @Test fun cancellationAfterTheCommitRestoresThePreviousConnectionWithoutASwitchedNotice() = runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val next = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val gate = CompletableDeferred<Unit>()
        var activated = false
        val store = object : ServerProfileStore by stored {
            override suspend fun setActive(id: ServerProfileId): ServerProfileWriteResult {
                if (id == next) activated = true
                return stored.setActive(id)
            }
            // Suspends after the commit's last cancellation check, where Back can still cancel the caller.
            override suspend fun profiles(): ServerProfileListResult {
                if (activated) gate.await()
                return stored.profiles()
            }
        }
        val session = FakeTvheadendSession()
        val notices = NoticeCenter({ 0L }, { NoticeContext(0, null) })
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing }, { notices })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val attempt = backgroundScope.launch { owner.switchActive(next) }
        runCurrent()
        session.publish(readyObservation())
        runCurrent()
        assertTrue(activated)
        attempt.cancel()
        gate.complete(Unit)
        runCurrent()
        assertEquals(first, (stored.profiles() as ServerProfileListResult.Available).activeId)
        assertEquals(first, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertNoSwitchedNotice(notices)
        assertFalse(owner.connectionChanges.isLocked)
        assertFalse(owner.switchInProgress.value)
    }

    private fun assertNoSwitchedNotice(notices: NoticeCenter) {
        assertFalse(notices.state.value.candidate?.notice is Notice.ServerSwitched)
        assertTrue(notices.state.value.pending.none { it.notice is Notice.ServerSwitched })
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
        val owner = owner(session, store)
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
        val owner = owner(session, store)
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
        val owner = owner(FakeTvheadendSession(), store)
        owner.saveServer("first.invalid", 9982)
        assertEquals(anonymous, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        owner.remove(anonymous)
        owner.savePasswordServer("second.invalid", 9982, "viewer", "synthetic")
        assertEquals(account, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertEquals(1, store.calls.count { it == FakeServerProfileStoreCall.ADD_ANONYMOUS })
        assertEquals(1, store.calls.count { it == FakeServerProfileStoreCall.ADD_PASSWORD })
        owner.remove(account)
        owner.saveServer("new.invalid", 9982)
        val added = (owner.serverProfile.value as ServerProfileReadResult.Available).id
        assertNotEquals(anonymous, added)
        assertNotEquals(account, added)
        assertEquals(listOf(added), (store.profiles() as ServerProfileListResult.Available).profiles.map { it.id })
        owner.saveServer("edited.invalid", 9983)
        assertEquals(added, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertEquals("edited.invalid", (owner.serverProfile.value as ServerProfileReadResult.Available).host)
        owner.savePasswordServer("password.invalid", 9982, "viewer", "synthetic")
        assertEquals(added, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertTrue(checkNotNull(owner.serverProfile.value).toServerSettings().passwordConfigured)
        owner.remove(added)
        owner.savePasswordServer("new-password.invalid", 9982, "viewer", "synthetic")
        val passwordAdded = (owner.serverProfile.value as ServerProfileReadResult.Available).id
        assertNotEquals(added, passwordAdded)
        assertEquals(listOf(passwordAdded), (store.profiles() as ServerProfileListResult.Available).profiles.map { it.id })
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

    @Test fun freshSetupReadsCurrentStoreAndRemainsDisconnected() {
        listOf(0, 2, -1).forEach { count -> runTest {
        val stored = FakeServerProfileStore()
        repeat(count.coerceAtLeast(0)) { stored.addAnonymous("server$it.invalid") }
        val store = object : ServerProfileStore by stored {
            override suspend fun profiles(): ServerProfileListResult {
                if (count < 0) error("Synthetic initialization failure")
                return stored.profiles()
            }
        }
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { error("No profile to edit") })
        backgroundScope.launch { owner.run() }
        runCurrent()
        if (count < 0) {
            assertEquals(ServerProfileReadResult.Unavailable, owner.serverProfile.value)
        } else {
        assertEquals(ServerProfileReadResult.Missing, owner.serverProfile.value)
        assertEquals(ServerSettings(), checkNotNull(owner.serverProfile.value).toServerSettings())
        assertEquals(listOf(FakeServerProfileStoreCall.PROFILES, FakeServerProfileStoreCall.LOAD_ACTIVE), stored.calls.takeLast(2))
        val list = owner.profiles.value as ServerProfileListResult.Available
        assertNull(list.activeId)
        assertEquals(count, list.profiles.size)
        assertNull(owner.audioProfileId)
        val calls = stored.calls.toList()
        owner.clearProfile()
        assertEquals(calls, stored.calls)
        }
        assertFalse(FakeSessionCall.CONNECT in session.calls)
        owner.loadServerForEditing { _, _, _, _ -> error("No editing values") }
        } }
    }

    @Test fun switchingKeepsOrderAndSelectingActiveDoesNotReconnect() = runTest {
        val store = FakeServerProfileStore()
        val first = (store.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val second = (store.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val session = FakeTvheadendSession()
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        val owner = owner(session, store, settings)
        val before = session.calls.count { it == FakeSessionCall.CONNECT }
        assertEquals(ServerProfileWriteResult.Applied(first), owner.setActive(first))
        assertEquals(before, session.calls.count { it == FakeSessionCall.CONNECT })
        owner.setActive(second)
        assertEquals(second, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertEquals(listOf(first, second), (owner.profiles.value as ServerProfileListResult.Available).profiles.map { it.id })
        assertEquals(second.value, owner.audioProfileId)
        assertEquals(before + 1, session.calls.count { it == FakeSessionCall.CONNECT })
        val server = checkNotNull(owner.serverProfile.value).toServerSettings()
        assertEquals("second.invalid", server.host)
        assertEquals(9982, server.htspPort)
        assertFalse(server.passwordConfigured)
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

    @Test fun editingRetainsProfileIdAndItsAppData() = runTest {
        val stored = FakeServerProfileStore()
        val id = (stored.addAnonymous("first.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val data = InMemoryPreferencesDataStore()
        val settings = PlayerSettingsStore(data)
        val owner = owner(FakeTvheadendSession(), stored, settings)
        settings.channelAspects.set(id.value, ChannelId(1), AspectRatioMode.FORCE_4_3)
        at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore(data).setChannelId(ChannelId(1))
        assertEquals(SaveServerProfileResult.Applied(id), owner.updateAnonymous(id, "edited.invalid", 9983, "Edited"))
        assertEquals(id, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertEquals(AspectRatioMode.FORCE_4_3, settings.channelAspects.choices.first().modeFor(ChannelId(1)))
        assertEquals(ChannelId(1), at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore(data).channelId.first())
        assertEquals(SaveServerProfileResult.Applied(id), owner.updatePassword(id, "edited.invalid", 9983, "viewer", "synthetic", "Edited"))
        assertEquals(id, (owner.serverProfile.value as ServerProfileReadResult.Available).id)
        assertTrue(checkNotNull(owner.serverProfile.value).toServerSettings().passwordConfigured)
        val added = owner.addAnonymous("second.invalid", 9982) as SaveServerProfileResult.Applied
        assertNotEquals(id, added.id)
        assertTrue((stored.profiles() as ServerProfileListResult.Available).profiles.any { it.id == added.id })
    }

    @Test fun unreadableActiveProfileCanBeRemovedAfterClearingItsSummaryCache() {
        listOf(false, true).forEach { unreadable -> runTest {
        val stored = FakeServerProfileStore()
        val id = (stored.addPassword("first.invalid", username = "viewer", password = "synthetic", activate = true) as ServerProfileWriteResult.Applied).id
        val other = (stored.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        if (unreadable) stored.scriptCredentialsUnavailable(id)
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
        if (unreadable) assertEquals(ServerProfileReadResult.Unavailable, owner.serverProfile.value)
        val generation = owner.configurationGeneration.value
        val calls = stored.calls.toList()
        val sessionCalls = fake.calls.toList()
        assertEquals(ServerProfileWriteResult.NotFound, owner.remove(ServerProfileId.generate()))
        assertTrue(stored.calls.drop(calls.size).all { it == FakeServerProfileStoreCall.PROFILES })
        assertEquals(sessionCalls, fake.calls)
        assertEquals(generation, owner.configurationGeneration.value)
        events.clear()
        assertEquals(ServerProfileWriteResult.Applied(id), owner.remove(id))
        assertTrue(events.indexOf("disconnect") < events.indexOf("profiles"))
        assertTrue(events.indexOf("profiles") < events.indexOf("summary-cache"))
        assertFalse(FakeServerProfileStoreCall.LOAD in stored.calls)
        assertTrue(events.indexOf("summary-cache") < events.indexOf("remove"))
        assertTrue(events.lastIndexOf("app-data") > events.indexOf("remove"))
        assertTrue(FakeSessionCall.CLEAR_PROFILE_CACHE in fake.calls)
        assertEquals(listOf(other), (owner.profiles.value as ServerProfileListResult.Available).profiles.map { it.id })
        assertNull((owner.profiles.value as ServerProfileListResult.Available).activeId)
        assertNull(owner.audioProfileId)
        assertEquals(ServerProfileReadResult.Missing, owner.serverProfile.value)
        assertEquals(Notice.ServerProfileRemoved(id, "viewer on first.invalid"), notices.state.value.candidate?.notice)
        assertTrue(owner.configurationGeneration.value > generation)
        owner.setActive(other)
        owner.clearProfile()
        assertTrue((owner.profiles.value as ServerProfileListResult.Available).profiles.isEmpty())
        assertEquals(ServerProfileReadResult.Missing, owner.serverProfile.value)
        } }
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
        val owner = owner(session, stored)
        val generation = owner.configurationGeneration.value
        assertTrue(runCatching { owner.saveServer("first.invalid", 9982) }.isFailure)
        assertEquals(2, connects)
        assertTrue(owner.serverProfile.value is ServerProfileReadResult.Available)
        assertTrue(owner.configurationGeneration.value > generation)
    }

    @Test fun rejectedAddressDoesNotChangeConfiguration() = runTest {
        val owner = AppProfileOwner(FakeTvheadendSession(), FakeServerProfileStore(),
            PlayerSettingsStore(InMemoryPreferencesDataStore()), StandardTestDispatcher(testScheduler),
            { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        listOf("" to 9982, "bad\u0001" to 9982, "first.invalid" to 0, "first.invalid" to 65536).forEach { (host, port) ->
            val generation = owner.configurationGeneration.value
            assertTrue(runCatching { owner.addAnonymous(host, port) }.isFailure)
            assertTrue(runCatching { owner.addPassword(host, port, "viewer", "synthetic") }.isFailure)
            assertTrue(runCatching { owner.updateAnonymous(ServerProfileId.generate(), host, port, null) }.isFailure)
            assertTrue(runCatching { owner.updatePassword(ServerProfileId.generate(), host, port, "viewer", "synthetic", null) }.isFailure)
            assertTrue(runCatching { owner.savePasswordServer(host, port, "viewer", "synthetic") }.isFailure)
            assertEquals(generation, owner.configurationGeneration.value)
        }
        assertEquals(0L, owner.configurationGeneration.value)
        listOf(1, 65535).forEach { port ->
            val added = owner.addAnonymous("first.invalid", port) as SaveServerProfileResult.Applied
            assertTrue(owner.addPassword("first.invalid", port, "viewer", "synthetic") is SaveServerProfileResult.Applied)
            assertEquals(SaveServerProfileResult.Applied(added.id), owner.updateAnonymous(added.id, "edited.invalid", port, null))
            assertEquals(SaveServerProfileResult.Applied(added.id), owner.updatePassword(added.id, "edited.invalid", port, "viewer", "synthetic", null))
        }
    }
    private fun TestScope.owner(
        session: TvheadendSession,
        store: ServerProfileStore,
        settings: PlayerSettingsStore = PlayerSettingsStore(InMemoryPreferencesDataStore()),
    ): AppProfileOwner = AppProfileOwner(session, store, settings,
        StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing }).also { owner ->
        backgroundScope.launch { owner.run() }
        runCurrent()
    }
}
