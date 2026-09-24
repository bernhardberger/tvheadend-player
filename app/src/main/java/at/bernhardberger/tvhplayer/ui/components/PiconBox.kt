package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import coil3.ImageLoader
import coil3.compose.AsyncImage
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.profiling.profileLayout
import at.bernhardberger.tvhplayer.profiling.profileTrace

@Composable
fun PiconBox(
    imageLoader: ImageLoader,
    piconPath: ArtworkId?,
    modifier: Modifier = Modifier,
    currentSession: CurrentSessionObservation? = null,
    contentScale: ContentScale = ContentScale.Fit,
) = profileTrace("P1:compose:picon") {
    val piconUrl = remember(currentSession, piconPath) {
        currentSession?.let { session -> piconPath?.let { AppArtworkSource(session, it) } }
    }

    Box(
        modifier = modifier.profileLayout("picon"),
        contentAlignment = Alignment.Center
    ) {
        if (piconUrl == null) {
            PiconPlaceholder(modifier = Modifier.fillMaxSize(0.5f))
        } else {
            val icon = painterResource(R.drawable.ic_live_tv_outlined)
            val tint = MaterialTheme.colorScheme.onSurfaceVariant
            val loading = remember(icon, tint) { PiconPlaceholderPainter(icon, tint, PICON_LOADING_ALPHA) }
            val failed = remember(icon, tint) { PiconPlaceholderPainter(icon, tint, 1f) }
            AsyncImage(
                model = piconUrl,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                // Painter slots preserve loading/error appearance without image subcomposition.
                placeholder = loading,
                error = failed,
            )
        }
    }
}

private class PiconPlaceholderPainter(
    private val icon: Painter,
    tint: Color,
    private val opacity: Float,
) : Painter() {
    private val colorFilter = ColorFilter.tint(tint)
    // Let the image's allocated bounds size the placeholder, independently of contentScale.
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

private const val PICON_LOADING_ALPHA = 0.35f
