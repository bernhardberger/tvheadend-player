package at.bernhardberger.tvhplayer.htsp

import at.bernhardberger.tvhplayer.core.ConnectionUiState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvheadendClientInvalidationTest {
    @Test
    fun invalidationDisconnectsAnExistingHealthyConnection() = runTest {
        val service = ControlledService(StandardTestDispatcher(testScheduler)).apply {
            currentState = ConnectionState.Connected(
                host = HOST,
                port = PORT,
                htspVersion = 43,
            )
        }
        val client = client(service)

        assertTrue(client.connect(CONNECTION))
        assertEquals(ConnectionUiState.Ready, client.frontendState.value)

        client.invalidateConnection(preservePublishedMetadata = true)

        assertEquals(1, service.disconnectCount)
        assertFalse(client.frontendState.value is ConnectionUiState.Ready)
        client.close()
    }

    @Test
    fun invalidationCancelsAQueuedReconnectBeforeItsDelayExpires() = runTest {
        val service = ControlledService(StandardTestDispatcher(testScheduler)).apply {
            connectBehavior = { throw IOException("fixture connection failure") }
        }
        val client = client(service, reconnectDelayMs = 1_000L)

        assertFalse(client.connect(CONNECTION))
        assertEquals(1, service.connectCalls.size)

        client.invalidateConnection(preservePublishedMetadata = true)
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(1, service.connectCalls.size)
        assertEquals(1, service.disconnectCount)
        client.close()
    }

    @Test
    fun invalidatedLateSuccessCannotPublishReady() = runTest {
        val connectStarted = CompletableDeferred<Unit>()
        val allowConnectToReturn = CompletableDeferred<Unit>()
        val service = ControlledService(StandardTestDispatcher(testScheduler)).apply {
            connectBehavior = {
                connectStarted.complete(Unit)
                allowConnectToReturn.await()
            }
        }
        val client = client(service)
        val oldAttempt = async { client.connect(CONNECTION) }
        connectStarted.await()

        val invalidation = async {
            client.invalidateConnection(preservePublishedMetadata = true)
        }
        runCurrent()
        allowConnectToReturn.complete(Unit)
        runCurrent()

        assertCancelled(oldAttempt)
        invalidation.await()
        assertFalse(client.frontendState.value is ConnectionUiState.Ready)
        assertEquals(1, service.disconnectCount)
        client.close()
    }

    @Test
    fun replacementStartedDuringInvalidationExclusivelyOwnsTheFinalTransport() = runTest {
        val disconnectStarted = CompletableDeferred<Unit>()
        val allowDisconnectToReturn = CompletableDeferred<Unit>()
        val service = ControlledService(StandardTestDispatcher(testScheduler)).apply {
            currentState = ConnectionState.Connected(HOST, PORT, htspVersion = 43)
        }
        val client = client(service)
        assertTrue(client.connect(CONNECTION))
        service.disconnectBehavior = {
            disconnectStarted.complete(Unit)
            allowDisconnectToReturn.await()
        }

        val invalidation = async {
            client.invalidateConnection(preservePublishedMetadata = true)
        }
        disconnectStarted.await()
        val replacement = async {
            client.connect(
                connection = CONNECTION.copy(
                    username = NEW_USERNAME,
                    password = NEW_PASSWORD,
                ),
                reuseMatchingConnection = true,
                preservePublishedMetadata = false,
            )
        }
        runCurrent()
        allowDisconnectToReturn.complete(Unit)
        runCurrent()

        invalidation.await()
        val replacementConnected = replacement.await()
        val observedUsernames = service.connectCalls.map { it.username }
        val maximumConcurrentTransportOperations = service.maximumConcurrentTransportOperations
        val finalConnectionState = service.currentConnectionState()
        val finalFrontendState = client.frontendState.value
        client.close()

        assertTrue(replacementConnected)
        assertEquals(
            listOf(NEW_USERNAME),
            observedUsernames,
        )
        assertEquals(1, maximumConcurrentTransportOperations)
        assertTrue(finalConnectionState is ConnectionState.Connected)
        assertEquals(ConnectionUiState.Ready, finalFrontendState)
    }

    @Test
    fun cancellationDuringInvalidationPropagatesAfterCleanupWithoutContinuingCaller() = runTest {
        val disconnectStarted = CompletableDeferred<Unit>()
        val allowDisconnectToReturn = CompletableDeferred<Unit>()
        val service = ControlledService(StandardTestDispatcher(testScheduler)).apply {
            currentState = ConnectionState.Connected(HOST, PORT, htspVersion = 43)
        }
        val client = client(service)
        assertTrue(client.connect(CONNECTION))
        service.disconnectBehavior = {
            disconnectStarted.complete(Unit)
            allowDisconnectToReturn.await()
        }
        var callerContinued = false

        val invalidation = async {
            client.invalidateConnection(preservePublishedMetadata = true)
            callerContinued = true
        }
        disconnectStarted.await()
        invalidation.cancel()
        allowDisconnectToReturn.complete(Unit)
        runCurrent()

        assertCancelled(invalidation)
        val disconnectCount = service.disconnectCount
        val finalFrontendState = client.frontendState.value
        client.close()

        assertFalse(callerContinued)
        assertEquals(1, disconnectCount)
        assertFalse(finalFrontendState is ConnectionUiState.Ready)
    }

    @Test
    fun replacementCredentialsCannotRaceWithOrReuseTheOldAttempt() = runTest {
        val firstConnectStarted = CompletableDeferred<Unit>()
        val allowFirstConnectToReturn = CompletableDeferred<Unit>()
        val service = ControlledService(StandardTestDispatcher(testScheduler)).apply {
            connectBehavior = { credentials ->
                if (credentials.username == OLD_USERNAME) {
                    firstConnectStarted.complete(Unit)
                    allowFirstConnectToReturn.await()
                } else {
                    throw IOException("fixture replacement failure")
                }
            }
        }
        val client = client(service, reconnectDelayMs = 10_000L)
        val oldAttempt = async { client.connect(CONNECTION) }
        firstConnectStarted.await()

        val replacementAttempt = async {
            client.connect(
                connection = CONNECTION.copy(
                    username = NEW_USERNAME,
                    password = NEW_PASSWORD,
                ),
                reuseMatchingConnection = false,
                preservePublishedMetadata = false,
            )
        }
        runCurrent()
        allowFirstConnectToReturn.complete(Unit)
        runCurrent()

        assertCancelled(oldAttempt)
        assertFalse(replacementAttempt.await())
        assertEquals(listOf(OLD_USERNAME, NEW_USERNAME), service.connectCalls.map { it.username })
        assertEquals(1, service.maximumConcurrentConnects)

        client.invalidateConnection(preservePublishedMetadata = false)
        client.close()
    }

    @Test
    fun repeatedInvalidationIsSafeAndLeavesNoRetryOrReadyState() = runTest {
        val service = ControlledService(StandardTestDispatcher(testScheduler)).apply {
            connectBehavior = { throw IOException("fixture connection failure") }
        }
        val client = client(service, reconnectDelayMs = 1_000L)

        assertFalse(client.connect(CONNECTION))
        client.invalidateConnection(preservePublishedMetadata = true)
        client.invalidateConnection(preservePublishedMetadata = true)
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(1, service.connectCalls.size)
        assertEquals(2, service.disconnectCount)
        assertFalse(client.frontendState.value is ConnectionUiState.Ready)
        client.close()
    }

    private fun client(
        service: ControlledService,
        reconnectDelayMs: Long = 1_000L,
    ): TvheadendClient = TvheadendClient(
        ioDispatcher = service.dispatcher,
        clientIdentity = HtspClientIdentity.Default,
        logger = HtspLogger.None,
        timings = TvheadendClientTimings(reconnectDelayMs = reconnectDelayMs),
        epgTimings = at.bernhardberger.tvhplayer.repositories.EpgRuntimeTimings(),
        epochSeconds = { 0L },
        service = service,
    )

    private suspend fun assertCancelled(result: kotlinx.coroutines.Deferred<*>) {
        try {
            result.await()
            fail("The invalidated attempt must complete with cancellation")
        } catch (_: CancellationException) {
            // Expected: client generation invalidation is represented as cancellation.
        }
    }

    private data class ObservedCredentials(
        val username: String,
        val password: String,
    )

    private class ControlledService(
        val dispatcher: CoroutineDispatcher,
    ) : HtspService(dispatcher) {
        private val controlledEvents = HtspEventStream()

        override val controlEvents = controlledEvents.events
        var currentState: ConnectionState = ConnectionState.Disconnected
        var connectBehavior: suspend (ObservedCredentials) -> Unit = {}
        val connectCalls = mutableListOf<ObservedCredentials>()
        var disconnectBehavior: suspend () -> Unit = {}
        var disconnectCount = 0
        var maximumConcurrentConnects = 0
        var maximumConcurrentTransportOperations = 0
        private var activeConnects = 0
        private var activeTransportOperations = 0

        override fun currentConnectionState(): ConnectionState = currentState

        override suspend fun connect(
            host: String,
            port: Int,
            username: String?,
            password: String?,
            clientName: String,
            clientVersion: String,
            htspVersion: Int,
            connectTimeoutMs: Int,
            responseTimeoutMs: Long,
            soTimeoutMs: Int,
            socketBufferBytes: Int,
            forceReconnect: Boolean,
        ) {
            val credentials = ObservedCredentials(
                username = username.orEmpty(),
                password = password.orEmpty(),
            )
            connectCalls += credentials
            activeConnects++
            activeTransportOperations++
            maximumConcurrentConnects = maxOf(maximumConcurrentConnects, activeConnects)
            maximumConcurrentTransportOperations = maxOf(
                maximumConcurrentTransportOperations,
                activeTransportOperations,
            )
            try {
                connectBehavior(credentials)
                currentState = ConnectionState.Connected(host, port, htspVersion)
            } finally {
                activeConnects--
                activeTransportOperations--
            }
        }

        override suspend fun disconnect() {
            disconnectCount++
            activeTransportOperations++
            maximumConcurrentTransportOperations = maxOf(
                maximumConcurrentTransportOperations,
                activeTransportOperations,
            )
            try {
                disconnectBehavior()
                currentState = ConnectionState.Disconnected
            } finally {
                activeTransportOperations--
            }
        }

        override suspend fun enableAsyncMetadataAndWaitInitialSync(timeoutMs: Long) {
            controlledEvents.emit(
                HtspEvent.ServerMessage(
                    msg = HtspMessage(
                        method = "initialSyncCompleted",
                        seq = null,
                        fields = emptyMap(),
                    ),
                ),
            )
        }

        override suspend fun close() = Unit
    }

    private companion object {
        const val HOST = "example.invalid"
        const val PORT = 9982
        const val OLD_USERNAME = "credential-a"
        const val OLD_PASSWORD = "secret-a"
        const val NEW_USERNAME = "credential-b"
        const val NEW_PASSWORD = "secret-b"
        val CONNECTION = TvheadendConnection(
            host = HOST,
            port = PORT,
            username = OLD_USERNAME,
            password = OLD_PASSWORD,
        )
    }
}
