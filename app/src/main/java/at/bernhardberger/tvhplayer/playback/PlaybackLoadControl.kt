@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Media buffered before playback starts on a freshly installed target.
 *
 * A live TVHeadend subscription is pushed at one times real time and cannot be pulled ahead, so
 * every millisecond demanded here is a millisecond of visible tuning delay. Two seconds absorbs
 * the delivery jitter of a subscription that has just started without a stall shortly after the
 * first frame, which one second did not on hardware.
 */
private const val BUFFER_FOR_PLAYBACK_MS = 2_000

/**
 * Media buffered before playback resumes after a stall.
 *
 * Media3's five second default is spent waiting for real time to pass on a live subscription. That
 * turns a brief transport glitch into a multi-second freeze and pushes the target towards the
 * SDK's stuck-buffering recovery.
 */
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_500

/** Prefetch target. Only recording playback can act on it; a live subscription cannot load ahead. */
private const val MIN_BUFFER_MS = 15_000

/**
 * Upper bound on requested media.
 *
 * This is a loading policy for the pull-based recording path. It is not backpressure and not a
 * byte limit, so it does not bound what a live subscription pushes into the SDK period.
 */
private const val MAX_BUFFER_MS = 30_000

/** Buffering policy shared by live and recording playback on the application-owned player. */
@UnstableApi
fun createPlaybackLoadControl(): LoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        MIN_BUFFER_MS,
        MAX_BUFFER_MS,
        BUFFER_FOR_PLAYBACK_MS,
        BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
    )
    .build()
