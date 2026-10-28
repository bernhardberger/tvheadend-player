package at.bernhardberger.tvhplayer.images

import android.content.Context
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.memory.MemoryCache
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath

/** Only Coil's network fetcher writes here; the SDK owns its image-cache bytes separately. */
class RemoteLogoCache(context: Context) : Interceptor {
    val diskCache: DiskCache = DiskCache.Builder()
        .directory(context.cacheDir.resolve("remote-channel-logos").toOkioPath())
        .maxSizeBytes(16L * 1024 * 1024)
        .build()
    private val generation = AtomicLong()
    internal var memoryCache: MemoryCache? = null

    suspend fun clear() = withContext(Dispatchers.IO) {
        generation.incrementAndGet()
        diskCache.clear()
        memoryCache?.clear()
        DetailsArtColors.clear()
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // SDK models do not pass through the network fetcher or this cache's clearing fence.
        val url = chain.request.data as? String ?: return chain.proceed()
        val started = generation.get()
        try {
            val result = chain.proceed()
            return if (generation.get() == started) result else ErrorResult(
                image = null, request = chain.request, throwable = IOException("Logo cache cleared"),
            )
        } finally {
            if (generation.get() != started) {
                // A response arriving after clear must not repopulate saved or decoded logos.
                diskCache.remove(chain.request.diskCacheKey ?: url)
                memoryCache?.clear()
            }
        }
    }
}
