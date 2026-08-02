package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DvrRepositoryMessageTest {
    @Test
    fun asyncMessagesAreAuthoritativeAndInitialStateIsAtomic() = runBlocking {
        val repository = DvrRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
        assertFalse(repository.entriesReady.value)
        repository.acceptDvrMessage(
            message(
                "dvrEntryAdd",
                "id" to 4,
                "eventId" to 44,
                "channelId" to 3,
                "start" to 100L,
                "stop" to 200L,
                "title" to "Programme",
                "state" to "scheduled",
            )
        )
        assertEquals(emptyList<Any>(), repository.entries.value)

        repository.acceptDvrMessage(message("initialSyncCompleted"))
        assertTrue(repository.entriesReady.value)
        assertEquals(DvrState.SCHEDULED, repository.entries.value.single().state)

        repository.acceptDvrMessage(
            message("dvrEntryUpdate", "id" to 4, "state" to "recording")
        )
        assertEquals(DvrState.RECORDING, repository.entries.value.single().state)

        repository.acceptDvrMessage(message("dvrEntryDelete", "id" to 4))
        assertEquals(emptyList<Any>(), repository.entries.value)

        repository.onNewConnectionStarting(preservePublished = true)
        assertTrue(repository.entriesReady.value)
        repository.onNewConnectionStarting(preservePublished = false)
        assertFalse(repository.entriesReady.value)
    }

    @Test
    fun entryMessagesRetainFolderOwnershipArtworkAndEpisodeMetadata() = runBlocking {
        val repository = DvrRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
        repository.acceptDvrMessage(
            message(
                "dvrEntryAdd",
                "id" to 9,
                "eventId" to 99,
                "channel" to 7,
                "channelName" to "Channel Seven",
                "start" to 100L,
                "stop" to 200L,
                "title" to "Programme",
                "state" to "completed",
                "owner" to "viewer",
                "creator" to "viewer",
                "path" to "Sport/Formula 1/Programme.ts",
                "image" to "image-ref",
                "fanartImage" to "fanart-ref",
                "playposition" to 42L,
                "playcount" to 3,
                "seasonNumber" to 2,
                "episodeNumber" to 5,
                "episodeCount" to 10,
                "partNumber" to 1,
                "partCount" to 2,
                "autorecId" to "auto-id",
                "timerecId" to "time-id",
                "files" to listOf(mapOf("filename" to "Sport/Formula 1/Programme.ts")),
            )
        )
        repository.acceptDvrMessage(message("initialSyncCompleted"))

        val entry = repository.entries.value.single()
        assertEquals(7, entry.channelId)
        assertEquals("Channel Seven", entry.channelName)
        assertEquals("viewer", entry.owner)
        assertEquals("viewer", entry.creator)
        assertEquals("Sport/Formula 1/Programme.ts", entry.path)
        assertEquals("image-ref", entry.image)
        assertEquals("fanart-ref", entry.fanartImage)
        assertEquals(42L, entry.playPosition)
        assertEquals(3, entry.playCount)
        assertEquals(2, entry.seasonNumber)
        assertEquals(5, entry.episodeNumber)
        assertEquals(10, entry.episodeCount)
        assertEquals(1, entry.partNumber)
        assertEquals(2, entry.partCount)
        assertEquals("auto-id", entry.autorecId)
        assertEquals("time-id", entry.timerecId)

        repository.acceptDvrMessage(message("dvrEntryUpdate", "id" to 9, "playposition" to 84L))
        assertEquals(84L, repository.entries.value.single().playPosition)
        assertNull(repository.entries.value.single().failureReason)
    }

    @Test
    fun staleConnectionAttemptCannotReachDelegatedMetadata() = runBlocking {
        val service = HtspService(Dispatchers.Unconfined)
        val repository = DvrRepository(
            htsp = service,
            ioDispatcher = Dispatchers.Unconfined,
        )
        service.disconnect()
        val staleAttempt = service.currentConnectionAttemptId()
        service.disconnect()
        val currentAttempt = service.currentConnectionAttemptId()

        repository.acceptDvrMessage(
            message("dvrEntryAdd", "id" to 1, "title" to "Stale"),
            connectionAttemptId = staleAttempt,
        )
        repository.acceptDvrMessage(
            message("initialSyncCompleted"),
            connectionAttemptId = staleAttempt,
        )

        assertFalse(repository.entriesReady.value)
        assertTrue(repository.entries.value.isEmpty())

        repository.acceptDvrMessage(
            message("dvrEntryAdd", "id" to 2, "title" to "Current"),
            connectionAttemptId = currentAttempt,
        )
        repository.acceptDvrMessage(
            message("initialSyncCompleted"),
            connectionAttemptId = currentAttempt,
        )

        assertTrue(repository.entriesReady.value)
        assertEquals(listOf(2), repository.entries.value.map { it.id })
    }

    private fun message(method: String, vararg fields: Pair<String, Any?>) =
        HtspMessage(method = method, seq = null, fields = mapOf(*fields))
}
