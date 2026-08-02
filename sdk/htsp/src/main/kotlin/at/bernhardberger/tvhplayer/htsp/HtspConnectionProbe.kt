package at.bernhardberger.tvhplayer.htsp

import at.bernhardberger.tvhplayer.core.ConnectionProbeResult
import at.bernhardberger.tvhplayer.core.ConnectionProbeSession
import at.bernhardberger.tvhplayer.core.runConnectionProbe
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal class HtspConnectionProbe(
    private val ioDispatcher: CoroutineDispatcher,
    private val clientIdentity: HtspClientIdentity = HtspClientIdentity.Default,
    private val logger: HtspLogger = HtspLogger.None,
) {
    suspend fun test(
        host: String,
        port: Int,
        username: String,
        password: String,
    ): ConnectionProbeResult {
        val service = HtspService(
            ioDispatcher = ioDispatcher,
            clientIdentity = clientIdentity,
            logger = logger,
        )
        return runConnectionProbe(
            HtspProbeSession(
                service = service,
                host = host,
                port = port,
                username = username,
                password = password,
                ioDispatcher = ioDispatcher,
            )
        )
    }

    private class HtspProbeSession(
        private val service: HtspService,
        private val host: String,
        private val port: Int,
        private val username: String,
        private val password: String,
        private val ioDispatcher: CoroutineDispatcher,
    ) : ConnectionProbeSession {
        override suspend fun connect(): Int {
            service.connect(
                host = host,
                port = port,
                username = username,
                password = password,
                forceReconnect = true,
                connectTimeoutMs = 10_000,
                responseTimeoutMs = 5_000,
            )
            return (service.state.value as? ConnectionState.Connected)?.htspVersion ?: 0
        }

        override suspend fun syncChannelMetadata(): Int = coroutineScope {
            val channelIds = ConcurrentHashMap.newKeySet<Int>()
            val completed = CompletableDeferred<Int>()
            val collector = launch(ioDispatcher, start = CoroutineStart.UNDISPATCHED) {
                service.controlEvents.collect { event ->
                    val message = (event as? HtspEvent.ServerMessage)?.msg ?: return@collect
                    when (message.method) {
                        "channelAdd", "channelUpdate" ->
                            message.int("channelId")?.let(channelIds::add)
                        "channelDelete" -> message.int("channelId")?.let(channelIds::remove)
                        "initialSyncCompleted" -> completed.complete(channelIds.size)
                    }
                }
            }

            try {
                service.enableAsyncMetadataAndWaitInitialSync()
                withTimeout(30_000) { completed.await() }
            } finally {
                collector.cancelAndJoin()
            }
        }

        override suspend fun close() {
            service.close()
        }
    }
}
