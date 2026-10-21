package at.bernhardberger.tvhplayer.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.transform.Transformation
import kotlin.math.roundToInt

/**
 * Fits a logo into its view with [margin] pixels to spare on every side and bakes a soft dark halo
 * behind its shape into that room, so it reads over a picture without the whole picture darkening.
 * The logo's outline is widened by a quarter of the margin and then blurred over the rest, so even
 * thin lettering gets an even cloud rather than a dark edge; it is [alpha] strong next to the logo.
 */
internal class LogoHaloTransformation(private val margin: Int, private val alpha: Float) : Transformation() {
    override val cacheKey: String = "logoHalo($margin;$alpha)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = size.width.pxOrElse { input.width + 2 * margin }
        val height = size.height.pxOrElse { input.height + 2 * margin }
        val logo = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val scale = minOf((width - 2 * margin) / input.width.toFloat(), (height - 2 * margin) / input.height.toFloat())
            .coerceAtLeast(0f)
        Canvas(logo).apply {
            translate((width - input.width * scale) / 2f, (height - input.height * scale) / 2f)
            scale(scale, scale)
            drawBitmap(input, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val pixels = IntArray(width * height)
        logo.getPixels(pixels, 0, width, 0, 0, width, height)
        val coverage = IntArray(pixels.size) { pixels[it] ushr 24 }
        val spread = margin / 4
        dilate(coverage, width, height, spread, horizontal = true)
        dilate(coverage, width, height, spread, horizontal = false)
        // Black carrying the widened coverage; three box passes soften it over the rest of the margin.
        val halo = IntArray(pixels.size) { coverage[it] shl 24 }
        val radius = ((margin - spread) / 3).coerceAtLeast(1)
        repeat(3) {
            boxBlur(halo, width, height, radius, horizontal = true)
            boxBlur(halo, width, height, radius, horizontal = false)
        }
        for (i in halo.indices) halo[i] = ((halo[i] ushr 24) * alpha).roundToInt() shl 24
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(halo, 0, width, 0, 0, width, height)
        Canvas(output).drawBitmap(logo, 0f, 0f, null)
        logo.recycle()
        return output
    }
}

/** Each value becomes the largest within [radius] along rows or columns. */
private fun dilate(values: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    if (radius <= 0) return
    val length = if (horizontal) width else height
    val lines = if (horizontal) height else width
    val line = IntArray(length)
    for (l in 0 until lines) {
        fun index(i: Int) = if (horizontal) l * width + i else i * width + l
        for (i in 0 until length) line[i] = values[index(i)]
        for (i in 0 until length) {
            var max = 0
            for (k in maxOf(0, i - radius)..minOf(length - 1, i + radius)) if (line[k] > max) max = line[k]
            values[index(i)] = max
        }
    }
}
