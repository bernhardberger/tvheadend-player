package at.bernhardberger.tvhplayer.images

import android.content.Context
import android.util.LruCache
import androidx.palette.graphics.Palette
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small process-only cache, scoped by the captured session and artwork, not by programme title. */
internal object DetailsArtColors {
    // Art without a usable colour is remembered too, so it is not decoded again on every focus.
    private val colors = LruCache<Any, Sample>(128)
    private var generation = 0L

    private class Sample(val rgb: Int?)

    fun stored(source: Any): Int? = colors.get(source)?.rgb

    fun clear() = synchronized(colors) {
        generation += 1
        colors.evictAll()
    }

    // Only admission/publication take the lock; a slow decode never holds up other artwork.
    suspend fun sample(context: Context, source: Any): Int? =
        withContext(Dispatchers.Default) {
            val started = synchronized(colors) {
                colors.get(source)?.let { return@withContext it.rgb }
                generation
            }
            val result = loadArtworkSample(context, source) as? SuccessResult
                ?: return@withContext null
            // Prefer the colour that stands out (default filters skip skin tones and near
            // black/white); art without one, such as a black-and-white film, stays neutral.
            val bitmap = result.image.toBitmap()
            val vivid = Palette.from(bitmap).generate().let { it.vibrantSwatch ?: it.darkVibrantSwatch }
            val swatch = vivid ?: Palette.from(bitmap).clearFilters().generate().dominantSwatch
            val rgb = swatch?.rgb?.and(0xFFFFFF)
            synchronized(colors) {
                if (generation != started) return@withContext null
                colors.put(source, Sample(rgb))
                rgb
            }
        }
}
