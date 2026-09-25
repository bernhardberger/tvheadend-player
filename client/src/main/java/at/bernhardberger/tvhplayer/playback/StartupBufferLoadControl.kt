@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import java.util.concurrent.atomic.AtomicReference

/**
 * Wraps the SDK load control and only replaces the buffer a live start waits
 * for. Recording starts and every rebuffer keep the delegate's decision, and
 * loading, allocation and back buffer stay entirely with the delegate.
 *
 * Every non-deprecated [LoadControl] method is forwarded explicitly: Kotlin
 * class delegation does not forward Java default methods, and their defaults
 * throw.
 *
 * A start is live when the media item of the period being started is the SDK's
 * live item ([LIVE_MEDIA_ID]); everything else delegates, including recordings
 * (which may be dynamic while they grow), unknown items and an empty timeline.
 * The kind is read from the parameters of each query, so no announcement by the
 * runtime can reach a period it does not belong to, and a changed SDK id only
 * makes every start delegate.
 *
 * A timeshift seek or return to live is a server skip. Media3 sees the restart
 * after it as a rebuffer, but it is a live start: after [liveSeekStarting] the
 * one rebuffer that began during the request, or at most [SKIP_REBUFFER_GRACE_MS]
 * after its result ([liveSeekFinished]), restarts at the live threshold; so does
 * a rebuffer already in progress when the skip was requested. The
 * mark is used once and dropped by [clearLiveSeekStart]; any other rebuffer
 * delegates.
 *
 * [shouldStartPlayback] runs on the playback thread and reuses scratch timeline
 * objects confined to it. The threshold and the skip mark are published
 * through volatile fields, so a change applies to the next start without
 * rebuilding the player.
 */
class StartupBufferLoadControl(
    private val delegate: LoadControl,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) : LoadControl {
    @Volatile private var liveStartBufferUs = DEFAULT_LIVE_START_BUFFER_US

    /** The pending server skip. Only a new skip or a clear replaces it; its result is recorded in place. */
    private val liveSeek = AtomicReference<LiveSeek?>(null)

    /** Playback-thread scratch objects for resolving a period's media item. */
    private val scratchPeriod = Timeline.Period()
    private val scratchWindow = Timeline.Window()

    fun setLiveStartBufferMillis(millis: Int) {
        liveStartBufferUs = Util.msToUs(millis.coerceAtLeast(0).toLong())
    }

    /**
     * A server skip is about to restart the live period; the rebuffer it causes
     * is a live start. When the player is [alreadyBuffering] (a skip pressed
     * during an earlier skip's restart or a stall), Media3 keeps that rebuffer's
     * start time, so the mark takes over the rebuffer in progress.
     */
    fun liveSeekStarting(alreadyBuffering: Boolean = false) {
        liveSeek.set(LiveSeek(if (alreadyBuffering) Long.MIN_VALUE else elapsedRealtimeMs()))
    }

    /** The skip was accepted; its rebuffer must begin within the grace from now. */
    fun liveSeekFinished() {
        liveSeek.get()?.finishedAtMs = elapsedRealtimeMs()
    }

    /** No skip rebuffer follows: rejected, cancelled, absorbed, or a new target is installing. */
    fun clearLiveSeekStart() {
        liveSeek.set(null)
    }

    internal val isLiveSeekStartPending: Boolean get() = liveSeek.get() != null

    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<ExoTrackSelection?>,
    ) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

    override fun getAllocator(playerId: PlayerId): Allocator = delegate.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
        delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldContinueLoading(parameters)

    override fun shouldContinuePreloading(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        bufferedDurationUs: Long,
    ): Boolean = delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val seek = liveSeek.get()?.takeIf { parameters.rebuffering && it.owns(parameters.lastRebufferRealtimeMs) }
        if (!isLive(parameters) || (parameters.rebuffering && seek == null)) {
            return delegate.shouldStartPlayback(parameters)
        }
        // Same time rule as DefaultLoadControl, with the live start threshold swapped in.
        val bufferedUs = Util.getPlayoutDurationForMediaDuration(parameters.bufferedDurationUs, parameters.playbackSpeed)
        var minimumUs = liveStartBufferUs
        if (parameters.targetLiveOffsetUs != C.TIME_UNSET) {
            minimumUs = minOf(parameters.targetLiveOffsetUs / 2, minimumUs)
        }
        return (minimumUs <= 0 || bufferedUs >= minimumUs).also { start ->
            if (start && seek != null) liveSeek.compareAndSet(seek, null)
        }
    }

    private fun isLive(parameters: LoadControl.Parameters): Boolean {
        val timeline = parameters.timeline
        val periodIndex = timeline.getIndexOfPeriod(parameters.mediaPeriodId.periodUid)
        if (periodIndex == C.INDEX_UNSET) return false
        val windowIndex = timeline.getPeriod(periodIndex, scratchPeriod).windowIndex
        return timeline.getWindow(windowIndex, scratchWindow).mediaItem.mediaId == LIVE_MEDIA_ID
    }

    /**
     * A server skip requested at [startedAtMs] whose result arrived at
     * [finishedAtMs], if it did. The result is set on the same object, so it
     * cannot make the playback thread's one-shot consumption miss.
     */
    private class LiveSeek(val startedAtMs: Long) {
        @Volatile var finishedAtMs: Long = C.TIME_UNSET

        fun owns(rebufferAtMs: Long): Boolean =
            rebufferAtMs != C.TIME_UNSET &&
                rebufferAtMs >= startedAtMs &&
                (finishedAtMs == C.TIME_UNSET || rebufferAtMs <= finishedAtMs + SKIP_REBUFFER_GRACE_MS)
    }

    companion object {
        /**
         * Media id of the SDK's live media item, set by tvheadend-sdk's
         * `TvheadendLiveMediaSource` (sdk-media3). It is an internal SDK value,
         * not public API; the app reads it because no SDK release exposes a
         * live marker for this feature. If the SDK changes it, no start is
         * classified live and every start delegates as before this feature.
         */
        internal const val LIVE_MEDIA_ID = "tvheadend-live"

        /** How long after a skip's result its rebuffer may still begin. */
        const val SKIP_REBUFFER_GRACE_MS = 3_000L

        private const val DEFAULT_LIVE_START_BUFFER_US = 1_000_000L
    }
}
