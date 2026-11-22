package at.bernhardberger.tvhplayer.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import at.bernhardberger.tvhplayer.core.DETAILS_LIGHTEST_TINT_CHANNEL
import at.bernhardberger.tvhplayer.core.detailsBackdropDimAlpha
import at.bernhardberger.tvhplayer.core.detailsRelativeLuminance
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.transform.Transformation
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The kit's "Cinematic scrim" (Figma 8689:24742 › Scrim) baked into a picture's alpha once per
 * decode, cropped to the request's shape. The scrim is the page's colour at 10% at the frame's
 * top-right, opaque from 70.74% of the way to the bottom-left corner, so the picture keeps 90% of
 * itself there and fades out to clear. Over that colour it looks the same as the scrim drawn over
 * the picture, without the GPU drawing the gradient again on every frame, and it follows the
 * colour while that changes.
 */
internal class CinematicScrimTransformation(private val density: Float, private val rtl: Boolean) : Transformation() {
    override val cacheKey: String = "cinematicScrim-clock:$density:$rtl"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap =
        scrimmed(input, size, density, rtl) { width, height -> listOf(kitCinematicScrimMask(width, height)) }
}

/**
 * The Guide headline's softer scrim. The picture keeps 90% of itself at its top-right, as under
 * the kit's scrim, and eases out to clear along the whole of its left edge and, by 64% of its
 * height, its bottom, so it never shows a cut. The picture reaches into the grid; gone by then,
 * it never sits behind the time ruler at any font size.
 */
internal class GuideScrimTransformation(private val density: Float, private val rtl: Boolean) : Transformation() {
    override val cacheKey: String = "guideScrim-clock:$density:$rtl"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap =
        scrimmed(input, size, density, rtl) { width, height ->
            listOf(
                easedRamp(0f, 0f, width * .6f, 0f, from = 0f, to = .9f),
                easedRamp(0f, height * .2f, 0f, height * .64f, from = 1f, to = 0f),
            )
        }
}

/** Alpha easing from [from] at (x0, y0) to [to] at (x1, y1) along a smoothstep curve, held beyond either end. */
private fun easedRamp(x0: Float, y0: Float, x1: Float, y1: Float, from: Float, to: Float): Shader {
    val positions = FloatArray(9) { it / 8f }
    val colors = IntArray(positions.size) { index ->
        val t = positions[index]
        val alpha = from + (to - from) * t * t * (3 - 2 * t)
        (alpha * 0xff).roundToInt() shl 24
    }
    return LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP)
}

/** [input] cropped as ContentScale.Crop would, at the decode's own resolution, with [masks] applied to its alpha. */
private fun scrimmed(
    input: Bitmap, size: Size, density: Float, rtl: Boolean, masks: (Float, Float) -> List<Shader>,
): Bitmap {
    val width = size.width.pxOrElse { 16 }
    val height = size.height.pxOrElse { 9 }
    val scale = maxOf(width / input.width.toFloat(), height / input.height.toFloat())
    val outputWidth = (width / scale).roundToInt().coerceIn(1, input.width)
    val outputHeight = (height / scale).roundToInt().coerceIn(1, input.height)
    val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(input, (outputWidth - input.width) / 2f, (outputHeight - input.height) / 2f,
        Paint(Paint.FILTER_BITMAP_FLAG))
    masks(outputWidth.toFloat(), outputHeight.toFloat()).forEach { mask ->
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), Paint().apply {
            isDither = false
            shader = mask
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        })
    }
    shadeClock(output, canvas, width / density / outputWidth, height / density / outputHeight, rtl)
    return output
}

// Distances from the picture's trailing edge and top on the 960×540 reference canvas. The
// picture and clock move together in RTL; the existing artwork scrims do not move.
private const val CLOCK_SHADE_REACH = 400f
private const val CLOCK_SHADE_DEPTH = 180f
private const val CLOCK_SHADE_FULL_END = 125f
private const val CLOCK_SHADE_FULL_TOP = 68f
private const val CLOCK_NEAR_END = 55f
private const val CLOCK_FAR_END = 160f
private const val CLOCK_TOP = 24f
private const val CLOCK_BOTTOM = 76f
private const val CLOCK_SAMPLE_COLUMNS = 16
private const val CLOCK_SAMPLE_ROWS = 16
private const val CLOCK_MAX_MISS_SHARE = .1
private const val CLOCK_CONTRAST_TARGET = 4.55
private const val CONTRAST_LUMINANCE_OFFSET = .05
private const val CLOCK_SHADE_CEILING = .85f
private const val OPAQUE_ALPHA = 255
private const val ALPHA_SHIFT = 24

@Suppress("MagicNumber") // Smoothstep polynomial coefficients.
private fun smoothstep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private fun clockEndWeight(endDp: Float) =
    smoothstep((CLOCK_SHADE_REACH - endDp) / (CLOCK_SHADE_REACH - CLOCK_SHADE_FULL_END))

private fun clockTopWeight(topDp: Float) =
    smoothstep((CLOCK_SHADE_DEPTH - topDp) / (CLOCK_SHADE_DEPTH - CLOCK_SHADE_FULL_TOP))

private fun clockMaskAlpha(endAlphaLoss: Float, topWeight: Float) =
    (OPAQUE_ALPHA - endAlphaLoss * topWeight).roundToInt()

private fun shadeClock(output: Bitmap, canvas: Canvas, dpPerX: Float, dpPerY: Float, rtl: Boolean) {
    val strength = measureClockShade(output, dpPerX, dpPerY, rtl)
    if (strength == 0f) return

    // Write just the corner mask, then multiply it into the existing alpha in one software draw.
    val width = ceil(CLOCK_SHADE_REACH / dpPerX).toInt().coerceAtMost(output.width)
    val height = ceil(CLOCK_SHADE_DEPTH / dpPerY).toInt().coerceAtMost(output.height)
    val horizontal = FloatArray(width) { x ->
        val end = if (rtl) x + .5f else width - x - .5f
        OPAQUE_ALPHA * strength * clockEndWeight(end * dpPerX)
    }
    val vertical = FloatArray(height) { y -> clockTopWeight((y + .5f) * dpPerY) }
    val mask = IntArray(width * height)
    for (y in vertical.indices) {
        val row = y * width
        val topWeight = vertical[y]
        for (x in horizontal.indices) mask[row + x] = clockMaskAlpha(horizontal[x], topWeight) shl ALPHA_SHIFT
    }
    val left = if (rtl) 0 else output.width - width
    @Suppress("DEPRECATION") // The canvas is software and consumes the pixels synchronously.
    canvas.drawBitmap(mask, 0, width, left, 0, width, height, true, Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    })
}

private fun measureClockShade(output: Bitmap, dpPerX: Float, dpPerY: Float, rtl: Boolean): Float {
    val clockEnd = minOf(CLOCK_FAR_END, output.width * dpPerX)
    val clockBottom = minOf(CLOCK_BOTTOM, output.height * dpPerY)
    if (clockEnd <= CLOCK_NEAR_END || clockBottom <= CLOCK_TOP) return 0f
    val columns = IntArray(CLOCK_SAMPLE_COLUMNS) { column ->
        val end = CLOCK_NEAR_END + (clockEnd - CLOCK_NEAR_END) * (column + .5f) / CLOCK_SAMPLE_COLUMNS
        (end / dpPerX).toInt().coerceAtMost(output.width - 1)
    }
    val rows = IntArray(CLOCK_SAMPLE_ROWS) { row ->
        val top = CLOCK_TOP + (clockBottom - CLOCK_TOP) * (row + .5f) / CLOCK_SAMPLE_ROWS
        (top / dpPerY).toInt().coerceAtMost(output.height - 1)
    }
    val endWeights = FloatArray(columns.size) { clockEndWeight((columns[it] + .5f) * dpPerX) }
    val topWeights = FloatArray(rows.size) { clockTopWeight((rows[it] + .5f) * dpPerY) }
    val samples = IntArray(columns.size * rows.size) { index ->
        val end = columns[index % columns.size]
        val x = if (rtl) end else output.width - 1 - end
        output.getPixel(x, rows[index / columns.size])
    }
    val allowedMisses = (samples.size * CLOCK_MAX_MISS_SHARE).toInt()
    // Colors can change brightness order as they fade into the tint. Count passes at this strength.
    return detailsBackdropDimAlpha(floor = 0f, ceiling = CLOCK_SHADE_CEILING) { strength ->
        var misses = 0
        for (row in rows.indices) for (column in columns.indices) {
            val alpha = clockMaskAlpha(OPAQUE_ALPHA * strength * endWeights[column], topWeights[row])
            if (!clockDatePasses(samples[row * columns.size + column], alpha) && ++misses > allowedMisses) {
                return@detailsBackdropDimAlpha false
            }
        }
        true
    }
}

private fun clockDatePasses(pixel: Int, maskAlpha: Int): Boolean {
    // Round up remaining alpha to conservatively account for 8-bit compositing.
    val coverage = ceil((pixel ushr ALPHA_SHIFT) * maskAlpha / OPAQUE_ALPHA.toDouble()) / OPAQUE_ALPHA
    val r = clockChannel(pixel, 16, coverage)
    val g = clockChannel(pixel, 8, coverage)
    val b = clockChannel(pixel, 0, coverage)
    val date = detailsRelativeLuminance(
        .8 * 192 / 255 + .2 * r, .8 * 199 / 255 + .2 * g, .8 * 205 / 255 + .2 * b,
    )
    val background = detailsRelativeLuminance(r, g, b)
    return (date + CONTRAST_LUMINANCE_OFFSET) / (background + CONTRAST_LUMINANCE_OFFSET) >= CLOCK_CONTRAST_TARGET
}

private fun clockChannel(pixel: Int, shift: Int, coverage: Double) =
    ((pixel shr shift and OPAQUE_ALPHA) * coverage + DETAILS_LIGHTEST_TINT_CHANNEL * (1 - coverage)) / OPAQUE_ALPHA

/**
 * The share of the picture the scrim leaves, as alpha: the kit's radial gradient centred at the
 * frame's top-right, with its SVG gradientTransform on the kit's 960×540 viewBox.
 */
private fun kitCinematicScrimMask(width: Float, height: Float): Shader {
    val near = 0xff - (.1f * 0xff).roundToInt()
    val shader = RadialGradient(0f, 0f, 10f, intArrayOf(near shl 24, 0), floatArrayOf(0f, .7074f),
        Shader.TileMode.CLAMP)
    val sx = width / 960f
    val sy = height / 540f
    shader.setLocalMatrix(Matrix().apply {
        setValues(floatArrayOf(-91.24f * sx, -91.24f * sx, 912.4f * sx, 54f * sy, -50.1f * sy, 0f, 0f, 0f, 1f))
    })
    return shader
}
