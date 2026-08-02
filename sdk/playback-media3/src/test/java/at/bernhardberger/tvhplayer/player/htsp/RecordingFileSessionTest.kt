package at.bernhardberger.tvhplayer.player.htsp

import at.bernhardberger.tvhplayer.htsp.DVR_PLAY_COUNT_KEEP
import at.bernhardberger.tvhplayer.htsp.ConnectionState
import at.bernhardberger.tvhplayer.htsp.HtspConnectionAttemptStatus
import at.bernhardberger.tvhplayer.htsp.HtspEvent
import at.bernhardberger.tvhplayer.htsp.HtspMuxEvent
import at.bernhardberger.tvhplayer.htsp.PlaybackHtspTransport
import at.bernhardberger.tvhplayer.htsp.PlaybackSubscriptionStart
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
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

    @Test
    fun staleConnectionAttemptRejectsTheOldFileHandle() {
        val service = RecordingSessionHtspService()
        var connectionAttemptId = 1L
        val session = RecordingFileSession(
            htsp = service,
            path = "/recordings/example.ts",
            htspVersion = { 27 },
            connectionAttemptId = { connectionAttemptId },
        )
        session.open(position = 0L)

        connectionAttemptId = 2L

        assertThrows(RecordingConnectionChangedException::class.java) {
            session.currentHandle()
        }
        session.close()
        assertEquals(0, service.requests.count { it.method == "fileClose" })
    }

    private fun session(
        service: PlaybackHtspTransport,
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
    ) : PlaybackHtspTransport {
        private val nextFileId = AtomicInteger(1)
        val requests = ConcurrentLinkedQueue<Request>()
        override val state: StateFlow<ConnectionState> = MutableStateFlow(
            ConnectionState.Connected(
                host = "example.invalid",
                port = 9982,
                htspVersion = 43,
            )
        )
        override val controlEvents: Flow<HtspEvent> = emptyFlow()
        override val muxEvents: Flow<HtspMuxEvent> = emptyFlow()

        override suspend fun startSubscription(
            expectedConnectionAttemptId: Long,
            subscriptionId: Int,
            channelId: Int,
            timeshiftPeriodSec: Int,
            profile: String?,
        ): PlaybackSubscriptionStart = PlaybackSubscriptionStart(null)

        override suspend fun stopSubscription(
            expectedConnectionAttemptId: Long,
            subscriptionId: Int,
        ) = Unit

        override suspend fun setSubscriptionSpeed(
            expectedConnectionAttemptId: Long,
            subscriptionId: Int,
            speed: Int,
        ) = Unit

        override suspend fun seekSubscription(
            expectedConnectionAttemptId: Long,
            subscriptionId: Int,
            timeUs: Long,
            absolute: Boolean,
        ) = Unit

        override suspend fun fileOpen(
            path: String,
            timeoutMs: Long,
            expectedConnectionAttemptId: Long?,
        ): Int {
            val id = nextFileId.getAndIncrement()
            requests += Request("fileOpen", mapOf("file" to path))
            return id
        }

        override suspend fun fileRead(
            id: Int,
            size: Int,
            timeoutMs: Long,
            expectedConnectionAttemptId: Long?,
        ): ByteArray = ByteArray(0)

        override suspend fun fileSeek(
            id: Int,
            offset: Long,
            whence: String,
            timeoutMs: Long,
            expectedConnectionAttemptId: Long?,
        ): Long {
            if (failSeek) error("seek failed")
            requests += Request(
                "fileSeek",
                mapOf("id" to id, "offset" to offset, "whence" to whence),
            )
            return offset
        }

        override suspend fun fileCloseRecording(
            id: Int,
            htspVersion: Int?,
            timeoutMs: Long,
            expectedConnectionAttemptId: Long?,
        ) {
            requests += Request(
                "fileClose",
                if (htspVersion != null && htspVersion >= 27) {
                    mapOf("id" to id, "playcount" to DVR_PLAY_COUNT_KEEP)
                } else {
                    mapOf("id" to id)
                },
            )
        }

        override fun currentConnectionAttemptId(): Long = 1L

        override fun currentMuxSequenceForConnectionAttempt(attemptId: Long): Long? = 0L

        override fun isCurrentConnectionAttemptId(attemptId: Long): Boolean = attemptId == 1L

        override fun connectionAttemptStatus(attemptId: Long): HtspConnectionAttemptStatus =
            if (attemptId == 1L) {
                HtspConnectionAttemptStatus.LIVE
            } else {
                HtspConnectionAttemptStatus.REPLACED
            }

        override fun <T> commitIfCurrentConnectionAttempt(attemptId: Long, block: () -> T): T? =
            if (attemptId == 1L) block() else null

        override fun <T> commitIfLiveConnectionAttempt(attemptId: Long, block: () -> T): T? =
            if (attemptId == 1L) block() else null
    }
}
