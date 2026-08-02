package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TvhRepositoryTagSyncTest {
    @Test
    fun syntheticHtspMessagesPublishChannelsAndTagsOnlyAtInitialSyncBoundary() = runBlocking {
        val repository = TvhRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )

        repository.acceptMetadataMessage(
            message("tagAdd", "tagId" to 7, "tagName" to "News", "tagIndex" to 2)
        )
        repository.acceptMetadataMessage(
            message(
                "channelAdd",
                "channelId" to 42,
                "channelName" to "World",
                "channelNumber" to 8,
                "tagIds" to listOf(7),
            )
        )

        assertEquals(emptyList<Any>(), repository.tagsUi.value)
        assertEquals(emptyList<Any>(), repository.channelsUi.value)

        repository.acceptMetadataMessage(message("initialSyncCompleted"))

        assertEquals(listOf(7), repository.tagsUi.value.map { it.id })
        assertEquals(setOf(7), repository.channelsUi.value.single().tagIds)
    }

    @Test
    fun syntheticAsyncTagChangesArePublishedWithoutRestart() = runBlocking {
        val repository = TvhRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
        repository.acceptMetadataMessage(message("initialSyncCompleted"))
        repository.acceptMetadataMessage(
            message("tagAdd", "tagId" to 7, "tagName" to "News", "tagIndex" to 1)
        )
        repository.acceptMetadataMessage(
            message("tagUpdate", "tagId" to 7, "tagName" to "Current affairs")
        )

        assertEquals("Current affairs", repository.tagsUi.value.single().name)

        repository.acceptMetadataMessage(message("tagDelete", "tagId" to 7))
        assertEquals(emptyList<Any>(), repository.tagsUi.value)
    }

    @Test
    fun initialSyncPrunesRemovedChannelStateBeforeReadinessResumes() = runBlocking {
        val repository = TvhRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val now = System.currentTimeMillis() / 1_000L
        repository.acceptMetadataMessage(
            message(
                "eventAdd",
                "eventId" to 99,
                "channelId" to 99,
                "start" to now,
                "stop" to now + 60,
                "title" to "Removed channel event",
            )
        )
        repository.acceptMetadataMessage(
            message("channelAdd", "channelId" to 42, "channelName" to "Current")
        )
        val removedEventsAtReadiness = async(start = CoroutineStart.UNDISPATCHED) {
            repository.awaitChannelsReady()
            repository.epgForChannel(99).value
        }

        repository.acceptMetadataMessage(message("initialSyncCompleted"))

        assertEquals(emptyList<Any>(), removedEventsAtReadiness.await())
    }

    @Test
    fun replacementConnectionRejectsStaleTransportMetadata() = runBlocking {
        val htsp = HtspService(Dispatchers.Unconfined)
        val repository = TvhRepository(
            htsp = htsp,
            ioDispatcher = Dispatchers.Unconfined,
        )

        htsp.disconnect()
        val firstTransportAttempt = htsp.currentConnectionAttemptId()
        repository.acceptMetadataMessage(
            message("channelAdd", "channelId" to 10, "channelName" to "Old"),
            connectionAttemptId = firstTransportAttempt,
        )
        repository.acceptMetadataMessage(
            message("initialSyncCompleted"),
            connectionAttemptId = firstTransportAttempt,
        )

        htsp.disconnect()
        val replacementTransportAttempt = htsp.currentConnectionAttemptId()
        repository.onNewConnectionStarting(
            preservePublishedChannels = true,
            attemptId = replacementTransportAttempt,
        )
        assertEquals(listOf(10), repository.channelsUi.value.map { it.id })

        repository.acceptMetadataMessage(
            message("channelAdd", "channelId" to 99, "channelName" to "Stale"),
            connectionAttemptId = firstTransportAttempt,
        )
        repository.acceptMetadataMessage(
            message("channelAdd", "channelId" to 20, "channelName" to "New"),
            connectionAttemptId = replacementTransportAttempt,
        )
        repository.acceptMetadataMessage(
            message("initialSyncCompleted"),
            connectionAttemptId = replacementTransportAttempt,
        )

        assertEquals(listOf(20), repository.channelsUi.value.map { it.id })
    }

    @Test
    fun olderAppConnectionAttemptCannotResetCurrentMetadataGeneration() = runBlocking {
        val repository = TvhRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
        repository.advanceConnectionAttempt(attemptId = 2L)
        repository.onNewConnectionStarting(
            preservePublishedChannels = false,
            attemptId = 2L,
        )
        repository.acceptMetadataMessage(
            message("channelAdd", "channelId" to 20, "channelName" to "Current")
        )
        repository.acceptMetadataMessage(message("initialSyncCompleted"))

        repository.onNewConnectionStarting(
            preservePublishedChannels = false,
            attemptId = 1L,
        )

        assertEquals(listOf(20), repository.channelsUi.value.map { it.id })
        assertEquals(true, repository.metadataReady.value)
    }

    private fun message(method: String, vararg fields: Pair<String, Any?>) =
        HtspMessage(method = method, seq = null, fields = mapOf(*fields))
}
