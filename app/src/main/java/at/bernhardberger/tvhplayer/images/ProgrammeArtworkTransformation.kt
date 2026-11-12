package at.bernhardberger.tvhplayer.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.transform.Transformation
import kotlin.math.roundToInt

internal enum class ProgrammeArtworkMode { CROP, BOX, TITLE_CARD, BACKDROP }
private const val WIDE_ASPECT = 16.0 / 9
private const val RGB_MASK = 0xffffff

/** Shape is learned after decode; the destination and all its overlays keep their geometry. */
internal class ProgrammeArtworkTransformation(
    private val mode: ProgrammeArtworkMode = ProgrammeArtworkMode.CROP,
    private val ratio: Double = WIDE_ASPECT,
    private val avoidPlate: Boolean = false,
    private val plateAtEnd: Boolean = false,
    private val fallback: Int = 0,
    private val startPlateShare: Double = 0.0,
    private val keepWideTitleCrop: Boolean = false,
) : Transformation() {
    override val cacheKey = "programmeArtwork-v5($mode;$ratio;$avoidPlate;$plateAtEnd;$fallback;" +
        "$startPlateShare;$keepWideTitleCrop)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!needsArtworkTreatment(input.width, input.height, ratio)) return input
        val (sampleWidth, sampleHeight) = artworkAnalysisSize(input.width, input.height)
        val pixels = analysisPixels(input, sampleWidth, sampleHeight)
        val width = size.width.pxOrElse { input.width }.coerceAtLeast(1)
        val height = (width / ratio).roundToInt().coerceAtLeast(1)
        val backdrop = if (mode == ProgrammeArtworkMode.BACKDROP) {
            backdropWindow(input, pixels, sampleWidth, sampleHeight)
        } else null
        if (mode == ProgrammeArtworkMode.BACKDROP && backdrop == null) {
            // Coil's existing error path removes the backdrop and restores the no-art page.
            throw NoProgrammeBackdropException()
        }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val wideTitleCrop = mode == ProgrammeArtworkMode.BOX && keepWideTitleCrop &&
            !usesCoverBox(input.width, input.height)
        if (mode == ProgrammeArtworkMode.TITLE_CARD || wideTitleCrop) {
            drawTitleCard(input, output, pixels, sampleWidth, sampleHeight)
        } else if (backdrop != null) {
            canvas.drawBitmap(input, sourceRect(backdrop, input, sampleWidth, sampleHeight),
                Rect(0, 0, width, height), paint)
        } else if (mode == ProgrammeArtworkMode.BOX && usesCoverBox(input.width, input.height)) {
            output.setPixels(artworkFill(pixels, sampleWidth, sampleHeight, width, height, fallback),
                0, width, 0, 0, width, height)
            val cover = coverWindow(input.width, input.height, width, height,
                kotlin.math.ceil(width * startPlateShare).toInt(), plateAtEnd)
            canvas.drawBitmap(input, null, Rect(cover.x, cover.y, cover.x + cover.width, cover.y + cover.height), paint)
        } else {
            val window = attentionWindow(pixels, sampleWidth, sampleHeight, ratio, avoidPlate, plateAtEnd)
            val left = (window.x * input.width.toDouble() / sampleWidth).roundToInt()
            val top = (window.y * input.height.toDouble() / sampleHeight).roundToInt()
            val cropWidth = minOf(input.width, (input.height * ratio).roundToInt()).coerceAtLeast(1)
            val cropHeight = minOf(input.height, (input.width / ratio).roundToInt()).coerceAtLeast(1)
            val x = left.coerceIn(0, input.width - cropWidth)
            val y = top.coerceIn(0, input.height - cropHeight)
            canvas.drawBitmap(input, Rect(x, y, x + cropWidth, y + cropHeight), Rect(0, 0, width, height), paint)
        }
        return output
    }

    private fun analysisPixels(input: Bitmap, sampleWidth: Int, sampleHeight: Int): IntArray =
        artworkAnalysis(input.width, input.height, sampleWidth, sampleHeight) { y, row ->
            input.getPixels(row, 0, input.width, 0, y, input.width, 1)
        }

    private fun backdropWindow(input: Bitmap, pixels: IntArray, sampleWidth: Int, sampleHeight: Int): ArtworkCrop? {
        val window = titleBackdropWindow(pixels, sampleWidth, sampleHeight, ratio)
        val rect = sourceRect(window, input, sampleWidth, sampleHeight)
        val (croppedWidth, croppedHeight) = artworkAnalysisSize(rect.width(), rect.height())
        val selected = artworkAnalysis(rect.width(), rect.height(), croppedWidth, croppedHeight) { y, row ->
            input.getPixels(row, 0, rect.width(), rect.left, rect.top + y, rect.width(), 1)
        }
        return window.takeIf { backdropHasScene(selected, croppedWidth, croppedHeight) }
    }

    private fun drawTitleCard(input: Bitmap, output: Bitmap, pixels: IntArray, sampleWidth: Int, sampleHeight: Int) {
        val window = titleCardWindow(pixels, sampleWidth, sampleHeight, ratio)
        val fill = bottomArtworkFill(pixels, sampleWidth, sampleHeight, fallback)
        output.eraseColor(fill)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val top = window.y * input.height / sampleHeight
        val scale = output.width.toFloat() / input.width
        val cut = ((input.height - top) * scale).toFloat()
        canvas.drawBitmap(input, null, RectF(0f, (-top * scale).toFloat(), output.width.toFloat(), cut), paint)
        if (cut < output.height) {
            val fade = output.height * CARD_EDGE_FADE.toFloat()
            paint.shader = LinearGradient(0f, cut - fade, 0f, cut, fill and RGB_MASK, fill, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, maxOf(0f, cut - fade), output.width.toFloat(), cut, paint)
        }
    }

    private fun sourceRect(window: ArtworkCrop, input: Bitmap, sampleWidth: Int, sampleHeight: Int): Rect {
        val left = (window.x * input.width / sampleWidth).roundToInt().coerceIn(0, input.width - 1)
        val top = (window.y * input.height / sampleHeight).roundToInt().coerceIn(0, input.height - 1)
        val right = ((window.x + window.width) * input.width / sampleWidth).roundToInt()
            .coerceIn(left + 1, input.width)
        val bottom = ((window.y + window.height) * input.height / sampleHeight).roundToInt()
            .coerceIn(top + 1, input.height)
        return Rect(left, top, right, bottom)
    }
}

internal class NoProgrammeBackdropException : IllegalStateException("Programme cover has no backdrop scene")
