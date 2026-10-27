package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.images.DetailsArtColors

/** Native focus glow, coloured by the card's picture, or its logo when there is no picture. */
@Composable
internal fun artworkCardGlow(
    art: ArtworkId?,
    picon: ArtworkId?,
    interactionSource: InteractionSource,
): CardGlow {
    val focused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    val artwork = LocalArtworkContext.current
    val session = artwork.currentSession
    val accents = LocalChannelAccents.current
    val logo by remember(artwork, picon) { derivedStateOf { picon?.let(artwork::logo) } }
    val picture = remember(session, art) { session?.let { current -> art?.let { AppArtworkSource(current, it) } } }
    val source = picture ?: logo
    val neutral = MaterialTheme.colorScheme.onSurface
    // A changed picture/profile must never inherit another card's colour, even for one frame.
    return key(source, picture != null, accents, context) {
        var rgb by remember { mutableStateOf(source?.let {
            if (picture != null) DetailsArtColors.stored(it) else accents.stored(it)
        }) }
        // Off-focus cards can use a cached colour, but do not start extra artwork decodes.
        LaunchedEffect(source, focused) {
            if (focused && source != null && rgb == null) {
                rgb = if (picture != null) DetailsArtColors.sample(context, source)
                    else accents.sample(context, source)
            }
        }
        val color by animateColorAsState(
            rgb?.let { Color(0xFF000000.toInt() or it) } ?: neutral,
            tween(320), label = "cardGlowColor",
        )
        CardDefaults.glow(focusedGlow = Glow(color.copy(alpha = 0.45f), 24.dp))
    }
}
