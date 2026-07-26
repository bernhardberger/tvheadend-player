package at.bernhardberger.tvhplayer.player.htsp

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import at.bernhardberger.tvhplayer.core.recordingReadLength
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.player.PlaybackReadMetrics
import java.io.Closeable
import kotlinx.coroutines.runBlocking

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
    private val path: String,
    private val knownSize: Long?,
    private val readMetrics: PlaybackReadMetrics,
) : DataSource, Closeable {
    private var uri: Uri? = null
    private var fileId: Int? = null
    private var bytesRemaining: Long? = null

    class Factory(
        private val htsp: HtspService,
        private val path: String,
        private val knownSize: Long?,
    ) : DataSource.Factory {
        internal val readMetrics = PlaybackReadMetrics()
        private var current: HtspRecordingDataSource? = null

        override fun createDataSource(): DataSource = HtspRecordingDataSource(
            htsp = htsp,
            path = path,
            knownSize = knownSize,
            readMetrics = readMetrics,
        ).also { current = it }

        fun releaseCurrentDataSource() {
            current?.close()
            current = null
        }
    }

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        close()
        uri = dataSpec.uri
        val id = runBlocking { htsp.fileOpen(path) }
        fileId = id
        if (dataSpec.position > 0L) {
            runBlocking { htsp.fileSeek(id, dataSpec.position) }
        }
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            knownSize != null -> (knownSize - dataSpec.position).coerceAtLeast(0L)
            else -> null
        }
        return bytesRemaining ?: C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val id = fileId ?: return C.RESULT_END_OF_INPUT
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
        val id = fileId ?: return
        fileId = null
        runCatching { runBlocking { htsp.fileClose(id) } }
        bytesRemaining = null
        uri = null
    }
}
