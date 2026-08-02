package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.core.DvrActionFailure
import at.bernhardberger.tvhplayer.core.DvrActionResult
import at.bernhardberger.tvhplayer.core.RecordingWriteCapability
import at.bernhardberger.tvhplayer.core.dvrActionFailure
import at.bernhardberger.tvhplayer.htsp.DvrConfig
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrRuntime
import at.bernhardberger.tvhplayer.htsp.DvrMetadataRepository
import at.bernhardberger.tvhplayer.htsp.ConnectionState
import at.bernhardberger.tvhplayer.htsp.HtspEvent
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspRequestTimeoutException
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.htsp.HtspLogLevel
import at.bernhardberger.tvhplayer.htsp.HtspLogger
import at.bernhardberger.tvhplayer.htsp.RecordingProgressCapability
import at.bernhardberger.tvhplayer.htsp.RecordingProgressRequest
import at.bernhardberger.tvhplayer.htsp.RecordingProgressUpdateResult
import at.bernhardberger.tvhplayer.htsp.recordingProgressCapability
import at.bernhardberger.tvhplayer.htsp.recordingProgressReplyResult
import at.bernhardberger.tvhplayer.htsp.recordingProgressRequest
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DvrRepository(
    private val htsp: HtspService,
    ioDispatcher: CoroutineDispatcher,
    private val logger: HtspLogger = HtspLogger.None,
) : DvrRuntime {
    private val runtimeJob = SupervisorJob()
    private val scope = CoroutineScope(runtimeJob + ioDispatcher)
    private val lifecycleLock = Any()
    private var started = false
    private var closed = false
    private val mutex = Mutex()
    private val metadata = DvrMetadataRepository()
    override val entries: StateFlow<List<DvrEntry>> = metadata.entries
    override val entriesReady: StateFlow<Boolean> = metadata.entriesReady
    override val configs: StateFlow<List<DvrConfig>> = metadata.configs
    /**
     * Three-state DVR write capability for the current HTSP session.
     * Starts [RecordingWriteCapability.Unknown] so write UI stays hidden until a
     * positive probe (auth `dvr` and/or `getDvrConfigs` / write RPC).
     */
    private val _writeCapability =
        MutableStateFlow(RecordingWriteCapability.Unknown)
    override val writeCapability: StateFlow<RecordingWriteCapability> = _writeCapability
    /** Convenience for UI: true only when [writeCapability] is [RecordingWriteCapability.Allowed]. */
    private val _canModifyRecordings = MutableStateFlow(false)
    override val canModifyRecordings: StateFlow<Boolean> = _canModifyRecordings
    private val _progressCapability = MutableStateFlow(
        recordingProgressCapability(htsp.currentConnectionState())
    )
    override val progressCapability: StateFlow<RecordingProgressCapability> = _progressCapability
    @Volatile
    private var progressMethodUnsupported = false
    private val connectionAttemptLock = Any()
    @Volatile
    private var latestConnectionAttemptId = 0L

    fun startIfNeeded() {
        synchronized(lifecycleLock) {
            check(!closed) { "DvrRepository is closed" }
            if (started) return
            started = true
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            htsp.controlEvents.collect { event ->
                if (event is HtspEvent.ServerMessage) {
                    acceptDvrMessage(
                        message = event.msg,
                        connectionAttemptId = event.connectionAttemptId,
                    )
                }
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            htsp.state.collect { state ->
                if (state !is ConnectionState.Connected) progressMethodUnsupported = false
                _progressCapability.value = if (
                    progressMethodUnsupported && state is ConnectionState.Connected
                ) {
                    RecordingProgressCapability.Unsupported
                } else {
                    recordingProgressCapability(state)
                }
            }
        }
    }

    suspend fun close() {
        val shouldClose = synchronized(lifecycleLock) {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }
        if (!shouldClose) return
        runtimeJob.cancelAndJoin()
    }

    suspend fun onNewConnectionStarting(
        preservePublished: Boolean,
        attemptId: Long? = null,
    ) {
        mutex.withLock {
            synchronized(connectionAttemptLock) {
                if (attemptId != null && attemptId < latestConnectionAttemptId) {
                    return@synchronized
                }
                if (attemptId != null) latestConnectionAttemptId = attemptId
                metadata.reset(preservePublished)
                // Hide write actions until auth / getDvrConfigs prove access.
                setWriteCapability(RecordingWriteCapability.Unknown)
            }
        }
    }

    fun advanceConnectionAttempt(attemptId: Long) {
        synchronized(connectionAttemptLock) {
            if (attemptId > latestConnectionAttemptId) latestConnectionAttemptId = attemptId
        }
    }

    /**
     * Apply ACCESS_HTSP_RECORDER from the authenticate reply (HTSP ≥ 26).
     * true → [Allowed], false → [Denied], null → leave [Unknown].
     */
    fun applyAuthenticatedDvrAccess(dvrAccess: Boolean?) {
        when (dvrAccess) {
            true -> setWriteCapability(RecordingWriteCapability.Allowed)
            false -> setWriteCapability(RecordingWriteCapability.Denied)
            null -> Unit
        }
    }

    suspend fun applyAuthenticatedDvrAccess(dvrAccess: Boolean?, attemptId: Long) {
        mutex.withLock {
            synchronized(connectionAttemptLock) {
                if (attemptId == latestConnectionAttemptId) {
                    applyAuthenticatedDvrAccess(dvrAccess)
                }
            }
        }
    }

    override suspend fun refreshConfigs() {
        refreshConfigsForAttempt(attemptId = null)
    }

    internal suspend fun refreshConfigsForAttempt(attemptId: Long?) {
        val reply = htsp.request(
            method = "getDvrConfigs",
            fields = emptyMap(),
            timeoutMs = 10_000,
            disconnectOnTimeout = false,
        )
        mutex.withLock {
            synchronized(connectionAttemptLock) {
                if (attemptId != null && attemptId != latestConnectionAttemptId) {
                    return@synchronized
                }
                when (val failure = dvrActionFailure(reply.fields)) {
                    // getDvrConfigs is gated on ACCESS_HTSP_RECORDER, so a bare noaccess=1
                    // is authoritative for every DVR write method too.
                    DvrActionFailure.PERMISSION_DENIED -> {
                        setWriteCapability(RecordingWriteCapability.Denied)
                        metadata.clearConfigs()
                    }
                    null -> {
                        setWriteCapability(RecordingWriteCapability.Allowed)
                        metadata.ingestDvrConfigsReply(reply)
                    }
                    // Any other error (unknown method on old servers, connection limit,
                    // malformed reply) proves nothing either way: leave the capability alone
                    // rather than reading it as access.
                    else -> logger.log(
                        HtspLogLevel.WARNING,
                        "getDvrConfigs failed; write capability unchanged: $failure",
                        null,
                    )
                }
            }
        }
    }

    internal suspend fun acceptDvrMessage(
        message: HtspMessage,
        connectionAttemptId: Long = 0L,
    ) {
        mutex.withLock {
            htsp.commitIfCurrentConnectionAttempt(connectionAttemptId) {
                metadata.accept(message)
            }
        }
    }

    override fun entryForEvent(eventId: Int): DvrEntry? =
        metadata.entryForEvent(eventId)

    /**
     * Schedule a recording for an EPG event.
     * @param configName DVR profile name/uuid (`configName` in HTSP `addDvrEntry`).
     */
    override suspend fun scheduleEvent(eventId: Int, configName: String?): DvrActionResult =
        performAction(
            method = "addDvrEntry",
            fields = buildMap {
                put("eventId", eventId)
                if (configName != null) put("configName", configName)
            },
        )

    override suspend fun cancelEntry(entryId: Int): DvrActionResult = performAction(
        method = "cancelDvrEntry",
        fields = mapOf("id" to entryId),
    )

    override suspend fun deleteEntry(entryId: Int): DvrActionResult = performAction(
        method = "deleteDvrEntry",
        fields = mapOf("id" to entryId),
    )

    override suspend fun updateRecordingProgress(
        entryId: Int,
        playPositionSeconds: Long,
        setWatched: Boolean,
        timeoutMs: Long,
    ): RecordingProgressUpdateResult {
        val request = recordingProgressRequest(
            entryId = entryId,
            playPositionSeconds = playPositionSeconds,
            setWatched = setWatched,
        ) ?: return RecordingProgressUpdateResult.Rejected
        val connectionState = htsp.currentConnectionState()
        val capability = if (
            progressMethodUnsupported && connectionState is ConnectionState.Connected
        ) {
            RecordingProgressCapability.Unsupported
        } else {
            recordingProgressCapability(connectionState)
        }
        return when (capability) {
            RecordingProgressCapability.Disconnected ->
                RecordingProgressUpdateResult.Disconnected
            RecordingProgressCapability.Unsupported ->
                RecordingProgressUpdateResult.Unsupported
            RecordingProgressCapability.ReadOnly ->
                RecordingProgressUpdateResult.PermissionDenied
            RecordingProgressCapability.Full -> performProgressUpdate(
                request = request,
                timeoutMs = timeoutMs,
            )
        }
    }

    private suspend fun performProgressUpdate(
        request: RecordingProgressRequest,
        timeoutMs: Long,
    ): RecordingProgressUpdateResult = try {
        val reply = htsp.request(
            method = request.method,
            fields = request.fields,
            timeoutMs = timeoutMs,
            disconnectOnTimeout = false,
        )
        recordingProgressReplyResult(reply).also { result ->
            if (result == RecordingProgressUpdateResult.Unsupported) {
                progressMethodUnsupported = true
                _progressCapability.value = RecordingProgressCapability.Unsupported
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: HtspRequestTimeoutException) {
        RecordingProgressUpdateResult.Timeout
    } catch (_: ConnectException) {
        RecordingProgressUpdateResult.Disconnected
    } catch (_: IOException) {
        RecordingProgressUpdateResult.Disconnected
    } catch (error: IllegalStateException) {
        if (error.message == "Not connected") {
            RecordingProgressUpdateResult.Disconnected
        } else {
            RecordingProgressUpdateResult.Rejected
        }
    } catch (_: Exception) {
        RecordingProgressUpdateResult.Rejected
    }

    private suspend fun performAction(
        method: String,
        fields: Map<String, Any?>,
    ): DvrActionResult = try {
        val reply = htsp.request(
            method = method,
            fields = fields,
            timeoutMs = 10_000,
            disconnectOnTimeout = false,
        )
        val failure = dvrActionFailure(reply.fields)
        if (failure == null) {
            setWriteCapability(RecordingWriteCapability.Allowed)
            DvrActionResult.Accepted(reply.int("id") ?: reply.int("dvrId"))
        } else {
            if (failure == DvrActionFailure.PERMISSION_DENIED) {
                setWriteCapability(RecordingWriteCapability.Denied)
            }
            DvrActionResult.Failed(failure)
        }
    } catch (_: SocketTimeoutException) {
        DvrActionResult.Failed(DvrActionFailure.CONNECTION)
    } catch (_: ConnectException) {
        DvrActionResult.Failed(DvrActionFailure.CONNECTION)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        DvrActionResult.Failed(DvrActionFailure.REJECTED)
    }

    private fun setWriteCapability(capability: RecordingWriteCapability) {
        _writeCapability.value = capability
        _canModifyRecordings.value = capability == RecordingWriteCapability.Allowed
    }

}
