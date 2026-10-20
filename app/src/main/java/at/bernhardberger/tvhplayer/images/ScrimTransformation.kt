package at.bernhardberger.tvhplayer.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.transform.Transformation

/**
 * Crops a picture to its view's size, as ContentScale.Crop would, and draws a vertical scrim into
 * it once per decode. A TV's GPU pays for a scrim blended over every card on every frame; a baked
 * one costs nothing after loading. [positions] and [colors] are a vertical linear gradient's.
 */
internal class ScrimTransformation(private val positions: FloatArray, private val colors: IntArray) : Transformation() {
    override val cacheKey: String = "scrim(${positions.joinToString()};${colors.joinToString()})"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = size.width.pxOrElse { input.width }
        val height = size.height.pxOrElse { input.height }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        // Centred like the crop, so the scrim lands where the view's text does.
        val scale = maxOf(width / input.width.toFloat(), height / input.height.toFloat())
        canvas.save()
        canvas.translate((width - input.width * scale) / 2f, (height - input.height * scale) / 2f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(input, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), colors, positions, Shader.TileMode.CLAMP)
        })
        return output
    }
}
