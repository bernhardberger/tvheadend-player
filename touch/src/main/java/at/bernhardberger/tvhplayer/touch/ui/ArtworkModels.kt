package at.bernhardberger.tvhplayer.touch.ui

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.channelLogoModel
import at.bernhardberger.tvhplayer.core.programmeArtworkModel
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import java.net.URI

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
    LogFailedLoad(model, state)
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
            LogFailedLoad((model as? ImageRequest)?.data ?: model, state)
            if (state !is AsyncImagePainter.State.Success) fallback()
            // Drawn while it loads too: the painter resolves its size when it draws.
            Image(server, null, modifier, contentScale = contentScale)
        }
        else -> fallback()
    }
}

/**
 * In a debuggable build, one warning per server image that fails to load, so "the event has no
 * picture" can be told from "its picture failed" (a failed one is left out, as a missing one).
 */
@Composable
private fun LogFailedLoad(model: Any, state: AsyncImagePainter.State) {
    val error = (state as? AsyncImagePainter.State.Error)?.result?.throwable
    val debuggable = (LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    LaunchedEffect(model, error != null) {
        if (error != null && debuggable) Log.w(ARTWORK_LOG_TAG, artworkFailureLine(model, error))
    }
}

/**
 * The failed load's line: the kind of source, a remote one's scheme and host only, and the error's
 * class. Paths, queries, user info and the error's message (which may hold the URL) stay out.
 */
internal fun artworkFailureLine(model: Any, error: Throwable): String {
    val source = when (model) {
        is AppArtworkSource -> "image cache"
        is String -> runCatching { URI(model) }.getOrNull()
            ?.let { uri -> "remote ${uri.scheme ?: "?"}://${uri.host ?: "?"}" } ?: "remote (unreadable address)"
        else -> "other source"
    }
    return "Image failed to load from $source: ${error.javaClass.name}"
}

private const val ARTWORK_LOG_TAG = "TouchArtwork"
