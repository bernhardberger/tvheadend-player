package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrProgressPolicy
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import kotlin.time.Duration

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

/** How opening [entry] starts, following the SDK's [admission] of it from the observation it was read from. */
@OptIn(ExperimentalTvheadendApi::class)
fun recordingOpenAction(entry: DvrEntry, admission: RecordingPlaybackAdmission?): RecordingOpenAction {
    if (!recordingPlaybackOffered(admission)) return RecordingOpenAction.DETAILS
    val position = offeredResumePosition(admission)
    if (admission is RecordingPlaybackAdmission.Growing) {
        return if (position != null) RecordingOpenAction.RESUME else RecordingOpenAction.START_OVER
    }
    val duration = if (entry.start != null && entry.stop != null) entry.stop!! - entry.start!! else null
    val finished = position != null && duration != null && duration.isPositive() &&
        position >= duration * DvrProgressPolicy().orderlyCompletionFraction
    return if (position == null || finished) RecordingOpenAction.START_OVER else RecordingOpenAction.RESUME_CHOICE
}
