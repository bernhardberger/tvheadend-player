package at.bernhardberger.tvhplayer.images

import android.graphics.Bitmap
import android.graphics.Canvas
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

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = size.width.pxOrElse { 16 }
        val height = size.height.pxOrElse { 9 }
        // Cropped as ContentScale.Crop would, at the decode's own resolution.
        val scale = maxOf(width / input.width.toFloat(), height / input.height.toFloat())
        val outputWidth = (width / scale).roundToInt().coerceIn(1, input.width)
        val outputHeight = (height / scale).roundToInt().coerceIn(1, input.height)
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(input, (outputWidth - input.width) / 2f, (outputHeight - input.height) / 2f,
            Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), Paint().apply {
            isDither = false
            shader = kitCinematicScrimMask(outputWidth.toFloat(), outputHeight.toFloat())
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        })
        return output
    }
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
