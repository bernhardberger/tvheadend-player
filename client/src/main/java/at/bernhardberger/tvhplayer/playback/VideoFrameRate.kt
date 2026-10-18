@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
import kotlin.math.abs

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
 * The standard rate that held for [requiredFrames] consecutive frames. A new rate replaces the
 * stable one only once it has held as long itself, so a zap keeps the current rate until the
 * new target's rate is settled, and a rate equal to the stable one changes nothing.
 */
internal class StableFrameRate(private val requiredFrames: Int = 48) {
    var stable: Float? = null
        private set
    private var candidate: Float? = null
    private var frames = 0

    /** Offers one frame's standard rate (null: not measurable yet); returns a newly stable rate. */
    fun offer(rate: Float?): Float? {
        if (rate == null) {
            candidate = null
            frames = 0
            return null
        }
        if (rate != candidate) {
            candidate = rate
            frames = 0
        }
        frames++
        if (frames < requiredFrames || rate == stable) return null
        stable = rate
        return rate
    }

    fun reset() {
        stable = null
        candidate = null
        frames = 0
    }
}

/** The rate the app asks the display for itself, or null for none. */
internal fun frameRateRequest(switching: FrameRateSwitching, sdkInt: Int, stableRate: Float?): Float? =
    stableRate.takeIf { switching == FrameRateSwitching.ALWAYS && sdkInt >= ANDROID_S }

/** Media3 handles seamless switches; Always below Android 12 falls back to them. */
internal fun media3FrameRateStrategy(switching: FrameRateSwitching, sdkInt: Int): Int = when {
    switching == FrameRateSwitching.OFF -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
    switching == FrameRateSwitching.ALWAYS && sdkInt >= ANDROID_S -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
    else -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
}

/** Surface.setFrameRate with a change strategy (CHANGE_FRAME_RATE_ALWAYS) needs Android 12. */
internal const val ANDROID_S = 31

/**
 * What the video surface is asked for, published as one value so a change of owner alone also
 * reaches it: the app's own [rate] in Hz or null, and whether Media3's change strategy votes on
 * the surface itself ([media3Votes], strategy not Off).
 */
data class SurfaceFrameRateRequest(val rate: Float? = null, val media3Votes: Boolean = false)

/**
 * What one video surface carries: [request] and the surface callbacks return the rate to set now
 * (0 clears it), or null when the surface already carries the right vote or cannot take one. A
 * recreated surface starts without a vote.
 */
class SurfaceFrameRateVote {
    private var target: Float? = null
    private var applied: Float? = null
    private var media3Votes = false
    /** The surface may carry Media3's vote: it owned the surface while the surface was valid. */
    private var media3Voted = false
    private var surfaceValid = false

    /**
     * While [media3Votes] a withdrawn request is not cleared: Media3 replaced this vote with its
     * own on taking over (same surface), so a 0 now would erase Media3's. Once ownership returns,
     * the app's rate replaces Media3's vote, or without one a 0 clears it, since Media3 does not
     * touch the surface while its strategy is Off.
     */
    fun request(rate: Float?, media3Votes: Boolean = false): Float? {
        target = rate
        this.media3Votes = media3Votes
        if (rate == null && media3Votes) {
            applied = null
            if (surfaceValid) media3Voted = true
            return null
        }
        return pending()
    }

    fun surfaceCreated(): Float? {
        surfaceValid = true
        applied = null
        media3Voted = media3Votes
        return if (media3Votes && target == null) null else pending()
    }

    /** The surface is about to go: clears a vote the app still has on it. */
    fun surfaceDestroyed(): Float? {
        val clear = 0f.takeIf { surfaceValid && applied != null }
        surfaceValid = false
        applied = null
        media3Voted = false
        return clear
    }

    /** The surface leaves the app: withdraws its request under the current owner. */
    fun release(): Float? = request(null, media3Votes)

    private fun pending(): Float? {
        if (!surfaceValid || (target == applied && !media3Voted)) return null
        applied = target
        media3Voted = false
        return target ?: 0f
    }
}
