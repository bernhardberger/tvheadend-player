package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.core.DvrActionFailure
import at.bernhardberger.tvhplayer.core.DvrActionResult
import at.bernhardberger.tvhplayer.core.RecordingWriteCapability
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DvrRepositoryCapabilityTest {

    @Test
    fun supersededConfigRefreshCannotPublishOverNewConnectionAttempt() = runBlocking {
        val requestStarted = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        val repository = repository { method, _ ->
            if (method != "getDvrConfigs") error("unexpected $method")
            requestStarted.countDown()
            check(releaseResponse.await(1, TimeUnit.SECONDS))
            message("dvrconfigs" to listOf(mapOf("uuid" to "old", "name" to "Old")))
        }
        repository.onNewConnectionStarting(preservePublished = false, attemptId = 1L)
        val staleRefresh = launch(Dispatchers.Default) {
            repository.refreshConfigsForAttempt(attemptId = 1L)
        }
        assertTrue(requestStarted.await(1, TimeUnit.SECONDS))

        repository.advanceConnectionAttempt(attemptId = 2L)
        releaseResponse.countDown()
        staleRefresh.join()

        assertEquals(RecordingWriteCapability.Unknown, repository.writeCapability.value)
        assertTrue(repository.configs.value.isEmpty())
    }

    @Test
    fun staleAuthenticatedAccessCannotPublishOverNewConnectionAttempt() = runBlocking {
        val repository = repository()
        repository.onNewConnectionStarting(preservePublished = false, attemptId = 2L)

        repository.applyAuthenticatedDvrAccess(dvrAccess = true, attemptId = 1L)

        assertEquals(RecordingWriteCapability.Unknown, repository.writeCapability.value)
        assertFalse(repository.canModifyRecordings.value)
    }

    @Test
    fun writeActionPropagatesCoroutineCancellation() {
        var cancellationPropagated = false

        try {
            runBlocking {
                repository { _, _ -> throw CancellationException("cancelled") }
                    .scheduleEvent(eventId = 42)
            }
        } catch (_: CancellationException) {
            cancellationPropagated = true
        }

        assertTrue(cancellationPropagated)
    }

    @Test
    fun startsUnknownAndHidesWriteActions() = runBlocking {
        val repository = repository()
        assertEquals(RecordingWriteCapability.Unknown, repository.writeCapability.value)
        assertFalse(repository.canModifyRecordings.value)
    }

    @Test
    fun onNewConnectionStartingResetsToUnknown() = runBlocking {
        val repository = repository { method, _ ->
            when (method) {
                "getDvrConfigs" -> message("dvrconfigs" to emptyList<Any>())
                else -> error("unexpected $method")
            }
        }
        repository.refreshConfigs()
        assertTrue(repository.canModifyRecordings.value)

        repository.onNewConnectionStarting(preservePublished = true)
        assertEquals(RecordingWriteCapability.Unknown, repository.writeCapability.value)
        assertFalse(repository.canModifyRecordings.value)
    }

    @Test
    fun getDvrConfigsNoAccessDeniesWrite() = runBlocking {
        val repository = repository { method, _ ->
            when (method) {
                "getDvrConfigs" -> message("noaccess" to 1)
                else -> error("unexpected $method")
            }
        }
        repository.refreshConfigs()
        assertEquals(RecordingWriteCapability.Denied, repository.writeCapability.value)
        assertFalse(repository.canModifyRecordings.value)
        assertTrue(repository.configs.value.isEmpty())
    }

    @Test
    fun getDvrConfigsSuccessAllowsWrite() = runBlocking {
        val repository = repository { method, _ ->
            when (method) {
                "getDvrConfigs" -> message(
                    "dvrconfigs" to listOf(
                        mapOf("uuid" to "default", "name" to "Default"),
                    ),
                )
                else -> error("unexpected $method")
            }
        }
        repository.refreshConfigs()
        assertEquals(RecordingWriteCapability.Allowed, repository.writeCapability.value)
        assertTrue(repository.canModifyRecordings.value)
        assertEquals(1, repository.configs.value.size)
    }

    @Test
    fun getDvrConfigsUnknownMethodLeavesCapabilityUnknown() = runBlocking {
        val repository = repository { method, _ ->
            when (method) {
                "getDvrConfigs" -> message("error" to "Method not found")
                else -> error("unexpected $method")
            }
        }
        repository.refreshConfigs()
        // An old server that lacks the probe proves nothing about write access.
        assertEquals(RecordingWriteCapability.Unknown, repository.writeCapability.value)
        assertFalse(repository.canModifyRecordings.value)
    }

    @Test
    fun getDvrConfigsConnectionLimitDoesNotDenyWrite() = runBlocking {
        val repository = repository { method, _ ->
            when (method) {
                "getDvrConfigs" -> message("noaccess" to 1, "connlimit" to 1)
                else -> error("unexpected $method")
            }
        }
        repository.applyAuthenticatedDvrAccess(true)
        repository.refreshConfigs()
        // connlimit is transient; it must not latch a permission denial.
        assertEquals(RecordingWriteCapability.Allowed, repository.writeCapability.value)
        assertTrue(repository.canModifyRecordings.value)
    }

    @Test
    fun addDvrEntryConnectionLimitDoesNotDenyWrite() = runBlocking {
        val repository = repository { _, _ -> message("noaccess" to 1, "connlimit" to 1) }
        repository.applyAuthenticatedDvrAccess(true)

        val result = repository.scheduleEvent(eventId = 42)
        assertEquals(
            DvrActionResult.Failed(DvrActionFailure.CONNECTION_LIMIT),
            result,
        )
        assertEquals(RecordingWriteCapability.Allowed, repository.writeCapability.value)
    }

    @Test
    fun authenticateDvrFlagInitializesCapability() = runBlocking {
        val repository = repository()
        repository.applyAuthenticatedDvrAccess(true)
        assertEquals(RecordingWriteCapability.Allowed, repository.writeCapability.value)
        assertTrue(repository.canModifyRecordings.value)

        repository.onNewConnectionStarting(preservePublished = false)
        repository.applyAuthenticatedDvrAccess(false)
        assertEquals(RecordingWriteCapability.Denied, repository.writeCapability.value)
        assertFalse(repository.canModifyRecordings.value)

        repository.onNewConnectionStarting(preservePublished = false)
        repository.applyAuthenticatedDvrAccess(null)
        assertEquals(RecordingWriteCapability.Unknown, repository.writeCapability.value)
    }

    @Test
    fun addDvrEntryNoAccessDeniesWrite() = runBlocking {
        var lastMethod: String? = null
        var lastFields: Map<String, Any?> = emptyMap()
        val repository = repository { method, fields ->
            lastMethod = method
            lastFields = fields
            message("noaccess" to 1)
        }

        val result = repository.scheduleEvent(eventId = 42, configName = "default")
        assertEquals("addDvrEntry", lastMethod)
        assertEquals(42, lastFields["eventId"])
        assertEquals("default", lastFields["configName"])
        assertEquals(
            DvrActionResult.Failed(DvrActionFailure.PERMISSION_DENIED),
            result,
        )
        assertEquals(RecordingWriteCapability.Denied, repository.writeCapability.value)
        assertFalse(repository.canModifyRecordings.value)
    }

    @Test
    fun methodNotFoundDoesNotClearCapability() = runBlocking {
        val repository = repository { method, _ ->
            when (method) {
                "getDvrConfigs" -> message(
                    "dvrconfigs" to listOf(mapOf("uuid" to "default", "name" to "Default")),
                )
                "addDvrEntry" -> message("error" to "Method not found")
                else -> error("unexpected $method")
            }
        }
        repository.refreshConfigs()
        assertTrue(repository.canModifyRecordings.value)

        val result = repository.scheduleEvent(eventId = 7)
        assertEquals(
            DvrActionResult.Failed(DvrActionFailure.REJECTED),
            result,
        )
        // Method-not-found is REJECTED, not PERMISSION_DENIED — keep Allowed.
        assertEquals(RecordingWriteCapability.Allowed, repository.writeCapability.value)
        assertTrue(repository.canModifyRecordings.value)
    }

    @Test
    fun writeRpcNamesMatchHtspClientMethods() = runBlocking {
        val methods = mutableListOf<String>()
        val repository = repository { method, _ ->
            methods += method
            message("id" to 1)
        }

        repository.scheduleEvent(eventId = 1)
        repository.cancelEntry(entryId = 2)
        repository.deleteEntry(entryId = 3)

        assertEquals(
            listOf("addDvrEntry", "cancelDvrEntry", "deleteDvrEntry"),
            methods,
        )
        assertEquals(RecordingWriteCapability.Allowed, repository.writeCapability.value)
    }

    @Test
    fun authDeniedSurvivesFailedRefreshWithoutOptimisticAllow() = runBlocking {
        val repository = repository { _, _ ->
            throw SocketTimeoutException("probe timeout")
        }
        repository.applyAuthenticatedDvrAccess(false)
        assertEquals(RecordingWriteCapability.Denied, repository.writeCapability.value)

        runCatching { repository.refreshConfigs() }
        // Transport failure must not flip Denied → Allowed / Unknown hide is already Denied.
        assertEquals(RecordingWriteCapability.Denied, repository.writeCapability.value)
        assertFalse(repository.canModifyRecordings.value)
    }

    private fun repository(
        handler: (method: String, fields: Map<String, Any?>) -> HtspMessage = { method, _ ->
            error("unexpected request: $method")
        },
    ) = DvrRepository(
        htsp = ScriptedHtspService(handler),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun message(vararg fields: Pair<String, Any?>) =
        HtspMessage(method = null, seq = 1, fields = mapOf(*fields))

    private class ScriptedHtspService(
        private val handler: (method: String, fields: Map<String, Any?>) -> HtspMessage,
    ) : HtspService(Dispatchers.Unconfined) {
        override suspend fun request(
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage = handler(method, fields)
    }
}

// Local alias so the timeout test does not need a full java.net import collision in signatures.
private typealias SocketTimeoutException = java.net.SocketTimeoutException
