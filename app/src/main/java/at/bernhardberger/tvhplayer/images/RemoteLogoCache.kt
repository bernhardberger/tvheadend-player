package at.bernhardberger.tvhplayer.images

import android.content.Context
import android.util.AtomicFile
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.memory.MemoryCache
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath

/** Only Coil's network fetcher writes here; the SDK owns its image-cache bytes separately. */
class RemoteLogoCache(context: Context) : Interceptor {
    private val directory = context.cacheDir.resolve("remote-channel-logos")
    private val generationFile = AtomicFile(directory.resolve("generation"))
    private val lock = Any()
    private var generation = if (generationFile.baseFile.exists()) {
        generationFile.openRead().bufferedReader().use { it.readText().toLong() }
    } else 0L
    val diskCache: DiskCache = DiskCache.Builder()
        // Keep the generation beside the entries: DiskCache.clear() removes its directory.
        .directory(directory.resolve("entries").toOkioPath())
        .maxSizeBytes(16L * 1024 * 1024)
        .build()
    @Volatile
    internal var memoryCache: MemoryCache? = null

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            // Admission waits until both persistence and eviction finish.
            generation += 1
            try {
                val output = generationFile.startWrite()
                try {
                    output.write(generation.toString().toByteArray())
                    generationFile.finishWrite(output)
                } catch (error: Throwable) {
                    generationFile.failWrite(output)
                    throw error
                }
            } finally {
                try {
                    diskCache.clear()
                } finally {
                    memoryCache?.clear()
                    DetailsArtColors.clear()
                }
            }
        }
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // SDK models do not pass through the network fetcher or this cache's clearing fence.
        val url = chain.request.data as? String ?: return chain.proceed()
        val started = synchronized(lock) { generation }
        val key = "$started|$url"
        val request = chain.request.newBuilder().diskCacheKey(key).memoryCacheKey(key).build()
        try {
            val result = chain.withRequest(request).proceed()
            return synchronized(lock) {
                if (generation == started) result else ErrorResult(
                    image = null, request = request, throwable = IOException("Logo cache cleared"),
                )
            }
        } finally {
            synchronized(lock) {
                if (generation != started) {
                    // Never evict a newer request for this URL, or unrelated SDK artwork.
                    diskCache.remove(key)
                    memoryCache?.let { memory ->
                        memory.keys.filter { it.key == key }.forEach(memory::remove)
                    }
                }
            }
        }
    }
}
