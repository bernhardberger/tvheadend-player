package at.bernhardberger.tvhplayer.playback

/** How often the runtime samples a target the viewer wants playing. */
const val STALL_POLL_MILLIS = 500L

/**
 * Media ahead of the position that rules out network buffering. Above the largest start-up
 * buffer (3 s) and the SDK load control's rebuffer threshold (1.5 s): with this much queued,
 * Media3 still buffering means a renderer never becomes ready.
 */
const val STALL_BUFFERED_AHEAD_MILLIS = 5_000L

/** How long buffering with [STALL_BUFFERED_AHEAD_MILLIS] queued must last to be a stall. */
const val STALL_BUFFERING_MILLIS = 4_000L

/**
 * How long a playing target may render no frame. Longer than the zap cover's own wait for a
 * moving picture (one frame after READY, or the 1.5 s tunnelling fallback), so it never races it.
 */
const val PICTURE_STALL_MILLIS = 3_000L

/** The position must advance at least this far meanwhile: the clock runs, the picture does not. */
const val PICTURE_STALL_MIN_PROGRESS_MILLIS = 2_000L

/**
 * Added to [PICTURE_STALL_MILLIS] while the zap cover still waits for the target's picture to
 * move: a slow first keyframe is no stall, a cover that would wait forever is.
 */
const val PICTURE_COVER_GRACE_MILLIS = 4_000L

/** One automatic recovery per target and viewing intent within this window; a second stall fails. */
const val STALL_RECOVERY_WINDOW_MILLIS = 60_000L

enum class PlaybackStall { BUFFERING, PICTURE }

/**
 * One sample of the target of [epoch]. [videoCounters] is the identity of the video decoder
 * counters [renderedFrames] was read from, or null when the picture is not watched (no video
 * track selected, tunnelling, no counters). [pictureShown]: the zap cover lifted for [epoch].
 */
internal data class StallSample(
    val epoch: Long,
    val buffering: Boolean,
    val bufferedAheadMillis: Long,
    val playing: Boolean,
    val positionMillis: Long,
    val videoCounters: Any?,
    val renderedFrames: Int,
    val pictureShown: Boolean,
)

/**
 * Detects a target that has data but makes no progress: Media3 buffering with plenty queued
 * ([PlaybackStall.BUFFERING]), or the clock running while the video decoder renders nothing
 * ([PlaybackStall.PICTURE]). Fed only while the viewer wants the target playing; [reset] on every
 * seek, pause, discontinuity and track change, and a new [StallSample.epoch] starts over. A
 * reported stall restarts its own window, so each needs a full one.
 */
internal class PlaybackStallWatch {
    /**
     * Changes with every [reset] and whenever a running window starts over because the target
     * progressed: a stall reported under an older value is superseded.
     */
    @Volatile
    var evidence = 0L
        private set
    private var epoch: Long? = null
    private var bufferingSince: Long? = null
    private var pictureSince: Long? = null
    private var pictureCounters: Any? = null
    private var pictureFrames = 0
    private var picturePosition = 0L

    fun reset() {
        evidence++
        epoch = null
        bufferingSince = null
        pictureSince = null
        pictureCounters = null
    }

    fun onSample(sample: StallSample, nowMillis: Long): PlaybackStall? {
        if (sample.epoch != epoch) {
            reset()
            epoch = sample.epoch
        }
        val stall = bufferingStall(sample, nowMillis) ?: pictureStall(sample, nowMillis)
        // Still stalled is no new evidence; progress after the report is.
        when (stall) {
            PlaybackStall.BUFFERING -> bufferingSince = nowMillis
            PlaybackStall.PICTURE -> {
                pictureSince = nowMillis
                picturePosition = sample.positionMillis
            }
            null -> Unit
        }
        return stall
    }

    // Low buffered-ahead is real network buffering and never counts.
    private fun bufferingStall(sample: StallSample, nowMillis: Long): PlaybackStall? {
        if (!sample.buffering || sample.bufferedAheadMillis < STALL_BUFFERED_AHEAD_MILLIS) {
            if (bufferingSince != null) evidence++
            bufferingSince = null
            return null
        }
        val since = bufferingSince ?: nowMillis.also { bufferingSince = it }
        return PlaybackStall.BUFFERING.takeIf { nowMillis - since >= STALL_BUFFERING_MILLIS }
    }

    // Any rendered frame, other counters, a stopped clock or a backward position starts over.
    private fun pictureStall(sample: StallSample, nowMillis: Long): PlaybackStall? {
        val counters = sample.videoCounters
        val since = pictureSince
        if (!sample.playing || counters == null || since == null || counters !== pictureCounters ||
            sample.renderedFrames != pictureFrames || sample.positionMillis < picturePosition
        ) {
            if (since != null) evidence++
            pictureSince = if (sample.playing && counters != null) nowMillis else null
            pictureCounters = counters
            pictureFrames = sample.renderedFrames
            picturePosition = sample.positionMillis
            return null
        }
        val window = if (sample.pictureShown) PICTURE_STALL_MILLIS else PICTURE_STALL_MILLIS + PICTURE_COVER_GRACE_MILLIS
        return PlaybackStall.PICTURE.takeIf {
            nowMillis - since >= window &&
                sample.positionMillis - picturePosition >= PICTURE_STALL_MIN_PROGRESS_MILLIS
        }
    }
}

/** The last automatic stall recovery: of [target], under [viewingIntent], at [atMillis]. */
internal data class StallRecovery(
    val target: AppPlaybackTarget,
    val viewingIntent: Long,
    val atMillis: Long,
)

/**
 * Whether a stall of [target] may recover again: not if this recovery was of the same target
 * and viewing intent within [STALL_RECOVERY_WINDOW_MILLIS]. A new viewer start or Retry is a new
 * intent with its own recovery.
 */
internal fun StallRecovery?.allowsRecovery(target: AppPlaybackTarget, viewingIntent: Long, nowMillis: Long): Boolean =
    this == null || this.target != target || this.viewingIntent != viewingIntent ||
        nowMillis - atMillis >= STALL_RECOVERY_WINDOW_MILLIS
