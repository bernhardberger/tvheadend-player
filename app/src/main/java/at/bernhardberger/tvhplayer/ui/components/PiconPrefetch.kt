package at.bernhardberger.tvhplayer.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.IntSize
import at.bernhardberger.tvhplayer.images.LogoInkTransformation
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield

/** One builder for the displayed and warmed variants, including Coil's transformation key. */
internal fun inkedPiconRequest(
    context: Context,
    data: Any,
    ground: Int,
    size: IntSize,
    placeholder: MemoryCache.Key? = null,
): ImageRequest = ImageRequest.Builder(context).data(data).size(400, 240)
    .transformations(LogoInkTransformation(ground, size.width, size.height))
    .placeholderMemoryCacheKey(placeholder)
    .apply { if (placeholder != null) crossfade(false) }
    .build()

// Visible loads never wait for this permit. Speculative work is deferred and single-flight,
// rather than decoding every channel at startup or competing with all the visible rows at once.
private val prefetchPermit = Semaphore(1)

internal suspend fun prefetchPicon(loader: ImageLoader, request: ImageRequest) =
    prefetchPermit.withPermit {
        yield()
        loader.execute(request)
    }

@Composable
internal fun PrefetchPicon(data: Any, ground: Int, size: IntSize) {
    val context = LocalPlatformContext.current
    val loader = SingletonImageLoader.get(context)
    LaunchedEffect(loader, data, ground, size) {
        // Called only after the visible variant succeeds. Let it draw before warming the other
        // fill. Leaving the lazy row or changing profile cancels queued/in-flight work.
        withFrameNanos { }
        withFrameNanos { }
        prefetchPicon(loader, inkedPiconRequest(context, data, ground, size))
    }
}
