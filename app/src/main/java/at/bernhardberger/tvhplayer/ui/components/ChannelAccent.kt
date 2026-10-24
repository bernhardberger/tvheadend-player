package at.bernhardberger.tvhplayer.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.core.NEUTRAL_ACCENT_RGB
import at.bernhardberger.tvhplayer.core.logoGlowPixels
import at.bernhardberger.tvhplayer.images.ChannelAccents
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The app's channel colours. The default, for a composition outside the app, keeps them in memory only. */
val LocalChannelAccents = staticCompositionLocalOf { ChannelAccents() }

/**
 * The channel's own brand colour, taken from its picon.
 *
 * A stored colour is returned in the first frame. Otherwise this returns the neutral tint, samples
 * the picon and crossfades to its colour when it arrives, so cards never pop. Channels without an
 * image-cache icon keep the neutral tint. A caller that waits for [onSettled] instead gets the
 * colour without the crossfade.
 */
@Composable
fun rememberChannelAccent(
    piconPath: ArtworkId?,
    channelId: ChannelId,
    onSettled: (() -> Unit)? = null,
): Color {
    val context = LocalContext.current
    val accents = LocalChannelAccents.current
    val artwork = LocalArtworkContext.current
    val currentOnSettled by rememberUpdatedState(onSettled)
    // As the logo's: before a session its saved copy names the stored colour.
    val model by remember(artwork, piconPath) { derivedStateOf { piconPath?.let(artwork::logo) } }
    var rgb by remember(channelId) {
        mutableIntStateOf(model?.let(accents::stored) ?: NEUTRAL_ACCENT_RGB)
    }

    LaunchedEffect(channelId, model, accents) {
        rgb = model?.let { accents.stored(it) ?: accents.sample(context, it) } ?: NEUTRAL_ACCENT_RGB
        currentOnSettled?.invoke()
    }

    val target = Color(0xFF000000.toInt() or rgb)
    if (onSettled != null) return target
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 320),
        label = "channelAccent",
    )
    return animated
}

/**
 * The page background lit by the channel's colour from its logo, the logo cards' colour, for a
 * programme without a picture: [logoGlowPixels], a soft glow from the top right. Opaque. It is
 * baked once per colour and size off the main thread, at half the size and stretched smoothly,
 * so it costs a frame no more than a picture does. Until it is baked the plain background shows,
 * and only then does [onSettled] report it. As [scrolled] goes to 1 the glow gives way to an even
 * tint of the same colour over the whole page, as a scrolled picture does to its even dim; a
 * flat fill cannot band.
 */
@Composable
internal fun ChannelLogoGlow(
    picon: ArtworkId?,
    channelId: ChannelId,
    modifier: Modifier = Modifier,
    scrolled: () -> Float = { 0f },
    onSettled: (() -> Unit)? = null,
) {
    // The settled colour only: baking the accent's crossfade would bake every frame of it.
    var accentSettled by remember(channelId, picon) { mutableStateOf(false) }
    val accent = rememberChannelAccent(picon, channelId, onSettled = { accentSettled = true })
    val background = MaterialTheme.colorScheme.background
    var size by remember { mutableStateOf(IntSize.Zero) }
    val key = LogoGlowKey(accent.toArgb() and 0xffffff, background.toArgb() and 0xffffff,
        (size.width / LogoGlowScale).coerceAtLeast(1), (size.height / LogoGlowScale).coerceAtLeast(1))
    val glow by produceState(LogoGlows[key], key, accentSettled, size != IntSize.Zero) {
        value = LogoGlows[key]
        if (value == null && accentSettled && size != IntSize.Zero) value = LogoGlows.bake(key)
    }
    val currentOnSettled by rememberUpdatedState(onSettled)
    if (glow != null) LaunchedEffect(key) { currentOnSettled?.invoke() }
    val tint = lerp(background, accent, LogoTintShare)
    Spacer(modifier.fillMaxSize().onSizeChanged { size = it }.drawBehind {
        val baked = glow
        val toTint = scrolled().coerceIn(0f, 1f)
        if (baked == null) {
            drawRect(background)
            if (toTint > 0f) drawRect(tint, alpha = toTint)
        } else {
            if (toTint > 0f) drawRect(tint)
            if (toTint < 1f) drawImage(baked, dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                filterQuality = FilterQuality.Low, alpha = 1f - toTint)
        }
    })
}

/** The accent's share in the scrolled page's even tint: a calm wash of the channel's colour. */
private const val LogoTintShare = 0.12f

/** The glow is baked at this fraction of its size; its dither keeps the stretch free of bands. */
private const val LogoGlowScale = 2

private data class LogoGlowKey(val accent: Int, val background: Int, val width: Int, val height: Int)

/** The last few baked glows, so returning to a channel or page shows its glow at once. */
private object LogoGlows {
    private const val Capacity = 4
    private val baked = object : LinkedHashMap<LogoGlowKey, ImageBitmap>(Capacity, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LogoGlowKey, ImageBitmap>) = size > Capacity
    }

    operator fun get(key: LogoGlowKey): ImageBitmap? = synchronized(baked) { baked[key] }

    suspend fun bake(key: LogoGlowKey): ImageBitmap = withContext(Dispatchers.Default) {
        val pixels = logoGlowPixels(key.accent, key.background, key.width, key.height)
        Bitmap.createBitmap(pixels, key.width, key.height, Bitmap.Config.ARGB_8888).asImageBitmap()
            .also { synchronized(baked) { baked[key] = it } }
    }
}
