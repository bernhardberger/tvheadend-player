package at.bernhardberger.tvhplayer.images

import android.content.Context
import android.util.AtomicFile
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.memory.MemoryCache
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath

/** Only Coil's network fetcher writes here; the SDK owns its image-cache bytes separately. */
class RemoteLogoCache(context: Context) : Interceptor {
    private val directory = context.cacheDir.resolve("remote-channel-logos")
    private val generationFile = AtomicFile(directory.resolve("generation"))
    private val lock = Mutex()
    private var generation: Long? = null
    val diskCache: DiskCache = DiskCache.Builder()
        // Keep the generation beside the entries: DiskCache.clear() removes its directory.
        .directory(directory.resolve("entries").toOkioPath())
        .maxSizeBytes(16L * 1024 * 1024)
        .build()
    @Volatile
    internal var memoryCache: MemoryCache? = null

    // Called only under the mutex on IO. Construction does not read cache metadata.
    private fun initializedGeneration(): Long {
        generation?.let { return it }
        val saved = try {
            generationFile.openRead().bufferedReader().use { it.readText().trim().toLongOrNull() }
        } catch (_: IOException) {
            null
        }
        if (saved != null) return saved.also { generation = it }

        // Unknown metadata must never select surviving entries, even if eviction fails.
        val fresh = Random.nextLong()
        generation = fresh
        try {
            try {
                persistGeneration(fresh)
            } finally {
                diskCache.clear()
            }
        } catch (_: IOException) {
            android.util.Log.w("RemoteLogoCache", "Could not reset remote logo cache")
        }
        return fresh
    }

    private fun persistGeneration(value: Long) {
        val output = generationFile.startWrite()
        try {
            output.write(value.toString().toByteArray())
            generationFile.finishWrite(output)
        } catch (error: Throwable) {
            generationFile.failWrite(output)
            throw error
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock {
            // Admission waits until both persistence and eviction finish.
            val next = initializedGeneration() + 1
            generation = next
            try {
                persistGeneration(next)
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
        val started = withContext(Dispatchers.IO) {
            lock.withLock { initializedGeneration() }
        }
        val key = "$started|$url"
        val request = chain.request.newBuilder().diskCacheKey(key).memoryCacheKey(key).build()
        try {
            val result = chain.withRequest(request).proceed()
            return lock.withLock {
                if (generation == started) result else ErrorResult(
                    image = null, request = request, throwable = IOException("Logo cache cleared"),
                )
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                lock.withLock {
                    if (generation != started) {
                        // Never evict a newer request for this URL, or unrelated SDK artwork.
                        try {
                            diskCache.remove(key)
                        } finally {
                            memoryCache?.let { memory ->
                                memory.keys.filter { it.key == key }.forEach(memory::remove)
                            }
                        }
                    }
                }
            }
        }
    }
}
