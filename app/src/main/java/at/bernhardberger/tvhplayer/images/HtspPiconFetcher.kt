package at.bernhardberger.tvhplayer.images

import android.content.Context
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.map.Mapper
import coil3.request.Options
import at.bernhardberger.tvheadend.client.ConnectionState
import at.bernhardberger.tvheadend.client.TvheadendClient
import at.bernhardberger.tvhplayer.ui.components.HtspPiconData
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.Buffer
import okio.FileSystem

class HtspPiconMapper(
    private val defaultTtlMs: Long = 6 * 60 * 60 * 1000L
) : Mapper<String, HtspPiconData> {

    override fun map(data: String, options: Options): HtspPiconData? {
        if (!data.startsWith("htsp-picon://")) return null

        val noScheme = data.removePrefix("htsp-picon://")
        val slash = noScheme.indexOf('/')
        if (slash <= 0) return null

        val tag = noScheme.substring(0, slash)
        val path = noScheme.substring(slash + 1)

        return HtspPiconData(
            serverTag = tag,
            path = path,
            ttlMs = defaultTtlMs
        )
    }
}

class HtspPiconKeyer : Keyer<HtspPiconData> {
    override fun key(data: HtspPiconData, options: Options): String {
        val p = data.path.let { if (it.startsWith("/")) it else "/$it" }
        val bucket = if (data.ttlMs > 0) (System.currentTimeMillis() / data.ttlMs) else 0L
        return "htsp-picon:${data.serverTag}:$p:bucket=$bucket"
    }
}

class HtspPiconFetcher(
    private val client: TvheadendClient,
    private val data: HtspPiconData
) : Fetcher {

    override suspend fun fetch(): FetchResult = piconSemaphore.withPermit {
        val connected = ensureConnectedOrWait(maxWaitMs = 800)
        if (!connected) {
            throw IllegalStateException("HTSP not connected")
        }

        val path = data.path.let { if (it.startsWith("/")) it else "/$it" }

        val bytes = client.readFileBytes(path = path, maxBytes = MAX_PICON_BYTES)
        SourceFetchResult(
            source = ImageSource(
                source = Buffer().write(bytes),
                fileSystem = FileSystem.SYSTEM
            ),
            mimeType = null,
            dataSource = DataSource.NETWORK
        )
    }

    private suspend fun ensureConnectedOrWait(maxWaitMs: Long): Boolean {
        if (client.connectionState.value is ConnectionState.Connected) return true

        val step = 100L
        var waited = 0L
        while (waited < maxWaitMs) {
            delay(step)
            waited += step
            if (client.connectionState.value is ConnectionState.Connected) return true
        }
        return client.connectionState.value is ConnectionState.Connected
    }

    class Factory(private val client: TvheadendClient) : Fetcher.Factory<HtspPiconData> {
        override fun create(
            data: HtspPiconData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return HtspPiconFetcher(client, data)
        }
    }

    companion object {
        private const val MAX_PICON_BYTES = 4 * 1024 * 1024
        private val piconSemaphore = Semaphore(permits = 3)
    }
}

fun buildImageLoader(context: Context, client: TvheadendClient): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(HtspPiconMapper(defaultTtlMs = 6 * 60 * 60 * 1000L))
            add(HtspPiconKeyer())
            add(HtspPiconFetcher.Factory(client))
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("coil_disk_cache"))
                .maxSizeBytes(128L * 1024 * 1024)
                .build()
        }
        .build()
}
