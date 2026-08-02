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
import at.bernhardberger.tvhplayer.htsp.PlaybackHtspTransport
import at.bernhardberger.tvhplayer.player.PlaybackReadMetrics
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

internal data class RecordingFileHandle(
    val id: Int,
    val htspVersion: Int?,
    val connectionAttemptId: Long,
)

internal class RecordingConnectionChangedException(cause: Throwable? = null) :
    IOException("Recording connection changed", cause)

internal class RecordingDataSourceOwner {
    private val sources = LinkedHashSet<Closeable>()
    private var released = false

    @Synchronized
    fun register(source: Closeable): Boolean {
        if (released) {
            source.close()
            return false
        }
        sources += source
        return true
    }

    @Synchronized
    fun unregister(source: Closeable) {
        sources -= source
    }

    @Synchronized
    fun releaseAll() {
        released = true
        val owned = sources.toList()
        sources.clear()
        owned.forEach { runCatching { it.close() } }
    }
}

internal class RecordingFileSession(
    private val htsp: PlaybackHtspTransport,
    private val path: String,
    private val htspVersion: () -> Int? = {
        (htsp.state.value as? ConnectionState.Connected)?.htspVersion
    },
    private val connectionAttemptId: () -> Long = htsp::currentConnectionAttemptId,
) {
    private val current = AtomicReference<RecordingFileHandle?>()

    @Synchronized
    fun open(position: Long) {
        close()
        val attemptId = connectionAttemptId()
        val id = try {
            runBlocking {
                htsp.fileOpen(
                    path = path,
                    expectedConnectionAttemptId = attemptId,
                )
            }
        } catch (cancelled: CancellationException) {
            throw RecordingConnectionChangedException(cancelled)
        }
        if (connectionAttemptId() != attemptId) {
            throw RecordingConnectionChangedException()
        }
        current.set(
            RecordingFileHandle(
                id = id,
                htspVersion = htspVersion(),
                connectionAttemptId = attemptId,
            )
        )
        try {
            if (position > 0L) {
                try {
                    runBlocking {
                        htsp.fileSeek(
                            id = id,
                            offset = position,
                            expectedConnectionAttemptId = attemptId,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw RecordingConnectionChangedException(cancelled)
                }
            }
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun currentHandle(): RecordingFileHandle? {
        val handle = current.get() ?: return null
        if (connectionAttemptId() != handle.connectionAttemptId) {
            throw RecordingConnectionChangedException()
        }
        return handle
    }

    @Synchronized
    fun close() {
        val handle = current.getAndSet(null) ?: return
        if (connectionAttemptId() != handle.connectionAttemptId) return
        runCatching {
            runBlocking {
                htsp.fileCloseRecording(
                    id = handle.id,
                    htspVersion = handle.htspVersion,
                    expectedConnectionAttemptId = handle.connectionAttemptId,
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
internal class HtspRecordingDataSource private constructor(
    private val htsp: PlaybackHtspTransport,
    path: String,
    private val knownSize: Long?,
    private val readMetrics: PlaybackReadMetrics,
    private val owner: RecordingDataSourceOwner,
) : DataSource, Closeable {
    private var uri: Uri? = null
    private val fileSession = RecordingFileSession(htsp = htsp, path = path)
    private var bytesRemaining: Long? = null

    class Factory(
        private val htsp: PlaybackHtspTransport,
        private val path: String,
        private val knownSize: Long?,
    ) : DataSource.Factory {
        internal val readMetrics = PlaybackReadMetrics()
        private val owner = RecordingDataSourceOwner()

        override fun createDataSource(): DataSource = HtspRecordingDataSource(
            htsp = htsp,
            path = path,
            knownSize = knownSize,
            readMetrics = readMetrics,
            owner = owner,
        ).also(owner::register)

        fun releaseCurrentDataSource() {
            owner.releaseAll()
        }
    }

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        close()
        check(owner.register(this)) { "Recording data source owner has been released" }
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
        val handle = fileSession.currentHandle() ?: return C.RESULT_END_OF_INPUT
        val requested = recordingReadLength(readLength, bytesRemaining)
        if (requested == 0) return C.RESULT_END_OF_INPUT
        val bytes = try {
            runBlocking {
                htsp.fileRead(
                    id = handle.id,
                    size = requested,
                    expectedConnectionAttemptId = handle.connectionAttemptId,
                )
            }
        } catch (cancelled: CancellationException) {
            throw RecordingConnectionChangedException(cancelled)
        }
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
        owner.unregister(this)
    }
}
