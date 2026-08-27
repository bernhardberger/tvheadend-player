package at.bernhardberger.tvhplayer.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import at.bernhardberger.tvhplayer.core.NEUTRAL_ACCENT_RGB
import at.bernhardberger.tvhplayer.core.resolvePiconModel
import at.bernhardberger.tvhplayer.core.selectAccentRgb
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Accent colours sampled once per channel and reused for the lifetime of the process.
 * Picons do not change while the app is running, so re-decoding them on every scroll
 * would be pure waste.
 */
private val accentCache = ConcurrentHashMap<Int, Int>()

/**
 * The channel's own brand colour, taken from its picon.
 *
 * Returns the neutral tint immediately and crossfades to the sampled colour when it
 * arrives, so cards never pop. Channels with no picon — or an `http(s)` icon, which this
 * HTSP-only client does not fetch — keep the neutral tint.
 */
@Composable
fun rememberChannelAccent(
    imageLoader: ImageLoader,
    piconPath: String?,
    channelId: Int,
    serverTag: String = "default",
): Color {
    val context = LocalContext.current
    val model = remember(serverTag, piconPath) { resolvePiconModel(serverTag, piconPath) }
    var rgb by remember(channelId) {
        mutableIntStateOf(accentCache[channelId] ?: NEUTRAL_ACCENT_RGB)
    }

    LaunchedEffect(channelId, model) {
        if (model == null) {
            rgb = NEUTRAL_ACCENT_RGB
            return@LaunchedEffect
        }
        val cached = accentCache[channelId]
        if (cached != null) {
            rgb = cached
            return@LaunchedEffect
        }
        val sampled = sampleChannelAccent(imageLoader, context, model)
        accentCache[channelId] = sampled
        rgb = sampled
    }

    val target = Color(0xFF000000.toInt() or rgb)
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 320),
        label = "channelAccent",
    )
    return animated
}

private suspend fun sampleChannelAccent(
    imageLoader: ImageLoader,
    context: Context,
    model: Any,
): Int = withContext(Dispatchers.Default) {
    val request = ImageRequest.Builder(context)
        .data(model)
        // Palette cannot read hardware bitmaps, and 64 px is plenty for a dominant colour.
        .allowHardware(false)
        .size(64, 64)
        .build()
    val bitmap = (imageLoader.execute(request) as? SuccessResult)
        ?.image
        ?.toBitmap()
        ?: return@withContext NEUTRAL_ACCENT_RGB

    val palette = Palette.from(bitmap).clearFilters().generate()
    // Deliberately not dominantSwatch first: for a logo on a white plate that is white.
    selectAccentRgb(
        listOf(
            palette.vibrantSwatch?.rgb?.and(0xFFFFFF),
            palette.lightVibrantSwatch?.rgb?.and(0xFFFFFF),
            palette.darkVibrantSwatch?.rgb?.and(0xFFFFFF),
            palette.mutedSwatch?.rgb?.and(0xFFFFFF),
            palette.dominantSwatch?.rgb?.and(0xFFFFFF),
        ),
    )
}
