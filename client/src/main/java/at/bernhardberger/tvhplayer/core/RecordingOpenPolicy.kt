package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrProgressPolicy
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class RecordingOpenAction { START_OVER, RESUME_CHOICE, RESUME, DETAILS }

/**
 * Whether the SDK's [admission] of a recording lets the app offer Play. A completed file always plays; a
 * growing one only with supported progress, because the SDK's player refuses it otherwise. A deferred or
 * unavailable target, and no admission because there is no session, offer no playback. Play itself stays
 * authoritative and may still refuse an entry from an older observation.
 */
@OptIn(ExperimentalTvheadendApi::class)
fun recordingPlaybackOffered(admission: RecordingPlaybackAdmission?): Boolean = when (admission) {
    is RecordingPlaybackAdmission.Completed -> true
    is RecordingPlaybackAdmission.Growing -> admission.progressCapability == RecordingProgressCapability.SUPPORTED
    RecordingPlaybackAdmission.GrowingDeferred,
    RecordingPlaybackAdmission.TargetUnavailable,
    RecordingPlaybackAdmission.ObservationExpired,
    null -> false
}

/** The saved position Resume starts from, or null when [admission] offers no playback or no Resume. */
@OptIn(ExperimentalTvheadendApi::class)
fun offeredResumePosition(admission: RecordingPlaybackAdmission?): Duration? = when {
    !recordingPlaybackOffered(admission) -> null
    admission is RecordingPlaybackAdmission.Completed -> admission.resumePosition
    admission is RecordingPlaybackAdmission.Growing -> admission.resumePosition
    else -> null
}

/**
 * What is left to watch of a finished recording: what was recorded less the position it offers to
 * resume from, or all of it when opening starts over. Null unless [admission] offers its completed
 * file with a known length; a growing one is still recording.
 */
@OptIn(ExperimentalTvheadendApi::class)
fun recordingTimeLeft(entry: DvrEntry, admission: RecordingPlaybackAdmission?): Duration? {
    if (admission !is RecordingPlaybackAdmission.Completed) return null
    val length = recordingLengthSeconds(entry, nowSec = 0L)?.takeIf { it > 0L }?.seconds ?: return null
    val position = offeredResumePosition(admission)
        ?.takeIf { recordingOpenAction(entry, admission) == RecordingOpenAction.RESUME_CHOICE } ?: Duration.ZERO
    return (length - position).coerceAtLeast(Duration.ZERO)
}

/** How opening [entry] starts, following the SDK's [admission] of it from the observation it was read from. */
@OptIn(ExperimentalTvheadendApi::class)
fun recordingOpenAction(entry: DvrEntry, admission: RecordingPlaybackAdmission?): RecordingOpenAction {
    if (!recordingPlaybackOffered(admission)) return RecordingOpenAction.DETAILS
    val position = offeredResumePosition(admission)
    if (admission is RecordingPlaybackAdmission.Growing) {
        return if (position != null) RecordingOpenAction.RESUME else RecordingOpenAction.START_OVER
    }
    return if (position == null || recordingPositionFinished(entry, position)) RecordingOpenAction.START_OVER
    else RecordingOpenAction.RESUME_CHOICE
}

/** Whether a saved [position] lies past the point where Tvheadend counts [entry] as watched to the end. */
@OptIn(ExperimentalTvheadendApi::class)
internal fun recordingPositionFinished(entry: DvrEntry, position: Duration): Boolean {
    val duration = if (entry.start != null && entry.stop != null) entry.stop!! - entry.start!! else return false
    return duration.isPositive() && position >= duration * DvrProgressPolicy().orderlyCompletionFraction
}
