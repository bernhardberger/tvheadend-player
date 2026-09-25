@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.Format
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Front-end policy for [AppPlaybackRuntime]. The runtime owns the mechanics; each host chooses
 * these values explicitly.
 *
 * The runtime calls [keepTunedLimit] and [liveTimeshiftPeriod] inline inside its serialized playback
 * commands (for example while backgrounding, after playback is already paused). Both must be pure,
 * fast, synchronous and non-throwing, and must not block on or call back into the runtime.
 */
class PlaybackRuntimePolicy(
    /**
     * How long a paused live timeshift channel may stay tuned after the app leaves the screen.
     * ZERO or negative releases immediately; [Duration.INFINITE] keeps it until foreground, standby
     * or tuner loss (the deadline saturates).
     */
    val keepTunedLimit: (PlayerSettings) -> Duration,
    /**
     * Live timeshift period requested per subscription: ZERO (no timeshift) or a positive finite
     * duration. The SDK receives it unvalidated.
     */
    val liveTimeshiftPeriod: (PlayerSettings) -> Duration,
    val trace: PlaybackTrace = PlaybackTrace.None,
    /** Debug-only seek/decoder diagnostics listener and logging. */
    val seekDiagnostics: Boolean = false,
) {
    companion object {
        /** The TV app's policy: keep-tuned limit and timeshift come from the persisted settings. */
        fun fromPlayerSettings(
            trace: PlaybackTrace = PlaybackTrace.None,
            seekDiagnostics: Boolean = false,
        ): PlaybackRuntimePolicy = PlaybackRuntimePolicy(
            keepTunedLimit = { it.keepChannelMinutes.coerceAtLeast(0).minutes },
            liveTimeshiftPeriod = { requestedLiveTimeshiftPeriod(it.timeshiftEnabled) },
            trace = trace,
            seekDiagnostics = seekDiagnostics,
        )
    }
}

/**
 * Profiling hooks at the runtime's trace points. The runtime calls them only when [enabled], on its
 * command and player threads; implementations must be fast and non-throwing.
 */
interface PlaybackTrace {
    val enabled: Boolean get() = false

    fun tuneAdmitted() {}

    fun tuneBound(epoch: Long) {}

    fun ready(epoch: Long?) {}

    fun firstVideoFrame(epoch: Long, format: Format?, adapterName: String?) {}

    /** A target install began; the video is covered until a target's first frame. */
    fun videoCoverShown() {}

    /** The video of [epoch] is uncovered. */
    fun videoCoverLifted(epoch: Long) {}

    object None : PlaybackTrace
}
