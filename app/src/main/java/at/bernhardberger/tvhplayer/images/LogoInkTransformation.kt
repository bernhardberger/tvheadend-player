package at.bernhardberger.tvhplayer.images

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Bakes [inkFadingLogoParts] into a logo for the solid [ground] colour it sits on, so parts that
 * would vanish there change lightness. A logo without such parts comes back as decoded.
 */
internal class LogoInkTransformation(private val ground: Int) : Transformation() {
    override val cacheKey: String = "logoInk(${Integer.toHexString(ground)})"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val source = if (input.config == Bitmap.Config.ARGB_8888) input else input.copy(Bitmap.Config.ARGB_8888, false)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val inked = inkFadingLogoParts(pixels, width, height, ground) ?: return source
        return Bitmap.createBitmap(inked, width, height, Bitmap.Config.ARGB_8888)
    }
}
