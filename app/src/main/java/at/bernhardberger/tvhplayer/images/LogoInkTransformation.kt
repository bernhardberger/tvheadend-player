package at.bernhardberger.tvhplayer.images

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bakes [inkFadingLogoParts] into a logo for the solid [ground] colour it sits on, so parts that
 * would vanish there change lightness, then shrinks it to fit the [width] × [height] pixels it is
 * drawn in. The rule reads the 400×240 picon; drawn straight from that size, a logo several times
 * smaller turns thin lines into dots, so it is halved step by step, each step averaging pixels.
 */
internal class LogoInkTransformation(private val ground: Int, private val width: Int, private val height: Int) :
    Transformation() {
    override val cacheKey: String = "logoInk(${Integer.toHexString(ground)},${width}x$height)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val source = if (input.config == Bitmap.Config.ARGB_8888) input else input.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val inked = inkFadingLogoParts(pixels, source.width, source.height, ground)
        val logo = if (inked == null) source else Bitmap.createBitmap(inked, source.width, source.height, Bitmap.Config.ARGB_8888)
        return logo.shrunkToFit(width, height)
    }
}

private fun Bitmap.shrunkToFit(boxWidth: Int, boxHeight: Int): Bitmap {
    val scale = min(boxWidth.toFloat() / width, boxHeight.toFloat() / height)
    if (scale >= 1f) return this
    val targetWidth = max(1, (width * scale).roundToInt())
    val targetHeight = max(1, (height * scale).roundToInt())
    var shrunk = this
    while (shrunk.width / 2 >= targetWidth && shrunk.height / 2 >= targetHeight) {
        shrunk = Bitmap.createScaledBitmap(shrunk, shrunk.width / 2, shrunk.height / 2, true)
    }
    return if (shrunk.width == targetWidth && shrunk.height == targetHeight) shrunk
    else Bitmap.createScaledBitmap(shrunk, targetWidth, targetHeight, true)
}
