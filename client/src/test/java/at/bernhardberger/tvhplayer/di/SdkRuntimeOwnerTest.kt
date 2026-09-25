package at.bernhardberger.tvhplayer.di

import at.bernhardberger.tvheadend.sdk.media3.PlaybackShutdownResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SdkRuntimeOwnerTest {
    @Test
    fun coordinatorRunStartsBeforeItsLaunchReturns() = runTest {
        var started = false

        val runJob = launchCoordinatorRun(this) {
            started = true
            awaitCancellation()
        }

        assertTrue(started)
        runJob.cancelAndJoin()
    }

    @Test
    fun terminalCloseUsesRequiredReleasedSdkOrder() = runTest {
        val calls = mutableListOf<String>()
        closeSdkRuntime(FakeShutdownActions(calls))
        assertEquals(
            listOf(
                "coordinator.shutdown",
                "coordinator.join",
                "profileOwner.cancel",
                "profileOwner.join",
                "session.shutdown",
                "listeners.detach",
                "player.release",
                "applicationScope.cancel",
            ),
            calls,
        )
    }

    @Test
    fun notRunningShutdownCancelsTheUnstartedCoordinatorBoundaryBeforeJoin() = runTest {
        val calls = mutableListOf<String>()

        closeSdkRuntime(
            FakeShutdownActions(
                calls = calls,
                shutdownResult = PlaybackShutdownResult.NOT_RUNNING,
            ),
        )

        assertEquals(
            listOf(
                "coordinator.shutdown",
                "coordinator.cancel",
                "coordinator.join",
                "profileOwner.cancel",
                "profileOwner.join",
                "session.shutdown",
                "listeners.detach",
                "player.release",
                "applicationScope.cancel",
            ),
            calls,
        )
    }

    @Test
    fun successfulShutdownResultsNeverCancelTheCoordinatorRunBoundary() = runTest {
        PlaybackShutdownResult.entries
            .filterNot { it == PlaybackShutdownResult.NOT_RUNNING }
            .forEach { result ->
                val calls = mutableListOf<String>()

                closeSdkRuntime(FakeShutdownActions(calls = calls, shutdownResult = result))

                assertFalse("$result must not cancel a successful shutdown", "coordinator.cancel" in calls)
            }
    }

    @Test
    fun abnormalCoordinatorCompletionTriggersCleanupWithItsFailure() = runTest {
        val expected = IllegalStateException("coordinator actor failed")
        var initialFailure: Throwable? = null
        val controller = SdkRuntimeShutdownController(
            shutdownDispatcher = StandardTestDispatcher(testScheduler),
            closeRuntime = { initialFailure = it },
        )
        val coordinatorRun = CompletableDeferred<Unit>()
        controller.observeActor("coordinator", coordinatorRun)

        coordinatorRun.completeExceptionally(expected)
        runCurrent()
        controller.requestClose().await()

        assertSame(expected, initialFailure)
    }

    @Test
    fun unexpectedProfileOwnerCompletionTriggersDeterministicFailureCleanup() = runTest {
        var initialFailure: Throwable? = null
        val controller = SdkRuntimeShutdownController(
            shutdownDispatcher = StandardTestDispatcher(testScheduler),
            closeRuntime = { initialFailure = it },
        )
        val profileOwnerRun = CompletableDeferred<Unit>()
        controller.observeActor("profile owner", profileOwnerRun)

        profileOwnerRun.complete(Unit)
        runCurrent()
        controller.requestClose().await()

        assertEquals("SDK profile owner actor completed unexpectedly", initialFailure?.message)
    }

    @Test
    fun actorCompletionDuringRequestedShutdownDoesNotTurnSuccessIntoFailure() = runTest {
        var initialFailure: Throwable? = IllegalStateException("not closed")
        val controller = SdkRuntimeShutdownController(
            shutdownDispatcher = StandardTestDispatcher(testScheduler),
            closeRuntime = { initialFailure = it },
        )
        val coordinatorRun = CompletableDeferred<Unit>()
        controller.observeActor("coordinator", coordinatorRun)

        val shutdown = controller.requestClose()
        coordinatorRun.complete(Unit)
        shutdown.await()

        assertNull(initialFailure)
    }
    @Test
    fun coordinatorFailureCancelsRunAndStillReleasesEveryLaterOwner() = runTest {
        val calls = mutableListOf<String>()
        val expected = IllegalStateException("coordinator failed")
        try {
            closeSdkRuntime(FakeShutdownActions(calls, expected))
            fail("failure must be preserved")
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }
        assertEquals(
            listOf(
                "coordinator.shutdown",
                "coordinator.cancel",
                "coordinator.join",
                "profileOwner.cancel",
                "profileOwner.join",
                "session.shutdown",
                "listeners.detach",
                "player.release",
                "applicationScope.cancel",
            ),
            calls,
        )
    }

    @Test
    fun coordinatorCancellationFailureIsPreservedAfterEveryTerminalOwnerIsClosed() = runTest {
        val calls = mutableListOf<String>()
        try {
            closeSdkRuntime(FakeShutdownActions(calls, CancellationException("cancelled")))
            fail("cancellation must be preserved")
        } catch (_: CancellationException) {
            // Expected after deterministic cleanup.
        }
        assertEquals(
            listOf(
                "coordinator.shutdown",
                "coordinator.cancel",
                "coordinator.join",
                "profileOwner.cancel",
                "profileOwner.join",
                "session.shutdown",
                "listeners.detach",
                "player.release",
                "applicationScope.cancel",
            ),
            calls,
        )
    }

    @Test
    fun laterShutdownFailuresAreSuppressedInEncounterOrder() = runTest {
        val calls = mutableListOf<String>()
        val coordinatorFailure = IllegalStateException("coordinator failed")
        val cancelFailure = IllegalStateException("coordinator cancel failed")
        val joinFailure = IllegalStateException("coordinator join failed")
        val profileFailure = IllegalStateException("profile owner failed")
        val profileJoinFailure = IllegalStateException("profile owner join failed")
        val sessionFailure = IllegalStateException("session failed")
        val detachFailure = IllegalStateException("listener detach failed")
        val releaseFailure = IllegalStateException("player release failed")
        val scopeFailure = IllegalStateException("scope cancel failed")
        val actions = FakeShutdownActions(
            calls = calls,
            failures = mapOf(
                "coordinator.shutdown" to coordinatorFailure,
                "coordinator.cancel" to cancelFailure,
                "coordinator.join" to joinFailure,
                "profileOwner.cancel" to profileFailure,
                "profileOwner.join" to profileJoinFailure,
                "session.shutdown" to sessionFailure,
                "listeners.detach" to detachFailure,
                "player.release" to releaseFailure,
                "applicationScope.cancel" to scopeFailure,
            ),
        )

        try {
            closeSdkRuntime(actions)
            fail("first failure and suppressed failures must be preserved")
        } catch (actual: IllegalStateException) {
            assertSame(coordinatorFailure, actual)
            assertArrayEquals(
                arrayOf(
                    cancelFailure,
                    joinFailure,
                    profileFailure,
                    profileJoinFailure,
                    sessionFailure,
                    detachFailure,
                    releaseFailure,
                    scopeFailure,
                ),
                actual.suppressed,
            )
        }
        assertEquals(
            listOf(
                "coordinator.shutdown",
                "coordinator.cancel",
                "coordinator.join",
                "profileOwner.cancel",
                "profileOwner.join",
                "session.shutdown",
                "listeners.detach",
                "player.release",
                "applicationScope.cancel",
            ),
            calls,
        )
    }
}

private class FakeShutdownActions(
    private val calls: MutableList<String>,
    coordinatorFailure: Throwable? = null,
    private val shutdownResult: PlaybackShutdownResult = PlaybackShutdownResult.DRAINED,
    private val failures: Map<String, Throwable> = coordinatorFailure
        ?.let { mapOf("coordinator.shutdown" to it) }
        .orEmpty(),
) : SdkShutdownActions {
    private fun record(call: String) {
        calls += call
        failures[call]?.let { throw it }
    }

    override suspend fun shutdownCoordinator(): PlaybackShutdownResult {
        record("coordinator.shutdown")
        return shutdownResult
    }
    override fun cancelCoordinatorRun() = record("coordinator.cancel")
    override suspend fun joinCoordinatorRun() = record("coordinator.join")
    override fun cancelProfileOwnerRun() = record("profileOwner.cancel")
    override suspend fun joinProfileOwnerRun() = record("profileOwner.join")
    override suspend fun shutdownSession() = record("session.shutdown")
    override suspend fun detachApplicationListeners() = record("listeners.detach")
    override fun releasePlayer() = record("player.release")
    override suspend fun cancelApplicationScope() = record("applicationScope.cancel")
}
