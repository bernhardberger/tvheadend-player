package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TvhRepositoryEpgMappingTest {
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
}
