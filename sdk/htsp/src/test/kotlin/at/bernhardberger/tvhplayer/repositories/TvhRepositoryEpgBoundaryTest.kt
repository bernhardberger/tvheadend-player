package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TvhRepositoryEpgBoundaryTest {
    @Test
    fun warmupRequestsKeepChannelOrderCountAndArguments() = runBlocking {
        val htsp = RecordingHtspService(expectedRequestCount = 2)
        val repository = TvhRepository(htsp = htsp, ioDispatcher = Dispatchers.Unconfined)
        repository.acceptMetadataMessage(
            message(
                "channelAdd",
                "channelId" to 20,
                "channelName" to "Second",
                "channelNumber" to 2,
            )
        )
        repository.acceptMetadataMessage(
            message(
                "channelAdd",
                "channelId" to 10,
                "channelName" to "First",
                "channelNumber" to 1,
            )
        )
        repository.acceptMetadataMessage(message("initialSyncCompleted"))
        val beforeStartSec = System.currentTimeMillis() / 1_000L

        repository.startEpgWorker(batchSize = 2, intervalMs = 60_000L)
        withTimeout(2_000L) { htsp.expectedRequestsCompleted.await() }
        repository.stopEpgWorker()
        val afterStopSec = System.currentTimeMillis() / 1_000L

        val calls = htsp.calls
        assertEquals(2, calls.size)
        assertEquals(listOf(10, 20), calls.map { it.fields["channelId"] })
        val targetTo = calls.first().fields.getValue("maxTime") as Long
        assertTrue(targetTo >= beforeStartSec + 4 * 3_600L)
        assertTrue(targetTo <= afterStopSec + 4 * 3_600L)
        assertEquals(
            listOf(
                mapOf<String, Any?>("channelId" to 10, "maxTime" to targetTo),
                mapOf<String, Any?>("channelId" to 20, "maxTime" to targetTo),
            ),
            calls.map(RequestCall::fields),
        )
        assertTrue(calls.all { it.method == "getEvents" })
        assertTrue(calls.all { it.timeoutMs == 20_000L })
        assertTrue(calls.all(RequestCall::flush))
        assertTrue(calls.none(RequestCall::disconnectOnTimeout))
    }

    @Test
    fun frontierRequestsKeepDistinctInputOrderAndInclusiveWindowArguments() = runBlocking {
        val htsp = RecordingHtspService(expectedRequestCount = 3)
        val repository = TvhRepository(htsp = htsp, ioDispatcher = Dispatchers.Unconfined)
        repository.onNewConnectionStarting(attemptId = 1L)
        repository.bindConnectionAttempt(
            repositoryAttemptId = 1L,
            transportAttemptId = htsp.currentConnectionAttemptId(),
        )

        repository.requestEpgAtFrontier(
            channelIds = listOf(7, 3, 7, 9),
            anchorSec = 100_000L,
        )
        withTimeout(2_000L) { htsp.expectedRequestsCompleted.await() }

        assertEquals(
            listOf(
                mapOf<String, Any?>(
                    "channelId" to 7,
                    "minTime" to 78_400L,
                    "maxTime" to 186_400L,
                ),
                mapOf<String, Any?>(
                    "channelId" to 3,
                    "minTime" to 78_400L,
                    "maxTime" to 186_400L,
                ),
                mapOf<String, Any?>(
                    "channelId" to 9,
                    "minTime" to 78_400L,
                    "maxTime" to 186_400L,
                ),
            ),
            htsp.calls.map(RequestCall::fields),
        )
        assertTrue(htsp.calls.all { it.method == "getEvents" })
        assertTrue(htsp.calls.all { it.timeoutMs == 20_000L })
        assertTrue(htsp.calls.all(RequestCall::flush))
        assertTrue(htsp.calls.none(RequestCall::disconnectOnTimeout))
    }

    @Test
    fun asyncEventMappingPublishesExtendedMetadata() = runBlocking {
        val now = System.currentTimeMillis() / 1000L
        val repository = TvhRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )

        repository.acceptMetadataMessage(
            HtspMessage(
                method = "eventAdd",
                seq = null,
                fields = mapOf(
                    "eventId" to 12,
                    "channelId" to 3,
                    "start" to now,
                    "stop" to now + 1_000L,
                    "title" to "Programme",
                    "summary" to "Summary",
                    "description" to "Description",
                    "genre" to "News",
                    "contentType" to 0x20,
                    "seasonNumber" to 2,
                    "episodeNumber" to 6,
                ),
            )
        )

        val event = repository.epgForChannel(3).value.single()
        assertEquals("Description", event.description)
        assertEquals("News", event.genre)
        assertEquals(0x20, event.contentType)
        assertEquals(2, event.seasonNumber)
        assertEquals(6, event.episodeNumber)
    }

    @Test
    fun channelDeletePrunesEpgBeforePublishingTheUpdatedChannelList() = runBlocking {
        val repository = TvhRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val now = System.currentTimeMillis() / 1_000L
        repository.acceptMetadataMessage(
            message("channelAdd", "channelId" to 42, "channelName" to "Channel")
        )
        repository.acceptMetadataMessage(message("initialSyncCompleted"))
        repository.acceptMetadataMessage(
            eventMessage(eventId = 7, channelId = 42, start = now, stop = now + 60)
        )
        val eventsAtChannelPublication = async(start = CoroutineStart.UNDISPATCHED) {
            repository.channelsUi.drop(1).first { channels -> channels.none { it.id == 42 } }
            repository.epgForChannel(42).value
        }

        repository.acceptMetadataMessage(message("channelDelete", "channelId" to 42))

        assertTrue(eventsAtChannelPublication.await().isEmpty())
    }

    @Test
    fun staleReplacementAttemptCannotPublishEpgMetadata() = runBlocking {
        val htsp = HtspService(Dispatchers.Unconfined)
        val repository = TvhRepository(htsp = htsp, ioDispatcher = Dispatchers.Unconfined)
        val now = System.currentTimeMillis() / 1_000L

        htsp.disconnect()
        val firstAttempt = htsp.currentConnectionAttemptId()
        htsp.disconnect()
        val replacementAttempt = htsp.currentConnectionAttemptId()
        repository.onNewConnectionStarting(attemptId = replacementAttempt)

        repository.acceptMetadataMessage(
            eventMessage(eventId = 1, channelId = 10, start = now, stop = now + 60),
            connectionAttemptId = firstAttempt,
        )
        repository.acceptMetadataMessage(
            eventMessage(eventId = 2, channelId = 20, start = now, stop = now + 60),
            connectionAttemptId = replacementAttempt,
        )

        assertTrue(repository.epgForChannel(10).value.isEmpty())
        assertEquals(listOf(2), repository.epgForChannel(20).value.map { it.eventId })
    }

    @Test
    fun supersededFrontierRequestCannotPublishOrContinueIntoTheReplacement() = runBlocking {
        val anchorSec = 100_000L
        val htsp = DelayedFrontierHtspService(
            firstReply = message(
                method = null,
                "events" to listOf(
                    mapOf(
                        "eventId" to 7,
                        "channelId" to 42,
                        "start" to anchorSec,
                        "stop" to anchorSec + 60L,
                        "title" to "Stale frontier event",
                    )
                ),
            )
        )
        val repository = TvhRepository(
            htsp = htsp,
            ioDispatcher = Dispatchers.Unconfined,
            timings = EpgRuntimeTimings(requestDelayMs = 0L),
            epochSeconds = { anchorSec },
        )

        try {
            repository.onNewConnectionStarting(attemptId = 1L)
            repository.bindConnectionAttempt(
                repositoryAttemptId = 1L,
                transportAttemptId = htsp.currentConnectionAttemptId(),
            )
            repository.requestEpgAtFrontier(
                channelIds = listOf(42, 43),
                anchorSec = anchorSec,
            )
            withTimeout(1_000L) { htsp.firstRequestStarted.await() }

            repository.onNewConnectionStarting(attemptId = 2L)
            htsp.releaseFirstReply.complete(Unit)
            withTimeout(1_000L) { htsp.firstReplyReleased.await() }
            launch(Dispatchers.Unconfined) {}.join()

            assertTrue(repository.epgForChannel(42).value.isEmpty())
            assertEquals(listOf(42), htsp.requestedChannelIds)
        } finally {
            repository.close()
        }
    }

    @Test
    fun advancedRepositoryGenerationCannotUseTheStillCurrentPreviousTransport() = runBlocking {
        val htsp = RecordingHtspService(expectedRequestCount = 1)
        val repository = TvhRepository(
            htsp = htsp,
            ioDispatcher = Dispatchers.Unconfined,
            timings = EpgRuntimeTimings(requestDelayMs = 0L),
        )
        val previousTransportAttemptId = htsp.currentConnectionAttemptId()

        repository.onNewConnectionStarting(attemptId = 1L)
        repository.bindConnectionAttempt(
            repositoryAttemptId = 1L,
            transportAttemptId = previousTransportAttemptId,
        )
        repository.advanceConnectionAttempt(attemptId = 2L)
        repository.requestEpgAtFrontier(channelIds = listOf(42), anchorSec = 100_000L)

        assertTrue(htsp.calls.isEmpty())
        repository.close()
    }

    @Test
    fun advancementBetweenPrecheckAndDispatchRejectsOldAdmissionAndReservation() = runBlocking {
        val htsp = FrontierAdmissionHtspService()
        val repository = TvhRepository(
            htsp = htsp,
            ioDispatcher = Dispatchers.Unconfined,
            timings = EpgRuntimeTimings(requestDelayMs = 0L),
        )
        val transportAttemptId = htsp.currentConnectionAttemptId()

        try {
            repository.onNewConnectionStarting(attemptId = 1L)
            repository.bindConnectionAttempt(
                repositoryAttemptId = 1L,
                transportAttemptId = transportAttemptId,
            )
            repository.requestEpgAtFrontier(channelIds = listOf(42), anchorSec = 100_000L)
            withTimeout(1_000L) { htsp.firstAdmissionReached.await() }

            repository.advanceConnectionAttempt(attemptId = 2L)
            htsp.releaseFirstAdmission.complete(Unit)
            assertTrue(withTimeout(1_000L) { htsp.firstAdmissionRejected.await() })

            repository.bindConnectionAttempt(
                repositoryAttemptId = 2L,
                transportAttemptId = transportAttemptId,
            )
            repository.requestEpgAtFrontier(channelIds = listOf(42), anchorSec = 100_000L)

            assertEquals(listOf(42), htsp.requestedChannelIds)
        } finally {
            htsp.releaseFirstCompletion.complete(Unit)
            repository.close()
        }
    }

    @Test
    fun cancelledOldWorkerCannotApplyItsDelayedReplyToReplacementState() = runBlocking {
        val now = System.currentTimeMillis() / 1_000L
        val htsp = DelayedReplyHtspService(
            reply = message(
                method = null,
                "events" to listOf(
                    mapOf(
                        "eventId" to 7,
                        "channelId" to 42,
                        "start" to now,
                        "stop" to now + 60,
                        "title" to "Stale",
                    )
                ),
            )
        )
        val repository = TvhRepository(htsp = htsp, ioDispatcher = Dispatchers.Unconfined)
        repository.acceptMetadataMessage(
            message("channelAdd", "channelId" to 42, "channelName" to "Old")
        )
        repository.acceptMetadataMessage(message("initialSyncCompleted"))
        repository.startEpgWorker(batchSize = 1, intervalMs = 60_000L)
        withTimeout(1_000L) { htsp.requestStarted.await() }

        repository.onDisconnected()
        repository.onNewConnectionStarting(preservePublishedChannels = true)
        repository.acceptMetadataMessage(
            message("channelAdd", "channelId" to 42, "channelName" to "Replacement")
        )
        repository.acceptMetadataMessage(message("initialSyncCompleted"))
        htsp.releaseReply.complete(Unit)
        withTimeout(1_000L) { htsp.replyReleased.await() }
        launch(Dispatchers.Unconfined) {}.join()

        assertTrue(repository.epgForChannel(42).value.isEmpty())
    }

    @Test
    fun cancelledOldRequestCannotReleaseReplacementReservation() = runBlocking {
        val htsp = ReservationHtspService()
        val repository = TvhRepository(htsp = htsp, ioDispatcher = Dispatchers.Unconfined)
        repository.acceptMetadataMessage(
            message("channelAdd", "channelId" to 42, "channelName" to "Old")
        )
        repository.acceptMetadataMessage(message("initialSyncCompleted"))
        repository.startEpgWorker(batchSize = 1, intervalMs = 60_000L)
        withTimeout(1_000L) { htsp.firstRequestStarted.await() }

        repository.onDisconnected()
        repository.onNewConnectionStarting(preservePublishedChannels = true)
        repository.acceptMetadataMessage(
            message("channelAdd", "channelId" to 42, "channelName" to "Replacement")
        )
        repository.acceptMetadataMessage(message("initialSyncCompleted"))
        repository.startEpgWorker(batchSize = 1, intervalMs = 60_000L)
        withTimeout(1_000L) { htsp.replacementRequestStarted.await() }

        htsp.releaseFirstRequest.complete(Unit)
        withTimeout(1_000L) { htsp.firstReplyReleased.await() }
        launch(Dispatchers.Unconfined) {}.join()
        repository.bindConnectionAttempt(
            repositoryAttemptId = 0L,
            transportAttemptId = htsp.currentConnectionAttemptId(),
        )
        repository.requestEpgAtFrontier(listOf(42), anchorSec = 10_000L)

        assertFalse(htsp.unexpectedThirdRequest.isCompleted)
        htsp.releaseReplacementRequest.complete(Unit)
        withTimeout(1_000L) { htsp.replacementReplyReleased.await() }
        repository.stopEpgWorker()
    }

    private class DelayedReplyHtspService(
        private val reply: HtspMessage,
    ) : HtspService(Dispatchers.Unconfined) {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseReply = CompletableDeferred<Unit>()
        val replyReleased = CompletableDeferred<Unit>()

        override suspend fun request(
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage = withContext(NonCancellable) {
            requestStarted.complete(Unit)
            releaseReply.await()
            replyReleased.complete(Unit)
            reply
        }
    }

    private class FrontierAdmissionHtspService : HtspService(Dispatchers.Unconfined) {
        val firstAdmissionReached = CompletableDeferred<Unit>()
        val releaseFirstAdmission = CompletableDeferred<Unit>()
        val firstAdmissionRejected = CompletableDeferred<Boolean>()
        val releaseFirstCompletion = CompletableDeferred<Unit>()
        private val admissionCount = AtomicInteger()
        private val requestedChannels = mutableListOf<Int>()
        val requestedChannelIds: List<Int>
            get() = synchronized(requestedChannels) { requestedChannels.toList() }

        override suspend fun requestForConnectionAttemptIf(
            expectedConnectionAttemptId: Long,
            isRequestAdmitted: () -> Boolean,
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage {
            if (admissionCount.incrementAndGet() == 1) {
                return withContext(NonCancellable) {
                    firstAdmissionReached.complete(Unit)
                    releaseFirstAdmission.await()
                    val admitted = isRequestAdmitted()
                    firstAdmissionRejected.complete(!admitted)
                    releaseFirstCompletion.await()
                    if (!admitted) throw CancellationException("Rejected stale frontier admission")
                    recordRequest(fields)
                    emptyReply()
                }
            }
            if (!isRequestAdmitted()) throw CancellationException("Rejected frontier admission")
            recordRequest(fields)
            return emptyReply()
        }

        private fun recordRequest(fields: Map<String, Any?>) {
            synchronized(requestedChannels) {
                requestedChannels += fields.getValue("channelId") as Int
            }
        }

        private fun emptyReply() = HtspMessage(
            method = null,
            seq = null,
            fields = mapOf("events" to emptyList<Any?>()),
        )
    }

    private class DelayedFrontierHtspService(
        private val firstReply: HtspMessage,
    ) : HtspService(Dispatchers.Unconfined) {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstReply = CompletableDeferred<Unit>()
        val firstReplyReleased = CompletableDeferred<Unit>()
        private val requestedChannels = mutableListOf<Int>()
        val requestedChannelIds: List<Int>
            get() = synchronized(requestedChannels) { requestedChannels.toList() }

        override suspend fun request(
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage = respond(fields)

        override suspend fun requestForConnectionAttempt(
            expectedConnectionAttemptId: Long,
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage = respond(fields)

        override suspend fun requestForConnectionAttemptIf(
            expectedConnectionAttemptId: Long,
            isRequestAdmitted: () -> Boolean,
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage {
            if (!isRequestAdmitted()) throw CancellationException("Rejected frontier admission")
            return respond(fields)
        }

        private suspend fun respond(fields: Map<String, Any?>): HtspMessage {
            val requestNumber = synchronized(requestedChannels) {
                requestedChannels += fields.getValue("channelId") as Int
                requestedChannels.size
            }
            if (requestNumber != 1) return emptyReply()
            return withContext(NonCancellable) {
                firstRequestStarted.complete(Unit)
                releaseFirstReply.await()
                firstReplyReleased.complete(Unit)
                firstReply
            }
        }

        private fun emptyReply() = HtspMessage(
            method = null,
            seq = null,
            fields = mapOf("events" to emptyList<Any?>()),
        )
    }

    private data class RequestCall(
        val method: String,
        val fields: Map<String, Any?>,
        val timeoutMs: Long,
        val flush: Boolean,
        val disconnectOnTimeout: Boolean,
    )

    private class RecordingHtspService(
        private val expectedRequestCount: Int,
    ) : HtspService(Dispatchers.Unconfined) {
        private val recordedCalls = mutableListOf<RequestCall>()
        val expectedRequestsCompleted = CompletableDeferred<Unit>()
        val calls: List<RequestCall>
            get() = synchronized(recordedCalls) { recordedCalls.toList() }

        override suspend fun request(
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage {
            val count = synchronized(recordedCalls) {
                recordedCalls += RequestCall(
                    method = method,
                    fields = fields,
                    timeoutMs = timeoutMs,
                    flush = flush,
                    disconnectOnTimeout = disconnectOnTimeout,
                )
                recordedCalls.size
            }
            if (count == expectedRequestCount) expectedRequestsCompleted.complete(Unit)
            return HtspMessage(
                method = null,
                seq = null,
                fields = mapOf("events" to emptyList<Any?>()),
            )
        }

        override suspend fun requestForConnectionAttempt(
            expectedConnectionAttemptId: Long,
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage = request(
            method = method,
            fields = fields,
            timeoutMs = timeoutMs,
            flush = flush,
            disconnectOnTimeout = disconnectOnTimeout,
        )


        override suspend fun requestForConnectionAttemptIf(
            expectedConnectionAttemptId: Long,
            isRequestAdmitted: () -> Boolean,
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage {
            if (!isRequestAdmitted()) throw CancellationException("Rejected frontier admission")
            return request(
                method = method,
                fields = fields,
                timeoutMs = timeoutMs,
                flush = flush,
                disconnectOnTimeout = disconnectOnTimeout,
            )
        }
    }

    private class ReservationHtspService : HtspService(Dispatchers.Unconfined) {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        val firstReplyReleased = CompletableDeferred<Unit>()
        val replacementRequestStarted = CompletableDeferred<Unit>()
        val releaseReplacementRequest = CompletableDeferred<Unit>()
        val replacementReplyReleased = CompletableDeferred<Unit>()
        val unexpectedThirdRequest = CompletableDeferred<Unit>()
        private val requestCount = AtomicInteger()

        override suspend fun request(
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage = when (requestCount.incrementAndGet()) {
            1 -> withContext(NonCancellable) {
                firstRequestStarted.complete(Unit)
                releaseFirstRequest.await()
                firstReplyReleased.complete(Unit)
                emptyReply()
            }
            2 -> {
                replacementRequestStarted.complete(Unit)
                releaseReplacementRequest.await()
                replacementReplyReleased.complete(Unit)
                emptyReply()
            }
            else -> {
                unexpectedThirdRequest.complete(Unit)
                emptyReply()
            }
        }

        private fun emptyReply() = HtspMessage(
            method = null,
            seq = null,
            fields = mapOf("events" to emptyList<Any?>()),
        )
    }

    private fun eventMessage(eventId: Int, channelId: Int, start: Long, stop: Long) = message(
        "eventAdd",
        "eventId" to eventId,
        "channelId" to channelId,
        "start" to start,
        "stop" to stop,
        "title" to "Event $eventId",
    )

    private fun message(method: String?, vararg fields: Pair<String, Any?>) =
        HtspMessage(method = method, seq = null, fields = mapOf(*fields))
}
