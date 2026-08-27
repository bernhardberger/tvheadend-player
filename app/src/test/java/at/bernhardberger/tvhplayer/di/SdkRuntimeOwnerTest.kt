package at.bernhardberger.tvhplayer.di

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class SdkRuntimeOwnerTest {
    @Test
    fun terminalCloseUsesRequiredReleasedSdkOrder() = runTest {
        val calls = mutableListOf<String>()
        closeSdkRuntime(FakeShutdownActions(calls))
        assertEquals(
            listOf(
                "coordinator.shutdown",
                "coordinator.join",
                "session.shutdown",
                "listeners.detach",
                "player.release",
                "applicationScope.cancel",
            ),
            calls,
        )
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
                "session.shutdown",
                "listeners.detach",
                "player.release",
                "applicationScope.cancel",
            ),
            calls,
        )
    }

    @Test
    fun callerCancellationIsPreservedOnlyAfterEveryTerminalOwnerIsClosed() = runTest {
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
    private val coordinatorFailure: Throwable? = null,
) : SdkShutdownActions {
    override suspend fun shutdownCoordinator() {
        calls += "coordinator.shutdown"
        coordinatorFailure?.let { throw it }
    }
    override fun cancelCoordinatorRun() { calls += "coordinator.cancel" }
    override suspend fun joinCoordinatorRun() { calls += "coordinator.join" }
    override suspend fun shutdownSession() { calls += "session.shutdown" }
    override fun detachApplicationListeners() { calls += "listeners.detach" }
    override fun releasePlayer() { calls += "player.release" }
    override suspend fun cancelApplicationScope() { calls += "applicationScope.cancel" }
}
