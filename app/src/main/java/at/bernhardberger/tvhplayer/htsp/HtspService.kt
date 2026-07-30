package at.bernhardberger.tvhplayer.htsp

import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.core.ConnectionPolicy
import at.bernhardberger.tvhplayer.core.MetadataPermissionDeniedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.text.Charsets.UTF_8

internal const val DVR_PLAY_COUNT_KEEP: Int = Int.MAX_VALUE - 1

class HtspRequestTimeoutException(
    val requestMethod: String,
    val timeoutMs: Long,
    cause: Throwable? = null,
) : IOException("HTSP request timed out", cause)

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connecting(val host: String, val port: Int) : ConnectionState()
    /**
     * @param dvrAccess HTSP `ACCESS_HTSP_RECORDER` from authenticate (version ≥ 26).
     * null when unauthenticated or the field was not returned.
     */
    data class Connected(
        val host: String,
        val port: Int,
        val htspVersion: Int?,
        val dvrAccess: Boolean? = null,
    ) : ConnectionState()
    data class Error(val throwable: Throwable) : ConnectionState()
}

internal enum class HtspConnectionAttemptStatus {
    LIVE,
    GONE,
    REPLACED,
}

open class HtspService(
    ioDispatcher: CoroutineDispatcher
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    internal open fun currentConnectionState(): ConnectionState = state.value

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val controlEventStream = HtspEventStream()
    val controlEvents: Flow<HtspEvent> = controlEventStream.events.filter { event ->
        event.connectionAttemptId == 0L ||
            isCurrentConnectionAttempt(event.connectionAttemptId)
    }

    private val _muxEvents = MutableSharedFlow<HtspMuxEvent>(
        extraBufferCapacity = 8192,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val muxEvents: SharedFlow<HtspMuxEvent> = _muxEvents

    private val pending = ConcurrentHashMap<Int, PendingReq>()

    private data class PendingReq(
        val def: CompletableDeferred<HtspMessage>,
        val startedAtMs: Long
    )

    private val seq = AtomicInteger(1)

    private val writeMutex = Mutex()
    private val connectMutex = Mutex()
    private val connectionAttemptLock = Any()
    @Volatile
    private var connectionAttempt = 0L

    @Volatile
    private var liveTransportAttempt: Long? = null

    @Volatile
    private var muxCursorAttempt = 0L

    @Volatile
    private var muxCursorSequence = 0L

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var connectingSocket: Socket? = null

    @Volatile
    private var input: InputStream? = null

    @Volatile
    private var output: OutputStream? = null

    @Volatile
    private var readerJob: Job? = null

    @Volatile
    private var challenge: ByteArray? = null

    @Volatile
    private var negotiatedHtspVersion: Int? = null

    @Volatile
    private var initialSyncDef: CompletableDeferred<Unit>? = null

    // ---- health ----
    @Volatile
    private var lastReadAtMs: Long = 0L

    suspend fun connect(
        host: String,
        port: Int,
        username: String? = null,
        password: String? = null,
        clientName: String = "TVHeadend Player / " + BuildConfig.VERSION_NAME,
        clientVersion: String = BuildConfig.VERSION_NAME,
        htspVersion: Int = 43,

        connectTimeoutMs: Int = 10_000,
        responseTimeoutMs: Long = 5_000,

        soTimeoutMs: Int = 60_000,

        socketBufferBytes: Int = 64 * 1024,

        forceReconnect: Boolean = false
    ) {
        if (!forceReconnect && isConnectedUnsafe()) return
        val attemptId = beginConnectionAttempt()
        if (forceReconnect) supersedeCurrentTransport()
        try {
            connectMutex.withLock {
                ensureCurrentConnectionAttempt(attemptId)
                if (!forceReconnect && isConnectedUnsafe()) {
                    restorePreviousConnectionAttempt(attemptId)
                    return
                }

                disconnectInternal(
                    t = CancellationException("Reconnect"),
                    attemptId = attemptId,
                    publishState = false,
                )
                ensureCurrentConnectionAttempt(attemptId)
                publishConnectionState(
                    attemptId,
                    ConnectionState.Connecting(host, port),
                )

                val s = Socket()
                try {
                    connectingSocket = s
                    s.tcpNoDelay = true
                    s.keepAlive = true
                    s.soTimeout = soTimeoutMs
                    s.connect(InetSocketAddress(host, port), connectTimeoutMs)

                    val inp = BufferedInputStream(s.getInputStream(), socketBufferBytes)
                    val out = BufferedOutputStream(s.getOutputStream(), socketBufferBytes)

                    installTransport(attemptId, s, inp, out)
                    lastReadAtMs = System.currentTimeMillis()

                    if (readerJob != null) {
                        throw IllegalStateException("Reader job already running")
                    }
                    readerJob = scope.launch {
                        readerLoop(
                            responseTimeoutMs = responseTimeoutMs,
                            attemptId = attemptId,
                        )
                    }

                    val hello = request(
                        method = "hello",
                        fields = mapOf(
                            "htspversion" to htspVersion,
                            "clientname" to clientName,
                            "clientversion" to clientVersion
                        ),
                        timeoutMs = responseTimeoutMs,
                        flush = true,
                        disconnectOnTimeout = true
                    )

                    challenge = hello.bin("challenge")
                    val serverMax = hello.int("htspversion") ?: htspVersion
                    negotiatedHtspVersion = min(htspVersion, serverMax)

                    val user = username?.trim().orEmpty()
                    val pass = password?.trim().orEmpty()

                    // Always call authenticate, even without credentials: the server leaves
                    // address-based anonymous rights untouched when the message carries no
                    // username, and the reply is the only place HTSP reports our rights.
                    val withCredentials =
                        ConnectionPolicy.shouldAuthenticate(username, password) && challenge != null
                    val authFields = if (withCredentials) {
                        mapOf("username" to user, "digest" to makeDigest(pass, challenge!!))
                    } else {
                        emptyMap()
                    }
                    val auth = request(
                        method = "authenticate",
                        fields = authFields,
                        timeoutMs = responseTimeoutMs,
                        flush = true,
                        disconnectOnTimeout = true
                    )
                    if (auth.int("noaccess") == 1) {
                        throw IllegalStateException(
                            if (withCredentials) {
                                "HTSP authentication failed (noaccess=1)"
                            } else {
                                "HTSP server requires credentials (noaccess=1)"
                            }
                        )
                    }
                    // HTSP ≥ 26 includes ACCESS_HTSP_RECORDER as "dvr".
                    val dvrAccess =
                        if (negotiatedHtspVersion != null && negotiatedHtspVersion!! > 25) {
                            auth.int("dvr")?.let { it == 1 }
                        } else {
                            null
                        }

                    ensureCurrentConnectionAttempt(attemptId)
                    publishConnectionState(
                        attemptId,
                        ConnectionState.Connected(
                            host = host,
                            port = port,
                            htspVersion = negotiatedHtspVersion,
                            dvrAccess = dvrAccess,
                        ),
                    )

                } catch (cancelled: CancellationException) {
                    disconnectInternal(
                        t = cancelled,
                        attemptId = attemptId,
                        publishState = true,
                    )
                    throw cancelled
                } catch (t: Throwable) {
                    if (!isCurrentConnectionAttempt(attemptId)) {
                        val superseded = CancellationException("Superseded connection attempt")
                        superseded.initCause(t)
                        disconnectInternal(
                            t = superseded,
                            attemptId = attemptId,
                            publishState = false,
                        )
                        throw superseded
                    }
                    publishConnectionState(attemptId, ConnectionState.Error(t))
                    disconnectInternal(
                        t = t,
                        attemptId = attemptId,
                        publishState = true,
                    )
                    throw t
                }
            }
        } catch (cancelled: CancellationException) {
            publishConnectionState(attemptId, ConnectionState.Disconnected)
            throw cancelled
        }
    }

    suspend fun enableAsyncMetadataAndWaitInitialSync(timeoutMs: Long = 30_000) {
        if (!isConnectedUnsafe()) throw IllegalStateException("Not connected")

        val def = CompletableDeferred<Unit>()
        initialSyncDef = def

        try {
            val reply = request(
                method = "enableAsyncMetadata",
                fields = emptyMap(),
                timeoutMs = timeoutMs,
                flush = true,
                disconnectOnTimeout = true
            )
            if (reply.int("noaccess") == 1 || reply.fields.containsKey("error")) {
                throw MetadataPermissionDeniedException()
            }
            withTimeout(timeoutMs) { def.await() }
        } finally {
            if (initialSyncDef === def) initialSyncDef = null
        }
    }

    open suspend fun request(
        method: String,
        fields: Map<String, Any?> = emptyMap(),
        timeoutMs: Long = 5_000,
        flush: Boolean = true,
        disconnectOnTimeout: Boolean = true
    ): HtspMessage = requestInternal(
        expectedConnectionAttemptId = null,
        method = method,
        fields = fields,
        timeoutMs = timeoutMs,
        flush = flush,
        disconnectOnTimeout = disconnectOnTimeout,
    )

    internal open suspend fun requestForConnectionAttempt(
        expectedConnectionAttemptId: Long,
        method: String,
        fields: Map<String, Any?> = emptyMap(),
        timeoutMs: Long = 5_000,
        flush: Boolean = true,
        disconnectOnTimeout: Boolean = true,
    ): HtspMessage = requestInternal(
        expectedConnectionAttemptId = expectedConnectionAttemptId,
        method = method,
        fields = fields,
        timeoutMs = timeoutMs,
        flush = flush,
        disconnectOnTimeout = disconnectOnTimeout,
    )

    private suspend fun requestInternal(
        expectedConnectionAttemptId: Long?,
        method: String,
        fields: Map<String, Any?>,
        timeoutMs: Long,
        flush: Boolean,
        disconnectOnTimeout: Boolean,
    ): HtspMessage {
        val s = seq.getAndIncrement()
        val def = CompletableDeferred<HtspMessage>()
        pending[s] = PendingReq(def, System.currentTimeMillis())
        val transport = if (expectedConnectionAttemptId == null) {
            socket to output
        } else {
            commitIfLiveConnectionAttempt(expectedConnectionAttemptId) {
                socket to output
            } ?: run {
                pending.remove(s)
                throw CancellationException("Stale HTSP connection attempt")
            }
        }
        val requestSocket = transport.first
        val out = transport.second ?: run {
            pending.remove(s)
            throw IllegalStateException("Not connected")
        }

        try {
            val msgFields = HashMap<String, Any?>(fields.size + 1).apply {
                putAll(fields)
                this["seq"] = s
            }

            writeMutex.withLock {
                HtspCodec.writeMessage(out, method, msgFields)
                if (flush) out.flush()
            }
        } catch (t: Throwable) {
            pending.remove(s)
            def.completeExceptionally(t)
            throw t
        }

        return try {
            val response = withTimeoutOrNull(timeoutMs) { def.await() }
            if (response == null) {
                pending.remove(s)

                if (disconnectOnTimeout) {
                    markTransportGone(requestSocket)
                    throw SocketTimeoutException(
                        "HTSP request '$method' timed out after ${timeoutMs}ms"
                    )
                }

                throw HtspRequestTimeoutException(method, timeoutMs)
            }
            response
        } catch (t: Throwable) {
            pending.remove(s)
            throw t
        }
    }

    suspend fun fileOpen(
        path: String,
        timeoutMs: Long = 5_000,
        expectedConnectionAttemptId: Long? = null,
    ): Int {
        val p = if (path.startsWith("/")) path else "/$path"
        val msg = fileRequest(
            expectedConnectionAttemptId = expectedConnectionAttemptId,
            method = "fileOpen",
            fields = mapOf("file" to p),
            timeoutMs = timeoutMs,
            flush = true,
            disconnectOnTimeout = false
        )
        return msg.int("id") ?: error("fileOpen: missing id")
    }

    suspend fun fileRead(
        id: Int,
        size: Int,
        timeoutMs: Long = 5_000,
        expectedConnectionAttemptId: Long? = null,
    ): ByteArray {
        val msg = fileRequest(
            expectedConnectionAttemptId = expectedConnectionAttemptId,
            method = "fileRead",
            fields = mapOf("id" to id, "size" to size),
            timeoutMs = timeoutMs,
            flush = true,
            disconnectOnTimeout = false
        )
        return msg.bin("data") ?: ByteArray(0) // EOF => empty
    }

    suspend fun fileSeek(
        id: Int,
        offset: Long,
        whence: String = "SEEK_SET",
        timeoutMs: Long = 5_000,
        expectedConnectionAttemptId: Long? = null,
    ): Long {
        val msg = fileRequest(
            expectedConnectionAttemptId = expectedConnectionAttemptId,
            method = "fileSeek",
            fields = mapOf("id" to id, "offset" to offset, "whence" to whence),
            timeoutMs = timeoutMs,
            flush = true,
            disconnectOnTimeout = false,
        )
        return msg.long("offset") ?: offset
    }

    suspend fun fileClose(
        id: Int,
        timeoutMs: Long = 5_000,
        expectedConnectionAttemptId: Long? = null,
    ) {
        fileRequest(
            expectedConnectionAttemptId = expectedConnectionAttemptId,
            method = "fileClose",
            fields = mapOf("id" to id),
            timeoutMs = timeoutMs,
            flush = true,
            disconnectOnTimeout = false
        )
    }

    suspend fun fileCloseRecording(
        id: Int,
        htspVersion: Int?,
        timeoutMs: Long = 5_000,
        expectedConnectionAttemptId: Long? = null,
    ) {
        val fields = if (htspVersion != null && htspVersion >= 27) {
            mapOf(
                "id" to id,
                "playcount" to DVR_PLAY_COUNT_KEEP,
            )
        } else {
            mapOf("id" to id)
        }
        fileRequest(
            expectedConnectionAttemptId = expectedConnectionAttemptId,
            method = "fileClose",
            fields = fields,
            timeoutMs = timeoutMs,
            flush = true,
            disconnectOnTimeout = false,
        )
    }

    private suspend fun fileRequest(
        expectedConnectionAttemptId: Long?,
        method: String,
        fields: Map<String, Any?>,
        timeoutMs: Long,
        flush: Boolean,
        disconnectOnTimeout: Boolean,
    ): HtspMessage = if (expectedConnectionAttemptId == null) {
        request(method, fields, timeoutMs, flush, disconnectOnTimeout)
    } else {
        requestForConnectionAttempt(
            expectedConnectionAttemptId,
            method,
            fields,
            timeoutMs,
            flush,
            disconnectOnTimeout,
        )
    }

    suspend fun disconnect() = withContext(NonCancellable) {
        val attemptId = beginConnectionAttempt()
        supersedeCurrentTransport()
        connectMutex.withLock {
            disconnectInternal(
                t = CancellationException("Disconnected"),
                attemptId = attemptId,
                publishState = true,
            )
        }
    }

    private suspend fun readerLoop(responseTimeoutMs: Long, attemptId: Long) {
        val inp = input ?: return

        val pendingMaxSilentMs = responseTimeoutMs * 2
        var messageSequence = 0L
        var muxSequence = 0L
        if (
            withCurrentConnectionAttempt(attemptId) {
                muxCursorAttempt = attemptId
                muxCursorSequence = 0L
            } == null
        ) return

        try {
            while (currentCoroutineContext().isActive) {
                try {
                    val msg = HtspCodec.readMessage(inp)
                    val currentMessageSequence = ++messageSequence
                    var controlEvent: HtspEvent.ServerMessage? = null
                    val published = withCurrentConnectionAttempt(attemptId) {
                        lastReadAtMs = System.currentTimeMillis()

                        // Special-cased latch
                        if (msg.seq == null && msg.method == "initialSyncCompleted") {
                            initialSyncDef?.complete(Unit)
                        }

                        val seqNo = msg.seq
                        if (seqNo != null) {
                            val pr = pending.remove(seqNo)
                            if (pr != null) {
                                pr.def.complete(msg)
                                return@withCurrentConnectionAttempt
                            }
                        }

                        if (msg.method == "muxpkt") {
                            val currentMuxSequence = ++muxSequence
                            muxCursorSequence = currentMuxSequence
                            _muxEvents.tryEmit(
                                HtspMuxEvent(
                                    msg = msg,
                                    connectionAttemptId = attemptId,
                                    messageSequence = currentMessageSequence,
                                    muxSequence = currentMuxSequence,
                                )
                            )
                        } else {
                            controlEvent = HtspEvent.ServerMessage(
                                msg = msg,
                                connectionAttemptId = attemptId,
                                messageSequence = currentMessageSequence,
                            )
                        }
                    } != null
                    if (!published) return
                    controlEvent?.let { controlEventStream.emit(it) }
                } catch (t: SocketTimeoutException) {
                    val now = System.currentTimeMillis()
                    if (pending.isNotEmpty()) {
                        val silent = now - lastReadAtMs
                        if (silent >= pendingMaxSilentMs) {
                            failAll(
                                SocketTimeoutException("HTSP no incoming data for ${silent}ms with ${pending.size} pending requests"),
                                attemptId,
                            )
                            return
                        }
                    }
                    continue
                }
            }
        } catch (t: NoSuchElementException) {
            failAll(
                EOFException("Broken/EOF HTSP stream").apply { initCause(t) },
                attemptId,
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (!currentCoroutineContext().isActive) return
            failAll(t, attemptId)
        }
    }

    private fun makeDigest(password: String, challenge: ByteArray): ByteArray {
        val p = password.toByteArray(UTF_8)
        val all = ByteArray(p.size + challenge.size)
        System.arraycopy(p, 0, all, 0, p.size)
        System.arraycopy(challenge, 0, all, p.size, challenge.size)
        return MessageDigest.getInstance("SHA-1").digest(all)
    }

    private fun isConnectedUnsafe(): Boolean {
        val sj = readerJob
        val s = socket
        return sj?.isActive == true &&
                output != null &&
                s?.isConnected == true && !s.isClosed
    }

    private suspend fun disconnectInternal(
        t: Throwable,
        attemptId: Long,
        publishState: Boolean,
    ) {
        val callerJob = currentCoroutineContext()[Job]
        withContext(NonCancellable) {
            val defs = pending.values.toList()
            pending.clear()
            defs.forEach { it.def.completeExceptionally(t) }

            initialSyncDef?.completeExceptionally(t)
            initialSyncDef = null

            val job = readerJob
            readerJob = null
            if (job != null && job !== callerJob) job.cancel()

            // Socket reads are blocking. Closing the transport is what makes a
            // cancelled reader observable; joining first can wait forever.
            closeTransport()
            if (job != null && job !== callerJob) job.join()

            challenge = null
            negotiatedHtspVersion = null
            if (publishState && isCurrentConnectionAttempt(attemptId)) {
                publishConnectionState(attemptId, ConnectionState.Disconnected)
            }
        }
    }

    private suspend fun failAll(t: Throwable, attemptId: Long) {
        if (!isCurrentConnectionAttempt(attemptId)) return
        val event = connectMutex.withLock<HtspEvent.ConnectionError?> {
            if (!isCurrentConnectionAttempt(attemptId)) return@withLock null
            val event = withCurrentConnectionAttempt(attemptId) {
                _state.value = ConnectionState.Error(t)
                HtspEvent.ConnectionError(
                    error = t,
                    connectionAttemptId = attemptId,
                )
            } ?: return@withLock null
            disconnectInternal(
                t = t,
                attemptId = attemptId,
                publishState = true,
            )
            event
        } ?: return
        controlEventStream.emit(event)
    }

    private fun ensureCurrentConnectionAttempt(attemptId: Long) {
        if (!isCurrentConnectionAttempt(attemptId)) {
            throw CancellationException("Superseded connection attempt")
        }
    }

    private fun beginConnectionAttempt(): Long = synchronized(connectionAttemptLock) {
        ++connectionAttempt
    }

    private fun restorePreviousConnectionAttempt(attemptId: Long) {
        synchronized(connectionAttemptLock) {
            if (connectionAttempt == attemptId) connectionAttempt--
        }
    }

    internal fun currentConnectionAttemptId(): Long = connectionAttempt

    internal fun currentMuxSequenceForConnectionAttempt(attemptId: Long): Long? =
        synchronized(connectionAttemptLock) {
            if (connectionAttempt == attemptId && muxCursorAttempt == attemptId) {
                muxCursorSequence
            } else {
                null
            }
        }

    internal fun isCurrentConnectionAttemptId(attemptId: Long): Boolean =
        isCurrentConnectionAttempt(attemptId)

    internal fun connectionAttemptStatus(attemptId: Long): HtspConnectionAttemptStatus =
        synchronized(connectionAttemptLock) {
            when {
                connectionAttempt != attemptId -> HtspConnectionAttemptStatus.REPLACED
                liveTransportAttempt == attemptId -> HtspConnectionAttemptStatus.LIVE
                else -> HtspConnectionAttemptStatus.GONE
            }
        }

    private fun isCurrentConnectionAttempt(attemptId: Long): Boolean = connectionAttempt == attemptId

    private fun publishConnectionState(
        attemptId: Long,
        state: ConnectionState,
    ): Boolean = withCurrentConnectionAttempt(attemptId) {
        _state.value = state
    } != null

    internal fun <T> commitIfCurrentConnectionAttempt(
        attemptId: Long,
        block: () -> T,
    ): T? = synchronized(connectionAttemptLock) {
        if (attemptId == 0L) return@synchronized block()
        if (connectionAttempt != attemptId) return@synchronized null
        block()
    }

    internal fun <T> commitIfLiveConnectionAttempt(
        attemptId: Long,
        block: () -> T,
    ): T? = synchronized(connectionAttemptLock) {
        if (connectionAttempt != attemptId || liveTransportAttempt != attemptId) {
            return@synchronized null
        }
        block()
    }

    private fun <T> withCurrentConnectionAttempt(
        attemptId: Long,
        block: () -> T,
    ): T? = commitIfCurrentConnectionAttempt(attemptId, block)

    private fun installTransport(
        attemptId: Long,
        transportSocket: Socket,
        transportInput: InputStream,
        transportOutput: OutputStream,
    ) = synchronized(connectionAttemptLock) {
        ensureCurrentConnectionAttempt(attemptId)
        socket = transportSocket
        connectingSocket = null
        input = transportInput
        output = transportOutput
        liveTransportAttempt = attemptId
    }

    private fun closeTransport() {
        val (
            currentConnectingSocket,
            currentSocket,
            currentInput,
            currentOutput,
        ) = synchronized(connectionAttemptLock) {
            val snapshot = TransportSnapshot(connectingSocket, socket, input, output)
            connectingSocket = null
            socket = null
            input = null
            output = null
            liveTransportAttempt = null
            snapshot
        }

        closeSocket(currentConnectingSocket)
        closeSocket(currentSocket)
        runCatching { currentInput?.close() }
        runCatching { currentOutput?.close() }
    }

    private fun markTransportGone(target: Socket?) {
        synchronized(connectionAttemptLock) {
            if (socket === target) liveTransportAttempt = null
        }
        closeSocket(target)
    }

    private fun closeSocket(target: Socket?) {
        runCatching { target?.close() }
    }

    private fun supersedeCurrentTransport() {
        val cancellation = CancellationException("Superseded connection attempt")
        val defs = pending.values.toList()
        pending.clear()
        defs.forEach { it.def.completeExceptionally(cancellation) }
        initialSyncDef?.completeExceptionally(cancellation)
        closeTransport()
    }


    private data class TransportSnapshot(
        val connectingSocket: Socket?,
        val socket: Socket?,
        val input: InputStream?,
        val output: OutputStream?,
    )
}
