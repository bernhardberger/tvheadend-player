@file:kotlin.OptIn(at.bernhardberger.tvhplayer.htsp.PlaybackIntegrationApi::class)

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
import at.bernhardberger.tvhplayer.htsp.HtspConnectionAttemptStatus
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspMuxEvent
import at.bernhardberger.tvhplayer.htsp.PlaybackHtspTransport
import at.bernhardberger.tvhplayer.core.REQUESTED_TIMESHIFT_PERIOD_SEC
import at.bernhardberger.tvhplayer.core.SubscriptionFailureKind
import at.bernhardberger.tvhplayer.core.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.core.timeshiftAbsoluteTargetUs
import at.bernhardberger.tvhplayer.core.timeshiftSeek
import at.bernhardberger.tvhplayer.core.timeshiftStateFromStatus
import at.bernhardberger.tvhplayer.core.subscriptionFailureKind
import at.bernhardberger.tvhplayer.player.PlaybackReadMetrics
import at.bernhardberger.tvhplayer.player.PlaybackQueueDiagnostics
import at.bernhardberger.tvhplayer.player.PlaybackTransportDiagnostics
import at.bernhardberger.tvhplayer.player.PlaybackTunerDiagnostics
import at.bernhardberger.tvhplayer.player.relativeSignalPercent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

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
internal class HtspSubscriptionDataSource private constructor(
    private val context: Context,
    private val htspConnection: PlaybackHtspTransport,
    private val streamProfile: String?,
    private val timeshiftEnabled: Boolean,
    private val sharedTimeshiftState: MutableStateFlow<TimeshiftState>,
    private val sharedSubscriptionFailure: MutableStateFlow<SubscriptionFailureKind?>,
    private val readMetrics: PlaybackReadMetrics,
    private val sharedTransportDiagnostics: MutableStateFlow<PlaybackTransportDiagnostics>,
) : DataSource, Closeable, HtspDataSourceInterface {

    private var dataSpec: DataSpec? = null
    private val dataSourceNumber: Int
    private val subscriptionId: Int

    private var timeshiftPeriod = 0
    private var subscriptionStarted = false
    private val subscriptionLifecycle = LiveSubscriptionLifecycle()

    private val dataSourceJob = SupervisorJob()
    private val jobScope = CoroutineScope(dataSourceJob + Dispatchers.IO)
    private var eventJob: Job? = null
    @Volatile
    private var connectionAttemptId: Long? = null
    private val timeshiftSeekMuxGate = TimeshiftSeekMuxGate<HtspMuxEvent>(
        sequenceOf = HtspMuxEvent::messageSequence,
        maxPendingMux = MAX_PENDING_MUX,
    )
    private var muxDeliveryTracker: HtspMuxDeliveryTracker? = null

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
    private val ring = FramedRingBuffer(BUFFER_SIZE)

    class Factory internal constructor(
        private val context: Context,
        private val htspConnection: PlaybackHtspTransport,
        private val streamProfile: String?,
        private val timeshiftEnabled: Boolean,
    ) : DataSource.Factory {
        internal val readMetrics = PlaybackReadMetrics()
        private val owner = LiveSubscriptionFactoryOwner<HtspSubscriptionDataSource>()
        private val _timeshiftState = MutableStateFlow(TimeshiftState())
        val timeshiftState: StateFlow<TimeshiftState> = _timeshiftState.asStateFlow()
        private val _subscriptionFailure = MutableStateFlow<SubscriptionFailureKind?>(null)
        val subscriptionFailure: StateFlow<SubscriptionFailureKind?> =
            _subscriptionFailure.asStateFlow()
        private val _transportDiagnostics = MutableStateFlow(PlaybackTransportDiagnostics())
        internal val transportDiagnostics: StateFlow<PlaybackTransportDiagnostics> =
            _transportDiagnostics.asStateFlow()

        override fun createDataSource(): DataSource {
            Timber.d("Created new data source from factory")
            return owner.create {
                HtspSubscriptionDataSource(
                    context,
                    htspConnection,
                    streamProfile,
                    timeshiftEnabled,
                    _timeshiftState,
                    _subscriptionFailure,
                    readMetrics,
                    _transportDiagnostics,
                )
            }
        }

        val currentDataSource: HtspDataSourceInterface?
            get() = owner.current()

        fun releaseCurrentDataSource() {
            Timber.d("Releasing data source")
            var firstFailure: Throwable? = null
            owner.retire().forEach { source ->
                try {
                    source.release()
                    owner.releaseSettled(source)
                } catch (error: Throwable) {
                    if (firstFailure == null) firstFailure = error
                }
            }
            firstFailure?.let { throw it }
            _timeshiftState.value = TimeshiftState()
            _subscriptionFailure.value = null
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
        runCatching(::release)
    }

    override fun addTransferListener(transferListener: TransferListener) {
        // no-op
    }

    override fun open(dataSpec: DataSpec): Long {
        check(subscriptionLifecycle.beginOpen()) { "Subscription data source is unavailable" }
        return try {
            openSubscription(dataSpec)
        } finally {
            subscriptionLifecycle.openSettled()
        }
    }

    private fun openSubscription(dataSpec: DataSpec): Long {
        val attemptId = htspConnection.currentConnectionAttemptId()
        subscriptionLifecycle.ownershipAttemptId()?.let { ownershipAttemptId ->
            if (
                htspConnection.connectionAttemptStatus(ownershipAttemptId) !=
                HtspConnectionAttemptStatus.LIVE
            ) {
                subscriptionLifecycle.connectionAttemptUnavailable(ownershipAttemptId)
            }
        }
        check(
            htspConnection.connectionAttemptStatus(attemptId) ==
                HtspConnectionAttemptStatus.LIVE
        ) {
            "HTSP transport is unavailable before subscription open"
        }
        connectionAttemptId = attemptId
        startPumpIfNeeded()
        Timber.d("Opening subscription data source (%d)", dataSourceNumber)
        this.dataSpec = dataSpec

        if (!subscriptionLifecycle.hasAcceptedSubscription(attemptId)) {
            val path = dataSpec.uri.path
            Timber.d("We are not yet subscribed to path %s", path)

            if (!path.isNullOrBlank() && path.length > 1) {
                val channelId = path.substring(1).toInt()
                check(subscriptionLifecycle.beginSubscriptionRequest(attemptId)) {
                    "Subscription ownership has not settled for this data source"
                }

                Timber.d(
                    "Sending subscription start (subscriptionId=%s channelId=%s)",
                    subscriptionId,
                    channelId
                )

                try {
                    runBlocking {
                        val response = htspConnection.startSubscription(
                            expectedConnectionAttemptId = attemptId,
                            subscriptionId = subscriptionId,
                            channelId = channelId,
                            timeshiftPeriodSec = if (timeshiftEnabled) {
                                REQUESTED_TIMESHIFT_PERIOD_SEC
                            } else {
                                0
                            },
                            profile = streamProfile,
                        )

                        val availableTimeshiftPeriod = response.availableTimeshiftPeriodSec
                        if (availableTimeshiftPeriod != null) {
                            timeshiftPeriod = availableTimeshiftPeriod.coerceAtLeast(0)
                            Timber.d(
                                "Available timeshift period in seconds: %s",
                                availableTimeshiftPeriod
                            )
                        }
                    }
                } catch (error: Throwable) {
                    val cleanupFailure = if (
                        subscriptionLifecycle.failedOpenRequiresUnsubscribe(attemptId)
                    ) {
                        unsubscribeClaimed(attemptId)
                    } else {
                        null
                    }
                    if (cleanupFailure != null) error.addSuppressed(cleanupFailure)
                    if (subscriptionLifecycle.ownershipAttemptId() == null) {
                        connectionAttemptId = null
                    }
                    throw error
                }

                val acceptance = checkNotNull(
                    htspConnection.commitIfLiveConnectionAttempt(attemptId) {
                        subscriptionLifecycle.subscriptionAccepted(attemptId)
                    }
                ) {
                    subscriptionLifecycle.connectionAttemptUnavailable(attemptId)
                    connectionAttemptId = null
                    "HTSP transport changed after subscription open"
                }
                if (acceptance == LiveSubscriptionAcceptance.RELEASE_IMMEDIATELY) {
                    check(subscriptionLifecycle.pendingUnsubscribeRequiresUnsubscribe())
                    unsubscribeClaimed(attemptId)?.let { error ->
                        throw SubscriptionSettlementException(error)
                    }
                    connectionAttemptId = null
                    throw IOException("Subscription data source released during open")
                }
            }
        }

        val started = checkNotNull(
            htspConnection.commitIfLiveConnectionAttempt(attemptId) {
                subscriptionLifecycle.commitIfOwned(attemptId) {
                    subscriptionStarted = true
                }
            }
        ) {
            subscriptionLifecycle.connectionAttemptUnavailable(attemptId)
            connectionAttemptId = null
            "HTSP transport changed before subscription start"
        }
        check(started) { "Subscription data source released before subscription start" }
        return C.LENGTH_UNSET.toLong()
    }

    private fun startPumpIfNeeded() {
        if (eventJob != null) return
        val sourceAttemptId = requireNotNull(connectionAttemptId)
        val initialMuxSequence = checkNotNull(
            htspConnection.currentMuxSequenceForConnectionAttempt(sourceAttemptId)
        ) { "HTSP mux cursor is unavailable for the subscription attempt" }
        muxDeliveryTracker = HtspMuxDeliveryTracker(initialMuxSequence)

        eventJob = jobScope.launchSubscriptionEventPump(
            controlEvents = htspConnection.controlEvents,
            muxEvents = htspConnection.muxEvents,
            onControl = control@{ ev ->
                val event = ev as? HtspEvent.ServerMessage ?: return@control
                val sourceAttemptId = connectionAttemptId ?: return@control
                if (event.connectionAttemptId != sourceAttemptId) return@control
                if (!htspConnection.isCurrentConnectionAttemptId(sourceAttemptId)) return@control
                val msg = event.msg
                val msgSubId = msg.int("subscriptionId")
                if (msg.method == "subscriptionStatus" && msgSubId == null) return@control
                if (msgSubId != null && msgSubId != subscriptionId) return@control
                if (
                    htspConnection.commitIfCurrentConnectionAttempt(sourceAttemptId) {
                        sharedTransportDiagnostics.value = updatePlaybackTransportDiagnostics(
                            current = sharedTransportDiagnostics.value,
                            message = msg,
                            subscriptionId = subscriptionId,
                        )
                    } == null
                ) return@control

                when (msg.method) {
                    "subscriptionStatus" -> {
                        val failure = subscriptionFailureKind(
                            subscriptionError = msg.str("subscriptionError")
                                ?: msg.str("error"),
                            state = msg.str("state") ?: msg.str("status"),
                        )
                        if (failure != null) {
                            invalidateTerminalSubscription(sourceAttemptId, failure)
                        }
                    }

                    "timeshiftStatus" -> {
                        htspConnection.commitIfCurrentConnectionAttempt(sourceAttemptId) {
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
                    }

                    "subscriptionStart" -> {
                        val started =
                            htspConnection.commitIfCurrentConnectionAttempt(sourceAttemptId) {
                                subscriptionLifecycle.commitIfOwned(sourceAttemptId) {
                                    subscriptionStarted = true
                                }
                            } ?: return@control
                        if (!started) return@control
                        // Write the start frame first, then release any muxpkts that
                        // arrived before it (preserving their original order).
                        if (!writeFramedMessage(msg, sourceAttemptId)) {
                            invalidateStaleSubscription()
                            return@control
                        }
                        val drained = htspConnection.commitIfCurrentConnectionAttempt(
                            sourceAttemptId
                        ) {
                            synchronized(startGate) {
                                subscriptionStartWritten = true
                                pendingMux.toList().also { pendingMux.clear() }
                            }
                        } ?: run {
                            invalidateStaleSubscription()
                            return@control
                        }
                        drained.forEach {
                            if (!writeFramedMessage(it, sourceAttemptId)) {
                                invalidateStaleSubscription()
                                return@control
                            }
                        }
                    }

                    "subscriptionStop" -> {
                        if (
                            htspConnection.commitIfCurrentConnectionAttempt(sourceAttemptId) {
                                subscriptionStarted = false
                                sharedTimeshiftState.value = TimeshiftState()
                                synchronized(startGate) {
                                    subscriptionStartWritten = false
                                    pendingMux.clear()
                                }
                            } == null
                        ) return@control
                        timeshiftSeekMuxGate.cancelCurrent()
                        lock.lock()
                        try {
                            notEmpty.signalAll()
                            notFull.signalAll()
                        } finally {
                            lock.unlock()
                        }
                    }

                    "subscriptionSkip" -> {
                        if (
                            htspConnection.commitIfCurrentConnectionAttempt(sourceAttemptId) {
                                true
                            } == null
                        ) return@control
                        var transitionAttempted = false
                        val committed = timeshiftSeekMuxGate.acknowledge(
                            messageSequence = event.messageSequence,
                            succeeded = msg.int("error") != 1,
                        ) { clearBufferedFrames, readyMux ->
                            transitionAttempted = true
                            val prepared = if (!clearBufferedFrames) {
                                true
                            } else {
                                clearBufferedFramesForSkip(sourceAttemptId) &&
                                    writeFramedMessage(
                                        message = TIMESHIFT_DISCONTINUITY_MESSAGE,
                                        expectedConnectionAttemptId = sourceAttemptId,
                                    )
                            }
                            prepared && readyMux.all {
                                writeFramedMessage(it.msg, sourceAttemptId)
                            }
                        }
                        if (transitionAttempted && !committed) {
                            invalidateStaleSubscription()
                        }
                    }
                }
            },
            onMux = mux@{ event: HtspMuxEvent ->
                val sourceAttemptId = connectionAttemptId ?: return@mux
                if (event.connectionAttemptId != sourceAttemptId) return@mux
                if (!htspConnection.isCurrentConnectionAttemptId(sourceAttemptId)) return@mux
                if (muxDeliveryTracker?.accept(event.muxSequence) != true) {
                    invalidateStaleSubscription()
                    return@mux
                }
                val msg = event.msg
                val msgSubId = msg.int("subscriptionId")
                if (msgSubId != null && msgSubId != subscriptionId) return@mux
                when (
                    timeshiftSeekMuxGate.offer(event) { muxEvent, writePermit ->
                        // If subscriptionStart hasn't been framed yet, hold the muxpkt back
                        // so the extractor never sees a packet before its stream definition.
                        val deferred = synchronized(startGate) {
                            if (!subscriptionStartWritten) {
                                pendingMux.addLast(muxEvent.msg)
                                while (pendingMux.size > MAX_PENDING_MUX) {
                                    pendingMux.removeFirst()
                                }
                                true
                            } else {
                                false
                            }
                        }
                        deferred || writeFramedMessage(
                            message = muxEvent.msg,
                            expectedConnectionAttemptId = sourceAttemptId,
                            writePermit = writePermit,
                        )
                    }
                ) {
                    TimeshiftMuxOffer.QUEUED,
                    TimeshiftMuxOffer.DROPPED_STALE,
                    TimeshiftMuxOffer.WRITTEN -> Unit
                    TimeshiftMuxOffer.FAILED -> invalidateStaleSubscription()
                }
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

            val actuallyRead = ring.read(buffer, offset, readLength)
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
        closeLocalReader()
    }

    private fun release() {
        Timber.d("Releasing subscription data source (%d)", dataSourceNumber)
        try {
            val attemptId = subscriptionLifecycle.ownershipAttemptId()
            val unsubscribeRequired = subscriptionLifecycle.retireRequiresUnsubscribe()
            closeLocalReader()

            var unsubscribeFailure: Throwable? = null
            if (unsubscribeRequired) {
                unsubscribeFailure = unsubscribeClaimed(
                    checkNotNull(attemptId) {
                        "Subscription lifecycle claimed unsubscribe without an attempt"
                    }
                )
            }

            val settled = subscriptionLifecycle.awaitOwnershipSettlement(
                SUBSCRIPTION_SETTLEMENT_TIMEOUT_MS
            )
            if (settled) connectionAttemptId = null
            if (!settled || unsubscribeFailure != null) {
                throw IOException("Subscription teardown did not settle", unsubscribeFailure)
            }
        } finally {
            dataSourceJob.cancel()
        }
    }

    private fun closeLocalReader() {
        subscriptionStarted = false
        timeshiftSeekMuxGate.cancelCurrent()

        eventJob?.cancel()
        eventJob = null
        muxDeliveryTracker = null

        lock.lock()
        try {
            notEmpty.signalAll()
            notFull.signalAll()
        } finally {
            lock.unlock()
        }

    }

    private fun unsubscribe(attemptId: Long) {
        runBlocking {
            htspConnection.stopSubscription(
                expectedConnectionAttemptId = attemptId,
                subscriptionId = subscriptionId,
            )
        }
    }

    private fun unsubscribeClaimed(attemptId: Long): Throwable? {
        val failure = when (htspConnection.connectionAttemptStatus(attemptId)) {
            HtspConnectionAttemptStatus.LIVE -> try {
                unsubscribe(attemptId)
                null
            } catch (error: Throwable) {
                if (
                    htspConnection.connectionAttemptStatus(attemptId) ==
                    HtspConnectionAttemptStatus.LIVE
                ) {
                    error
                } else {
                    null
                }
            }

            HtspConnectionAttemptStatus.GONE,
            HtspConnectionAttemptStatus.REPLACED -> null
        }
        subscriptionLifecycle.unsubscribeSettled(attemptId, success = failure == null)
        if (failure != null) Timber.w(failure, "unsubscribe failed (%d)", dataSourceNumber)
        return failure
    }

    override fun pause() {
        Timber.d("Pausing subscription data source (%d)", dataSourceNumber)
        runBlocking {
            htspConnection.setSubscriptionSpeed(
                expectedConnectionAttemptId = requireNotNull(connectionAttemptId),
                subscriptionId = subscriptionId,
                speed = 0,
            )
        }
        connectionAttemptId?.let { attemptId ->
            htspConnection.commitIfCurrentConnectionAttempt(attemptId) {
                sharedTimeshiftState.value = sharedTimeshiftState.value.copy(paused = true)
            }
        }
    }

    override val timeshiftState: StateFlow<TimeshiftState>
        get() = sharedTimeshiftState.asStateFlow()

    override val timeshiftOffsetPts: Long
        get() = sharedTimeshiftState.value.positionMs * 90L

    override fun setSpeed(tvhSpeed: Int) {
        runBlocking {
            htspConnection.setSubscriptionSpeed(
                expectedConnectionAttemptId = requireNotNull(connectionAttemptId),
                subscriptionId = subscriptionId,
                speed = tvhSpeed,
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
            htspConnection.setSubscriptionSpeed(
                expectedConnectionAttemptId = requireNotNull(connectionAttemptId),
                subscriptionId = subscriptionId,
                speed = 100,
            )
        }
        connectionAttemptId?.let { attemptId ->
            htspConnection.commitIfCurrentConnectionAttempt(attemptId) {
                sharedTimeshiftState.value = sharedTimeshiftState.value.copy(paused = false)
            }
        }
    }

    override fun seekTimeshift(deltaMs: Long): TimeshiftSeekDecision {
        val state = sharedTimeshiftState.value
        val decision = timeshiftSeek(state, deltaMs)
        if (decision.deltaMs != 0L) {
            val acknowledgement = timeshiftSeekMuxGate.beginSeek()
            wakeBufferedWriters()
            try {
                val acknowledged = runBlocking {
                    val absoluteTargetUs = timeshiftAbsoluteTargetUs(state, decision)
                    htspConnection.seekSubscription(
                        expectedConnectionAttemptId = requireNotNull(connectionAttemptId),
                        subscriptionId = subscriptionId,
                        timeUs = absoluteTargetUs ?: decision.deltaMs * 1_000L,
                        absolute = absoluteTargetUs != null,
                    )
                    withTimeoutOrNull(SKIP_ACK_TIMEOUT_MS) {
                        acknowledgement.await()
                    } == true
                }
                check(acknowledged) { "TVHeadend did not confirm the timeshift seek" }
            } finally {
                if (timeshiftSeekMuxGate.cancel(acknowledgement)) {
                    invalidateStaleSubscription()
                }
            }
        }
        return decision
    }

    override fun goLive(): TimeshiftSeekDecision =
        seekTimeshift(sharedTimeshiftState.value.liveEdgeMs - sharedTimeshiftState.value.positionMs)

    private fun clearBufferedFramesForSkip(
        expectedConnectionAttemptId: Long? = null,
    ): Boolean {
        lock.lock()
        try {
            val cleared = if (expectedConnectionAttemptId == null) {
                ring.clear()
                true
            } else {
                htspConnection.commitIfCurrentConnectionAttempt(expectedConnectionAttemptId) {
                    if (!subscriptionStarted) {
                        false
                    } else {
                        ring.clearCompleteFramesForSeek()
                        true
                    }
                } ?: false
            }
            if (cleared) {
                notEmpty.signalAll()
                notFull.signalAll()
            }
            return cleared
        } finally {
            lock.unlock()
        }
    }

    /**
     * Writes a framed message into ring buffer:
     * [int32 payloadLen][payload bytes]
     */
    private fun writeFramedMessage(
        message: HtspMessage,
        expectedConnectionAttemptId: Long? = null,
        writePermit: TimeshiftMuxWritePermit? = null,
    ): Boolean {
        val frame = try {
            HtspFramedCodec.frameMessage(message)
        } catch (t: Throwable) {
            Timber.e(t, "Failed to encode message (%d)", dataSourceNumber)
            return false
        }
        if (frame.size > BUFFER_SIZE) {
            Timber.e("Framed HTSP message exceeds the subscription buffer")
            return false
        }

        val written: Boolean
        lock.lock()
        try {
            while (subscriptionStarted && frame.size > ring.free()) {
                if (writePermit != null && !writePermit.commit { true }) return false
                if (
                    expectedConnectionAttemptId != null &&
                    !htspConnection.isCurrentConnectionAttemptId(
                        expectedConnectionAttemptId
                    )
                ) return false
                try {
                    notFull.await(100L, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            if (!subscriptionStarted) return false
            val writeFrame = {
                ring.write(frame, 0, frame.size) { needed ->
                    subscriptionStarted && needed <= ring.free()
                }
            }
            val writeWithPermit = {
                writePermit?.commit(writeFrame) ?: writeFrame()
            }
            written = if (expectedConnectionAttemptId == null) {
                writeWithPermit()
            } else {
                htspConnection.commitIfCurrentConnectionAttempt(
                    expectedConnectionAttemptId
                ) {
                    writeWithPermit()
                } ?: false
            }
            if (written) notEmpty.signalAll()
        } finally {
            lock.unlock()
        }
        return written
    }

    private fun wakeBufferedWriters() {
        lock.lock()
        try {
            notFull.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private fun invalidateStaleSubscription() {
        subscriptionStarted = false
        timeshiftSeekMuxGate.cancelCurrent()
        sharedTimeshiftState.value = TimeshiftState()
        synchronized(startGate) {
            subscriptionStartWritten = false
            pendingMux.clear()
        }
        clearBufferedFramesForSkip()
    }

    private fun invalidateTerminalSubscription(
        attemptId: Long,
        failure: SubscriptionFailureKind,
    ) {
        sharedSubscriptionFailure.value = failure
        val unsubscribeRequired = subscriptionLifecycle.terminalFailureRequiresUnsubscribe()
        invalidateStaleSubscription()
        if (unsubscribeRequired) {
            unsubscribeClaimed(attemptId)
        }
    }

    companion object {
        private const val SKIP_ACK_TIMEOUT_MS = 5_000L
        private const val SUBSCRIPTION_SETTLEMENT_TIMEOUT_MS = 11_000L
        private val dataSourceCount = AtomicInteger()
        private val subscriptionCount = AtomicInteger()

        private const val BUFFER_SIZE = 10 * 1024 * 1024

        // Safety cap on muxpkts buffered while waiting for subscriptionStart (normally
        // only a handful arrive before it); drop oldest beyond this to bound memory.
        private const val MAX_PENDING_MUX = 4096
    }
}

private class SubscriptionSettlementException(cause: Throwable) : IOException(
    "Subscription ownership did not settle",
    cause,
)
