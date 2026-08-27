package at.bernhardberger.tvhplayer.images

import android.content.Context
import at.bernhardberger.tvheadend.sdk.android.TvheadendArtwork
import at.bernhardberger.tvheadend.sdk.android.createTvheadendArtworkFetcherFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.map.Mapper
import coil3.request.Options

private class AppArtworkMapper(
    private val session: TvheadendSession,
) : Mapper<AppArtworkSource, TvheadendArtwork> {
    override fun map(data: AppArtworkSource, options: Options): TvheadendArtwork? =
        TvheadendArtwork.create(session, data.selector)
}

fun buildImageLoader(context: Context, session: TvheadendSession): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(AppArtworkMapper(session))
            add(createTvheadendArtworkFetcherFactory())
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("coil_disk_cache"))
                .maxSizeBytes(128L * 1024 * 1024)
                .build()
        }
        .build()
