package at.bernhardberger.tvhplayer.core

/** Initial D-pad/repeat seek step. */
const val SEEKBAR_STEP_INITIAL_MS = 30_000L

/** After sustained repeats, step grows to two minutes. */
const val SEEKBAR_STEP_MEDIUM_MS = 120_000L

/** Long holds use five-minute steps. */
const val SEEKBAR_STEP_LONG_MS = 300_000L

const val SEEKBAR_MEDIUM_AFTER_REPEATS = 4
const val SEEKBAR_LONG_AFTER_REPEATS = 12

enum class SeekbarDomain {
    /** Zero to duration for recordings. */
    RECORDING,

    /** Buffer start to live edge for timeshift. */
    TIMESHIFT,
}

data class SeekbarRange(
    val domain: SeekbarDomain,
    val startMs: Long,
    val endMs: Long,
    val positionMs: Long,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    val progress: Float
        get() = if (durationMs <= 0L) 0f
        else ((positionMs - startMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/** Programme-anchored timeshift axis. Fractions are 0..1 across the programme. */
data class ProgrammeAxis(
    val playbackFraction: Float,
    val liveEdgeFraction: Float,
    val rewindableStartFraction: Float,
)

/**
 * Returns null when there is no usable programme, allowing the caller to fall
 * back to the timeshift buffer axis.
 */
fun programmeAnchoredAxis(
    state: TimeshiftState,
    nowEpochSec: Long,
    programmeStartSec: Long?,
    programmeStopSec: Long?,
): ProgrammeAxis? {
    if (!state.available || programmeStartSec == null || programmeStopSec == null) return null
    val span = programmeStopSec - programmeStartSec
    if (span <= 0L) return null

    fun fraction(epochSec: Long): Float =
        ((epochSec - programmeStartSec).toDouble() / span.toDouble()).toFloat().coerceIn(0f, 1f)

    return ProgrammeAxis(
        playbackFraction = fraction(nowEpochSec + state.positionMs / 1_000L),
        liveEdgeFraction = fraction(nowEpochSec),
        rewindableStartFraction = fraction(nowEpochSec + state.bufferStartMs / 1_000L),
    )
}

fun recordingSeekbarRange(positionMs: Long, durationMs: Long?): SeekbarRange {
    val end = durationMs?.coerceAtLeast(0L) ?: positionMs.coerceAtLeast(0L)
    return SeekbarRange(
        domain = SeekbarDomain.RECORDING,
        startMs = 0L,
        endMs = end,
        positionMs = positionMs.coerceIn(0L, end),
    )
}

fun timeshiftSeekbarRange(state: TimeshiftState): SeekbarRange =
    SeekbarRange(
        domain = SeekbarDomain.TIMESHIFT,
        startMs = state.bufferStartMs,
        endMs = state.liveEdgeMs,
        positionMs = state.positionMs.coerceIn(state.bufferStartMs, state.liveEdgeMs),
    )

/**
 * Deterministic repeat acceleration for held Left/Right on a seekbar or hidden
 * seek gesture.
 */
fun seekStepMs(repeatCount: Int): Long = when {
    repeatCount >= SEEKBAR_LONG_AFTER_REPEATS -> SEEKBAR_STEP_LONG_MS
    repeatCount >= SEEKBAR_MEDIUM_AFTER_REPEATS -> SEEKBAR_STEP_MEDIUM_MS
    else -> SEEKBAR_STEP_INITIAL_MS
}

fun seekbarScrub(
    range: SeekbarRange,
    direction: Int,
    repeatCount: Int,
): Long {
    val delta = seekStepMs(repeatCount) * if (direction >= 0) 1 else -1
    return (range.positionMs + delta).coerceIn(range.startMs, range.endMs)
}

/** Whether programme progress should be shown as non-interactive information. */
fun shouldShowProgrammeProgress(hasCurrentEpgEvent: Boolean): Boolean = hasCurrentEpgEvent
