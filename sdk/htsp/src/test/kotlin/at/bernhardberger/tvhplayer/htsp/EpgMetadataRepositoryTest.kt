package at.bernhardberger.tvhplayer.htsp

import at.bernhardberger.tvhplayer.core.epgRetentionWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EpgMetadataRepositoryTest {
    @Test
    fun perChannelFlowCreationIsAtomicAcrossThreads() {
        val repository = EpgMetadataRepository()
        val executor = Executors.newFixedThreadPool(THREADS)

        try {
            repeat(ROUNDS) { channelId ->
                val barrier = CyclicBarrier(THREADS)
                val tasks = List(THREADS) {
                    Callable {
                        barrier.await(2, TimeUnit.SECONDS)
                        repository.epgForChannel(channelId)
                    }
                }

                val flows = executor.invokeAll(tasks).map { it.get(2, TimeUnit.SECONDS) }
                flows.forEach { flow -> assertSame(flows.first(), flow) }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun eventAddAndUpdateReplaceByIdAndPublishInStartOrder() {
        val repository = EpgMetadataRepository()

        repository.accept(eventMessage("eventAdd", id = 2, channelId = 7, start = 300, stop = 400)) {
            ANCHOR_SEC
        }
        repository.accept(eventMessage("eventAdd", id = 1, channelId = 7, start = 100, stop = 200)) {
            ANCHOR_SEC
        }
        val effect = repository.accept(
            message(
                "eventUpdate",
                "id" to 1L,
                "channel" to 7L,
                "startTime" to 350L,
                "stopTime" to 450L,
                "eventTitle" to "Updated",
                "description" to "Description",
                "category" to "News",
            )
        ) { ANCHOR_SEC }

        assertEquals(
            EpgMetadataEffect.EventUpserted(
                event(
                    id = 1,
                    channelId = 7,
                    start = 350,
                    stop = 450,
                    title = "Updated",
                    description = "Description",
                    genre = "News",
                )
            ),
            effect,
        )
        assertEquals(listOf(2, 1), repository.epgForChannel(7).value.map(EpgEventEntry::eventId))
        assertEquals("Updated", repository.epgForChannel(7).value.last().title)
    }

    @Test
    fun eventDeleteInterpretsEventAndChannelAliases() {
        val repository = EpgMetadataRepository()
        repository.accept(eventMessage("eventAdd", id = 1, channelId = 7, start = 100, stop = 200)) {
            ANCHOR_SEC
        }
        repository.accept(eventMessage("eventAdd", id = 2, channelId = 7, start = 300, stop = 400)) {
            ANCHOR_SEC
        }

        val effect = repository.accept(message("eventDelete", "id" to 1L, "channel" to 7L)) {
            ANCHOR_SEC
        }

        assertEquals(EpgMetadataEffect.EventDeleted(channelId = 7, eventId = 1), effect)
        assertEquals(listOf(2), repository.epgForChannel(7).value.map(EpgEventEntry::eventId))
        assertNull(repository.accept(message("eventDelete", "id" to 2L)) { ANCHOR_SEC })
        assertEquals(listOf(2), repository.epgForChannel(7).value.map(EpgEventEntry::eventId))
    }

    @Test
    fun retentionTrimmingKeepsEventsTouchingBothInclusiveBoundaries() {
        val repository = EpgMetadataRepository()
        val window = epgRetentionWindow(ANCHOR_SEC)
        val events = listOf(
            event(id = 1, start = window.fromSec - 20, stop = window.fromSec - 1),
            event(id = 2, start = window.fromSec - 20, stop = window.fromSec),
            event(id = 3, start = window.toSec, stop = window.toSec + 20),
            event(id = 4, start = window.toSec + 1, stop = window.toSec + 20),
        )

        events.forEach { item ->
            repository.accept(message("eventAdd", *item.toFields())) { ANCHOR_SEC }
        }

        assertEquals(listOf(2, 3), repository.epgForChannel(1).value.map(EpgEventEntry::eventId))
    }

    @Test
    fun getEventsInterpretsPayloadAliasesAndSkipsMalformedEntries() {
        for (payloadKey in listOf("events", "epg", "entries")) {
            val repository = EpgMetadataRepository()
            val valid = mapOf(
                "id" to 8L,
                "channel" to 9L,
                "startTime" to 10_000L,
                "stopTime" to 11_000L,
                "eventTitle" to "Nested",
                "category" to "Movie",
                "episode" to mapOf("season" to 2, "number" to 4),
            )

            val result = repository.ingestGetEventsReply(
                reply = message(
                    method = null,
                    payloadKey to listOf(
                        mapOf("eventId" to 1, "channelId" to 9, "start" to 1_000L),
                        "not-an-event-map",
                        valid,
                    ),
                ),
                anchorSec = ANCHOR_SEC,
            )

            assertEquals(1, result.totalEvents)
            assertEquals(EpgEventBounds(earliestStart = 10_000L, latestStop = 11_000L), result.perChannelBounds[9])
            val event = repository.epgForChannel(9).value.single()
            assertEquals(8, event.eventId)
            assertEquals("Movie", event.genre)
            assertEquals(2, event.seasonNumber)
            assertEquals(4, event.episodeNumber)
        }
    }

    @Test
    fun emptyAndUnsupportedGetEventsRepliesProduceAnEmptyResult() {
        val replies = listOf(
            message(method = null),
            message(method = null, "events" to emptyList<Any?>()),
            message(method = null, "events" to "not-a-list"),
        )

        for (reply in replies) {
            val repository = EpgMetadataRepository()

            val result = repository.ingestGetEventsReply(reply, anchorSec = ANCHOR_SEC)

            assertEquals(EpgMetadataIngestResult.Empty, result)
            assertTrue(repository.epgForChannel(1).value.isEmpty())
        }
    }

    @Test
    fun nowAndNextQueriesPreserveCurrentAndNearestFallbackBehavior() {
        val repository = EpgMetadataRepository()
        listOf(
            event(id = 1, start = 100, stop = 200),
            event(id = 2, start = 300, stop = 400),
            event(id = 3, start = 500, stop = 600),
        ).forEach { item ->
            repository.accept(message("eventAdd", *item.toFields())) { ANCHOR_SEC }
        }

        assertEquals(2, repository.nowEvent(channelId = 1, nowSec = 350)?.eventId)
        assertEquals(2, repository.nowEvent(channelId = 1, nowSec = 275)?.eventId)
        assertEquals(3, repository.nextEvent(channelId = 1, nowSec = 350)?.eventId)
        assertNull(repository.nextEvent(channelId = 1, nowSec = 700))
    }

    @Test
    fun channelDeletionAndInitialSyncRetentionPruneOnlyRemovedChannelState() {
        val repository = EpgMetadataRepository()
        repository.accept(eventMessage("eventAdd", id = 1, channelId = 10, start = 100, stop = 200)) {
            ANCHOR_SEC
        }
        repository.accept(eventMessage("eventAdd", id = 2, channelId = 20, start = 300, stop = 400)) {
            ANCHOR_SEC
        }
        val deletedFlow = repository.epgForChannel(10)

        repository.removeChannel(10)

        val replacementFlow = repository.epgForChannel(10)
        assertNotSame(deletedFlow, replacementFlow)
        assertTrue(replacementFlow.value.isEmpty())
        assertEquals(listOf(2), repository.epgForChannel(20).value.map(EpgEventEntry::eventId))

        repository.accept(eventMessage("eventAdd", id = 3, channelId = 30, start = 500, stop = 600)) {
            ANCHOR_SEC
        }
        repository.retainChannels(setOf(20))

        assertTrue(repository.epgForChannel(30).value.isEmpty())
        assertEquals(listOf(2), repository.epgForChannel(20).value.map(EpgEventEntry::eventId))
    }

    @Test
    fun trimAllReturnsAuthoritativeBoundsForEveryRetainedFlow() {
        val repository = EpgMetadataRepository()
        repository.accept(eventMessage("eventAdd", id = 1, channelId = 10, start = 100, stop = 200)) {
            ANCHOR_SEC
        }
        repository.epgForChannel(20)

        val bounds = repository.trimAll { ANCHOR_SEC }

        assertEquals(EpgEventBounds(earliestStart = 100, latestStop = 200), bounds[10])
        assertEquals(EpgEventBounds.Empty, bounds[20])
    }

    private fun eventMessage(
        method: String,
        id: Int,
        channelId: Int,
        start: Long,
        stop: Long,
    ) = message(method, *event(id, channelId, start, stop).toFields())

    private fun event(
        id: Int,
        channelId: Int = 1,
        start: Long,
        stop: Long,
        title: String = "Event $id",
        description: String? = null,
        genre: String? = null,
    ) = EpgEventEntry(
        eventId = id,
        channelId = channelId,
        start = start,
        stop = stop,
        title = title,
        description = description,
        genre = genre,
    )

    private fun EpgEventEntry.toFields(): Array<Pair<String, Any?>> = arrayOf(
        "eventId" to eventId,
        "channelId" to channelId,
        "start" to start,
        "stop" to stop,
        "title" to title,
    )

    private fun message(method: String?, vararg fields: Pair<String, Any?>) =
        HtspMessage(method = method, seq = null, fields = mapOf(*fields))

    private companion object {
        const val THREADS = 16
        const val ROUNDS = 100
        const val ANCHOR_SEC = 10_000L
    }
}
