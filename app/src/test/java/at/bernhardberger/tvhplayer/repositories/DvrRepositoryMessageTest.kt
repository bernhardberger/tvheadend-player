package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DvrRepositoryMessageTest {
    @Test
    fun asyncMessagesAreAuthoritativeAndInitialStateIsAtomic() = runBlocking {
        val repository = DvrRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
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
        assertEquals(DvrState.SCHEDULED, repository.entries.value.single().state)

        repository.acceptDvrMessage(
            message("dvrEntryUpdate", "id" to 4, "state" to "recording")
        )
        assertEquals(DvrState.RECORDING, repository.entries.value.single().state)

        repository.acceptDvrMessage(message("dvrEntryDelete", "id" to 4))
        assertEquals(emptyList<Any>(), repository.entries.value)
    }

    private fun message(method: String, vararg fields: Pair<String, Any?>) =
        HtspMessage(method = method, seq = null, fields = mapOf(*fields))
}
