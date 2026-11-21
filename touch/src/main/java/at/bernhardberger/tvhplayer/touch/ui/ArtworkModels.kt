package at.bernhardberger.tvhplayer.touch.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.channelLogoModel
import at.bernhardberger.tvhplayer.core.programmeArtworkModel
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

/**
 * The image models of the server's artwork, by the TV's rules, for the mappings to put into their
 * states as opaque values: a logo by `ArtworkContext.logo` (app ArtworkContext.kt), an image-cache
 * logo with the observation's current session, else the saved copy of its profile; a program or
 * recording picture by `ProgrammePane` (app ProgrammePane.kt:102), only with a current session.
 * [tvheadend] null leaves saved copies out, as a mapping test needs.
 */
internal class ArtworkModels(private val tvheadend: TvheadendSession?) {
    fun logo(icon: ChannelIcon?, observation: SessionObservation): Any? {
        icon ?: return null
        val cached = icon is ChannelIcon.ImageCache
        val current = if (cached) observation.currentSession else null
        val observed = if (cached && current == null) observation else null
        return channelLogoModel(icon, current, tvheadend, observed)
    }

    fun picture(icon: ChannelIcon?, observation: SessionObservation): Any? {
        val session = observation.currentSession ?: return null
        return icon?.let { programmeArtworkModel(it, session) }
    }
}

/** The app's artwork models; null outside the app, where states carry fixture painters only. */
internal val LocalArtworkModels = staticCompositionLocalOf<ArtworkModels?> { null }

/**
 * The picture that stands on its own (section 13, "A program without a picture"): a fixture's
 * [painter], else the server image of [model], else null, and the screen leaves the picture out for
 * its text to take the place. Nothing stands in for a missing picture; this is the one place that
 * decides so (the TV's logo-as-picture, not chosen, would be decided here). Whether a picture exists
 * is known from the data, so the place stays while the server image loads; one that fails to load
 * counts as none, and its place then goes once.
 */
@Composable
internal fun rememberStandalonePicture(painter: Painter?, model: Any?): Painter? {
    if (painter != null || model == null) return painter
    val server = rememberAsyncImagePainter(model, contentScale = ContentScale.Crop)
    val state by server.state.collectAsState()
    return server.takeUnless { state is AsyncImagePainter.State.Error }
}

/**
 * A fixture's [painter], else the server image of [model] through the app's image loader, else
 * nothing; [fallback] shows until the server image has loaded, and stays when it fails.
 */
@Composable
internal fun ArtworkImage(
    painter: Painter?,
    model: Any?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    fallback: @Composable () -> Unit = {},
) {
    when {
        painter != null -> Image(painter, null, modifier, contentScale = contentScale)
        model != null -> {
            val server = rememberAsyncImagePainter(model, contentScale = contentScale)
            val state by server.state.collectAsState()
            if (state !is AsyncImagePainter.State.Success) fallback()
            // Drawn while it loads too: the painter resolves its size when it draws.
            Image(server, null, modifier, contentScale = contentScale)
        }
        else -> fallback()
    }
}
