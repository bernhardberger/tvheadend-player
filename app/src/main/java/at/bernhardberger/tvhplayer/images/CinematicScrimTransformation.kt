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
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.transform.Transformation
import kotlin.math.roundToInt

/**
 * The kit's "Cinematic scrim" (Figma 8689:24742 › Scrim) baked into a picture's alpha once per
 * decode, cropped to the request's shape. The scrim is the page's colour at 10% at the frame's
 * top-right, opaque from 70.74% of the way to the bottom-left corner, so the picture keeps 90% of
 * itself there and fades out to clear. Over that colour it looks the same as the scrim drawn over
 * the picture, without the GPU drawing the gradient again on every frame, and it follows the
 * colour while that changes.
 */
internal object CinematicScrimTransformation : Transformation() {
    override val cacheKey: String = "cinematicScrim"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap =
        scrimmed(input, size) { width, height -> listOf(kitCinematicScrimMask(width, height)) }
}

/**
 * The Guide headline's softer scrim. The picture keeps 90% of itself at its top-right, as under
 * the kit's scrim, and eases out to clear along the whole of its left edge and, by 64% of its
 * height, its bottom, so it never shows a cut. The picture reaches into the grid; gone by then,
 * it never sits behind the time ruler at any font size.
 */
internal object GuideScrimTransformation : Transformation() {
    override val cacheKey: String = "guideScrim"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = scrimmed(input, size) { width, height ->
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
private fun scrimmed(input: Bitmap, size: Size, masks: (Float, Float) -> List<Shader>): Bitmap {
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
    return output
}

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
