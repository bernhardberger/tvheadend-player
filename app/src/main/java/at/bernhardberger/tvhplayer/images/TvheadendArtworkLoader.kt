package at.bernhardberger.tvhplayer.images

import android.content.Context
import at.bernhardberger.tvheadend.sdk.android.TvheadendArtwork
import at.bernhardberger.tvheadend.sdk.android.addTvheadendArtwork
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options
import coil3.request.crossfade

private class AppArtworkMapper(
    private val session: TvheadendSession,
    private val startupProfileOnly: () -> Boolean,
) : Mapper<AppArtworkSource, TvheadendArtwork> {
    // Before a current session the model reads only the active profile's saved copy, so saved
    // logos show at startup and offline; it shares the memory key the live model uses at Ready.
    // The saved copy belongs to the profile active at mapping, not to the one whose data named the
    // ID, so it is only used while the startup profile is the only one there has been: otherwise a
    // screen still built from the previous profile could show the new profile's logo for the same
    // ID. A connection change is recorded before the session switches, so checking it after the
    // capture rules out the new profile. From then on logos wait for the current session.
    @OptIn(ExperimentalTvheadendApi::class)
    override fun map(data: AppArtworkSource, options: Options): TvheadendArtwork? =
        data.currentSession?.let { TvheadendArtwork.create(session, it, data.id) }
            ?: TvheadendArtwork.cached(session, data.id)?.takeIf { startupProfileOnly() }
}

/** [startupProfileOnly] reports whether the profile loaded at process start is the only one so far. */
fun buildImageLoader(context: Context, session: TvheadendSession, startupProfileOnly: () -> Boolean): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(AppArtworkMapper(session, startupProfileOnly))
            addTvheadendArtwork()
        }
        .diskCache(null)
        // Every logo and programme picture fades in over its placeholder when it is actually
        // loaded; Coil shows memory-cache hits and failures at once, so scrolling never fades.
        .crossfade(PlayerMotion.MediumMs)
        .build()
