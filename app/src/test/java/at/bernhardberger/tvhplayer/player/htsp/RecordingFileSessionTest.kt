package at.bernhardberger.tvhplayer.player.htsp

import at.bernhardberger.tvhplayer.htsp.DVR_PLAY_COUNT_KEEP
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordingFileSessionTest {

    @Test
    fun extractorReopenAndSeekCloseEachHandleWithKeep() {
        val service = RecordingSessionHtspService()
        val session = session(service, htspVersion = 27)

        session.open(position = 0L)
        session.open(position = 4_096L)
        session.close()

        assertEquals(
            listOf(
                mapOf("id" to 1, "playcount" to DVR_PLAY_COUNT_KEEP),
                mapOf("id" to 2, "playcount" to DVR_PLAY_COUNT_KEEP),
            ),
            service.requests.filter { it.method == "fileClose" }.map { it.fields },
        )
        assertEquals(
            mapOf(
                "id" to 2,
                "offset" to 4_096L,
                "whence" to "SEEK_SET",
            ),
            service.requests.single { it.method == "fileSeek" }.fields,
        )
    }

    @Test
    fun concurrentCloseSendsOneRequestForTheHandle() {
        val service = RecordingSessionHtspService()
        val session = session(service, htspVersion = 27)
        session.open(position = 0L)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val tasks = List(2) {
                executor.submit {
                    start.await()
                    session.close()
                }
            }
            start.countDown()
            tasks.forEach { it.get(1, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, service.requests.count { it.method == "fileClose" })
    }

    @Test
    fun replacementSessionsCloseEachHandleExactlyOnce() {
        val service = RecordingSessionHtspService()
        val replaced = session(service, htspVersion = 27)
        val current = session(service, htspVersion = 27)
        replaced.open(position = 0L)
        current.open(position = 0L)

        replaced.close()
        replaced.close()
        current.close()
        current.close()

        assertEquals(
            listOf(1, 2),
            service.requests.filter { it.method == "fileClose" }.map { it.fields["id"] },
        )
    }

    @Test
    fun failedSeekClosesTheNewHandle() {
        val service = RecordingSessionHtspService(failSeek = true)
        val session = session(service, htspVersion = 27)

        assertThrows(IllegalStateException::class.java) {
            session.open(position = 4_096L)
        }

        assertEquals(1, service.requests.count { it.method == "fileClose" })
        session.close()
        assertEquals(1, service.requests.count { it.method == "fileClose" })
    }

    private fun session(
        service: HtspService,
        htspVersion: Int?,
    ) = RecordingFileSession(
        htsp = service,
        path = "/recordings/example.ts",
        htspVersion = { htspVersion },
    )

    private data class Request(
        val method: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingSessionHtspService(
        private val failSeek: Boolean = false,
    ) : HtspService(Dispatchers.Unconfined) {
        private val nextFileId = AtomicInteger(1)
        val requests = ConcurrentLinkedQueue<Request>()

        override suspend fun request(
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage {
            requests += Request(method, fields)
            if (method == "fileSeek" && failSeek) error("seek failed")
            val responseFields = when (method) {
                "fileOpen" -> mapOf("id" to nextFileId.getAndIncrement())
                "fileSeek" -> mapOf("offset" to fields.getValue("offset"))
                else -> emptyMap()
            }
            return HtspMessage(method = null, seq = 1, fields = responseFields)
        }
    }
}
