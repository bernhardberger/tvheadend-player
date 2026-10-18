@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Frame rate from the median spacing of recently released frames. Fed from the playback thread
 * and read from the UI, so access is synchronized. A backwards or long-gap timestamp (seek, pause,
 * stream switch) drops the window rather than skewing it. A frame within [sameTimeUs] of the
 * previous one is a same-time frame: it stays out of the median and only counts towards
 * [sameTimeShare], so paired timestamps neither clear the window nor halve the spacing.
 */
internal class VideoFrameRateEstimator(
    private val windowSize: Int = 32,
    private val minimumDeltas: Int = 8,
    private val maxGapUs: Long = 1_000_000L,
    private val sameTimeUs: Long = 1_000L,
) {
    private val deltas = LongArray(windowSize)
    private val sorted = LongArray(windowSize)
    private var estimated: Float? = null
    private var stale = false
    private var count = 0
    private var next = 0
    private var last: Long? = null
    private val sameTime = BooleanArray(windowSize)
    private var sameTimeFilled = 0
    private var sameTimeNext = 0
    private var sameTimeHits = 0

    @Synchronized
    fun onFrame(presentationTimeUs: Long) {
        val previous = last
        last = presentationTimeUs
        if (previous == null) return
        val delta = presentationTimeUs - previous
        if (delta < 0L || delta > maxGapUs) {
            clear()
            return
        }
        val repeated = delta <= sameTimeUs
        if (sameTimeFilled == windowSize && sameTime[sameTimeNext]) sameTimeHits--
        sameTime[sameTimeNext] = repeated
        if (repeated) sameTimeHits++
        sameTimeNext = (sameTimeNext + 1) % windowSize
        if (sameTimeFilled < windowSize) sameTimeFilled++
        if (repeated) return
        deltas[next] = delta
        next = (next + 1) % windowSize
        if (count < windowSize) count++
        stale = true
    }

    /** Recomputed only after the window changed, into a reused buffer. */
    @Synchronized
    fun estimate(): Float? {
        if (count < minimumDeltas) return null
        if (!stale) return estimated
        deltas.copyInto(sorted, endIndex = count)
        sorted.sort(toIndex = count)
        val median = if (count % 2 == 1) {
            sorted[count / 2].toDouble()
        } else {
            (sorted[count / 2 - 1] + sorted[count / 2]) / 2.0
        }
        stale = false
        return (1_000_000.0 / median).toFloat().also { estimated = it }
    }

    /** Share of the recent frame spacings that were same-time frames; 0 until [minimumDeltas]. */
    @Synchronized
    fun sameTimeShare(): Float =
        if (sameTimeFilled < minimumDeltas) 0f else sameTimeHits.toFloat() / sameTimeFilled

    @Synchronized
    fun reset() {
        clear()
        last = null
    }

    private fun clear() {
        count = 0
        next = 0
        sameTimeFilled = 0
        sameTimeNext = 0
        sameTimeHits = 0
    }
}

/** Broadcast and film rates a display can switch to. */
internal val STANDARD_FRAME_RATES = floatArrayOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f)

/**
 * The standard rate nearest to [measured], or null when none is within [tolerance] (relative).
 * An interlaced stream reports the frames it delivers (25 for 1080i at 25 frames a second).
 */
internal fun standardFrameRate(measured: Float, tolerance: Float = 0.02f): Float? =
    STANDARD_FRAME_RATES.minBy { abs(it - measured) / it }
        .takeIf { abs(it - measured) / it <= tolerance }

/**
 * The display rate for a video rate. A TV is never asked for a 25 or 30 Hz mode: 50 or 60 Hz
 * shows 25p/30p evenly (each frame twice) and is the right rate for deinterlaced 1080i, so
 * 25 -> 50, 29.97 -> 59.94 and 30 -> 60; film and full-motion rates stay as they are.
 */
internal fun displayCadence(rate: Float): Float = when (rate) {
    25f -> 50f
    29.97f -> 59.94f
    30f -> 60f
    else -> rate
}

/**
 * The display rate for the frame duration the server declared, or null when its rate is not a
 * standard one. Interlaced video declares the frame duration (1080i50: 40 ms), so it asks for 50.
 */
internal fun declaredDisplayRate(duration: Duration): Float? {
    val seconds = duration.toDouble(DurationUnit.SECONDS)
    if (seconds <= 0.0) return null
    return standardFrameRate((1.0 / seconds).toFloat())?.let(::displayCadence)
}

/** Which Surface.setFrameRate call carries the app's vote. */
enum class SurfaceFrameRateChange {
    /** Android 12 or later: CHANGE_FRAME_RATE_ALWAYS, also when the switch is not seamless. */
    ALWAYS,
    /** Android 12 or later: CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS. */
    ONLY_IF_SEAMLESS,
    /** Android 11: the two-argument call, which switches only seamlessly. */
    SEAMLESS_DEFAULT,
}

/** What the video surface is asked for: the app's [rate] in Hz and how, or no rate (null). */
data class SurfaceFrameRateRequest(
    val rate: Float? = null,
    val change: SurfaceFrameRateChange = SurfaceFrameRateChange.SEAMLESS_DEFAULT,
)

/**
 * The app's vote for [rate] under [switching]. Media3's own change strategy stays Off, so this is
 * the surface's only vote: none while Off or before Android 11, which clears an earlier one.
 */
internal fun surfaceFrameRateRequest(switching: FrameRateSwitching, sdkInt: Int, rate: Float?): SurfaceFrameRateRequest =
    when {
        rate == null || switching == FrameRateSwitching.OFF || sdkInt < ANDROID_R -> SurfaceFrameRateRequest()
        sdkInt < ANDROID_S -> SurfaceFrameRateRequest(rate, SurfaceFrameRateChange.SEAMLESS_DEFAULT)
        switching == FrameRateSwitching.ALWAYS -> SurfaceFrameRateRequest(rate, SurfaceFrameRateChange.ALWAYS)
        else -> SurfaceFrameRateRequest(rate, SurfaceFrameRateChange.ONLY_IF_SEAMLESS)
    }

/** Surface.setFrameRate needs Android 11; its change strategy argument needs Android 12. */
internal const val ANDROID_R = 30
internal const val ANDROID_S = 31

/**
 * What one video surface carries: [request] and the surface callbacks return the request to set
 * now (one without a rate clears the vote), or null when the surface already carries it or cannot
 * take one. A recreated surface starts without a vote.
 */
class SurfaceFrameRateVote {
    private var target = SurfaceFrameRateRequest()
    private var applied = SurfaceFrameRateRequest()
    private var surfaceValid = false

    fun request(request: SurfaceFrameRateRequest): SurfaceFrameRateRequest? {
        target = request
        return pending()
    }

    fun surfaceCreated(): SurfaceFrameRateRequest? {
        surfaceValid = true
        applied = SurfaceFrameRateRequest()
        return pending()
    }

    /** The surface is about to go: clears a vote the app still has on it. */
    fun surfaceDestroyed(): SurfaceFrameRateRequest? {
        val clear = SurfaceFrameRateRequest().takeIf { surfaceValid && applied.rate != null }
        surfaceValid = false
        applied = SurfaceFrameRateRequest()
        return clear
    }

    /** The surface leaves the app: withdraws its vote. */
    fun release(): SurfaceFrameRateRequest? = request(SurfaceFrameRateRequest())

    private fun pending(): SurfaceFrameRateRequest? {
        if (!surfaceValid || target == applied) return null
        applied = target
        return target
    }
}
