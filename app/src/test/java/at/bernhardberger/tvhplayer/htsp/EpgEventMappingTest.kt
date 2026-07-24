package at.bernhardberger.tvhplayer.htsp

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgEventMappingTest {
    @Test
    fun mapsDescriptionGenreContentAndEpisodeFieldsFromHtsp() {
        val event = epgEventFromFields(
            mapOf(
                "eventId" to 77,
                "channelId" to 4,
                "start" to 1_000L,
                "stop" to 2_000L,
                "title" to "Episode title",
                "summary" to "Short summary",
                "description" to "Full programme description",
                "genre" to "Documentary",
                "contentType" to 0x23,
                "seasonNumber" to 3,
                "episodeNumber" to 5,
                "episodeCount" to 12,
                "partNumber" to 1,
                "partCount" to 2,
                "episodeId" to 900,
                "serieslinkId" to 901,
            )
        )

        requireNotNull(event)
        assertEquals("Short summary", event.summary)
        assertEquals("Full programme description", event.description)
        assertEquals("Documentary", event.genre)
        assertEquals(0x23, event.contentType)
        assertEquals(3, event.seasonNumber)
        assertEquals(5, event.episodeNumber)
        assertEquals(12, event.episodeCount)
        assertEquals(1, event.partNumber)
        assertEquals(2, event.partCount)
        assertEquals(900, event.episodeId)
        assertEquals(901, event.seriesLinkId)
    }

    @Test
    fun mapsAliasesAndNestedEpisodeMetadataUsedByAsyncMessages() {
        val event = epgEventFromFields(
            mapOf(
                "id" to 8L,
                "channel" to 9L,
                "startTime" to 10_000L,
                "stopTime" to 11_000L,
                "eventTitle" to "Nested",
                "category" to "Movie",
                "episode" to mapOf(
                    "season" to 2,
                    "number" to 4,
                    "count" to 8,
                ),
            )
        )

        requireNotNull(event)
        assertEquals(8, event.eventId)
        assertEquals(9, event.channelId)
        assertEquals("Movie", event.genre)
        assertEquals(2, event.seasonNumber)
        assertEquals(4, event.episodeNumber)
        assertEquals(8, event.episodeCount)
    }
}
