package at.bernhardberger.tvhplayer.htsp

import at.bernhardberger.tvhplayer.core.ConnectionFailureKind
import at.bernhardberger.tvhplayer.core.connectionFailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class HtspServiceLifecycleTest {

    @Test
    fun connectFailureCompletesWhenServerDoesNotAnswerHello() {
        FakeHtspServer(respondToHello = false).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = executor.submit<Throwable?> {
                    runBlocking {
                        runCatching {
                            service().connect(
                                host = "127.0.0.1",
                                port = server.port,
                                connectTimeoutMs = 1_000,
                                responseTimeoutMs = 100,
                                soTimeoutMs = 50,
                            )
                        }.exceptionOrNull()
                    }
                }.get(2, TimeUnit.SECONDS)

                assertNotNull(result)
            } finally {
                server.close()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun disconnectCompletesWhileReaderIsIdle() {
        FakeHtspServer(respondToHello = true).use { server ->
            val service = service()
            runBlocking {
                service.connect(
                    host = "127.0.0.1",
                    port = server.port,
                    connectTimeoutMs = 1_000,
                    responseTimeoutMs = 1_000,
                    soTimeoutMs = 50,
                )
            }

            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = executor.submit<Boolean> {
                    runBlocking {
                        service.disconnect()
                        true
                    }
                }.get(2, TimeUnit.SECONDS)

                assertTrue(result)
            } finally {
                server.close()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun optionalRequestTimeoutLeavesSharedConnectionOpen() {
        FakeHtspServer(respondToHello = true).use { server ->
            val service = service()
            runBlocking {
                service.connect(
                    host = "127.0.0.1",
                    port = server.port,
                    connectTimeoutMs = 1_000,
                    responseTimeoutMs = 1_000,
                    soTimeoutMs = 50,
                )

                val failure = runCatching {
                    service.request(
                        method = "getEvents",
                        timeoutMs = 100,
                        disconnectOnTimeout = false,
                    )
                }.exceptionOrNull()

                assertNotNull(failure)
                assertTrue(service.state.value is ConnectionState.Connected)
                service.disconnect()
            }
        }
    }

    @Test
    fun anonymousConnectStillAuthenticatesAndReadsDvrRight() {
        FakeHtspServer(
            respondToHello = true,
            authFields = mapOf("dvr" to 1, "streaming" to 1),
        ).use { server ->
            val service = service()
            runBlocking {
                service.connect(
                    host = "127.0.0.1",
                    port = server.port,
                    connectTimeoutMs = 1_000,
                    responseTimeoutMs = 1_000,
                    soTimeoutMs = 50,
                )

                val state = service.state.value as ConnectionState.Connected
                assertEquals(true, state.dvrAccess)
                assertEquals(listOf("hello", "authenticate"), server.handshakeMethods)
                // No credentials configured: authenticate must stay bare so the server
                // keeps the address-based anonymous rights.
                assertNull(server.handshakeFields["authenticate"]?.get("username"))
                service.disconnect()
            }
        }
    }

    @Test
    fun anonymousConnectFailsWhenServerGrantsNoAccess() {
        FakeHtspServer(
            respondToHello = true,
            authFields = mapOf("noaccess" to 1),
        ).use { server ->
            val service = service()
            val failure = runBlocking {
                runCatching {
                    service.connect(
                        host = "127.0.0.1",
                        port = server.port,
                        connectTimeoutMs = 1_000,
                        responseTimeoutMs = 1_000,
                        soTimeoutMs = 50,
                    )
                }.exceptionOrNull()
            }

            assertNotNull(failure)
            assertEquals(
                ConnectionFailureKind.AUTHENTICATION,
                connectionFailureKind(requireNotNull(failure)),
            )
        }
    }

    private fun service() = HtspService(ioDispatcher = Dispatchers.IO)

    private class FakeHtspServer(
        private val respondToHello: Boolean,
        private val authFields: Map<String, Any?> = emptyMap(),
    ) : Closeable {
        private val serverSocket = ServerSocket(0)
        private val stop = CountDownLatch(1)
        @Volatile
        private var clientSocket: Socket? = null
        /** Methods the client sent during the handshake, in order. */
        val handshakeMethods = mutableListOf<String>()
        val handshakeFields = mutableMapOf<String, Map<String, Any?>>()
        private val serverThread = thread(
            start = true,
            isDaemon = true,
            name = "fake-htsp-server",
        ) {
            runCatching {
                val client = serverSocket.accept()
                clientSocket = client
                if (respondToHello) {
                    // The client always sends hello then authenticate, with or without
                    // credentials; anything after that is left unanswered on purpose.
                    repeat(2) {
                        val request = HtspCodec.readMessage(client.getInputStream())
                        val method = requireNotNull(request.method)
                        handshakeMethods += method
                        handshakeFields[method] = request.fields
                        val fields = mutableMapOf<String, Any?>(
                            "seq" to requireNotNull(request.seq),
                        )
                        if (method == "authenticate") {
                            fields += authFields
                        } else {
                            fields["htspversion"] = 43
                        }
                        HtspCodec.writeMessage(
                            output = client.getOutputStream(),
                            method = method,
                            fields = fields,
                        )
                        client.getOutputStream().flush()
                    }
                }
                stop.await()
            }
        }

        val port: Int = serverSocket.localPort

        override fun close() {
            stop.countDown()
            runCatching { clientSocket?.close() }
            runCatching { serverSocket.close() }
            serverThread.join(1_000)
        }
    }
}
