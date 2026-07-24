package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import kotlinx.coroutines.Dispatchers
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

    private fun message(method: String, vararg fields: Pair<String, Any?>) =
        HtspMessage(method = method, seq = null, fields = mapOf(*fields))
}
