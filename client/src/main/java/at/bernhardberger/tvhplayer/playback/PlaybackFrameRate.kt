@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import android.os.Build
import androidx.media3.common.C
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import at.bernhardberger.tvhplayer.client.BuildConfig
import at.bernhardberger.tvhplayer.profiling.ProfileVideoFrameTrace
import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [AppPlaybackRuntime]'s measured video frame rate and the display rate it asks for. HTSP formats
 * carry no frame rate, so the rate comes from the presentation times of released frames; it is
 * the player's only frame metadata listener. [requested] carries the stable standard rate while
 * the setting is Always (Android 12 or later), or null, together with whether Media3 votes on the
 * surface itself. The video surface applies it while the player is shown; the stats show the rate.
 */
class PlaybackFrameRate internal constructor(
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    requiredFrames: Int = 48,
) {
    private val lock = Any()
    private val estimator = VideoFrameRateEstimator()
    private val stability = StableFrameRate(requiredFrames)
    private var switching = FrameRateSwitching.ALWAYS
    private var closed = false

    private val _requested = MutableStateFlow(SurfaceFrameRateRequest())
    val requested: StateFlow<SurfaceFrameRateRequest> = _requested.asStateFlow()

    private val frameTrace = ProfileVideoFrameTrace()

    /** Called on the playback thread for each frame about to be rendered. */
    internal val listener = VideoFrameMetadataListener { presentationTimeUs, releaseTimeNs, _, _ ->
        if (BuildConfig.PROFILE_TRACE) frameTrace.onFrame(presentationTimeUs, releaseTimeNs)
        onFrame(presentationTimeUs)
    }

    /** The current measurement, also before it is stable; null until enough frames arrived. */
    fun measured(): Float? = estimator.estimate()

    /** Share of recent frames presented at (nearly) the same time as the one before; for stats. */
    fun sameTimeShare(): Float = estimator.sameTimeShare()

    internal fun onFrame(presentationTimeUs: Long) {
        synchronized(lock) {
            if (closed) return
            estimator.onFrame(presentationTimeUs)
            if (stability.offer(estimator.estimate()?.let(::standardFrameRate)) != null) publishLocked()
        }
    }

    internal fun setSwitching(value: FrameRateSwitching) {
        synchronized(lock) {
            switching = value
            publishLocked()
        }
    }

    /** No target, or one without video: forgets the rate, so nothing is requested. */
    internal fun reset() {
        synchronized(lock) {
            estimator.reset()
            stability.reset()
            publishLocked()
        }
    }

    /** The runtime detached: forgets the rate for good and ignores any frame still delivered. */
    internal fun close() {
        synchronized(lock) { closed = true }
        reset()
    }

    private fun publishLocked() {
        _requested.value = SurfaceFrameRateRequest(
            rate = frameRateRequest(switching, sdkInt, stability.stable),
            media3Votes = media3FrameRateStrategy(switching, sdkInt) != C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF,
        )
    }
}
