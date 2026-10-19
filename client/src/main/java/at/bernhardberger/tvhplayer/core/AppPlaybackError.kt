@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
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
 * The player's own error for a failure on this device, such as `DECODER_INIT_FAILED (4001)`, shown
 * small under the message because it is what a bug report needs; null for any other state.
 */
fun AppPlaybackState.deviceFaultDetail(): String? {
    val failed = (this as? AppPlaybackState.Failed)?.takeIf { it.deviceFault } ?: return null
    val name = failed.playerErrorCode?.removePrefix("ERROR_CODE_") ?: return null
    return failed.playerErrorNumber?.let { "$name ($it)" } ?: name
}

/**
 * The recording player's terminal condition, or null when there is nothing to report: the recording
 * plays, or it is still loading. [entry] is the recording in the current observation, null when it
 * is gone or not observed yet. The SDK's [admission] decides first for a target that cannot play; a
 * playable or unresolved target reports the playback [failure]. The connection while the recording
 * is still unresolved is the player alert's own (`recordingPlayerAlert`).
 */
@OptIn(ExperimentalTvheadendApi::class)
fun recordingErrorCondition(
    entry: DvrEntry?,
    admission: RecordingPlaybackAdmission?,
    failure: AppPlaybackState.Failed?,
): AppErrorCondition? = when {
    // The SDK defers an active recording whose single file is not MPEG-TS until it is finished.
    admission == RecordingPlaybackAdmission.GrowingDeferred ->
        if (entry == null) AppErrorCondition.RECORDING_UNAVAILABLE else AppErrorCondition.RECORDING_DEFERRED
    admission == RecordingPlaybackAdmission.ObservationExpired -> AppErrorCondition.RECORDING_UNAVAILABLE
    admission == RecordingPlaybackAdmission.TargetUnavailable -> when (entry?.state) {
        DvrEntryState.SCHEDULED -> AppErrorCondition.RECORDING_NOT_STARTED
        DvrEntryState.FILE_MISSING -> AppErrorCondition.RECORDING_FILE_MISSING
        // A running entry, also one in an error state: no file yet is missing-file evidence; several files can
        // only play once it is finished; anything else (blank path, replaced incarnation) means it changed.
        DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR -> when {
            entry.files.isNullOrEmpty() -> AppErrorCondition.RECORDING_FILE_MISSING
            entry.files.orEmpty().size > 1 -> AppErrorCondition.RECORDING_DEFERRED
            else -> AppErrorCondition.RECORDING_UNAVAILABLE
        }
        // The SDK admits a failed or missed entry that left a file with data, so one without has none to play;
        // one with a file only arrives here when it changed or was replaced.
        DvrEntryState.COMPLETED_ERROR, DvrEntryState.MISSED ->
            if (entry.files.isNullOrEmpty() || entry.dataSizeBytes == 0L) AppErrorCondition.RECORDING_FILE_MISSING
            else AppErrorCondition.RECORDING_UNAVAILABLE
        // A completed entry is always admitted, so it only arrives here when it changed or was replaced.
        else -> AppErrorCondition.RECORDING_UNAVAILABLE
    }
    failure?.deviceFault == true -> AppErrorCondition.RECORDING_PLAYBACK_FAILED
    // Failed(OTHER) included: any other playback failure of a playable or unresolved recording is an interruption.
    failure != null -> AppErrorCondition.RECORDING_INTERRUPTED
    // A playable admission for an entry that is no longer observed, or an entry the SDK did not admit.
    (entry == null && admission != null) || (entry != null && admission == null) ->
        AppErrorCondition.RECORDING_UNAVAILABLE
    // Completed or Growing with its entry plays; neither entry nor admission is still loading.
    else -> null
}

/** The recording player's alert, or null when [recordingErrorCondition] has none; it offers Close. */
fun recordingAppError(
    entry: DvrEntry?,
    admission: RecordingPlaybackAdmission?,
    failure: AppPlaybackState.Failed?,
): AppError? = recordingErrorCondition(entry, admission, failure)?.toAppError(closable = true)
