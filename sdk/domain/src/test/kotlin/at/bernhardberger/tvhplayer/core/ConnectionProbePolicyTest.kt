package at.bernhardberger.tvhplayer.core

import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ConnectionProbePolicyTest {
    @Test
    fun probePerformsHelloAuthenticationAndMetadataSyncBeforeSuccess() = runTest {
        val session = FakeProbeSession(serverVersion = 43, channelCount = 12)

        val result = runConnectionProbe(session)

        assertEquals(ConnectionProbeResult.Success(serverVersion = 43, channelCount = 12), result)
        assertEquals(listOf("connect", "sync", "close"), session.calls)
    }

    @Test
    fun probeDistinguishesEveryObservableFailure() = runTest {
        assertEquals(
            ConnectionProbeResult.Failure(ConnectionFailureKind.INCOMPATIBLE_SERVER),
            runConnectionProbe(FakeProbeSession(serverVersion = 18, channelCount = 1)),
        )
        assertEquals(
            ConnectionProbeResult.Failure(ConnectionFailureKind.ZERO_CHANNELS),
            runConnectionProbe(FakeProbeSession(serverVersion = 43, channelCount = 0)),
        )
        assertEquals(
            ConnectionProbeResult.Failure(ConnectionFailureKind.PERMISSION_DENIED),
            runConnectionProbe(
                FakeProbeSession(
                    serverVersion = 43,
                    channelCount = 1,
                    syncFailure = MetadataPermissionDeniedException(),
                )
            ),
        )
        assertEquals(
            ConnectionProbeResult.Failure(ConnectionFailureKind.DNS),
            runConnectionProbe(
                FakeProbeSession(
                    serverVersion = 43,
                    channelCount = 1,
                    connectFailure = UnknownHostException(),
                )
            ),
        )
    }

    @Test
    fun cancellationFromProbeWorkPropagatesAfterClose() = runTest {
        val cancellation = CancellationException("cancel probe")
        val session = FakeProbeSession(
            serverVersion = 43,
            channelCount = 1,
            connectFailure = cancellation,
        )

        try {
            runConnectionProbe(session)
            fail("Expected probe cancellation to propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(listOf("connect", "close"), session.calls)
    }

    @Test
    fun cancellationFromClosePropagates() = runTest {
        val cancellation = CancellationException("cancel close")
        val session = FakeProbeSession(
            serverVersion = 43,
            channelCount = 1,
            closeFailure = cancellation,
        )

        try {
            runConnectionProbe(session)
            fail("Expected close cancellation to propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(listOf("connect", "sync", "close"), session.calls)
    }

    @Test
    fun ordinaryCloseFailureDoesNotReplaceProbeResult() = runTest {
        val session = FakeProbeSession(
            serverVersion = 43,
            channelCount = 12,
            closeFailure = IllegalStateException("close failed"),
        )

        assertEquals(
            ConnectionProbeResult.Success(serverVersion = 43, channelCount = 12),
            runConnectionProbe(session),
        )
    }

    private class FakeProbeSession(
        private val serverVersion: Int,
        private val channelCount: Int,
        private val connectFailure: Throwable? = null,
        private val syncFailure: Throwable? = null,
        private val closeFailure: Throwable? = null,
    ) : ConnectionProbeSession {
        val calls = mutableListOf<String>()

        override suspend fun connect(): Int {
            calls += "connect"
            connectFailure?.let { throw it }
            return serverVersion
        }

        override suspend fun syncChannelMetadata(): Int {
            calls += "sync"
            syncFailure?.let { throw it }
            return channelCount
        }

        override suspend fun close() {
            calls += "close"
            closeFailure?.let { throw it }
        }
    }
}
