package at.bernhardberger.tvhplayer.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.transform.Transformation
import kotlin.math.roundToInt

/**
 * A blurred, dimmed backdrop baked into a small picture once per decode: cropped to the request's
 * shape, [width] pixels wide, three box-blur passes of [radius] and black at [dimAlpha] on top.
 * Drawn stretched to the screen it reads like a live blur without the GPU repeating one on every
 * frame. With [fit], a logo is fitted into that share of the frame instead and keeps its
 * transparency, so the backdrop's colour shows around it. A picture darker than usual is dimmed
 * less, down to [minDimAlpha], so it ends about as bright as an ordinary one rather than black.
 */
internal class BackdropTransformation(
    private val width: Int,
    private val radius: Int,
    private val dimAlpha: Float,
    private val fit: Float? = null,
    private val minDimAlpha: Float = dimAlpha,
) : Transformation() {
    override val cacheKey: String = "backdrop($width;$radius;$dimAlpha;$fit;$minDimAlpha)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val aspect = size.height.pxOrElse { input.height }.toFloat() / size.width.pxOrElse { input.width }
        val height = (width * aspect).roundToInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val scale = fit?.let { minOf(width / input.width.toFloat(), height / input.height.toFloat()) * it }
            ?: maxOf(width / input.width.toFloat(), height / input.height.toFloat())
        canvas.save()
        canvas.translate((width - input.width * scale) / 2f, (height - input.height * scale) / 2f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(input, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        val pixels = IntArray(width * height)
        output.getPixels(pixels, 0, width, 0, 0, width, height)
        premultiply(pixels, unpremultiply = false)
        repeat(3) {
            boxBlur(pixels, width, height, radius, horizontal = true)
            boxBlur(pixels, width, height, radius, horizontal = false)
        }
        premultiply(pixels, unpremultiply = true)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        val dim = if (minDimAlpha >= dimAlpha) dimAlpha
            else (1f - OrdinaryPictureLuma * (1f - dimAlpha) / meanLuma(pixels)).coerceIn(minDimAlpha, dimAlpha)
        // Only over what was drawn: a logo's transparent surroundings stay clear.
        canvas.drawColor(android.graphics.Color.argb((dim * 255).roundToInt(), 0, 0, 0), PorterDuff.Mode.SRC_ATOP)
        return output
    }
}

/** An ordinary picture's mean brightness: [dimAlpha] darkens it to the brightness a darker one is lifted towards. */
private const val OrdinaryPictureLuma = 0.4f

/** The mean of the pixels' encoded luma, 0 to 1. */
private fun meanLuma(pixels: IntArray): Float {
    var sum = 0L
    for (p in pixels) sum += 299L * (p shr 16 and 0xff) + 587L * (p shr 8 and 0xff) + 114L * (p and 0xff)
    return sum / (pixels.size * 255_000f)
}

/** Blurs colour weighted by coverage, so transparent pixels don't bleed black into a logo's edges. */
private fun premultiply(pixels: IntArray, unpremultiply: Boolean) {
    for (i in pixels.indices) {
        val p = pixels[i]
        val a = p ushr 24
        if (a == 0xff) continue
        if (a == 0) { pixels[i] = 0; continue }
        fun channel(c: Int) = if (unpremultiply) minOf(0xff, c * 0xff / a) else c * a / 0xff
        pixels[i] = (a shl 24) or (channel(p shr 16 and 0xff) shl 16) or (channel(p shr 8 and 0xff) shl 8) or channel(p and 0xff)
    }
}

/** One box-blur pass along rows or columns of premultiplied pixels, clamping at the edges. */
internal fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val length = if (horizontal) width else height
    val lines = if (horizontal) height else width
    val span = 2 * radius + 1
    val line = IntArray(length)
    for (l in 0 until lines) {
        fun index(i: Int) = if (horizontal) l * width + i else i * width + l
        for (i in 0 until length) line[i] = pixels[index(i)]
        var a = 0; var r = 0; var g = 0; var b = 0
        for (k in -radius..radius) {
            val p = line[k.coerceIn(0, length - 1)]
            a += p ushr 24; r += p shr 16 and 0xff; g += p shr 8 and 0xff; b += p and 0xff
        }
        for (i in 0 until length) {
            pixels[index(i)] = ((a / span) shl 24) or ((r / span) shl 16) or ((g / span) shl 8) or (b / span)
            val out = line[(i - radius).coerceIn(0, length - 1)]
            val inn = line[(i + radius + 1).coerceIn(0, length - 1)]
            a += (inn ushr 24) - (out ushr 24)
            r += (inn shr 16 and 0xff) - (out shr 16 and 0xff)
            g += (inn shr 8 and 0xff) - (out shr 8 and 0xff)
            b += (inn and 0xff) - (out and 0xff)
        }
    }
}
