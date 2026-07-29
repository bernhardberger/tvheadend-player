package at.bernhardberger.tvhplayer.player.htsp

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import at.bernhardberger.tvhplayer.core.recordingReadLength
import at.bernhardberger.tvhplayer.htsp.ConnectionState
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.player.PlaybackReadMetrics
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

internal data class RecordingFileHandle(
    val id: Int,
    val htspVersion: Int?,
)

internal class RecordingFileSession(
    private val htsp: HtspService,
    private val path: String,
    private val htspVersion: () -> Int? = {
        (htsp.state.value as? ConnectionState.Connected)?.htspVersion
    },
) {
    private val current = AtomicReference<RecordingFileHandle?>()

    @Synchronized
    fun open(position: Long) {
        close()
        val id = runBlocking { htsp.fileOpen(path) }
        current.set(RecordingFileHandle(id = id, htspVersion = htspVersion()))
        try {
            if (position > 0L) {
                runBlocking { htsp.fileSeek(id, position) }
            }
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun currentId(): Int? = current.get()?.id

    @Synchronized
    fun close() {
        val handle = current.getAndSet(null) ?: return
        runCatching {
            runBlocking {
                htsp.fileCloseRecording(
                    id = handle.id,
                    htspVersion = handle.htspVersion,
                )
            }
        }
    }
}

/**
 * Seekable access to a TVHeadend DVR file over the already authenticated HTSP session.
 *
 * Media3 reopens a data source at the requested byte position when an extractor seeks.
 * Each open therefore uses HTSP fileOpen followed by fileSeek, while reads remain
 * bounded to the server-reported file size when one is available.
 */
@OptIn(UnstableApi::class)
class HtspRecordingDataSource private constructor(
    private val htsp: HtspService,
    path: String,
    private val knownSize: Long?,
    private val readMetrics: PlaybackReadMetrics,
) : DataSource, Closeable {
    private var uri: Uri? = null
    private val fileSession = RecordingFileSession(htsp = htsp, path = path)
    private var bytesRemaining: Long? = null

    class Factory(
        private val htsp: HtspService,
        private val path: String,
        private val knownSize: Long?,
    ) : DataSource.Factory {
        internal val readMetrics = PlaybackReadMetrics()
        private val current = AtomicReference<HtspRecordingDataSource?>()

        override fun createDataSource(): DataSource = HtspRecordingDataSource(
            htsp = htsp,
            path = path,
            knownSize = knownSize,
            readMetrics = readMetrics,
        ).also(current::set)

        fun releaseCurrentDataSource() {
            current.getAndSet(null)?.close()
        }
    }

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        close()
        uri = dataSpec.uri
        fileSession.open(dataSpec.position)
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            knownSize != null -> (knownSize - dataSpec.position).coerceAtLeast(0L)
            else -> null
        }
        return bytesRemaining ?: C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val id = fileSession.currentId() ?: return C.RESULT_END_OF_INPUT
        val requested = recordingReadLength(readLength, bytesRemaining)
        if (requested == 0) return C.RESULT_END_OF_INPUT
        val bytes = runBlocking { htsp.fileRead(id, requested) }
        if (bytes.isEmpty()) return C.RESULT_END_OF_INPUT
        val count = minOf(bytes.size, requested)
        bytes.copyInto(buffer, destinationOffset = offset, endIndex = count)
        bytesRemaining = bytesRemaining?.let { (it - count).coerceAtLeast(0L) }
        readMetrics.record(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        fileSession.close()
        bytesRemaining = null
        uri = null
    }
}
