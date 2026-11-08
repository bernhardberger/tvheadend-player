package at.bernhardberger.tvhplayer.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.channelLogoModel
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.SessionArtworkLoader
import at.bernhardberger.tvhplayer.playback.SessionArtworkRequest
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt

/** Uses the app's authenticated Coil pipeline, never a controller-supplied URI. */
internal class AppSessionArtworkLoader(
    private val context: Context,
    private val tvheadend: TvheadendSession,
    private val imageLoader: ImageLoader,
) : SessionArtworkLoader {
    override suspend fun load(request: SessionArtworkRequest, observation: SessionObservation): ByteArray? {
        if (request.target is AppPlaybackTarget.Recording) {
            request.recordingImage?.let { image ->
                loadEncoded(AppArtworkSource(request.session, image), picon = false)?.let { return it }
            }
        }
        request.channelIcon?.let {
            loadEncoded(channelLogoModel(it, request.session, tvheadend, observation), picon = true)
                ?.let { bytes -> return bytes }
        }
        if (request.channelIcon != null || request.recordingImage != null) throw IOException("Session artwork unavailable")
        return null
    }

    private suspend fun loadEncoded(model: Any, picon: Boolean): ByteArray? = try {
        val result = imageLoader.execute(ImageRequest.Builder(context).data(model)
            .allowHardware(false).size(512, 512).build())
        (result as? SuccessResult)?.image?.toBitmap()?.let { encodeSessionArtwork(it, picon) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Try the fallback without retaining diagnostics or authenticated sources.
        null
    }
}

/** Picons retain alpha in square PNGs; photos use bounded JPEGs. Never recycle Coil-owned input. */
private fun encodeSessionArtwork(source: Bitmap, picon: Boolean): ByteArray? {
    var edge = minOf(512, max(source.width, source.height))
    while (edge >= 1) {
        val scale = edge.toFloat() / max(source.width, source.height)
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        val output = Bitmap.createBitmap(if (picon) edge else width, if (picon) edge else height, Bitmap.Config.ARGB_8888)
        try {
            val left = (output.width - width) / 2
            val top = (output.height - height) / 2
            Canvas(output).drawBitmap(source, null, Rect(left, top, left + width, top + height), Paint(Paint.FILTER_BITMAP_FLAG))
            for (quality in if (picon) listOf(100) else listOf(100, 90, 75, 60, 45)) {
                val bytes = ByteArrayOutputStream()
                if (output.compress(if (picon) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, bytes)
                    && bytes.size() <= 256 * 1024) return bytes.toByteArray()
            }
        } finally {
            output.recycle()
        }
        if (edge == 1) break
        edge = max(1, edge * 3 / 4)
    }
    return null
}
