package at.bernhardberger.tvhplayer.images

import android.content.Context
import at.bernhardberger.tvheadend.sdk.android.TvheadendArtwork
import at.bernhardberger.tvheadend.sdk.android.addTvheadendArtwork
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options

private class AppArtworkMapper(
    private val session: TvheadendSession,
) : Mapper<AppArtworkSource, TvheadendArtwork> {
    override fun map(data: AppArtworkSource, options: Options): TvheadendArtwork? =
        TvheadendArtwork.create(session, data.currentSession, data.selector)
}

fun buildImageLoader(context: Context, session: TvheadendSession): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(AppArtworkMapper(session))
            addTvheadendArtwork()
        }
        .diskCache(null)
        .build()
