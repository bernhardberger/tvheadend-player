package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.core.Coverage
import at.bernhardberger.tvhplayer.core.EpgCoverage
import at.bernhardberger.tvhplayer.core.epgWarmupProgress
import at.bernhardberger.tvhplayer.core.epgRetentionWindow
import at.bernhardberger.tvhplayer.core.selectEpgTopUpChannelIds
import at.bernhardberger.tvhplayer.htsp.ChannelMetadataEffect
import at.bernhardberger.tvhplayer.htsp.ChannelMetadataRepository
import at.bernhardberger.tvhplayer.htsp.ChannelTagUi
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.ChannelEpgRuntime
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.htsp.EpgMetadataEffect
import at.bernhardberger.tvhplayer.htsp.EpgMetadataIngestResult
import at.bernhardberger.tvhplayer.htsp.EpgMetadataRepository
import at.bernhardberger.tvhplayer.htsp.HtspEvent
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.htsp.HtspLogLevel
import at.bernhardberger.tvhplayer.htsp.HtspLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class EpgRuntimeTimings(
    val warmupFutureSec: Long = 4 * 3_600L,
    val steadyMinFutureSec: Long = 20 * 3_600L,
    val steadyMaxFutureSec: Long = 24 * 3_600L,
    val topUpChunkSec: Long = 4 * 3_600L,
    val perChannelCooldownSec: Long = 10 * 60L,
    val requestDelayMs: Long = 250L,
    val idleDelayMs: Long = 3_000L,
)

internal class TvhRepository(
    private val htsp: HtspService,
    ioDispatcher: CoroutineDispatcher,
    private val logger: HtspLogger = HtspLogger.None,
    private val timings: EpgRuntimeTimings = EpgRuntimeTimings(),
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : ChannelEpgRuntime {
    private val runtimeJob = SupervisorJob()
    private val scope = CoroutineScope(runtimeJob + ioDispatcher)
    private val lifecycleLock = Any()
    private var closed = false

    // ---------------------------
    // Tunables (your requested behavior)
    // ---------------------------

    /**
     * Fast start: fill a small window quickly so UI is usable right away.
     */
    private val warmupFutureSec = timings.warmupFutureSec

    @Volatile
    private var warmupCompleted = false

    /**
     * After warmup, keep at least this much future ahead (top-up when below).
     */
    private val steadyMinFutureSec = timings.steadyMinFutureSec

    /**
     * Try to keep up to this much future (cap) so cache doesn't balloon.
     */
    private val steadyMaxFutureSec = timings.steadyMaxFutureSec

    /**
     * Each top-up extends horizon by this much (per request).
     * (Keep it moderate so you don't DDOS tvheadend.)
     */
    private val topUpChunkSec = timings.topUpChunkSec

    /**
     * Don't refresh same channel too often (prevents spinning).
     */
    private val perChannelCooldownSec = timings.perChannelCooldownSec

    // Worker pacing
    private val requestDelayMs = timings.requestDelayMs
    private val idleDelayMs = timings.idleDelayMs

    // ---------------------------
    // Channels
    // ---------------------------

    private val channelMetadataRepository = ChannelMetadataRepository()
    override val channelsUi: StateFlow<List<ChannelUi>> = channelMetadataRepository.channelsUi
    override val tagsUi: StateFlow<List<ChannelTagUi>> = channelMetadataRepository.tagsUi
    override val metadataReady: StateFlow<Boolean> = channelMetadataRepository.metadataReady

    // ---------------------------
    // EPG store
    // ---------------------------

    private val epgMetadataRepository = EpgMetadataRepository()
    override fun epgForChannel(channelId: Int): StateFlow<List<EpgEventEntry>> =
        epgMetadataRepository.epgForChannel(channelId)

    /**
     * Coverage is what replaces "NOT_LOADED/LOADED".
     * We use it to maintain a sliding horizon.
     */
    private val epgCoverage = mutableMapOf<Int, EpgCoverage>()
    private val epgRetentionAnchor = mutableMapOf<Int, Long>()

    /**
     * Prevent parallel duplicate requests per channel.
     */
    private val epgInFlight = mutableMapOf<Int, Any>()

    private val stateMutex = Mutex()
    private val connectionAttemptLock = Any()
    @Volatile
    private var latestConnectionAttemptId = 0L
    private var boundFrontierGeneration: FrontierGeneration? = null
    private val frontierJobs = mutableSetOf<Job>()

    // Worker
    private var epgWorkerJob: Job? = null

    // Lifecycle
    @Volatile
    private var started = false

    fun startIfNeeded() {
        synchronized(lifecycleLock) {
            check(!closed) { "TvhRepository is closed" }
            if (started) return
            started = true
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            htsp.controlEvents.collect { e ->
                when (e) {
                    is HtspEvent.ServerMessage -> acceptMetadataMessage(
                        msg = e.msg,
                        connectionAttemptId = e.connectionAttemptId,
                    )
                    else -> {}
                }
            }
        }
    }

    suspend fun onDisconnected(connectionAttemptId: Long = 0L): Boolean {
        val staleFrontierJobs = stateMutex.withLock {
            htsp.commitIfCurrentConnectionAttempt(connectionAttemptId) {
                stopEpgWorker()
                synchronized(connectionAttemptLock) {
                    val jobs = clearFrontierGenerationLocked()
                    resetForNewConnectionLocked(preservePublishedChannels = true)
                    jobs
                }
            }
        } ?: return false
        staleFrontierJobs.forEach(Job::cancel)
        return true
    }

    suspend fun onNewConnectionStarting(
        preservePublishedChannels: Boolean = true,
        attemptId: Long? = null,
    ) {
        val staleFrontierJobs = stateMutex.withLock {
            synchronized(connectionAttemptLock) {
                if (attemptId != null && attemptId < latestConnectionAttemptId) {
                    return@synchronized null
                }
                if (attemptId != null) latestConnectionAttemptId = attemptId
                val jobs = clearFrontierGenerationLocked()
                resetForNewConnectionLocked(preservePublishedChannels)
                jobs
            }
        } ?: return
        staleFrontierJobs.forEach(Job::cancel)
    }

    private fun resetForNewConnectionLocked(preservePublishedChannels: Boolean) {
        channelMetadataRepository.reset(preservePublished = preservePublishedChannels)

        if (!preservePublishedChannels) epgMetadataRepository.clear()
        epgCoverage.clear()
        epgRetentionAnchor.clear()
        epgInFlight.clear()

        // Force a fresh warmup for the new connection; coverage was just cleared.
        warmupCompleted = false
    }

    fun advanceConnectionAttempt(attemptId: Long) {
        val staleFrontierJobs = synchronized(connectionAttemptLock) {
            if (attemptId <= latestConnectionAttemptId) return@synchronized emptyList()
            latestConnectionAttemptId = attemptId
            clearFrontierGenerationLocked()
        }
        staleFrontierJobs.forEach(Job::cancel)
    }

    fun bindConnectionAttempt(repositoryAttemptId: Long, transportAttemptId: Long) {
        val staleFrontierJobs = htsp.commitIfCurrentConnectionAttempt(transportAttemptId) {
            synchronized(connectionAttemptLock) {
                if (latestConnectionAttemptId != repositoryAttemptId) {
                    null
                } else {
                    val generation = FrontierGeneration(
                        repositoryAttemptId = repositoryAttemptId,
                        transportAttemptId = transportAttemptId,
                    )
                    if (boundFrontierGeneration == generation) {
                        emptyList()
                    } else {
                        clearFrontierGenerationLocked().also {
                            boundFrontierGeneration = generation
                        }
                    }
                }
            }
        } ?: throw CancellationException("Superseded connection-attempt binding")
        staleFrontierJobs.forEach(Job::cancel)
    }

    suspend fun awaitChannelsReady(timeoutMs: Long = 30_000) {
        channelMetadataRepository.awaitChannelsReady(timeoutMs)
    }

    // ---------------------------
    // EPG Worker (warmup -> steady sliding horizon)
    // ---------------------------

    /**
     * Starts an EPG worker that:
     *  - warmup: fill ~4h future quickly for all channels
     *  - steady: keep future horizon between 20–24h by periodic top-ups
     *
     * You can call this after initialSyncCompleted (or whenever channels are ready).
     */
    fun startEpgWorker(
        batchSize: Int = 6,
        intervalMs: Long = 1_500L
    ) {
        if (epgWorkerJob?.isActive == true) return

        epgWorkerJob = scope.launch {
            while (isActive) {
              try {
                val nowSec = nowSec()

                val targets = stateMutex.withLock {
                    selectEpgTopUpChannelIds(
                        orderedChannelIds = channelMetadataRepository
                            .currentChannelSnapshot()
                            .map { channel -> channel.id },
                        coverageByChannelId = epgCoverage.toMap(),
                        inFlightChannelIds = epgInFlight.keys.toSet(),
                        wantedTo = nowSec + steadyMinFutureSec,
                        nowSec = nowSec,
                        cooldownSec = perChannelCooldownSec,
                        limit = batchSize
                    )
                }

                if (targets.isEmpty()) {
                    val warmupProgress = stateMutex.withLock {
                        epgWarmupProgress(
                            orderedChannelIds = channelMetadataRepository
                                .currentChannelSnapshot()
                                .map { channel -> channel.id },
                            coverageByChannelId = epgCoverage.toMap(),
                            wantedTo = nowSec + warmupFutureSec,
                            nowSec = nowSec,
                        )
                    }
                    if (
                        warmupProgress.total > 0 &&
                        warmupProgress.completed >= warmupProgress.total
                    ) {
                        warmupCompleted = true
                    }
                    delay(idleDelayMs)
                    continue
                }

                for (chId in targets) {
                    if (!isActive) break
                    fetchEpgTopUpOnce(channelId = chId, nowSec = nowSec)
                    delay(requestDelayMs)
                }

                delay(intervalMs)
              } catch (ce: CancellationException) {
                  throw ce
              } catch (t: Throwable) {
                  // A single failed iteration (transient request/parse error) must never
                  // kill the worker, otherwise EPG would silently stop refreshing.
                   logger.log(
                       HtspLogLevel.WARNING,
                       "EPG worker iteration failed; backing off",
                       t,
                   )
                  delay(idleDelayMs)
              }
            }
        }
    }

    fun stopEpgWorker() {
        epgWorkerJob?.cancel()
        epgWorkerJob = null
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

    /**
     * Decides how far we should fetch for a channel right now.
     * Uses a sliding horizon:
     *  - if channel isn't warmed up, aim for now+warmupFuture
     *  - else keep horizon >= now+steadyMinFuture, extending in chunks up to steadyMaxFuture
     */
    private suspend fun fetchEpgTopUpOnce(channelId: Int, nowSec: Long): Boolean {
        val reservation = Any()
        // reserve channel
        stateMutex.withLock {
            val existingReservation = epgInFlight[channelId]
            if (
                existingReservation != null &&
                !synchronized(connectionAttemptLock) {
                    isStaleFrontierReservationLocked(existingReservation)
                }
            ) {
                return false
            }
            epgInFlight[channelId] = reservation
            epgCoverage.getOrPut(channelId) { EpgCoverage() }
        }

        try {
            val desiredMaxTo = nowSec + steadyMaxFutureSec
            val desiredWarmTo = nowSec + warmupFutureSec
            val desiredMinTo = nowSec + steadyMinFutureSec

            val targetTo: Long = stateMutex.withLock {
                val cov = epgCoverage[channelId] ?: EpgCoverage()

                // Cooldown check (avoid hammering same channel)
                if (!cov.needsTopUp(desiredMinTo, nowSec, perChannelCooldownSec)) return false

                val target = cov.nextTargetTo(
                    desiredWarmTo = desiredWarmTo,
                    desiredMinTo = desiredMinTo,
                    desiredMaxTo = desiredMaxTo,
                    chunkSec = topUpChunkSec,
                ) ?: return false
                epgCoverage[channelId] = cov.afterAttempt(nowSec)
                target
            }

            // If we're here, we want to fetch up to targetTo.
            // HTSP maxTime is a Unix timestamp in seconds.
            val reply = try {
                htsp.request(
                    method = "getEvents",
                    fields = mapOf(
                        "channelId" to channelId,
                        "maxTime" to targetTo,
                    ),
                    timeoutMs = 20_000,
                    disconnectOnTimeout = false,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return false
            }
            currentCoroutineContext().ensureActive()

            if (reply.fields.containsKey("error")) return false

            stateMutex.withLock {
                // ingest + update coverage from reply
                val retentionAnchor = epgRetentionAnchor.getOrPut(channelId) { nowSec }
                applyEpgIngestResultLocked(
                    epgMetadataRepository.ingestGetEventsReply(reply, anchorSec = retentionAnchor)
                )
                // An empty response still confirms that the server has no events in
                // this range; do not query the same empty horizon forever.
                epgCoverage[channelId] = epgCoverage
                    .getOrPut(channelId) { EpgCoverage() }
                    .afterSuccessfulFetch(targetTo, nowSec)

                // Aggressive trimming pass (keeps cache in bounds)
                trimAllEpgLocked(nowSec)
                return true
            }
        } finally {
            releaseEpgReservation(channelId, reservation)
        }
    }

    override fun requestEpgAtFrontier(channelIds: List<Int>, anchorSec: Long) {
        if (channelIds.isEmpty()) return
        val job = synchronized(connectionAttemptLock) {
            val generation = boundFrontierGeneration ?: return
            scope.launch(start = CoroutineStart.LAZY) {
                for (channelId in channelIds.distinct()) {
                    ensureCurrentFrontierGeneration(generation)
                    fetchEpgWindowOnce(channelId, anchorSec, generation)
                    delay(requestDelayMs)
                }
            }.also(frontierJobs::add)
        }
        job.invokeOnCompletion {
            synchronized(connectionAttemptLock) {
                frontierJobs.remove(job)
            }
        }
        job.start()
    }

    private suspend fun fetchEpgWindowOnce(
        channelId: Int,
        anchorSec: Long,
        generation: FrontierGeneration,
    ): Boolean {
        ensureCurrentFrontierGeneration(generation)
        val reservation = FrontierReservation(generation)
        val reserved = stateMutex.withLock {
            synchronized(connectionAttemptLock) {
                if (boundFrontierGeneration != generation) {
                    throw CancellationException("Superseded frontier reservation admission")
                }
                val existingReservation = epgInFlight[channelId]
                if (
                    existingReservation != null &&
                    !isStaleFrontierReservationLocked(existingReservation)
                ) {
                    false
                } else {
                    epgInFlight[channelId] = reservation
                    true
                }
            }
        }
        if (!reserved) return false

        try {
            val window = epgRetentionWindow(anchorSec)
            val reply = try {
                ensureCurrentFrontierGeneration(generation)
                htsp.requestForConnectionAttemptIf(
                    expectedConnectionAttemptId = generation.transportAttemptId,
                    isRequestAdmitted = {
                        synchronized(connectionAttemptLock) {
                            boundFrontierGeneration == generation
                        }
                    },
                    method = "getEvents",
                    fields = mapOf(
                        "channelId" to channelId,
                        "minTime" to window.fromSec,
                        "maxTime" to window.toSec,
                    ),
                    timeoutMs = 20_000,
                    disconnectOnTimeout = false,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return false
            }
            currentCoroutineContext().ensureActive()
            if (reply.fields.containsKey("error")) return false

            return stateMutex.withLock {
                htsp.commitIfCurrentConnectionAttempt(generation.transportAttemptId) {
                    synchronized(connectionAttemptLock) {
                        if (boundFrontierGeneration != generation) {
                            false
                        } else {
                            epgRetentionAnchor[channelId] = anchorSec
                            applyEpgIngestResultLocked(
                                epgMetadataRepository.ingestGetEventsReply(reply, anchorSec)
                            )
                            epgCoverage[channelId] = epgCoverage
                                .getOrPut(channelId) { EpgCoverage() }
                                .afterSuccessfulFetch(window.toSec, nowSec())
                            trimChannelEpgLocked(channelId, anchorSec)
                            true
                        }
                    }
                } ?: false
            }
        } finally {
            releaseEpgReservation(channelId, reservation)
        }
    }

    // ---------------------------
    // Server message handling
    // ---------------------------

    internal suspend fun acceptMetadataMessage(
        msg: HtspMessage,
        connectionAttemptId: Long = 0L,
    ) {
        stateMutex.withLock {
            htsp.commitIfCurrentConnectionAttempt(connectionAttemptId) {
                when (
                    val effect = channelMetadataRepository.accept(
                        message = msg,
                        beforePublishing = ::prepareChannelMetadataEffectLocked,
                    )
                ) {
                    is ChannelMetadataEffect.ChannelUpserted -> {
                        if (effect.isNew && warmupCompleted) {
                            scope.launch {
                                fetchEpgTopUpOnce(channelId = effect.channelId, nowSec = nowSec())
                            }
                        }
                    }
                    is ChannelMetadataEffect.ChannelDeleted,
                    is ChannelMetadataEffect.InitialSyncCompleted -> Unit
                    null -> when (msg.method) {
                        // Async EPG updates (only when server data changes)
                        "eventAdd", "eventUpdate", "eventDelete" -> {
                            applyEpgMetadataEffectLocked(
                                epgMetadataRepository.accept(msg) { channelId ->
                                    epgRetentionAnchor[channelId] ?: nowSec()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun prepareChannelMetadataEffectLocked(effect: ChannelMetadataEffect) {
        when (effect) {
            is ChannelMetadataEffect.ChannelUpserted -> {
                epgCoverage.getOrPut(effect.channelId) { EpgCoverage() }
            }
            is ChannelMetadataEffect.ChannelDeleted -> {
                epgMetadataRepository.removeChannel(effect.channelId)
                epgCoverage.remove(effect.channelId)
                epgInFlight.remove(effect.channelId)
            }
            is ChannelMetadataEffect.InitialSyncCompleted -> {
                epgMetadataRepository.retainChannels(effect.channelIds)
                epgCoverage.keys.removeAll { it !in effect.channelIds }
            }
        }
    }

    // ---------------------------
    // Query helpers
    // ---------------------------

    override fun nowEvent(channelId: Int, nowSec: Long): EpgEventEntry? =
        epgMetadataRepository.nowEvent(channelId, nowSec)

    override fun nextEvent(channelId: Int, nowSec: Long): EpgEventEntry? =
        epgMetadataRepository.nextEvent(channelId, nowSec)

    // ---------------------------
    // Async event handling (updates coverage too)
    // ---------------------------

    private fun applyEpgMetadataEffectLocked(effect: EpgMetadataEffect?) {
        when (effect) {
            is EpgMetadataEffect.EventUpserted -> {
                val event = effect.event
                epgCoverage[event.channelId] = epgCoverage
                    .getOrPut(event.channelId) { EpgCoverage() }
                    .includingObservedCoverage(
                        Coverage(from = event.start, to = event.stop)
                    )
            }
            is EpgMetadataEffect.EventDeleted,
            null -> Unit
        }
    }

    // ---------------------------
    // Ingest getEvents reply + update coverage
    // ---------------------------

    private fun applyEpgIngestResultLocked(result: EpgMetadataIngestResult) {
        for ((channelId, bounds) in result.perChannelBounds) {
            epgCoverage[channelId] = epgCoverage
                .getOrPut(channelId) { EpgCoverage() }
                .includingObservedCoverage(
                    Coverage(from = bounds.earliestStart, to = bounds.latestStop)
                )
        }
    }

    // ---------------------------
    // Trimming / maintenance
    // ---------------------------

    private fun trimAllEpgLocked(nowSec: Long) {
        val retainedBounds = epgMetadataRepository.trimAll { channelId ->
            epgRetentionAnchor[channelId] ?: nowSec
        }

        // Re-derive coverage from what we actually still hold. Coverage must track
        // the retained events authoritatively (NOT a monotonic max): otherwise after
        // a long uptime the horizon can stay "high" while the cache has already been
        // trimmed empty, so the worker believes it is up to date and stops topping up
        // -> "No EPG" everywhere until reconnect. Resetting coverage for an emptied
        // channel makes the worker re-fetch it on the next tick (self-healing).
        for ((channelId, bounds) in retainedBounds) {
            epgCoverage[channelId] = epgCoverage
                .getOrPut(channelId) { EpgCoverage() }
                .withRetainedCoverage(
                    Coverage(from = bounds.earliestStart, to = bounds.latestStop)
                )
        }
    }

    private fun trimChannelEpgLocked(channelId: Int, anchorSec: Long) {
        epgMetadataRepository.trimChannel(channelId, anchorSec)
    }

    private suspend fun releaseEpgReservation(channelId: Int, reservation: Any) {
        withContext(NonCancellable) {
            stateMutex.withLock {
                if (epgInFlight[channelId] === reservation) {
                    epgInFlight.remove(channelId)
                }
            }
        }
    }

    // ---------------------------
    // Utils
    // ---------------------------

    private fun nowSec(): Long = epochSeconds()

    private fun ensureCurrentFrontierGeneration(generation: FrontierGeneration) {
        if (synchronized(connectionAttemptLock) { boundFrontierGeneration != generation }) {
            throw CancellationException("Superseded frontier repository/transport binding")
        }
        if (!htsp.isCurrentConnectionAttemptId(generation.transportAttemptId)) {
            throw CancellationException("Superseded frontier HTSP attempt")
        }
    }

    private fun clearFrontierGenerationLocked(): List<Job> {
        boundFrontierGeneration = null
        return frontierJobs.toList().also { frontierJobs.clear() }
    }

    private data class FrontierGeneration(
        val repositoryAttemptId: Long,
        val transportAttemptId: Long,
    )

    private data class FrontierReservation(
        val generation: FrontierGeneration,
    )

    private fun isStaleFrontierReservationLocked(reservation: Any): Boolean =
        reservation is FrontierReservation && reservation.generation != boundFrontierGeneration
}
