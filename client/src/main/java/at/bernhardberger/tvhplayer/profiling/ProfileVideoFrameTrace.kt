package at.bernhardberger.tvhplayer.profiling

import at.bernhardberger.tvhplayer.client.BuildConfig
import kotlin.time.Duration

/**
 * One marker per frame about to be rendered, with the presentation and release time spacing to
 * the previous such frame. Called only from the playback thread; the first frame has no marker.
 */
internal class ProfileVideoFrameTrace {
    private var lastPresentationUs = 0L
    private var lastReleaseNs = 0L
    private var started = false

    fun onFrame(presentationTimeUs: Long, releaseTimeNs: Long) {
        if (!BuildConfig.PROFILE_TRACE) return
        if (started) {
            profileTrace(
                videoFrameMarker(presentationTimeUs - lastPresentationUs, (releaseTimeNs - lastReleaseNs) / 1_000L),
            ) {}
        }
        lastPresentationUs = presentationTimeUs
        lastReleaseNs = releaseTimeNs
        started = true
    }
}

/** Parsed by trace tooling; both deltas in microseconds and may be zero or negative. */
internal fun videoFrameMarker(presentationDeltaUs: Long, releaseDeltaUs: Long): String =
    "P44:frame:pts:$presentationDeltaUs:release:$releaseDeltaUs"

/** Parsed by trace tooling: the declared frame duration in microseconds, or none. */
internal fun declaredFrameDurationMarker(duration: Duration?): String =
    "P44:frame:declared:${duration?.inWholeMicroseconds ?: "none"}"
