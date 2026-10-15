package at.bernhardberger.tvhplayer.images

import android.content.Context
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.allowHardware

/** Shared small software decode for Palette; never replace a drawable's full-size memory entry. */
internal suspend fun loadArtworkSample(loader: ImageLoader, context: Context, source: AppArtworkSource): ImageResult =
    loader.execute(ImageRequest.Builder(context)
        .data(source)
        .allowHardware(false)
        .size(64, 64)
        .memoryCachePolicy(CachePolicy.READ_ONLY)
        .build())
