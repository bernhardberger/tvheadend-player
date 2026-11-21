package at.bernhardberger.tvhplayer.images

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.allowHardware

/** Shared small software decode for Palette; never replace a drawable's full-size memory entry. */
internal suspend fun loadArtworkSample(context: Context, source: Any): ImageResult =
    SingletonImageLoader.get(context).execute(ImageRequest.Builder(context)
        .data(source)
        .allowHardware(false)
        .size(64, 64)
        .memoryCachePolicy(CachePolicy.READ_ONLY)
        .build())
