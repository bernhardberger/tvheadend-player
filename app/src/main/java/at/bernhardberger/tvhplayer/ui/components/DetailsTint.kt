package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.detailsTintRgb
import at.bernhardberger.tvhplayer.images.DetailsArtColors

/**
 * Only the details backdrop changes colour; the content retains the normal theme. The colour
 * animates in once sampled; a caller that waits for [onSettled] instead gets it without animation.
 * Before a session only a logo's stored colour is known.
 */
@Composable
internal fun rememberDetailsTint(
    art: ArtworkId?,
    picon: ChannelIcon?,
    onSettled: (() -> Unit)? = null,
): State<Color> {
    val currentOnSettled by rememberUpdatedState(onSettled)
    val neutral = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    val accents = LocalChannelAccents.current
    val artwork = LocalArtworkContext.current
    val session = artwork.currentSession
    // As the logo's: before a session its saved copy names the stored colour. Art needs the session.
    val logo by remember(artwork, picon) { derivedStateOf { picon?.let(artwork::logo) } }
    val piconSource = logo
    val artSource = remember(session, art) { session?.let { current -> art?.let { AppArtworkSource(current, it) } } }
    val source = if (art != null) artSource else piconSource
    // Replacing a page/source must not inherit the previous page's seed or animation.
    return key(source, piconSource, art != null, accents) {
        var seed by remember { mutableStateOf(source?.let {
            if (art != null) DetailsArtColors.stored(it) else accents.stored(it)
        }) }
        LaunchedEffect(source, context) {
            if (source != null && seed == null) {
                seed = if (art != null) DetailsArtColors.sample(context, source)
                    else accents.sample(context, source)
                if (seed == null && art != null && piconSource != null) {
                    seed = accents.stored(piconSource) ?: accents.sample(context, piconSource)
                }
            }
            currentOnSettled?.invoke()
        }
        val color = seed?.let { Color(0xFF000000.toInt() or detailsTintRgb(it)) } ?: neutral
        if (onSettled != null) rememberUpdatedState(color)
        else animateColorAsState(color, tween(320), label = "detailsTint")
    }
}
