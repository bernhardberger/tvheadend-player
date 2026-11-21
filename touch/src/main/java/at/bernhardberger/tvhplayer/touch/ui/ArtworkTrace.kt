package at.bernhardberger.tvhplayer.touch.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.images.LogoInkTransformation
import coil3.EventListener
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.transformations
import coil3.size.Dimension
import coil3.size.Size
import java.util.concurrent.atomic.AtomicLong

internal fun ImageLoader.withArtworkTrace(context: Context): ImageLoader =
    if (context.isDebuggable) {
        newBuilder().eventListenerFactory { ArtworkRequestTrace(requestIds.incrementAndGet()) }.build()
    } else this

@Composable
internal fun ArtworkScreenShown(screen: String) {
    if (LocalContext.current.isDebuggable) {
        val shownMs = remember(screen) { SystemClock.elapsedRealtime() }
        LaunchedEffect(screen) { Log.d(ARTWORK_LOG_TAG, "screen $screen shown atMs=$shownMs") }
    }
}

internal fun <T> traceArtworkMapping(context: Context, screen: String, reason: String? = null, map: () -> T): T {
    if (!context.isDebuggable) return map()
    val startMs = SystemClock.elapsedRealtime()
    return map().also {
        val trigger = reason?.let { " reason=$it" }.orEmpty()
        Log.d(ARTWORK_LOG_TAG, "mapped $screen ms=${SystemClock.elapsedRealtime() - startMs} startMs=$startMs$trigger")
    }
}

/** Positional identity differences, including appended/removed entries, for debug traces only. */
internal fun Context.epgChangeCounts(previous: SessionObservation?, current: SessionObservation): String {
    if (!isDebuggable || previous == null) return ""
    val before = previous.epgSnapshotForDisplay?.events.orEmpty()
    val after = current.epgSnapshotForDisplay?.events.orEmpty()
    return if (before === after) "" else {
        val changed = (0 until maxOf(before.size, after.size)).count { before.getOrNull(it) !== after.getOrNull(it) }
        " changed=$changed of ${after.size} previous=${before.size}"
    }
}

internal data class ArtworkRequestTimes(
    val startMs: Long,
    val fetchStartMs: Long? = null,
    val fetchEndMs: Long? = null,
    val decodeEndMs: Long? = null,
    val transformEndMs: Long? = null,
    val endMs: Long,
)

/** Formats allowlisted metadata only, never the model, key or exception message. A null result means cancelled. */
internal fun artworkTraceLine(
    id: Long,
    request: ImageRequest,
    size: Size?,
    source: DataSource?,
    times: ArtworkRequestTimes,
    result: Result<Unit>?,
): String {
    val kind = if (request.transformations.any { it is LogoInkTransformation }) "logo" else "picture"
    val sizePx = size?.let { "${it.width.tracePixels}x${it.height.tracePixels}" } ?: "-"
    val error = result?.exceptionOrNull()
    val outcome = when {
        result == null -> "cancelledMs=${times.endMs}"
        error != null -> "errorMs=${times.endMs} error=${error.javaClass.name}"
        else -> "successMs=${times.endMs}"
    }
    return "request=$id kind=$kind sizePx=$sizePx source=${artworkSourceName(source)} startMs=${times.startMs} " +
        "fetchStartMs=${times.fetchStartMs ?: "-"} fetchEndMs=${times.fetchEndMs ?: "-"} " +
        "decodeEndMs=${times.decodeEndMs ?: "-"} transformEndMs=${times.transformEndMs ?: "-"} $outcome"
}

private fun artworkSourceName(source: DataSource?): String = when (source) {
    DataSource.MEMORY_CACHE, DataSource.MEMORY -> "memory"
    DataSource.DISK -> "disk"
    DataSource.NETWORK -> "network"
    null -> "-"
}

private class ArtworkRequestTrace(private val id: Long) : EventListener() {
    // A request can be cancelled before Coil calls onStart, e.g. while waiting for its lifecycle.
    private var startMs = SystemClock.elapsedRealtime()
    private var size: Size? = null
    private var source: DataSource? = null
    private var fetchStartMs: Long? = null
    private var fetchEndMs: Long? = null
    private var decodeEndMs: Long? = null
    private var transformEndMs: Long? = null

    override fun onStart(request: ImageRequest) {
        startMs = SystemClock.elapsedRealtime()
    }

    override fun resolveSizeEnd(request: ImageRequest, size: Size) {
        this.size = size
    }

    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) {
        if (fetchStartMs == null) fetchStartMs = elapsedMs()
    }

    override fun fetchEnd(request: ImageRequest, fetcher: Fetcher, options: Options, result: FetchResult?) {
        fetchEndMs = elapsedMs()
        source = when (result) {
            is SourceFetchResult -> result.dataSource
            is ImageFetchResult -> result.dataSource
            else -> null
        }
    }

    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) {
        decodeEndMs = elapsedMs()
    }

    override fun transformEnd(request: ImageRequest, output: Bitmap) {
        transformEndMs = elapsedMs()
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        source = result.dataSource
        finish(request, Result.success(Unit))
    }

    override fun onError(request: ImageRequest, result: ErrorResult) = finish(request, Result.failure(result.throwable))

    override fun onCancel(request: ImageRequest) = finish(request, null)

    private fun elapsedMs(): Long = SystemClock.elapsedRealtime() - startMs

    private fun finish(request: ImageRequest, result: Result<Unit>?) {
        val times = ArtworkRequestTimes(startMs, fetchStartMs, fetchEndMs, decodeEndMs, transformEndMs, elapsedMs())
        Log.d(ARTWORK_LOG_TAG, artworkTraceLine(id, request, size, source, times, result))
    }
}

private val Dimension.tracePixels: String get() = (this as? Dimension.Pixels)?.px?.toString() ?: "-"
private val Context.isDebuggable: Boolean get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
private val requestIds = AtomicLong()
private const val ARTWORK_LOG_TAG = "TouchArtwork"
