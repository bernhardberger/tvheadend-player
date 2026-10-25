package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.painterResource
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.transform.Transformation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.profiling.profileLayout
import at.bernhardberger.tvhplayer.profiling.profileTrace

@Composable
fun PiconBox(
    piconPath: ArtworkId?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    placeholderTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onError: (() -> Unit)? = null,
    /** Baked into the logo once per decode, at the view's size. */
    transformation: Transformation? = null,
    /** Without, only the logo itself shows: nothing while it loads or when there is none. */
    placeholders: Boolean = true,
    /**
     * The colour the logo sits on right now: the dark screen unless the caller knows better, such
     * as a focused row's light fill. Logo parts that would vanish on it change lightness. Null
     * keeps the logo as it is.
     */
    ground: Color? = MaterialTheme.colorScheme.background,
    /** The other focus state's fill. Only measured, composed rows warm this variant. */
    prefetchGround: Color? = null,
) = profileTrace("P1:compose:picon") {
    require(ground == null || transformation == null) { "a logo is either inked for its ground or transformed" }
    val artwork = LocalArtworkContext.current
    val currentSession = artwork.currentSession
    // Before a current session the saved copy is tried, so logos show at startup and offline.
    // Derived, so an observation of the same profile leaves the logo alone.
    val piconUrl by remember(artwork, piconPath) { derivedStateOf { piconPath?.let(artwork::logo) } }

    val icon = painterResource(R.drawable.ic_live_tv_outlined)
    // Keep the placeholder painters stable across focus changes; only their draw tint changes.
    val tint = rememberUpdatedState(placeholderTint)
    val loading = remember(icon) { PiconPlaceholderPainter(icon, tint, PICON_LOADING_ALPHA) }
    val failed = remember(icon) { PiconPlaceholderPainter(icon, tint, 1f) }

    // The pixels the logo is drawn in, so an inked logo is handed over at that size.
    var drawn by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier.profileLayout("picon").onSizeChanged { drawn = it },
        contentAlignment = alignment
    ) {
        if (piconUrl == null) {
            if (placeholders) Image(failed, null, Modifier.fillMaxSize())
        } else {
            val source = requireNotNull(piconUrl)
            // One icon, one size: a logo without a saved copy keeps looking like it is loading
            // until the session can fetch it, so it only ever changes once, when the logo fades
            // in. Only a failure with a current session is final; no logo keeps the full icon.
            val waitingForSession = currentSession == null
            val context = LocalPlatformContext.current
            val groundArgb = ground?.toArgb()
            // The logo last shown stands in while it is inked for a new ground, as when a row takes focus.
            val shown = remember(piconUrl) { arrayOfNulls<MemoryCache.Key>(1) }
            var loaded by remember(piconUrl) { mutableStateOf(false) }
            // Rounded up, so a box a pixel larger or smaller reuses the logo already inked.
            val inkSize = drawn.takeIf { it.width > 0 && it.height > 0 }
                ?.let { IntSize(it.width.roundUpTo(INK_SIZE_STEP), it.height.roundUpTo(INK_SIZE_STEP)) }
            val model = remember(piconUrl, transformation, groundArgb, inkSize) {
                when {
                    transformation != null ->
                        ImageRequest.Builder(context).data(piconUrl).transformations(transformation).build()
                    groundArgb == null -> piconUrl
                    inkSize == null -> null
                    // The ink rule is tuned on the server's 400×240 picons, so it reads that size, not the view's.
                    else -> inkedPiconRequest(context, source, groundArgb, inkSize, shown[0])
                }
            }
            if (loaded && transformation == null && groundArgb != null && inkSize != null && prefetchGround != null) {
                PrefetchPicon(source, prefetchGround.toArgb(), inkSize)
            }
            // Until the box has its size, one frame, nothing shows, so a logo already in memory
            // appears without the loading icon flashing before it. The box keeps the space it is given.
            if (model == null) Spacer(Modifier.fillMaxSize())
            else AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
                // Painter slots preserve loading/error appearance without image subcomposition.
                placeholder = loading.takeIf { placeholders && shown[0] == null },
                error = (if (waitingForSession) loading else failed).takeIf { placeholders },
                onSuccess = {
                    shown[0] = it.result.memoryCacheKey
                    if (prefetchGround != null) loaded = true
                },
                onError = { if (!waitingForSession) onError?.invoke() },
            )
        }
    }
}

private class PiconPlaceholderPainter(
    private val icon: Painter,
    private val tint: State<Color>,
    private val opacity: Float,
) : Painter() {
    // Let the image's allocated bounds size the placeholder, independently of contentScale.
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        val colorFilter = ColorFilter.tint(tint.value)
        val side = size.minDimension
        translate((size.width - side) / 2f, (size.height - side) / 2f) {
            with(icon) { draw(Size(side, side), alpha = opacity, colorFilter = colorFilter) }
        }
    }
}

@Composable
fun PiconPlaceholder(
    modifier: Modifier = Modifier,
    initials: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (!initials.isNullOrBlank()) {
        androidx.tv.material3.Text(
            text = initials,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.ic_live_tv_outlined),
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
    }
}

private const val PICON_LOADING_ALPHA = 0.35f
private const val INK_SIZE_STEP = 8

private fun Int.roundUpTo(step: Int) = (this + step - 1) / step * step
