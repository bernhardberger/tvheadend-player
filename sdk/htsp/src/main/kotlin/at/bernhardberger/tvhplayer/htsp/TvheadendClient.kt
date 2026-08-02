package at.bernhardberger.tvhplayer.htsp

import at.bernhardberger.tvhplayer.core.ConnectionAttemptState
import at.bernhardberger.tvhplayer.core.ConnectionPolicy
import at.bernhardberger.tvhplayer.core.ConnectionProbeResult
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrActionResult
import at.bernhardberger.tvhplayer.core.RecordingWriteCapability
import at.bernhardberger.tvhplayer.core.SubscriptionFailureKind
import at.bernhardberger.tvhplayer.core.SubscriptionFailureTrackerState
import at.bernhardberger.tvhplayer.core.beginConnectionAttempt
import at.bernhardberger.tvhplayer.core.connectionAttemptMayPublish
import at.bernhardberger.tvhplayer.core.connectionAttemptState
import at.bernhardberger.tvhplayer.core.connectionFailureKind
import at.bernhardberger.tvhplayer.core.invalidateConnectionAttempts
import at.bernhardberger.tvhplayer.core.removeSubscriptionFailure
import at.bernhardberger.tvhplayer.core.updateSubscriptionFailure
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.repositories.EpgRuntimeTimings
import at.bernhardberger.tvhplayer.repositories.TvhRepository
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TvheadendConnection(
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    override fun toString(): String =
        "TvheadendConnection(host=$host, port=$port, username=$username, password=<redacted>)"
}

data class TvheadendClientTimings(
    val reconnectDelayMs: Long = 5_000L,
    val connectTimeoutMs: Int = 10_000,
    val responseTimeoutMs: Long = 5_000L,
    val metadataTimeoutMs: Long = 30_000L,
)

class HtspFileTooLargeException(
    val maximumBytes: Int,
) : IllegalStateException("HTSP file exceeds the configured maximum size")

/**
 * Frontend-facing owner for one TVHeadend connection and its reusable metadata.
 * Credentials are retained in memory only for reconnect and are never persisted or logged.
 */
class TvheadendClient internal constructor(
    private val ioDispatcher: CoroutineDispatcher,
    clientIdentity: HtspClientIdentity,
    private val logger: HtspLogger,
    private val timings: TvheadendClientTimings,
    epgTimings: EpgRuntimeTimings,
    epochSeconds: () -> Long,
    service: HtspService?,
) : ChannelEpgRuntime, DvrRuntime {
    constructor(
        ioDispatcher: CoroutineDispatcher,
        clientIdentity: HtspClientIdentity = HtspClientIdentity.Default,
        logger: HtspLogger = HtspLogger.None,
        timings: TvheadendClientTimings = TvheadendClientTimings(),
        epochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    ) : this(
        ioDispatcher = ioDispatcher,
        clientIdentity = clientIdentity,
        logger = logger,
        timings = timings,
        epgTimings = EpgRuntimeTimings(),
        epochSeconds = epochSeconds,
        service = null,
    )

    internal constructor(
        ioDispatcher: CoroutineDispatcher,
        service: HtspService,
    ) : this(
        ioDispatcher = ioDispatcher,
        clientIdentity = HtspClientIdentity.Default,
        logger = HtspLogger.None,
        timings = TvheadendClientTimings(),
        epgTimings = EpgRuntimeTimings(),
        epochSeconds = { System.currentTimeMillis() / 1_000L },
        service = service,
    )

    private val service = service ?: HtspService(
        ioDispatcher = ioDispatcher,
        clientIdentity = clientIdentity,
        logger = logger,
    )
    private val connectionProbe = HtspConnectionProbe(
        ioDispatcher = ioDispatcher,
        clientIdentity = clientIdentity,
        logger = logger,
    )
    private val channelRepository = TvhRepository(
        htsp = this.service,
        ioDispatcher = ioDispatcher,
        logger = logger,
        timings = epgTimings,
        epochSeconds = epochSeconds,
    )
    private val dvrRepository = DvrRepository(
        htsp = this.service,
        ioDispatcher = ioDispatcher,
        logger = logger,
    )

    private val runtimeJob = SupervisorJob()
    private val scope = CoroutineScope(runtimeJob + ioDispatcher)
    private val lifecycle = TerminalLifecycleGate("TvheadendClient is closed")
    private var started = false
    private val connectionMutex = Mutex()
    private val connectionAttemptLock = Any()
    private var connectionAttempts = ConnectionAttemptState()
    private var activeConnection: TvheadendConnection? = null
    private var transportMayBeReused = true
    private val reconnectJobs = AttemptOwnedJobSlot(connectionAttemptLock) { attemptId ->
        connectionAttemptMayPublish(connectionAttempts, attemptId)
    }
    private val subscriptionStatusLock = Any()
    private var subscriptionFailureState = SubscriptionFailureTrackerState()

    val connectionState: StateFlow<ConnectionState> = this.service.state
    private val _frontendState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Connecting)
    val frontendState: StateFlow<ConnectionUiState> = _frontendState

    override val channelsUi: StateFlow<List<ChannelUi>> = channelRepository.channelsUi
    override val tagsUi: StateFlow<List<ChannelTagUi>> = channelRepository.tagsUi
    override val metadataReady: StateFlow<Boolean> = channelRepository.metadataReady
    override val entries: StateFlow<List<DvrEntry>> = dvrRepository.entries
    override val entriesReady: StateFlow<Boolean> = dvrRepository.entriesReady
    override val configs: StateFlow<List<DvrConfig>> = dvrRepository.configs
    override val writeCapability: StateFlow<RecordingWriteCapability> =
        dvrRepository.writeCapability
    override val canModifyRecordings: StateFlow<Boolean> = dvrRepository.canModifyRecordings
    override val progressCapability: StateFlow<RecordingProgressCapability> =
        dvrRepository.progressCapability

    val channels: StateFlow<List<ChannelUi>> = channelsUi
    val tags: StateFlow<List<ChannelTagUi>> = tagsUi
    val dvrEntries: StateFlow<List<DvrEntry>> = entries
    val dvrEntriesReady: StateFlow<Boolean> = entriesReady
    val dvrConfigurations: StateFlow<List<DvrConfig>> = configs
    val dvrWriteCapability: StateFlow<RecordingWriteCapability> = writeCapability
    val recordingProgressCapability: StateFlow<RecordingProgressCapability> = progressCapability

    /** Playback-only transport SPI. Frontends should not issue raw HTSP operations. */
    @PlaybackIntegrationApi
    val playbackTransport: PlaybackHtspTransport = this.service

    suspend fun connect(
        connection: TvheadendConnection,
        reuseMatchingConnection: Boolean = true,
        preservePublishedMetadata: Boolean = true,
    ): Boolean {
        ensureStarted()
        validateConnection(connection)
        val handoff = beginClientConnectionAttempt(connection)
        handoff.reconnectJob?.cancelAndJoin()
        val attemptId = handoff.attemptId
        publishConnectionState(
            attemptId,
            connectionAttemptState(
                hasPublishedChannels = preservePublishedMetadata && channels.value.isNotEmpty(),
            ),
        )
        val connected = connectInternal(
            attemptId = attemptId,
            connection = connection,
            reuseMatchingConnection = reuseMatchingConnection && handoff.mayReuseExistingTransport,
            preservePublishedMetadata = preservePublishedMetadata,
        )
        if (!connected) {
            scheduleReconnect(
                attemptId = attemptId,
                connection = connection,
                preservePublishedMetadata = preservePublishedMetadata,
                delayBeforeFirstAttempt = true,
            )
        }
        return connected
    }

    suspend fun reconnect(
        connection: TvheadendConnection? = null,
        preservePublishedMetadata: Boolean = true,
    ): Boolean {
        ensureStarted()
        val target = connection ?: synchronized(connectionAttemptLock) { activeConnection }
            ?: return false
        validateConnection(target)
        val handoff = beginClientConnectionAttempt(target)
        handoff.reconnectJob?.cancelAndJoin()
        val attemptId = handoff.attemptId
        publishConnectionState(attemptId, ConnectionUiState.Reconnecting)
        val connected = connectInternal(
            attemptId = attemptId,
            connection = target,
            reuseMatchingConnection = false,
            preservePublishedMetadata = preservePublishedMetadata,
        )
        if (!connected) {
            scheduleReconnect(
                attemptId = attemptId,
                connection = target,
                preservePublishedMetadata = preservePublishedMetadata,
                delayBeforeFirstAttempt = true,
            )
        }
        return connected
    }

    suspend fun invalidateConnection(preservePublishedMetadata: Boolean = true) {
        val handoff = lifecycle.admit { invalidateClientConnectionAttempt() }
        withContext(NonCancellable) {
            handoff.reconnectJob?.cancelAndJoin()
            connectionMutex.withLock {
                if (!isCurrentClientConnectionAttempt(handoff.attemptId)) return@withLock
                channelRepository.onDisconnected()
                dvrRepository.onNewConnectionStarting(
                    preservePublished = preservePublishedMetadata,
                    attemptId = handoff.attemptId,
                )
                service.disconnect()
                synchronized(connectionAttemptLock) {
                    if (connectionAttemptMayPublish(connectionAttempts, handoff.attemptId)) {
                        _frontendState.value = connectionAttemptState(
                            hasPublishedChannels =
                                preservePublishedMetadata && channels.value.isNotEmpty(),
                        )
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
    }

    suspend fun disconnect(preservePublishedMetadata: Boolean = true) =
        invalidateConnection(preservePublishedMetadata)

    suspend fun testConnection(connection: TvheadendConnection): ConnectionProbeResult {
        checkOpen()
        validateConnection(connection)
        return connectionProbe.test(
            host = connection.host,
            port = connection.port,
            username = connection.username,
            password = connection.password,
        )
    }

    override fun epgForChannel(channelId: Int): StateFlow<List<EpgEventEntry>> =
        channelRepository.epgForChannel(channelId)

    override fun nowEvent(channelId: Int, nowSec: Long): EpgEventEntry? =
        channelRepository.nowEvent(channelId, nowSec)

    override fun nextEvent(channelId: Int, nowSec: Long): EpgEventEntry? =
        channelRepository.nextEvent(channelId, nowSec)

    override fun requestEpgAtFrontier(channelIds: List<Int>, anchorSec: Long) {
        checkOpen()
        channelRepository.requestEpgAtFrontier(channelIds, anchorSec)
    }

    override fun entryForEvent(eventId: Int): DvrEntry? = dvrRepository.entryForEvent(eventId)

    fun dvrEntryForEvent(eventId: Int): DvrEntry? = entryForEvent(eventId)

    override suspend fun refreshConfigs() {
        checkOpen()
        dvrRepository.refreshConfigs()
    }

    suspend fun refreshDvrConfigurations() = refreshConfigs()

    override suspend fun scheduleEvent(
        eventId: Int,
        configName: String?,
    ): DvrActionResult {
        checkOpen()
        return dvrRepository.scheduleEvent(eventId, configName)
    }

    suspend fun scheduleRecording(eventId: Int, configName: String? = null): DvrActionResult {
        return scheduleEvent(eventId, configName)
    }

    override suspend fun cancelEntry(entryId: Int): DvrActionResult {
        checkOpen()
        return dvrRepository.cancelEntry(entryId)
    }

    suspend fun cancelRecording(entryId: Int): DvrActionResult {
        return cancelEntry(entryId)
    }

    override suspend fun deleteEntry(entryId: Int): DvrActionResult {
        checkOpen()
        return dvrRepository.deleteEntry(entryId)
    }

    suspend fun deleteRecording(entryId: Int): DvrActionResult {
        return deleteEntry(entryId)
    }

    override suspend fun updateRecordingProgress(
        entryId: Int,
        playPositionSeconds: Long,
        setWatched: Boolean,
        timeoutMs: Long,
    ): RecordingProgressUpdateResult {
        checkOpen()
        return dvrRepository.updateRecordingProgress(
            entryId = entryId,
            playPositionSeconds = playPositionSeconds,
            setWatched = setWatched,
            timeoutMs = timeoutMs,
        )
    }

    suspend fun discoverProfiles(): List<ProfileItem> = withContext(ioDispatcher) {
        checkOpen()
        val attemptId = connectedAttemptId()
        val message = service.requestForConnectionAttempt(
            expectedConnectionAttemptId = attemptId,
            method = "getProfiles",
            fields = emptyMap(),
            timeoutMs = 5_000L,
            flush = true,
            disconnectOnTimeout = false,
        )
        message.list("profiles")
            .orEmpty()
            .mapNotNull(::profileItem)
            .sortedBy { profile -> profile.name.lowercase() }
    }

    suspend fun readFileBytes(
        path: String,
        maxBytes: Int = DEFAULT_MAX_FILE_BYTES,
        chunkBytes: Int = DEFAULT_FILE_CHUNK_BYTES,
    ): ByteArray {
        checkOpen()
        require(maxBytes in 1..MAX_FILE_BYTES) {
            "maxBytes must be between 1 and $MAX_FILE_BYTES"
        }
        require(chunkBytes > 0) { "chunkBytes must be positive" }
        val attemptId = connectedAttemptId()
        val handle = service.fileOpen(
            path = path,
            timeoutMs = 3_000L,
            expectedConnectionAttemptId = attemptId,
        )
        try {
            val output = ByteArrayOutputStream(minOf(maxBytes, chunkBytes))
            while (true) {
                currentCoroutineContext().ensureActive()
                val remainingWithOverflowByte = maxBytes - output.size() + 1
                val chunk = service.fileRead(
                    id = handle,
                    size = minOf(chunkBytes, remainingWithOverflowByte),
                    timeoutMs = 3_000L,
                    expectedConnectionAttemptId = attemptId,
                )
                if (chunk.isEmpty()) return output.toByteArray()
                if (chunk.size > maxBytes - output.size()) {
                    throw HtspFileTooLargeException(maxBytes)
                }
                output.write(chunk)
            }
        } finally {
            withContext(NonCancellable) {
                try {
                    service.fileClose(
                        id = handle,
                        timeoutMs = 1_500L,
                        expectedConnectionAttemptId = attemptId,
                    )
                } catch (staleAttempt: CancellationException) {
                    // A replaced transport cannot safely close an old generation's handle.
                    logger.log(
                        HtspLogLevel.WARNING,
                        "HTSP file handle belonged to a replaced connection",
                        null,
                    )
                } catch (error: Exception) {
                    logger.log(HtspLogLevel.WARNING, "HTSP file handle close failed", error)
                }
            }
        }
    }

    suspend fun close() {
        val handoff = lifecycle.close {
            ClientCloseHandoff(
                connection = invalidateClientConnectionAttempt(),
                serviceAttemptId = service.beginClose(),
            )
        } ?: return
        withContext(NonCancellable) {
            handoff.connection.reconnectJob?.cancelAndJoin()
            runtimeJob.cancelAndJoin()
            try {
                handoff.serviceAttemptId?.let { service.finishClose(it) }
            } finally {
                connectionMutex.withLock {
                    try {
                        channelRepository.close()
                    } finally {
                        dvrRepository.close()
                    }
                }
            }
        }
    }

    private fun ensureStarted() {
        lifecycle.admit {
            if (started) return@admit
            started = true
            channelRepository.startIfNeeded()
            dvrRepository.startIfNeeded()
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                service.controlEvents.collect { event ->
                    when (event) {
                        is HtspEvent.ConnectionError -> handleConnectionError(event)
                        is HtspEvent.ServerMessage -> handleSubscriptionMessage(event)
                    }
                }
            }
        }
    }

    private suspend fun handleConnectionError(event: HtspEvent.ConnectionError) {
        if (!channelRepository.onDisconnected(event.connectionAttemptId)) return
        val reconnect = service.commitIfCurrentConnectionAttempt(event.connectionAttemptId) {
            _frontendState.value = ConnectionUiState.Reconnecting
            synchronized(connectionAttemptLock) {
                val attemptId = connectionAttempts.currentAttemptId
                activeConnection?.let { connection -> attemptId to connection }
            }
        } ?: return
        scheduleReconnect(
            attemptId = reconnect.first,
            connection = reconnect.second,
            preservePublishedMetadata = true,
            delayBeforeFirstAttempt = false,
        )
    }

    private fun handleSubscriptionMessage(event: HtspEvent.ServerMessage) {
        val message = event.msg
        message.toSubscriptionStatusOrNull()?.let { status ->
            service.commitIfCurrentConnectionAttempt(event.connectionAttemptId) {
                val failure = synchronized(subscriptionStatusLock) {
                    subscriptionFailureState = updateSubscriptionFailure(
                        state = subscriptionFailureState,
                        subscriptionId = status.id,
                        subscriptionError = status.subscriptionError,
                        status = status.state,
                    )
                    subscriptionFailureState.currentFailure
                }
                publishSubscriptionFailure(failure)
            }
            return
        }
        message.subscriptionStopIdOrNull()?.let { subscriptionId ->
            service.commitIfCurrentConnectionAttempt(event.connectionAttemptId) {
                val failure = synchronized(subscriptionStatusLock) {
                    subscriptionFailureState = removeSubscriptionFailure(
                        subscriptionFailureState,
                        subscriptionId,
                    )
                    subscriptionFailureState.currentFailure
                }
                publishSubscriptionFailure(failure)
            }
        }
    }

    private fun publishSubscriptionFailure(failure: SubscriptionFailureKind?) {
        if (failure == null) {
            if (_frontendState.value is ConnectionUiState.SubscriptionError) {
                _frontendState.value = ConnectionUiState.Ready
            }
        } else {
            _frontendState.value = ConnectionUiState.SubscriptionError(failure)
        }
    }

    private fun scheduleReconnect(
        attemptId: Long,
        connection: TvheadendConnection,
        preservePublishedMetadata: Boolean,
        delayBeforeFirstAttempt: Boolean,
    ) {
        val candidate = scope.launch(start = CoroutineStart.LAZY) {
            var delayBeforeAttempt = delayBeforeFirstAttempt
            while (isActive && isCurrentClientConnectionAttempt(attemptId)) {
                if (delayBeforeAttempt) delay(timings.reconnectDelayMs)
                delayBeforeAttempt = true
                if (
                    connectInternal(
                        attemptId = attemptId,
                        connection = connection,
                        reuseMatchingConnection = false,
                        preservePublishedMetadata = preservePublishedMetadata,
                    )
                ) {
                    return@launch
                }
            }
        }
        val replacement = reconnectJobs.replaceIfCurrent(attemptId, candidate)
        if (!replacement.accepted) {
            candidate.cancel()
            return
        }
        candidate.invokeOnCompletion {
            reconnectJobs.clearIfSame(candidate)
        }
        replacement.previous?.cancel()
        candidate.start()
    }

    private suspend fun connectInternal(
        attemptId: Long,
        connection: TvheadendConnection,
        reuseMatchingConnection: Boolean,
        preservePublishedMetadata: Boolean,
    ): Boolean = connectionMutex.withLock {
        try {
            ensureCurrentClientConnectionAttempt(attemptId)
            val connected = service.currentConnectionState() as? ConnectionState.Connected
            if (
                reuseMatchingConnection &&
                ConnectionPolicy.isSameEndpoint(
                    connectedHost = connected?.host,
                    connectedPort = connected?.port,
                    requestedHost = connection.host,
                    requestedPort = connection.port,
                )
            ) {
                channelRepository.bindConnectionAttempt(
                    repositoryAttemptId = attemptId,
                    transportAttemptId = service.currentConnectionAttemptId(),
                )
                runForCurrentClientConnectionAttempt(attemptId) {
                    transportMayBeReused = true
                    _frontendState.value = ConnectionUiState.Ready
                }
                return@withLock true
            }

            channelRepository.onNewConnectionStarting(
                preservePublishedChannels = preservePublishedMetadata,
                attemptId = attemptId,
            )
            ensureCurrentClientConnectionAttempt(attemptId)
            dvrRepository.onNewConnectionStarting(
                preservePublished = preservePublishedMetadata,
                attemptId = attemptId,
            )
            ensureCurrentClientConnectionAttempt(attemptId)

            service.connect(
                host = connection.host,
                port = connection.port,
                username = connection.username,
                password = connection.password,
                forceReconnect = true,
                connectTimeoutMs = timings.connectTimeoutMs,
                responseTimeoutMs = timings.responseTimeoutMs,
            )
            ensureCurrentClientConnectionAttempt(attemptId)
            channelRepository.bindConnectionAttempt(
                repositoryAttemptId = attemptId,
                transportAttemptId = service.currentConnectionAttemptId(),
            )
            val connectedAfterAuth = service.currentConnectionState() as? ConnectionState.Connected
            dvrRepository.applyAuthenticatedDvrAccess(
                dvrAccess = connectedAfterAuth?.dvrAccess,
                attemptId = attemptId,
            )

            publishConnectionState(attemptId, ConnectionUiState.SyncingChannels)
            service.enableAsyncMetadataAndWaitInitialSync(timings.metadataTimeoutMs)
            ensureCurrentClientConnectionAttempt(attemptId)
            channelRepository.awaitChannelsReady(timings.metadataTimeoutMs)
            ensureCurrentClientConnectionAttempt(attemptId)
            try {
                dvrRepository.refreshConfigsForAttempt(attemptId = attemptId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.log(HtspLogLevel.WARNING, "DVR configurations unavailable", error)
            }
            runForCurrentClientConnectionAttempt(attemptId) {
                channelRepository.startEpgWorker()
                transportMayBeReused = true
                _frontendState.value = ConnectionUiState.Ready
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ensureCurrentClientConnectionAttempt(attemptId)
            logger.log(HtspLogLevel.ERROR, "TVHeadend connection failed", null)
            publishConnectionState(
                attemptId,
                ConnectionUiState.Error(connectionFailureKind(error)),
            )
            false
        }
    }

    private fun beginClientConnectionAttempt(
        connection: TvheadendConnection,
    ): ClientConnectionAttemptHandoff = lifecycle.admit {
        var mayReuseExistingTransport = false
        val handoff = reconnectJobs.updateAttemptAndDetach {
            mayReuseExistingTransport = transportMayBeReused
            beginConnectionAttempt(connectionAttempts).also { attempt ->
                connectionAttempts = attempt.state
                activeConnection = connection
                synchronized(subscriptionStatusLock) {
                    subscriptionFailureState = SubscriptionFailureTrackerState()
                }
            }.attemptId
        }
        advanceRepositoryConnectionAttempt(handoff.value)
        ClientConnectionAttemptHandoff(
            attemptId = handoff.value,
            reconnectJob = handoff.previous,
            mayReuseExistingTransport = mayReuseExistingTransport,
        )
    }

    private fun invalidateClientConnectionAttempt(): ClientConnectionAttemptHandoff {
        val handoff = reconnectJobs.updateAttemptAndDetach {
            activeConnection = null
            transportMayBeReused = false
            connectionAttempts = invalidateConnectionAttempts(connectionAttempts)
            connectionAttempts.currentAttemptId
        }
        advanceRepositoryConnectionAttempt(handoff.value)
        return ClientConnectionAttemptHandoff(
            attemptId = handoff.value,
            reconnectJob = handoff.previous,
            mayReuseExistingTransport = false,
        )
    }

    private fun advanceRepositoryConnectionAttempt(attemptId: Long) {
        channelRepository.advanceConnectionAttempt(attemptId)
        dvrRepository.advanceConnectionAttempt(attemptId)
    }

    private fun ensureCurrentClientConnectionAttempt(attemptId: Long) {
        if (!isCurrentClientConnectionAttempt(attemptId)) {
            throw CancellationException("Superseded connection attempt")
        }
    }

    private fun isCurrentClientConnectionAttempt(attemptId: Long): Boolean =
        synchronized(connectionAttemptLock) {
            connectionAttemptMayPublish(connectionAttempts, attemptId)
        }

    private fun publishConnectionState(attemptId: Long, state: ConnectionUiState) {
        runForCurrentClientConnectionAttempt(attemptId) { _frontendState.value = state }
    }

    private fun runForCurrentClientConnectionAttempt(attemptId: Long, block: () -> Unit) {
        synchronized(connectionAttemptLock) {
            if (!connectionAttemptMayPublish(connectionAttempts, attemptId)) {
                throw CancellationException("Superseded connection attempt")
            }
            block()
        }
    }

    private fun connectedAttemptId(): Long {
        check(service.currentConnectionState() is ConnectionState.Connected) {
            "TVHeadend is not connected"
        }
        return service.currentConnectionAttemptId()
    }

    private fun validateConnection(connection: TvheadendConnection) {
        require(ConnectionPolicy.isAutoConnectReady(connection.host, connection.port)) {
            "A non-blank host and non-zero port are required"
        }
    }

    private fun checkOpen() {
        lifecycle.checkOpen()
    }

    private companion object {
        const val DEFAULT_FILE_CHUNK_BYTES = 64 * 1_024
        const val DEFAULT_MAX_FILE_BYTES = 4 * 1_024 * 1_024
        const val MAX_FILE_BYTES = 16 * 1_024 * 1_024
    }

    private data class ClientConnectionAttemptHandoff(
        val attemptId: Long,
        val reconnectJob: kotlinx.coroutines.Job?,
        val mayReuseExistingTransport: Boolean,
    )

    private data class ClientCloseHandoff(
        val connection: ClientConnectionAttemptHandoff,
        val serviceAttemptId: Long?,
    )
}

private fun HtspMessage.toSubscriptionStatusOrNull(): SubscriptionStatus? {
    val messageMethod = method ?: return null
    if (messageMethod != "subscriptionStatus" && messageMethod != "subscriptionStart") return null
    val id = int("subscriptionId") ?: int("id") ?: return null
    return SubscriptionStatus(
        id = id,
        state = str("state") ?: str("status"),
        subscriptionError = str("subscriptionError") ?: str("error"),
    )
}

private fun HtspMessage.subscriptionStopIdOrNull(): Int? {
    if (method != "subscriptionStop") return null
    return int("subscriptionId") ?: int("id")
}

private fun profileItem(value: Any?): ProfileItem? {
    val fields = value as? Map<*, *> ?: return null
    val id = fields["uuid"] as? String ?: return null
    val name = fields["name"] as? String ?: "Profile $id"
    return ProfileItem(id = id, name = name)
}
