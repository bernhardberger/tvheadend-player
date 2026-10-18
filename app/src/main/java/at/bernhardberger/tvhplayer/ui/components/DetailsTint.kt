package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.detailsTintRgb
import at.bernhardberger.tvhplayer.images.DetailsArtColors

/** Only the details backdrop changes colour; the content retains the normal theme. */
@Composable
internal fun rememberDetailsTint(
    art: ArtworkId?,
    picon: ArtworkId?,
): State<Color> {
    val neutral = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    val accents = LocalChannelAccents.current
    val session = LocalArtworkContext.current.currentSession
    val source = remember(session, art, picon) {
        session?.let { current -> (art ?: picon)?.let { AppArtworkSource(current, it) } }
    }
    val piconSource = remember(session, picon) {
        session?.let { current -> picon?.let { AppArtworkSource(current, it) } }
    }
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
        }
        animateColorAsState(seed?.let { Color(0xFF000000.toInt() or detailsTintRgb(it)) } ?: neutral,
            tween(320), label = "detailsTint")
    }
}
