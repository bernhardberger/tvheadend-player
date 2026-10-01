package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.NEUTRAL_ACCENT_RGB
import at.bernhardberger.tvhplayer.images.ChannelAccents
import coil3.ImageLoader

/** The app's channel colours. The default, for a composition outside the app, keeps them in memory only. */
val LocalChannelAccents = staticCompositionLocalOf { ChannelAccents() }

/**
 * The channel's own brand colour, taken from its picon.
 *
 * A stored colour is returned in the first frame. Otherwise this returns the neutral tint, samples
 * the picon and crossfades to its colour when it arrives, so cards never pop. Channels without an
 * image-cache icon keep the neutral tint.
 */
@Composable
fun rememberChannelAccent(
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    piconPath: ArtworkId?,
    channelId: ChannelId,
): Color {
    val context = LocalContext.current
    val accents = LocalChannelAccents.current
    val model = remember(currentSession, piconPath) {
        currentSession?.let { session -> piconPath?.let { AppArtworkSource(session, it) } }
    }
    var rgb by remember(channelId) {
        mutableIntStateOf(model?.let(accents::stored) ?: NEUTRAL_ACCENT_RGB)
    }

    LaunchedEffect(channelId, model, accents) {
        rgb = model?.let { accents.stored(it) ?: accents.sample(imageLoader, context, it) } ?: NEUTRAL_ACCENT_RGB
    }

    val target = Color(0xFF000000.toInt() or rgb)
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 320),
        label = "channelAccent",
    )
    return animated
}
