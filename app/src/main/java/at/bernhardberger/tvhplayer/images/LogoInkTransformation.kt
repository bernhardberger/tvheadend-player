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
 * With [trim], the picon's transparent margin goes first, so the logo's own shape is what fits.
 */
internal class LogoInkTransformation(
    private val ground: Int, private val width: Int, private val height: Int, private val trim: Boolean = false,
) : Transformation() {
    override val cacheKey: String = "logoInk(${Integer.toHexString(ground)},${width}x$height${if (trim) ",trim" else ""})"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val source = if (input.config == Bitmap.Config.ARGB_8888) input else input.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val inked = inkFadingLogoParts(pixels, source.width, source.height, ground)
        val logo = if (inked == null) source else Bitmap.createBitmap(inked, source.width, source.height, Bitmap.Config.ARGB_8888)
        val bounds = if (trim) visibleBounds(inked ?: pixels, source.width, source.height) else null
        val shape = if (bounds == null || bounds.width == source.width && bounds.height == source.height) logo
            else Bitmap.createBitmap(logo, bounds.left, bounds.top, bounds.width, bounds.height)
        return shape.shrunkToFit(width, height)
    }
}

internal data class LogoBounds(val left: Int, val top: Int, val width: Int, val height: Int)

/** The smallest rectangle holding every pixel at least [minAlpha] opaque; null when there is none. */
internal fun visibleBounds(pixels: IntArray, width: Int, height: Int, minAlpha: Int = 16): LogoBounds? {
    var left = width; var top = height; var right = -1; var bottom = -1
    for (y in 0 until height) for (x in 0 until width) {
        if (pixels[y * width + x] ushr 24 < minAlpha) continue
        if (x < left) left = x
        if (x > right) right = x
        if (y < top) top = y
        if (y > bottom) bottom = y
    }
    return if (right < 0) null else LogoBounds(left, top, right - left + 1, bottom - top + 1)
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
