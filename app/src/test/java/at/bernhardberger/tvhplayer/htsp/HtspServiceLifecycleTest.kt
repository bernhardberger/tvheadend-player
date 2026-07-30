package at.bernhardberger.tvhplayer.htsp

import at.bernhardberger.tvhplayer.core.ConnectionFailureKind
import at.bernhardberger.tvhplayer.core.connectionFailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class HtspServiceLifecycleTest {

    @Test
    fun cancelledConnectPropagatesWithoutPublishingTransportError() {
        FakeHtspServer(respondToHello = false).use { server ->
            val service = service()
            runBlocking {
                val observed = CopyOnWriteArrayList<ConnectionState>()
                val collector = launch {
                    service.state.collect { observed += it }
                }
                val connection = launch(Dispatchers.IO) {
                    service.connect(
                        host = "127.0.0.1",
                        port = server.port,
                        connectTimeoutMs = 1_000,
                        responseTimeoutMs = 5_000,
                        soTimeoutMs = 50,
                    )
                }
                withTimeout(1_000L) {
                    service.state.first { it is ConnectionState.Connecting }
                }

                connection.cancelAndJoin()
                delay(50L)

                assertTrue(observed.none { it is ConnectionState.Error })
                assertTrue(service.state.value is ConnectionState.Disconnected)
                collector.cancelAndJoin()
            }
        }
    }

    @Test
    fun replacementConnectCannotBeOverwrittenByCancelledAttemptState() {
        FakeHtspServer(respondToHello = false).use { firstServer ->
            FakeHtspServer(respondToHello = true).use { replacementServer ->
                val service = service()
                runBlocking {
                    val observed = CopyOnWriteArrayList<ConnectionState>()
                    val collector = launch {
                        service.state.collect { observed += it }
                    }
                    val first = launch(Dispatchers.IO) {
                        service.connect(
                            host = "127.0.0.1",
                            port = firstServer.port,
                            connectTimeoutMs = 1_000,
                            responseTimeoutMs = 5_000,
                            soTimeoutMs = 50,
                        )
                    }
                    withTimeout(1_000L) {
                        service.state.first {
                            it is ConnectionState.Connecting && it.port == firstServer.port
                        }
                    }

                    first.cancel()
                    val replacement = async(Dispatchers.IO) {
                        service.connect(
                            host = "127.0.0.1",
                            port = replacementServer.port,
                            connectTimeoutMs = 1_000,
                            responseTimeoutMs = 1_000,
                            soTimeoutMs = 50,
                            forceReconnect = true,
                        )
                    }
                    replacement.await()
                    first.join()
                    delay(50L)

                    val replacementStart = observed.indexOfFirst {
                        it is ConnectionState.Connecting && it.port == replacementServer.port
                    }
                    assertTrue(replacementStart >= 0)
                    assertTrue(observed.drop(replacementStart).none { it is ConnectionState.Error })
                    assertEquals(
                        replacementServer.port,
                        (service.state.value as ConnectionState.Connected).port,
                    )
                    service.disconnect()
                    collector.cancelAndJoin()
                }
            }
        }
    }

    @Test
    fun cancelledReplacementWaitingForConnectOwnerLeavesDisconnectedState() {
        FakeHtspServer(respondToHello = false).use { firstServer ->
            FakeHtspServer(respondToHello = true).use { replacementServer ->
                val service = service()
                runBlocking {
                    val first = launch(Dispatchers.IO) {
                        service.connect(
                            host = "127.0.0.1",
                            port = firstServer.port,
                            connectTimeoutMs = 1_000,
                            responseTimeoutMs = 5_000,
                            soTimeoutMs = 50,
                        )
                    }
                    withTimeout(1_000L) {
                        service.state.first { it is ConnectionState.Connecting }
                    }
                    val firstAttempt = service.currentConnectionAttemptId()

                    val replacement = launch(Dispatchers.IO) {
                        service.connect(
                            host = "127.0.0.1",
                            port = replacementServer.port,
                            connectTimeoutMs = 1_000,
                            responseTimeoutMs = 1_000,
                            soTimeoutMs = 50,
                            forceReconnect = true,
                        )
                    }
                    withTimeout(1_000L) {
                        while (service.currentConnectionAttemptId() == firstAttempt) delay(1L)
                    }

                    replacement.cancelAndJoin()
                    withTimeout(1_000L) { first.join() }

                    assertTrue(service.state.value is ConnectionState.Disconnected)
                }
            }
        }
    }

    @Test
    fun supersededReaderCannotPublishOldServerMessage() {
        val releaseOldAuthentication = CountDownLatch(1)
        FakeHtspServer(
            respondToHello = true,
            authenticateResponseGate = releaseOldAuthentication,
        ).use { firstServer ->
            FakeHtspServer(respondToHello = true).use { replacementServer ->
                val service = service()
                runBlocking {
                    val events = CopyOnWriteArrayList<HtspEvent>()
                    val collector = launch {
                        service.controlEvents.collect { events += it }
                    }
                    val first = launch(Dispatchers.IO) {
                        service.connect(
                            host = "127.0.0.1",
                            port = firstServer.port,
                            connectTimeoutMs = 1_000,
                            responseTimeoutMs = 5_000,
                            soTimeoutMs = 50,
                        )
                    }
                    assertTrue(firstServer.authenticateRequestReceived.await(1, TimeUnit.SECONDS))
                    val firstAttempt = service.currentConnectionAttemptId()

                    val replacement = async(Dispatchers.IO) {
                        service.connect(
                            host = "127.0.0.1",
                            port = replacementServer.port,
                            connectTimeoutMs = 1_000,
                            responseTimeoutMs = 1_000,
                            soTimeoutMs = 50,
                            forceReconnect = true,
                        )
                    }
                    withTimeout(1_000L) {
                        while (service.currentConnectionAttemptId() == firstAttempt) delay(1L)
                    }

                    firstServer.sendServerMessage("oldServerMarker")
                    delay(50L)
                    assertTrue(
                        events.none {
                            it is HtspEvent.ServerMessage && it.msg.method == "oldServerMarker"
                        }
                    )

                    first.cancel()
                    releaseOldAuthentication.countDown()
                    first.join()
                    replacement.await()
                    service.disconnect()
                    collector.cancelAndJoin()
                }
            }
        }
    }

    @Test
    fun staleSubscriptionCommandCannotUseReplacementTransport() {
        FakeHtspServer(respondToHello = true).use { firstServer ->
            FakeHtspServer(respondToHello = true).use { replacementServer ->
                val service = service()
                runBlocking {
                    service.connect(
                        host = "127.0.0.1",
                        port = firstServer.port,
                        connectTimeoutMs = 1_000,
                        responseTimeoutMs = 1_000,
                        soTimeoutMs = 50,
                    )
                    val staleAttempt = service.currentConnectionAttemptId()
                    service.connect(
                        host = "127.0.0.1",
                        port = replacementServer.port,
                        connectTimeoutMs = 1_000,
                        responseTimeoutMs = 1_000,
                        soTimeoutMs = 50,
                        forceReconnect = true,
                    )

                    val failure = runCatching {
                        service.requestForConnectionAttempt(
                            expectedConnectionAttemptId = staleAttempt,
                            method = "subscriptionSpeed",
                            fields = mapOf("subscriptionId" to 1, "speed" to 0),
                        )
                    }.exceptionOrNull()

                    assertTrue(failure is kotlinx.coroutines.CancellationException)
                    assertEquals(
                        listOf("hello", "authenticate"),
                        replacementServer.handshakeMethods,
                    )
                    service.disconnect()
                }
            }
        }
    }

    @Test
    fun controlAndMuxEventsCarrySharedReaderOrder() {
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
                val control = async(start = CoroutineStart.UNDISPATCHED) {
                    service.controlEvents.first {
                        it is HtspEvent.ServerMessage && it.msg.method == "subscriptionSkip"
                    } as HtspEvent.ServerMessage
                }
                val mux = async(start = CoroutineStart.UNDISPATCHED) {
                    service.muxEvents.first { it.msg.method == "muxpkt" }
                }

                server.sendServerMessage("subscriptionSkip")
                server.sendServerMessage("muxpkt")

                val controlEvent = withTimeout(1_000L) { control.await() }
                val muxEvent = withTimeout(1_000L) { mux.await() }
                assertEquals(controlEvent.connectionAttemptId, muxEvent.connectionAttemptId)
                assertTrue(controlEvent.messageSequence < muxEvent.messageSequence)
                assertTrue(muxEvent.muxSequence > 0L)
                assertEquals(
                    muxEvent.muxSequence,
                    service.currentMuxSequenceForConnectionAttempt(muxEvent.connectionAttemptId),
                )
                service.disconnect()
            }
        }
    }

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
                assertTrue(failure is HtspRequestTimeoutException)
                assertTrue(service.state.value is ConnectionState.Connected)
                service.disconnect()
            }
        }
    }

    @Test
    fun callerTimeoutIsNotConvertedToRequestTimeout() {
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
                    withTimeout(50L) {
                        service.request(
                            method = "getEvents",
                            timeoutMs = 500L,
                            disconnectOnTimeout = false,
                        )
                    }
                }.exceptionOrNull()

                assertTrue(failure is TimeoutCancellationException)
                assertTrue(service.state.value is ConnectionState.Connected)
                service.disconnect()
            }
        }
    }

    @Test
    fun cancelledWrittenRequestLeavesAttemptLiveUntilTransportDisconnects() {
        FakeHtspServer(
            respondToHello = true,
            captureOnePostHandshakeRequest = true,
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
                val attemptId = service.currentConnectionAttemptId()
                val request = launch(Dispatchers.IO) {
                    service.requestForConnectionAttempt(
                        expectedConnectionAttemptId = attemptId,
                        method = "subscribe",
                        fields = mapOf("subscriptionId" to 42),
                        timeoutMs = 5_000L,
                    )
                }

                assertTrue(server.postHandshakeRequestReceived.await(1, TimeUnit.SECONDS))
                request.cancelAndJoin()

                assertEquals(
                    HtspConnectionAttemptStatus.LIVE,
                    service.connectionAttemptStatus(attemptId),
                )
                service.disconnect()
                assertEquals(
                    HtspConnectionAttemptStatus.REPLACED,
                    service.connectionAttemptStatus(attemptId),
                )
            }
        }
    }

    @Test
    fun failedCurrentTransportBecomesGoneWithoutWaitingForANewAttempt() {
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
                val attemptId = service.currentConnectionAttemptId()

                server.closeClientTransport()
                withTimeout(1_000L) {
                    while (
                        service.connectionAttemptStatus(attemptId) ==
                        HtspConnectionAttemptStatus.LIVE
                    ) {
                        delay(1L)
                    }
                }

                assertEquals(
                    HtspConnectionAttemptStatus.GONE,
                    service.connectionAttemptStatus(attemptId),
                )
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
        private val authenticateResponseGate: CountDownLatch? = null,
        private val captureOnePostHandshakeRequest: Boolean = false,
    ) : Closeable {
        private val serverSocket = ServerSocket(0)
        private val stop = CountDownLatch(1)
        @Volatile
        private var clientSocket: Socket? = null
        val authenticateRequestReceived = CountDownLatch(1)
        val postHandshakeRequestReceived = CountDownLatch(1)
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
                        if (method == "authenticate") {
                            authenticateRequestReceived.countDown()
                            authenticateResponseGate?.await()
                        }
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
                    if (captureOnePostHandshakeRequest) {
                        HtspCodec.readMessage(client.getInputStream())
                        postHandshakeRequestReceived.countDown()
                    }
                }
                stop.await()
            }
        }

        val port: Int = serverSocket.localPort

        fun sendServerMessage(method: String) {
            val output = checkNotNull(clientSocket).getOutputStream()
            HtspCodec.writeMessage(
                output = output,
                method = method,
                fields = emptyMap(),
            )
            output.flush()
        }

        fun closeClientTransport() {
            runCatching { clientSocket?.close() }
        }

        override fun close() {
            stop.countDown()
            runCatching { clientSocket?.close() }
            runCatching { serverSocket.close() }
            serverThread.join(1_000)
        }
    }
}
