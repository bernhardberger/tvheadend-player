package at.bernhardberger.tvhplayer.htsp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HtspRecordingFileCloseTest {

    @Test
    fun htsp27RecordingCloseKeepsCurrentPlayCount() = runBlocking {
        val service = RecordingCloseHtspService()

        service.fileCloseRecording(id = 42, htspVersion = 27)

        assertEquals(
            Request(
                method = "fileClose",
                fields = mapOf(
                    "id" to 42,
                    "playcount" to DVR_PLAY_COUNT_KEEP,
                ),
            ),
            service.requests.single(),
        )
    }

    @Test
    fun currentHtspRecordingCloseKeepsCurrentPlayCount() = runBlocking {
        val service = RecordingCloseHtspService()

        service.fileCloseRecording(id = 42, htspVersion = 43)

        assertEquals(DVR_PLAY_COUNT_KEEP, service.requests.single().fields["playcount"])
    }

    @Test
    fun htsp26RecordingCloseRemainsBare() = runBlocking {
        val service = RecordingCloseHtspService()

        service.fileCloseRecording(id = 42, htspVersion = 26)

        assertEquals(
            Request(method = "fileClose", fields = mapOf("id" to 42)),
            service.requests.single(),
        )
    }

    @Test
    fun unknownVersionRecordingCloseRemainsBare() = runBlocking {
        val service = RecordingCloseHtspService()

        service.fileCloseRecording(id = 42, htspVersion = null)

        assertEquals(
            Request(method = "fileClose", fields = mapOf("id" to 42)),
            service.requests.single(),
        )
    }

    @Test
    fun unrelatedFileCloseRemainsBare() = runBlocking {
        val service = RecordingCloseHtspService()

        service.fileClose(id = 42)

        assertEquals(
            Request(method = "fileClose", fields = mapOf("id" to 42)),
            service.requests.single(),
        )
    }

    private data class Request(
        val method: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingCloseHtspService : HtspService(Dispatchers.Unconfined) {
        val requests = mutableListOf<Request>()

        override suspend fun request(
            method: String,
            fields: Map<String, Any?>,
            timeoutMs: Long,
            flush: Boolean,
            disconnectOnTimeout: Boolean,
        ): HtspMessage {
            requests += Request(method, fields)
            return HtspMessage(method = null, seq = 1, fields = emptyMap())
        }
    }
}
