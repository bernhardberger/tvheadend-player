package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.images.DetailsArtColors

/**
 * How far past a card's edge its focus glow still shows: the 24dp shadow fades below one colour
 * level about 36dp out, and the card's focus scale lifts its edge a few dp further.
 */
internal val CardGlowReach = 40.dp

/**
 * Lets a row or grid of cards draw [horizontal], [top] and [bottom] past its own bounds, so a
 * focused card's glow fades out instead of ending where the list clips. The list keeps its size and
 * place; its content padding grows by the same amounts so its cards keep theirs. Goes outside any
 * offscreen layer on the list (an edge fade, a dimming alpha), which would cut at its bounds too.
 */
internal fun Modifier.cardGlowRoom(horizontal: Dp = 0.dp, top: Dp = 0.dp, bottom: Dp = top): Modifier =
    layout { measurable, constraints ->
        val h = horizontal.roundToPx()
        val t = top.roundToPx()
        val b = bottom.roundToPx()
        val placeable = measurable.measure(constraints.offset(2 * h, t + b))
        layout(placeable.width - 2 * h, placeable.height - t - b) { placeable.place(-h, -t) }
    }

/** Native focus glow, coloured by the card's picture, or its logo when there is no picture. */
@Composable
internal fun artworkCardGlow(
    art: ArtworkId?,
    picon: ChannelIcon?,
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
