@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import android.os.Build
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import at.bernhardberger.tvhplayer.client.BuildConfig
import at.bernhardberger.tvhplayer.profiling.ProfileVideoFrameTrace
import at.bernhardberger.tvhplayer.profiling.declaredFrameDurationMarker
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [AppPlaybackRuntime]'s display rate request and frame rate diagnostics. [requested] follows the
 * frame duration the server declared for the playing video ([declared]) at its display cadence,
 * under the frame rate setting. A declaration that goes away (null) keeps the request, so a brief
 * live restart does not switch the display back and forth; a new one replaces it at once. The rate
 * is kept across a target change until the new target declares or playback stops: clearing it on
 * install would switch the display twice on a zap between channels of the same rate. The
 * video surface applies it while the player is shown. The rate measured from released frames
 * (the player's only frame metadata listener) is for the stats only; tunnelled playback reports
 * no frames.
 */
class PlaybackFrameRate internal constructor(private val sdkInt: Int = Build.VERSION.SDK_INT) {
    private val lock = Any()
    private val estimator = VideoFrameRateEstimator()
    private var switching = FrameRateSwitching.ALWAYS
    private var rate: Float? = null
    private var closed = false

    private val _requested = MutableStateFlow(SurfaceFrameRateRequest())
    val requested: StateFlow<SurfaceFrameRateRequest> = _requested.asStateFlow()

    private val _declared = MutableStateFlow<Duration?>(null)
    /** The frame duration the server declares for the playing video, or null; for the stats. */
    val declared: StateFlow<Duration?> = _declared.asStateFlow()

    private val frameTrace = ProfileVideoFrameTrace()

    /** Called on the playback thread for each frame about to be rendered. */
    internal val listener = VideoFrameMetadataListener { presentationTimeUs, releaseTimeNs, _, _ ->
        if (BuildConfig.PROFILE_TRACE) frameTrace.onFrame(presentationTimeUs, releaseTimeNs)
        onFrame(presentationTimeUs)
    }

    /** The measured rate of released frames; null until enough frames arrived. Stats only. */
    fun measured(): Float? = estimator.estimate()

    /** Share of recent frames presented at (nearly) the same time as the one before; for stats. */
    fun sameTimeShare(): Float = estimator.sameTimeShare()

    internal fun onFrame(presentationTimeUs: Long) {
        synchronized(lock) {
            if (!closed) estimator.onFrame(presentationTimeUs)
        }
    }

    /** The server's current declaration; null (none or not bound) keeps the current request. */
    internal fun onDeclared(duration: Duration?) {
        synchronized(lock) {
            if (closed) return
            if (BuildConfig.PROFILE_TRACE && duration != _declared.value) {
                profileTrace(declaredFrameDurationMarker(duration)) {}
            }
            _declared.value = duration
            if (duration == null) return
            rate = declaredDisplayRate(duration)
            publishLocked()
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
            rate = null
            publishLocked()
        }
    }

    /** The runtime detached: forgets the rate for good and ignores anything still delivered. */
    internal fun close() {
        synchronized(lock) {
            closed = true
            _declared.value = null
        }
        reset()
    }

    private fun publishLocked() {
        _requested.value = surfaceFrameRateRequest(switching, sdkInt, rate)
    }
}
