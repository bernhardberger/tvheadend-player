package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.htsp.ConnectionState
import at.bernhardberger.tvhplayer.htsp.DVR_PLAY_COUNT_KEEP
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspRequestTimeoutException
import at.bernhardberger.tvhplayer.htsp.HtspService
import java.net.ConnectException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DvrRepositoryProgressTest {
    @Test
    fun ordinaryAndCompletionUpdatesUseExactHtspFields() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, Any?>>>()
        val repository = repository { method, fields ->
            requests += method to fields
            message("success" to 1)
        }

        assertEquals(
            RecordingProgressUpdateResult.Accepted,
            repository.updateRecordingProgress(42, 901L, setWatched = false),
        )
        assertEquals(
            RecordingProgressUpdateResult.Accepted,
            repository.updateRecordingProgress(42, 0L, setWatched = true),
        )
        assertEquals(listOf("updateDvrEntry", "updateDvrEntry"), requests.map { it.first })
        assertEquals(42, requests[0].second["id"])
        assertEquals(901L, requests[0].second["playposition"])
        assertEquals(DVR_PLAY_COUNT_KEEP, requests[0].second["playcount"])
        assertEquals(42, requests[1].second["id"])
        assertEquals(0L, requests[1].second["playposition"])
        assertEquals(1, requests[1].second["playcount"])
    }

    @Test
    fun acceptedRequestDoesNotManufactureAsyncState() = runBlocking {
        val repository = repository { _, _ -> message("success" to 1) }
        repository.acceptDvrMessage(
            HtspMessage(
                method = "dvrEntryAdd",
                seq = null,
                fields = mapOf(
                    "id" to 42,
                    "channelId" to 1,
                    "state" to "completed",
                    "playposition" to 180L,
                ),
            ),
        )
        repository.acceptDvrMessage(HtspMessage("initialSyncCompleted", null, emptyMap()))

        repository.updateRecordingProgress(42, 901L, setWatched = false)

        assertEquals(180L, repository.entries.value.single().playPosition)
    }

    @Test
    fun capabilityGateDistinguishesLegacyReadOnlyAndDisconnected() = runBlocking {
        var requestCount = 0
        val handler: suspend (String, Map<String, Any?>) -> HtspMessage = { _, _ ->
            requestCount++
            message("success" to 1)
        }

        assertEquals(
            RecordingProgressUpdateResult.Unsupported,
            repository(version = 26, handler = handler)
                .updateRecordingProgress(1, 180L, false),
        )
        assertEquals(
            RecordingProgressUpdateResult.PermissionDenied,
            repository(version = 43, dvrAccess = false, handler = handler)
                .updateRecordingProgress(1, 180L, false),
        )
        assertEquals(
            RecordingProgressUpdateResult.Disconnected,
            repository(version = null, handler = handler)
                .updateRecordingProgress(1, 180L, false),
        )
        assertEquals(0, requestCount)
    }

    @Test
    fun serverFailuresAreTypedAtProgressScopeOnly() = runBlocking {
        assertEquals(
            RecordingProgressUpdateResult.PermissionDenied,
            repository { _, _ -> message("noaccess" to 1) }
                .updateRecordingProgress(1, 180L, false),
        )
        assertEquals(
            RecordingProgressUpdateResult.Unsupported,
            repository { _, _ -> message("error" to "Method not found") }
                .updateRecordingProgress(1, 180L, false),
        )
        assertEquals(
            RecordingProgressUpdateResult.Rejected,
            repository { _, _ -> message("error" to "entry is locked") }
                .updateRecordingProgress(1, 180L, false),
        )
    }

    @Test
    fun unsupportedProgressMethodIsCachedForTheConnection() = runBlocking {
        var requestCount = 0
        val repository = repository { _, _ ->
            requestCount++
            message("error" to "Method not found")
        }

        assertEquals(
            RecordingProgressUpdateResult.Unsupported,
            repository.updateRecordingProgress(1, 180L, false),
        )
        assertEquals(
            RecordingProgressUpdateResult.Unsupported,
            repository.updateRecordingProgress(1, 190L, false),
        )
        assertEquals(1, requestCount)
        assertEquals(RecordingProgressCapability.Unsupported, repository.progressCapability.value)
    }

    @Test
    fun timeoutAndDisconnectAreTypedWithoutRequestDisconnectPolicy() = runBlocking {
        var disconnectOnTimeout: Boolean? = null
        val timeoutRepository = repository(
            requestObserver = { _, _, disconnect -> disconnectOnTimeout = disconnect },
        ) { _, _ -> throw HtspRequestTimeoutException("updateDvrEntry", 2_000L) }

        assertEquals(
            RecordingProgressUpdateResult.Timeout,
            timeoutRepository.updateRecordingProgress(1, 180L, false),
        )
        assertEquals(false, disconnectOnTimeout)
        assertEquals(
            RecordingProgressUpdateResult.Disconnected,
            repository { _, _ -> throw ConnectException("offline") }
                .updateRecordingProgress(1, 180L, false),
        )
    }

    @Test
    fun callerCancellationPropagates() {
        val repository = repository { _, _ -> throw CancellationException("replaced") }

        val error = try {
            runBlocking { repository.updateRecordingProgress(1, 180L, false) }
            throw AssertionError("Expected CancellationException")
        } catch (error: CancellationException) {
            error
        }

        assertEquals("replaced", error.message)
    }

    @Test
    fun progressPermissionDenialDoesNotHideOtherDvrActions() = runBlocking {
        val repository = repository { _, _ -> message("noaccess" to 1) }
        repository.applyAuthenticatedDvrAccess(true)

        assertEquals(
            RecordingProgressUpdateResult.PermissionDenied,
            repository.updateRecordingProgress(1, 180L, false),
        )

        assertTrue(repository.canModifyRecordings.value)
    }

    private fun repository(
        version: Int? = 43,
        dvrAccess: Boolean? = true,
        requestObserver: ((String, Long, Boolean) -> Unit)? = null,
        handler: suspend (String, Map<String, Any?>) -> HtspMessage,
    ): DvrRepository {
        val state = if (version == null) {
            ConnectionState.Disconnected
        } else {
            ConnectionState.Connected("host", 9982, version, dvrAccess)
        }
        val service = ScriptedHtspService(state, handler).also {
            it.requestObserver = requestObserver
        }
        return DvrRepository(
            htsp = service,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun message(vararg fields: Pair<String, Any?>) =
        HtspMessage(method = null, seq = 1, fields = mapOf(*fields))

    private class ScriptedHtspService(
        private val connectionState: ConnectionState,
        private val handler: suspend (String, Map<String, Any?>) -> HtspMessage,
    ) : HtspService(Dispatchers.Unconfined) {
        var requestObserver: ((String, Long, Boolean) -> Unit)? = null

        override fun currentConnectionState(): ConnectionState = connectionState

        override suspend fun request(
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage {
            requestObserver?.invoke(method, timeoutMs, disconnectOnTimeout)
            return handler(method, fields)
        }
    }
}
