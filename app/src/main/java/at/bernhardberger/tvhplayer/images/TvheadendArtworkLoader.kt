package at.bernhardberger.tvhplayer.images

import android.content.Context
import at.bernhardberger.tvheadend.sdk.android.TvheadendArtwork
import at.bernhardberger.tvheadend.sdk.android.addTvheadendArtwork
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options
import coil3.request.crossfade

private class AppArtworkMapper(
    private val session: TvheadendSession,
) : Mapper<AppArtworkSource, TvheadendArtwork> {
    override fun map(data: AppArtworkSource, options: Options): TvheadendArtwork =
        TvheadendArtwork.create(session, data.currentSession, data.id)
}

fun buildImageLoader(context: Context, session: TvheadendSession): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(AppArtworkMapper(session))
            addTvheadendArtwork()
        }
        .diskCache(null)
        // Every logo and programme picture fades in over its placeholder when it is actually
        // loaded; Coil shows memory-cache hits and failures at once, so scrolling never fades.
        .crossfade(PlayerMotion.MediumMs)
        .build()
