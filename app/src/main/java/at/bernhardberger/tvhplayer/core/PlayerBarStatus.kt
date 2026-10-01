package at.bernhardberger.tvhplayer.core

/** The state cell at the start of the player's bar row: what playback is doing right now. */
enum class PlayerStateCell { PLAYING, PAUSED }

/** The chrome shows playback intent; tuning and buffering belong to the video overlay. */
fun playerStateCell(paused: Boolean): PlayerStateCell =
    if (paused) PlayerStateCell.PAUSED else PlayerStateCell.PLAYING

/** The state live TV announces when it changes; never the distance. */
enum class PlayerEndAnnouncement { LIVE, BEHIND_LIVE }

/** How far playback is from its live end: the part of the bar status that changes every second. */
data class PlayerBarDistance(
    /** Playback is within the live-edge tolerance of a live end: the chip over hidden chrome reads `Live`. */
    val atLive: Boolean = false,
    /** Distance to the end; null at a live end's edge or while unknown. */
    val ms: Long? = null,
)

/**
 * The bar's end (programme end clock or recording length) and how far playback is from the end it
 * heads for: live TV and a growing recording head for the live edge, a finished recording for its
 * end. The chrome draws a live end's distance behind live; the chip over hidden chrome shows any.
 */
data class PlayerBarEnd(
    /** The end is live: live TV's edge or a growing recording's head. */
    val liveEnd: Boolean = false,
    /** Playback is within the live-edge tolerance of a live end: the chip over hidden chrome reads `Live`. */
    val atLive: Boolean = false,
    /** Distance to the end; null at a live end's edge or while unknown. */
    val distanceMs: Long? = null,
    /** The programme's end clock or the recording's length; null while unknown or not shown. */
    val end: String? = null,
) {
    val hasDistance: Boolean get() = atLive || distanceMs != null

    /** The ticking part of this end. */
    val distance: PlayerBarDistance get() = PlayerBarDistance(atLive, distanceMs)

    /** This end without its distance: equal from one tick to the next while the end and length hold. */
    fun withoutDistance(): PlayerBarEnd = copy(atLive = false, distanceMs = null)

    /** This end with [distance] in place of its own. */
    fun withDistance(distance: PlayerBarDistance): PlayerBarEnd = copy(atLive = distance.atLive, distanceMs = distance.ms)

    /** The distance away from the end, `−3:23`; null at a live end's edge or while unknown. */
    val behindText: String? get() = distanceMs?.let { "−${formatPlaybackDuration(it)}" }

    /** The hidden chip's text, `Live` or `−3:23`; null while unknown. [liveLabel] is the localized `Live`. */
    fun distanceText(liveLabel: String): String? = if (atLive) liveLabel else behindText

    /** The state to announce politely; only a live end has one. */
    val announcement: PlayerEndAnnouncement?
        get() = when {
            !liveEnd -> null
            atLive -> PlayerEndAnnouncement.LIVE
            distanceMs != null -> PlayerEndAnnouncement.BEHIND_LIVE
            else -> null
        }
}

/**
 * Live TV's end and distance. [behindLiveMs] is how far behind live playback stands: within the
 * live-edge tolerance it is at the live edge, null (timing not known) leaves the distance unknown.
 */
fun liveBarEnd(behindLiveMs: Long?, end: String?): PlayerBarEnd? {
    val behind = behindLiveMs?.coerceAtLeast(0L)
    return PlayerBarEnd(
        liveEnd = true,
        atLive = behind != null && behind <= TIMESHIFT_LIVE_EDGE_TOLERANCE_MS,
        distanceMs = behind?.takeIf { it > TIMESHIFT_LIVE_EDGE_TOLERANCE_MS },
        end = end,
    )
}

/**
 * A recording's end and distance: its length and the remaining time, or for a growing recording its
 * current length and the distance behind its recording head (`Live` at it). Null while the length
 * is unknown.
 */
fun recordingBarEnd(positionMs: Long, durationMs: Long?, growing: Boolean, lengthMs: Long? = durationMs): PlayerBarEnd? {
    val duration = durationMs?.takeIf { it > 0L } ?: return null
    val remaining = (duration - positionMs).coerceAtLeast(0L)
    val atHead = growing && remaining <= TIMESHIFT_LIVE_EDGE_TOLERANCE_MS
    return PlayerBarEnd(
        liveEnd = growing,
        atLive = atHead,
        distanceMs = remaining.takeUnless { atHead },
        end = formatPlaybackDuration(lengthMs ?: duration),
    )
}

/** The chip over hidden chrome while paused: `❚❚ −3:23`. Null while playing. */
fun hiddenStatusText(state: PlayerStateCell, end: PlayerBarEnd?, liveLabel: String): String? =
    if (state == PlayerStateCell.PAUSED) end?.distanceText(liveLabel) else null

/** The step readout at a target: [text] is `−3:53`, a position, or `Live` with [atLive] for the play glyph. */
data class StepReadout(val text: String, val atLive: Boolean = false)

/** Live: the target's distance behind live; within the live-edge tolerance it is the live edge. */
fun liveStepReadout(behindLiveMs: Long, liveLabel: String): StepReadout =
    if (behindLiveMs <= TIMESHIFT_LIVE_EDGE_TOLERANCE_MS) StepReadout(liveLabel, atLive = true)
    else StepReadout("−${formatPlaybackDuration(behindLiveMs)}")

/**
 * A recording: the target position, or `Live` at a growing recording's head. [endMs] is the
 * recorded length the position label pads to.
 */
fun recordingStepReadout(targetMs: Long, endMs: Long, growing: Boolean, liveLabel: String): StepReadout =
    if (growing && endMs - targetMs <= TIMESHIFT_LIVE_EDGE_TOLERANCE_MS) StepReadout(liveLabel, atLive = true)
    else StepReadout(recordingElapsedLabel(targetMs, endMs))

/** A recording's elapsed position, as long as its length's so the label does not jump at an hour. */
fun recordingElapsedLabel(positionMs: Long, lengthMs: Long?): String {
    val elapsed = formatPlaybackDuration(positionMs)
    return if (lengthMs != null && lengthMs >= 3_600_000L && positionMs < 3_600_000L) "0:${elapsed.padStart(5, '0')}" else elapsed
}

/** The wall-clock second the timeshift buffer starts at, when the live edge is [nowSec]. */
fun bufferStartClockSec(nowSec: Long, liveEdgeMs: Long, bufferStartMs: Long): Long =
    nowSec - (liveEdgeMs - bufferStartMs).coerceAtLeast(0L) / 1_000L

/** Which way a step sequence moves playback. */
enum class StepDirection { BEHIND, AHEAD }

/**
 * The sign of the step sequence's net movement since it began (clamped at a boundary included),
 * never the target against the playback position, which moves as the seek lands. Null at zero.
 */
fun stepDirection(netDeltaMs: Long): StepDirection? = when {
    netDeltaMs < 0L -> StepDirection.BEHIND
    netDeltaMs > 0L -> StepDirection.AHEAD
    else -> null
}

/** When a finished recording ends at the current rate from [nowSec]; null while its length is unknown. */
fun recordingEndsAtSec(nowSec: Long, positionMs: Long, durationMs: Long?): Long? =
    durationMs?.let { nowSec + ((it - positionMs).coerceAtLeast(0L) + 999L) / 1_000L }
