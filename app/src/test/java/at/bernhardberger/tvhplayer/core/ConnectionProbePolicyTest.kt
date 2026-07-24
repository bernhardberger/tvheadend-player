package at.bernhardberger.tvhplayer.core

import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionProbePolicyTest {
    @Test
    fun probePerformsHelloAuthenticationAndMetadataSyncBeforeSuccess() = runBlocking {
        val session = FakeProbeSession(serverVersion = 43, channelCount = 12)

        val result = runConnectionProbe(session)

        assertEquals(ConnectionProbeResult.Success(serverVersion = 43, channelCount = 12), result)
        assertEquals(listOf("connect", "sync", "close"), session.calls)
    }

    @Test
    fun probeDistinguishesEveryObservableFailure() = runBlocking {
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

    private class FakeProbeSession(
        private val serverVersion: Int,
        private val channelCount: Int,
        private val connectFailure: Throwable? = null,
        private val syncFailure: Throwable? = null,
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
        }
    }
}
