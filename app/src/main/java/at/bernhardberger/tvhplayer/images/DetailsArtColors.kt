package at.bernhardberger.tvhplayer.images

import android.content.Context
import android.util.LruCache
import androidx.palette.graphics.Palette
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.ImageLoader
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Small process-only cache, scoped by the captured session and artwork, not by programme title. */
internal object DetailsArtColors {
    private val colors = LruCache<AppArtworkSource, Int>(128)
    private val sampling = Mutex()

    fun stored(source: AppArtworkSource): Int? = colors.get(source)

    suspend fun sample(loader: ImageLoader, context: Context, source: AppArtworkSource): Int? =
        withContext(Dispatchers.Default) {
            sampling.withLock {
                stored(source)?.let { return@withLock it }
                val result = loadArtworkSample(loader, context, source) as? SuccessResult
                    ?: return@withLock null
                // Prefer the colour that stands out (default filters skip skin tones and near
                // black/white); art without one, such as a black-and-white film, stays neutral.
                val bitmap = result.image.toBitmap()
                val vivid = Palette.from(bitmap).generate().let { it.vibrantSwatch ?: it.darkVibrantSwatch }
                val swatch = vivid ?: Palette.from(bitmap).clearFilters().generate().dominantSwatch
                val rgb = swatch?.rgb?.and(0xFFFFFF) ?: return@withLock null
                colors.put(source, rgb)
                rgb
            }
        }
}
