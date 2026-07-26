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

fun timeshiftEpgBoundaryFractions(
    state: TimeshiftState,
    nowEpochSec: Long,
    boundaryEpochSec: List<Long>,
): List<Float> {
    val duration = state.liveEdgeMs - state.bufferStartMs
    if (!state.available || duration <= 0L) return emptyList()
    return boundaryEpochSec
        .asSequence()
        .map { (it - nowEpochSec) * 1_000L }
        .filter { it in state.bufferStartMs..state.liveEdgeMs }
        .map { ((it - state.bufferStartMs).toFloat() / duration).coerceIn(0f, 1f) }
        .distinct()
        .sorted()
        .toList()
}

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
