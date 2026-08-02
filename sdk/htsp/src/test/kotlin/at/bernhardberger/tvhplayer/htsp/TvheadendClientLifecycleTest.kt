package at.bernhardberger.tvhplayer.htsp

import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TvheadendClientLifecycleTest {
    @Test
    fun terminalCloseIsIdempotentAndRejectsNewConnections() = runTest {
        val client = TvheadendClient(ioDispatcher = Dispatchers.Unconfined)

        client.close()
        client.close()

        try {
            client.connect(
                TvheadendConnection(
                    host = "example.invalid",
                    port = 9982,
                    username = "",
                    password = "",
                )
            )
            fail("A closed client must reject a new connection")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("closed", ignoreCase = true))
        }
    }

    @Test
    fun oversizedFileReadClosesTheAttemptScopedHandle() = runTest {
        val service = ScriptedFileService(
            chunks = ArrayDeque(
                listOf(
                    byteArrayOf(1, 2, 3, 4),
                    byteArrayOf(5, 6, 7, 8),
                )
            )
        )
        val client = TvheadendClient(
            ioDispatcher = Dispatchers.Unconfined,
            service = service,
        )

        try {
            client.readFileBytes(path = "/image.png", maxBytes = 6)
            fail("The configured byte limit must be enforced")
        } catch (expected: HtspFileTooLargeException) {
            assertEquals(6, expected.maximumBytes)
        } finally {
            client.close()
        }

        assertEquals(listOf(41), service.closedHandles)
        assertEquals(listOf(7L, 7L, 7L, 7L), service.observedAttemptIds)
    }

    @Test
    fun profileDiscoveryRunsTransportWorkOnTheConfiguredIoDispatcher() = runTest {
        val dispatcher = MarkerDispatcher()
        val service = ProfileService()
        val client = TvheadendClient(
            ioDispatcher = dispatcher,
            service = service,
        )

        try {
            assertEquals(listOf(ProfileItem("pass", "Pass")), client.discoverProfiles())
            assertTrue(service.requestUsedConfiguredDispatcher)
        } finally {
            client.close()
            dispatcher.close()
        }
    }

    @Test
    fun terminalClosePreventsAQueuedClientConnectFromOpeningAnotherSocket() = runTest {
        val dispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
        val blockingSocket = BlockingConnectSocket()
        val socketCreations = AtomicInteger()
        val service = HtspService(
            ioDispatcher = dispatcher,
            socketFactory = {
                check(socketCreations.incrementAndGet() == 1) {
                    "A socket was created after terminal close"
                }
                blockingSocket
            },
        )
        val client = TvheadendClient(
            ioDispatcher = dispatcher,
            service = service,
        )
        val connection = TvheadendConnection("example.invalid", 9982)

        try {
            val first = async(dispatcher) { captureFailure { client.connect(connection) } }
            assertTrue(blockingSocket.connectEntered.await(1, TimeUnit.SECONDS))

            val queued = async(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                captureFailure { client.connect(connection) }
            }
            val close = async(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                client.close()
            }
            val closeCompleted = CountDownLatch(1)
            close.invokeOnCompletion { closeCompleted.countDown() }

            assertTrue(closeCompleted.await(2, TimeUnit.SECONDS))
            close.await()
            assertTrue(first.await() is CancellationException)
            assertTrue(queued.await() is CancellationException)
            assertEquals(1, socketCreations.get())

            val afterClose = captureFailure { client.connect(connection) }
            assertTrue(afterClose is IllegalStateException)
            assertTrue(afterClose?.message.orEmpty().contains("closed", ignoreCase = true))
            assertEquals(1, socketCreations.get())
        } finally {
            blockingSocket.close()
            client.close()
            dispatcher.close()
        }
    }

    private class ScriptedFileService(
        private val chunks: ArrayDeque<ByteArray>,
    ) : HtspService(Dispatchers.Unconfined) {
        val closedHandles = mutableListOf<Int>()
        val observedAttemptIds = mutableListOf<Long?>()

        override fun currentConnectionState(): ConnectionState = ConnectionState.Connected(
            host = "example.invalid",
            port = 9982,
            htspVersion = 43,
        )

        override fun currentConnectionAttemptId(): Long = 7L

        override suspend fun fileOpen(
            path: String,
            timeoutMs: Long,
            expectedConnectionAttemptId: Long?,
        ): Int {
            observedAttemptIds += expectedConnectionAttemptId
            return 41
        }

        override suspend fun fileRead(
            id: Int,
            size: Int,
            timeoutMs: Long,
            expectedConnectionAttemptId: Long?,
        ): ByteArray {
            observedAttemptIds += expectedConnectionAttemptId
            return chunks.removeFirstOrNull() ?: ByteArray(0)
        }

        override suspend fun fileClose(
            id: Int,
            timeoutMs: Long,
            expectedConnectionAttemptId: Long?,
        ) {
            observedAttemptIds += expectedConnectionAttemptId
            closedHandles += id
        }
    }

    private class ProfileService : HtspService(Dispatchers.Unconfined) {
        var requestUsedConfiguredDispatcher = false

        override fun currentConnectionState(): ConnectionState = ConnectionState.Connected(
            host = "example.invalid",
            port = 9982,
            htspVersion = 43,
        )

        override fun currentConnectionAttemptId(): Long = 11L

        override suspend fun requestForConnectionAttempt(
            expectedConnectionAttemptId: Long,
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage {
            requestUsedConfiguredDispatcher = MarkerDispatcher.isMarkedThread()
            return HtspMessage(
                method = null,
                seq = 1,
                fields = mapOf(
                    "profiles" to listOf(
                        mapOf("uuid" to "pass", "name" to "Pass"),
                    ),
                ),
            )
        }
    }

    private class MarkerDispatcher : CoroutineDispatcher(), AutoCloseable {
        private val delegate = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "tvheadend-client-io")
        }.asCoroutineDispatcher()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            delegate.dispatch(context) {
                markedThread.set(true)
                try {
                    block.run()
                } finally {
                    markedThread.remove()
                }
            }
        }

        override fun close() {
            delegate.close()
        }

        companion object {
            private val markedThread = ThreadLocal<Boolean>()

            fun isMarkedThread(): Boolean = markedThread.get() == true
        }
    }

    private class BlockingConnectSocket : Socket() {
        val connectEntered = CountDownLatch(1)
        private val closed = CountDownLatch(1)

        override fun connect(endpoint: SocketAddress, timeout: Int) {
            connectEntered.countDown()
            check(closed.await(2, TimeUnit.SECONDS)) { "Test socket was not closed" }
            throw SocketException("Test socket closed")
        }

        override fun close() {
            closed.countDown()
        }
    }

    private suspend fun captureFailure(block: suspend () -> Any?): Throwable? = try {
        block()
        null
    } catch (cancelled: CancellationException) {
        cancelled
    } catch (error: Throwable) {
        error
    }
}
