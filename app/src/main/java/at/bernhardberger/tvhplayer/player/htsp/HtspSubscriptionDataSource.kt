package at.bernhardberger.tvhplayer.player.htsp

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import at.bernhardberger.tvhplayer.htsp.HtspEvent
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.core.REQUESTED_TIMESHIFT_PERIOD_SEC
import at.bernhardberger.tvhplayer.core.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.core.timeshiftAbsoluteTargetUs
import at.bernhardberger.tvhplayer.core.timeshiftSeek
import at.bernhardberger.tvhplayer.core.timeshiftStateFromStatus
import at.bernhardberger.tvhplayer.player.PlaybackReadMetrics
import at.bernhardberger.tvhplayer.player.PlaybackQueueDiagnostics
import at.bernhardberger.tvhplayer.player.PlaybackTransportDiagnostics
import at.bernhardberger.tvhplayer.player.PlaybackTunerDiagnostics
import at.bernhardberger.tvhplayer.player.relativeSignalPercent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.min

internal fun <C, M> CoroutineScope.launchSubscriptionEventPump(
    controlEvents: Flow<C>,
    muxEvents: Flow<M>,
    onControl: suspend (C) -> Unit,
    onMux: suspend (M) -> Unit,
): Job = launch(start = CoroutineStart.UNDISPATCHED) {
    coroutineScope {
        launch(start = CoroutineStart.UNDISPATCHED) {
            controlEvents.collect { onControl(it) }
        }
        launch(start = CoroutineStart.UNDISPATCHED) {
            muxEvents.collect { onMux(it) }
        }
    }
}

internal fun updatePlaybackTransportDiagnostics(
    current: PlaybackTransportDiagnostics,
    message: HtspMessage,
    subscriptionId: Int,
): PlaybackTransportDiagnostics {
    if (message.int("subscriptionId") != subscriptionId) return current
    return when (message.method) {
        "signalStatus" -> current.copy(
            tuner = PlaybackTunerDiagnostics(
                status = message.str("feStatus"),
                signalPercent = relativeSignalPercent(message.long("feSignal")),
                signalMilliDbm = message.long("feAbsoluteSignal"),
                snrPercent = relativeSignalPercent(message.long("feSNR")),
                snrMilliDb = message.long("feAbsoluteSNR"),
                bitErrorRate = message.long("feBER"),
                uncorrectedBlocks = message.long("feUNC"),
            )
        )

        "queueStatus" -> current.copy(
            queue = PlaybackQueueDiagnostics(
                packets = message.long("packets"),
                bytes = message.long("bytes"),
                delayMicros = message.long("delay"),
                bFrameDrops = message.long("Bdrops"),
                pFrameDrops = message.long("Pdrops"),
                iFrameDrops = message.long("Idrops"),
            )
        )

        else -> current
    }
}

@OptIn(UnstableApi::class)
class HtspSubscriptionDataSource private constructor(
    private val context: Context,
    private val htspConnection: HtspService,
    private val streamProfile: String?,
    private val timeshiftEnabled: Boolean,
    private val sharedTimeshiftState: MutableStateFlow<TimeshiftState>,
    private val readMetrics: PlaybackReadMetrics,
    private val sharedTransportDiagnostics: MutableStateFlow<PlaybackTransportDiagnostics>,
) : DataSource, Closeable, HtspDataSourceInterface {

    private var dataSpec: DataSpec? = null
    private val dataSourceNumber: Int
    private val subscriptionId: Int

    private var timeshiftPeriod = 0
    private var subscriptionStarted = false
    private var isSubscribed = false

    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventJob: Job? = null
    @Volatile
    private var pendingSkip: CompletableDeferred<Boolean>? = null

    // ---------- subscriptionStart / muxpkt ordering ----------
    // The control-event and mux-event flows are collected by two independent
    // coroutines that both feed the same ring buffer, so without gating a muxpkt
    // could be framed before its subscriptionStart. The extractor would then have
    // no StreamReader yet and silently drop it (lost initial key frame -> "one frame
    // then freeze"). Buffer muxpkts until subscriptionStart has been written, then
    // flush them in order.
    private val startGate = Any()
    private var subscriptionStartWritten = false
    private val pendingMux = ArrayDeque<HtspMessage>()


    // ---------- High-performance buffering ----------
    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val notFull = lock.newCondition()

    /**
     * Ring buffer: zero compact/flip.
     */
    private val ring = RingBuffer(BUFFER_SIZE)

    class Factory internal constructor(
        private val context: Context,
        private val htspConnection: HtspService,
        private val streamProfile: String?,
        private val timeshiftEnabled: Boolean,
    ) : DataSource.Factory {
        internal val readMetrics = PlaybackReadMetrics()
        private var dataSource: HtspSubscriptionDataSource? = null
        private val _timeshiftState = MutableStateFlow(TimeshiftState())
        val timeshiftState: StateFlow<TimeshiftState> = _timeshiftState.asStateFlow()
        private val _transportDiagnostics = MutableStateFlow(PlaybackTransportDiagnostics())
        internal val transportDiagnostics: StateFlow<PlaybackTransportDiagnostics> =
            _transportDiagnostics.asStateFlow()

        override fun createDataSource(): DataSource {
            Timber.d("Created new data source from factory")
            dataSource = HtspSubscriptionDataSource(
                context,
                htspConnection,
                streamProfile,
                timeshiftEnabled,
                _timeshiftState,
                readMetrics,
                _transportDiagnostics,
            )
            return dataSource!!
        }

        val currentDataSource: HtspDataSourceInterface?
            get() = dataSource

        fun releaseCurrentDataSource() {
            Timber.d("Releasing data source")
            dataSource?.release()
            dataSource = null
            _timeshiftState.value = TimeshiftState()
            _transportDiagnostics.value = PlaybackTransportDiagnostics()
        }
    }

    init {
        Timber.d("Initializing subscription data source")
        dataSourceNumber = dataSourceCount.incrementAndGet()
        subscriptionId = subscriptionCount.incrementAndGet()

        Timber.d("New subscription data source instantiated (%d)", dataSourceNumber)

        // Push HEADER once into the stream.
        lock.lock()
        try {
            ring.write(HtspFramedCodec.HEADER, 0, HtspFramedCodec.HEADER.size) { needed ->
                // There should always be space at init; if not, clear.
                Timber.e("Ring buffer unexpectedly full at init; clearing (%d)", dataSourceNumber)
                ring.clear()
                needed <= ring.free()
            }
            notEmpty.signalAll()
        } finally {
            lock.unlock()
        }
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        Timber.d("Finalizing subscription data source")
        release()
    }

    override fun addTransferListener(transferListener: TransferListener) {
        // no-op
    }

    override fun open(dataSpec: DataSpec): Long {
        startPumpIfNeeded()
        Timber.d("Opening subscription data source (%d)", dataSourceNumber)
        this.dataSpec = dataSpec

        if (!isSubscribed) {
            val path = dataSpec.uri.path
            Timber.d("We are not yet subscribed to path %s", path)

            if (!path.isNullOrBlank() && path.length > 1) {
                val channelId = path.substring(1).toInt()

                Timber.d(
                    "Sending subscription start (subscriptionId=%s channelId=%s)",
                    subscriptionId,
                    channelId
                )

                runBlocking {
                    val response = htspConnection.request(
                        "subscribe",
                        mapOf(
                            "subscriptionId" to subscriptionId,
                            "channelId" to channelId,
                            "timeshiftPeriod" to if (timeshiftEnabled) {
                                REQUESTED_TIMESHIFT_PERIOD_SEC
                            } else {
                                0
                            },
                            "profile" to streamProfile,
                        )
                    )

                    val availableTimeshiftPeriod = response.int("timeshiftPeriod")
                    if (availableTimeshiftPeriod != null) {
                        timeshiftPeriod = availableTimeshiftPeriod.coerceAtLeast(0)
                        Timber.d(
                            "Available timeshift period in seconds: %s",
                            availableTimeshiftPeriod
                        )
                    }
                }

                isSubscribed = true
            }
        }

        subscriptionStarted = true
        return C.LENGTH_UNSET.toLong()
    }

    private fun startPumpIfNeeded() {
        if (eventJob != null) return

        eventJob = jobScope.launchSubscriptionEventPump(
            controlEvents = htspConnection.controlEvents,
            muxEvents = htspConnection.muxEvents,
            onControl = control@{ ev ->
                val msg = (ev as? HtspEvent.ServerMessage)?.msg ?: return@control
                val msgSubId = msg.int("subscriptionId")
                if (msgSubId != null && msgSubId != subscriptionId) return@control
                sharedTransportDiagnostics.value = updatePlaybackTransportDiagnostics(
                    current = sharedTransportDiagnostics.value,
                    message = msg,
                    subscriptionId = subscriptionId,
                )

                when (msg.method) {
                    "timeshiftStatus" -> {
                        val previous = sharedTimeshiftState.value
                        sharedTimeshiftState.value = timeshiftStateFromStatus(
                            advertisedPeriodSec = timeshiftPeriod.takeIf { it > 0 }
                                ?: REQUESTED_TIMESHIFT_PERIOD_SEC,
                            shiftMicros = msg.long("shift"),
                            startMicros = msg.long("start"),
                            endMicros = msg.long("end"),
                            full = msg.bool("full") == true,
                            speed = msg.int("speed")
                                ?: if (previous.paused) 0 else 100,
                            nowEpochMs = System.currentTimeMillis(),
                        )
                    }

                    "subscriptionStart" -> {
                        subscriptionStarted = true
                        // Write the start frame first, then release any muxpkts that
                        // arrived before it (preserving their original order).
                        writeFramedMessage(msg)
                        val drained: List<HtspMessage>
                        synchronized(startGate) {
                            subscriptionStartWritten = true
                            drained = pendingMux.toList()
                            pendingMux.clear()
                        }
                        drained.forEach { writeFramedMessage(it) }
                    }

                    "subscriptionStop" -> {
                        subscriptionStarted = false
                        pendingSkip?.complete(false)
                        sharedTimeshiftState.value = TimeshiftState()
                        synchronized(startGate) {
                            subscriptionStartWritten = false
                            pendingMux.clear()
                        }
                        lock.lock()
                        try {
                            notEmpty.signalAll()
                            notFull.signalAll()
                        } finally {
                            lock.unlock()
                        }
                    }

                    "subscriptionSkip" -> {
                        pendingSkip?.complete(msg.int("error") != 1)
                    }
                }
            },
            onMux = mux@{ msg ->
                val msgSubId = msg.int("subscriptionId")
                if (msgSubId != null && msgSubId != subscriptionId) return@mux
                if (pendingSkip != null) return@mux

                // If subscriptionStart hasn't been framed yet, hold the muxpkt back
                // so the extractor never sees a packet before its stream definition.
                val deferred = synchronized(startGate) {
                    if (!subscriptionStartWritten) {
                        pendingMux.addLast(msg)
                        while (pendingMux.size > MAX_PENDING_MUX) pendingMux.removeFirst()
                        true
                    } else {
                        false
                    }
                }
                if (!deferred) writeFramedMessage(msg)
            },
        )
    }

    override fun getUri(): Uri? = dataSpec?.uri

    /**
     * DataSource.read() – blokuje bez polling/sleep.
     */
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0

        lock.lock()
        try {
            while (subscriptionStarted && ring.size() == 0) {
                try {
                    notEmpty.await()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    Timber.w("Interrupted while waiting for data (%d)", dataSourceNumber)
                    return 0
                }
            }

            if (!subscriptionStarted && ring.size() == 0) {
                Timber.d("End of input buffer (%d)", dataSourceNumber)
                return C.RESULT_END_OF_INPUT
            }

            val toRead = min(readLength, ring.size())
            val actuallyRead = ring.read(buffer, offset, toRead)
            if (actuallyRead > 0) {
                readMetrics.record(actuallyRead)
                notFull.signalAll()
            }
            return actuallyRead
        } finally {
            lock.unlock()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getResponseHeaders(): Map<String, List<String>> {
        return mutableMapOf<String?, MutableList<String?>?>() as Map<String, List<String>>
    }

    override fun close() {
        Timber.d("Closing subscription data source (%d)", dataSourceNumber)
        subscriptionStarted = false
        eventJob?.cancel()
        eventJob = null

        lock.lock()
        try {
            notEmpty.signalAll()
            notFull.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private fun release() {
        Timber.d("Releasing subscription data source (%d)", dataSourceNumber)
        subscriptionStarted = false

        eventJob?.cancel()
        eventJob = null

        lock.lock()
        try {
            notEmpty.signalAll()
            notFull.signalAll()
        } finally {
            lock.unlock()
        }

        if (isSubscribed) {
            runBlocking {
                try {
                    htspConnection.request(
                        "unsubscribe",
                        mapOf("subscriptionId" to subscriptionId)
                    )
                } catch (t: Throwable) {
                    Timber.w(t, "unsubscribe failed (%d)", dataSourceNumber)
                }
            }
        }

        isSubscribed = false
    }

    override fun pause() {
        Timber.d("Pausing subscription data source (%d)", dataSourceNumber)
        runBlocking {
            htspConnection.request(
                "subscriptionSpeed",
                mapOf(
                    "subscriptionId" to subscriptionId,
                    "speed" to 0
                )
            )
        }
        sharedTimeshiftState.value = sharedTimeshiftState.value.copy(paused = true)
    }

    override val timeshiftState: StateFlow<TimeshiftState>
        get() = sharedTimeshiftState.asStateFlow()

    override val timeshiftOffsetPts: Long
        get() = sharedTimeshiftState.value.positionMs * 90L

    override fun setSpeed(tvhSpeed: Int) {
        runBlocking {
            htspConnection.request(
                "subscriptionSpeed",
                mapOf(
                    "subscriptionId" to subscriptionId,
                    "speed" to tvhSpeed
                )
            )
        }
    }

    override val timeshiftStartTime: Long
        get() = (
            System.currentTimeMillis() + sharedTimeshiftState.value.bufferStartMs
        ) / 1_000L

    override val timeshiftStartPts: Long
        get() = sharedTimeshiftState.value.bufferStartMs * 90L

    override fun resume() {
        Timber.d("Resuming subscription data source (%d)", dataSourceNumber)
        runBlocking {
            htspConnection.request(
                "subscriptionSpeed",
                mapOf("subscriptionId" to subscriptionId, "speed" to 100)
            )
        }
        sharedTimeshiftState.value = sharedTimeshiftState.value.copy(paused = false)
    }

    override fun seekTimeshift(deltaMs: Long): TimeshiftSeekDecision {
        val state = sharedTimeshiftState.value
        val decision = timeshiftSeek(state, deltaMs)
        if (decision.deltaMs != 0L) {
            val acknowledgement = CompletableDeferred<Boolean>()
            pendingSkip = acknowledgement
            try {
                val acknowledged = runBlocking {
                    val absoluteTargetUs = timeshiftAbsoluteTargetUs(state, decision)
                    htspConnection.request(
                        "subscriptionSeek",
                        mapOf(
                            "subscriptionId" to subscriptionId,
                            "time" to (absoluteTargetUs ?: decision.deltaMs * 1_000L),
                            "absolute" to if (absoluteTargetUs != null) 1 else 0,
                        )
                    )
                    withTimeoutOrNull(SKIP_ACK_TIMEOUT_MS) {
                        acknowledgement.await()
                    } == true
                }
                check(acknowledged) { "TVHeadend did not confirm the timeshift seek" }
                clearBufferedFramesForSkip()
            } finally {
                if (pendingSkip === acknowledgement) pendingSkip = null
            }
        }
        return decision
    }

    override fun goLive(): TimeshiftSeekDecision =
        seekTimeshift(sharedTimeshiftState.value.liveEdgeMs - sharedTimeshiftState.value.positionMs)

    private fun clearBufferedFramesForSkip() {
        lock.lock()
        try {
            ring.clear()
            notEmpty.signalAll()
            notFull.signalAll()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Writes a framed message into ring buffer:
     * [int32 payloadLen][payload bytes]
     */
    private fun writeFramedMessage(message: HtspMessage) {
        val frame = try {
            HtspFramedCodec.frameMessage(message)
        } catch (t: Throwable) {
            Timber.e(t, "Failed to encode message (%d)", dataSourceNumber)
            return
        }

        lock.lock()
        try {
            ring.write(frame, 0, frame.size) { needed ->
                while (subscriptionStarted && needed > ring.free()) {
                    try {
                        notFull.await()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@write false
                    }
                }
                needed <= ring.free()
            }
            notEmpty.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private class RingBuffer(capacity: Int) {
        private val buf = ByteArray(capacity)
        private var head = 0 // read
        private var tail = 0 // write
        private var size = 0

        fun size(): Int = size
        fun free(): Int = buf.size - size

        fun clear() {
            head = 0
            tail = 0
            size = 0
        }

        fun write(src: ByteArray, off: Int, len: Int, spacePolicy: (needed: Int) -> Boolean) {
            if (len <= 0) return
            if (!spacePolicy(len)) return

            var remaining = len
            var srcPos = off
            while (remaining > 0) {
                val chunk = min(remaining, buf.size - tail)
                System.arraycopy(src, srcPos, buf, tail, chunk)
                tail = (tail + chunk) % buf.size
                size += chunk
                srcPos += chunk
                remaining -= chunk
            }
        }

        fun read(dst: ByteArray, off: Int, len: Int): Int {
            if (len <= 0 || size == 0) return 0
            val toRead = min(len, size)

            var remaining = toRead
            var dstPos = off
            while (remaining > 0) {
                val chunk = min(remaining, buf.size - head)
                System.arraycopy(buf, head, dst, dstPos, chunk)
                head = (head + chunk) % buf.size
                size -= chunk
                dstPos += chunk
                remaining -= chunk
            }
            return toRead
        }
    }

    companion object {
        private const val SKIP_ACK_TIMEOUT_MS = 5_000L
        private val dataSourceCount = AtomicInteger()
        private val subscriptionCount = AtomicInteger()

        private const val BUFFER_SIZE = 10 * 1024 * 1024

        // Safety cap on muxpkts buffered while waiting for subscriptionStart (normally
        // only a handful arrive before it); drop oldest beyond this to bound memory.
        private const val MAX_PENDING_MUX = 4096
    }
}
