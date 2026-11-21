package at.bernhardberger.tvhplayer.images

import android.content.Context
import at.bernhardberger.tvheadend.sdk.android.TvheadendArtwork
import at.bernhardberger.tvheadend.sdk.android.addTvheadendArtwork
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options
import coil3.request.crossfade
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private class AppArtworkMapper(private val session: TvheadendSession) : Mapper<AppArtworkSource, TvheadendArtwork> {
    // Before a current session the model is the saved copy its observation's profile has, so saved
    // logos show at startup and offline; it shares the memory key the live model uses at Ready.
    // Once another profile is active it fails instead of reading that profile's copy.
    override fun map(data: AppArtworkSource, options: Options): TvheadendArtwork? =
        data.currentSession?.let { TvheadendArtwork.create(session, it, data.id) } ?: data.saved
}

fun remoteLogoHttpClient(userAgent: String): OkHttpClient = OkHttpClient.Builder()
    .retryOnConnectionFailure(false)
    .callTimeout(10, TimeUnit.SECONDS)
    .addNetworkInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
    }
    .build()

fun buildImageLoader(
    context: Context,
    session: TvheadendSession,
    remoteLogos: RemoteLogoCache = RemoteLogoCache(context),
    /** The only header remote logo hosts see; the caller names its app and version. */
    userAgent: String,
    crossfadeMillis: Int,
): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(AppArtworkMapper(session))
            addTvheadendArtwork()
            add(remoteLogos)
            // Dedicated client: no Tvheadend credentials or cookies; only the app User-Agent.
            add(OkHttpNetworkFetcherFactory(callFactory = { remoteLogoHttpClient(userAgent) }))
        }
        .diskCache(remoteLogos.diskCache)
        // Every logo and programme picture fades in over its placeholder when it is actually
        // loaded; Coil shows memory-cache hits and failures at once, so scrolling never fades.
        .crossfade(crossfadeMillis)
        .build()
        .also { remoteLogos.memoryCache = it.memoryCache }
