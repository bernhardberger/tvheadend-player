package at.bernhardberger.tvhplayer.testutil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult

/**
 * Fictional broadcast art for captures: channel picons and programme key art from the
 * project's generated pack, described in `src/test/resources/fixture-art/README.md`.
 * Decoding needs `@GraphicsMode(NATIVE)`.
 */
object FixtureArt {
    /** A channel picon by its pack name, e.g. `harbor-sport`: 400×240, transparent. */
    fun picon(name: String): Bitmap = decode("picons/$name.png")

    /** Programme key art or a still by its pack name, e.g. `harbor-kickoff`. */
    fun art(name: String): Bitmap = decode("art/$name.jpg")

    /**
     * Answers every artwork request from [images] without server or network access;
     * an id without an image fails the way a missing server image does.
     */
    fun imageLoader(context: Context, images: Map<ArtworkId, Bitmap>): ImageLoader =
        ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                val id = (chain.request.data as? AppArtworkSource)?.id
                val image = images[id]
                if (image != null) SuccessResult(image.asImage(), chain.request, DataSource.MEMORY)
                else ErrorResult(null, chain.request, IllegalStateException("no fixture art for $id"))
            })
        }.build()

    private fun decode(path: String): Bitmap {
        val stream = checkNotNull(FixtureArt::class.java.getResourceAsStream("/fixture-art/$path")) { "missing fixture art $path" }
        return stream.use { checkNotNull(BitmapFactory.decodeStream(it)) { "undecodable fixture art $path" } }
    }
}
