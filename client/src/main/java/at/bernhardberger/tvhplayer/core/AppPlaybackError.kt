@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssueCategory
import at.bernhardberger.tvhplayer.playback.AppPlaybackState

/**
 * A live failure without an issue is the server stopping the stream. The SDK's issues are open
 * values, so a future exact issue falls back by category, never fails.
 */
fun liveErrorCondition(issue: SubscriptionIssue?): AppErrorCondition = when (issue) {
    null -> AppErrorCondition.STREAM_STOPPED
    SubscriptionIssue.NO_FREE_ADAPTER -> AppErrorCondition.NO_FREE_ADAPTER
    SubscriptionIssue.NO_INPUT,
    SubscriptionIssue.BAD_SIGNAL,
    SubscriptionIssue.WEAK_STREAM,
    SubscriptionIssue.TUNING_FAILED -> AppErrorCondition.NO_SIGNAL
    SubscriptionIssue.MUX_NOT_ENABLED -> AppErrorCondition.MUX_NOT_ENABLED
    SubscriptionIssue.SCRAMBLED -> AppErrorCondition.SCRAMBLED
    SubscriptionIssue.USER_ACCESS -> AppErrorCondition.CHANNEL_ACCESS_DENIED
    SubscriptionIssue.SUBSCRIPTION_OVERRIDDEN -> AppErrorCondition.CHANNEL_TAKEN_OVER
    SubscriptionIssue.USER_LIMIT -> AppErrorCondition.USER_LIMIT
    // Tvheadend reports no disk space only for recordings, never for live or timeshift.
    SubscriptionIssue.NO_DISK_SPACE,
    SubscriptionIssue.INVALID_TARGET,
    SubscriptionIssue.UNKNOWN -> AppErrorCondition.STREAM_STOPPED
    else -> when (issue.category) {
        SubscriptionIssueCategory.INPUT_OR_SIGNAL -> AppErrorCondition.NO_SIGNAL
        SubscriptionIssueCategory.RESOURCE,
        SubscriptionIssueCategory.CONFIGURATION_OR_ACCESS,
        SubscriptionIssueCategory.INTERRUPTION,
        SubscriptionIssueCategory.UNKNOWN -> AppErrorCondition.STREAM_STOPPED
    }
}

fun AppPlaybackState.Failed.toLiveErrorCondition(): AppErrorCondition = liveErrorCondition(subscriptionIssue)

/**
 * The recording player's terminal condition. [admission] decides first for a target that cannot
 * play; a playable or unresolved target reports the playback [failure]. An unresolved recording
 * with no connection is [initialConnectionFailure], which is retryable.
 */
@OptIn(ExperimentalTvheadendApi::class)
fun recordingErrorCondition(
    admission: RecordingPlaybackAdmission?,
    failure: AppPlaybackState.Failed?,
    initialConnectionFailure: Boolean = false,
): AppErrorCondition = when {
    initialConnectionFailure -> AppErrorCondition.UNREACHABLE
    admission == RecordingPlaybackAdmission.GrowingDeferred -> AppErrorCondition.RECORDING_NOT_STARTED
    admission == RecordingPlaybackAdmission.TargetUnavailable ||
        admission == RecordingPlaybackAdmission.ObservationExpired -> AppErrorCondition.RECORDING_UNAVAILABLE
    // Any playback failure of a playable recording is an interruption; 303 is only the missing file.
    failure != null -> AppErrorCondition.RECORDING_INTERRUPTED
    // Playable admission without a failure is the file itself; no admission and no failure is an entry that is gone.
    admission is RecordingPlaybackAdmission.Completed ||
        admission is RecordingPlaybackAdmission.Growing -> AppErrorCondition.RECORDING_FILE_MISSING
    else -> AppErrorCondition.RECORDING_UNAVAILABLE
}

/** The recording player always offers Close; an initial connection failure is retryable. */
fun recordingAppError(
    admission: RecordingPlaybackAdmission?,
    failure: AppPlaybackState.Failed?,
    initialConnectionFailure: Boolean = false,
): AppError {
    val condition = recordingErrorCondition(admission, failure, initialConnectionFailure)
    // Without a connection state the failure is the one the viewer can retry.
    val disposition = SessionRecoveryDisposition.EXPLICIT_RETRY.takeIf { initialConnectionFailure }
    return condition.toAppError(disposition, closable = true)
}
