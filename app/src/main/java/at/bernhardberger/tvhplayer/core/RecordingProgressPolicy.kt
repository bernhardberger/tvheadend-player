package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.DvrState

const val RECORDING_RESUME_FLOOR_SECONDS = 180L
const val RECORDING_PERIODIC_INTERVAL_MS = 30_000L
const val RECORDING_PERIODIC_MINIMUM_DELTA_SECONDS = 10L
const val RECORDING_SEEK_CHECKPOINT_DEBOUNCE_MS = 2_000L
const val RECORDING_COMPLETION_REMAINING_MS = 5 * 60_000L

sealed interface RecordingPlaybackIntent {
    data object DefaultPolicy : RecordingPlaybackIntent
    data object FromBeginning : RecordingPlaybackIntent
    data class Resume(val positionSeconds: Long) : RecordingPlaybackIntent
}

sealed interface RecordingStartDecision {
    data object FromBeginning : RecordingStartDecision
    data class ResumeAt(val positionMs: Long) : RecordingStartDecision
}

enum class RecordingCheckpointTrigger {
    Periodic,
    Seek,
    Pause,
    Final,
}

enum class RecordingCompletionTrigger {
    Final,
    NaturalEnd,
}

enum class RecordingRemoteUpdateDecision {
    AdoptRemote,
    KeepLocal,
}

fun mediaMillisecondsToRecordingSeconds(positionMs: Long): Long? =
    positionMs.takeIf { it >= 0L }?.div(1_000L)

fun recordingSecondsToMediaMilliseconds(positionSeconds: Long): Long? = when {
    positionSeconds < 0L -> null
    positionSeconds > Long.MAX_VALUE / 1_000L -> null
    else -> positionSeconds * 1_000L
}

fun recordingResumeCandidateSeconds(state: DvrState, serverPositionSeconds: Long?): Long? =
    serverPositionSeconds?.takeIf {
        state == DvrState.COMPLETED &&
            it >= RECORDING_RESUME_FLOOR_SECONDS &&
            recordingSecondsToMediaMilliseconds(it) != null
    }

fun recordingStartDecision(
    intent: RecordingPlaybackIntent,
    state: DvrState,
    serverPositionSeconds: Long?,
    durationMs: Long?,
    @Suppress("UNUSED_PARAMETER") playCount: Int? = null,
): RecordingStartDecision {
    if (intent == RecordingPlaybackIntent.FromBeginning) {
        return RecordingStartDecision.FromBeginning
    }
    val requestedSeconds = when (intent) {
        RecordingPlaybackIntent.DefaultPolicy -> serverPositionSeconds
        RecordingPlaybackIntent.FromBeginning -> null
        is RecordingPlaybackIntent.Resume -> intent.positionSeconds
    }
    val candidateSeconds = recordingResumeCandidateSeconds(state, requestedSeconds)
        ?: return RecordingStartDecision.FromBeginning
    val candidateMs = recordingSecondsToMediaMilliseconds(candidateSeconds)
        ?: return RecordingStartDecision.FromBeginning
    val usableDuration = durationMs?.takeIf { it > 0L }
        ?: return RecordingStartDecision.FromBeginning
    if (candidateMs >= usableDuration) return RecordingStartDecision.FromBeginning
    if (recordingIsComplete(state, candidateMs, usableDuration, naturalEnd = false)) {
        return RecordingStartDecision.FromBeginning
    }
    return RecordingStartDecision.ResumeAt(candidateMs)
}

fun recordingIsComplete(
    state: DvrState,
    positionMs: Long,
    durationMs: Long?,
    naturalEnd: Boolean,
): Boolean {
    if (state != DvrState.COMPLETED) return false
    if (positionMs < 0L) return false
    if (durationMs != null && durationMs > 0L && positionMs > durationMs) return false
    if (naturalEnd) return true
    val duration = durationMs?.takeIf { it > 0L } ?: return false
    val position = positionMs
    val ninetyFivePercent = duration - duration / 20L
    val remaining = (duration - position).coerceAtLeast(0L)
    return position >= ninetyFivePercent && remaining <= RECORDING_COMPLETION_REMAINING_MS
}

fun recordingCompletionDecision(
    state: DvrState,
    trigger: RecordingCompletionTrigger,
    positionMs: Long,
    durationMs: Long?,
    terminalError: Boolean,
): Boolean {
    if (terminalError) return false
    return recordingIsComplete(
        state = state,
        positionMs = positionMs,
        durationMs = durationMs,
        naturalEnd = trigger == RecordingCompletionTrigger.NaturalEnd,
    )
}

fun recordingCheckpointSeconds(
    @Suppress("UNUSED_PARAMETER") trigger: RecordingCheckpointTrigger,
    positionMs: Long,
): Long? = mediaMillisecondsToRecordingSeconds(positionMs)
    ?.takeIf { it >= RECORDING_RESUME_FLOOR_SECONDS }

fun recordingPeriodicCheckpointDue(
    nowMs: Long,
    lastAttemptMs: Long,
    positionSeconds: Long,
    lastAcceptedSeconds: Long?,
): Boolean {
    if (nowMs - lastAttemptMs < RECORDING_PERIODIC_INTERVAL_MS) return false
    val accepted = lastAcceptedSeconds ?: return true
    val delta = if (positionSeconds >= accepted) {
        positionSeconds - accepted
    } else {
        accepted - positionSeconds
    }
    return delta >= RECORDING_PERIODIC_MINIMUM_DELTA_SECONDS
}

fun recordingRemoteUpdateDecision(
    localDirty: Boolean,
    @Suppress("UNUSED_PARAMETER") remoteSeconds: Long,
): RecordingRemoteUpdateDecision = if (localDirty) {
    RecordingRemoteUpdateDecision.KeepLocal
} else {
    RecordingRemoteUpdateDecision.AdoptRemote
}

class RecordingCompletionLatch {
    private val completedGenerations = mutableSetOf<Long>()

    fun claim(generation: Long): Boolean = completedGenerations.add(generation)
}
