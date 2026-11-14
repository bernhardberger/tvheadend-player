package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.dvrProblemClearTargets
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.toDvrMutationFeedback

/**
 * Removes the problem entries the card counted, one request at a time: [fileless] as they were when it
 * opened, and [partial] only when the user chose to delete those too (empty otherwise). Each is checked
 * against the latest observation first, so consent never widens: one that is no longer a problem is
 * skipped, and so is a [fileless] one that has gained a file since. Once the opening session or the
 * permission is gone, the rest count as not cleared. Returns the notice to post when any request failed.
 */
@OptIn(ExperimentalTvheadendApi::class)
internal suspend fun DvrMutationActions.clearDvrProblems(
    openingSession: CurrentSessionObservation,
    fileless: List<DvrEntryId>,
    partial: List<DvrEntryId>,
    latestObservation: () -> SessionObservation,
): Notice.DvrProblemsClearFailed? {
    val entryIds = fileless + partial
    var attempted = 0
    var failed = 0
    var failure: DvrMutationFeedback? = null
    for ((index, id) in entryIds.withIndex()) {
        val current = latestObservation()
        val stopped = clearStopReason(current, openingSession)
        if (stopped != null) {
            attempted += entryIds.size - index
            failed += entryIds.size - index
            failure = failure ?: stopped
            break
        }
        val targets = dvrProblemClearTargets(listOfNotNull(current.dvrEntry(id)))
        val clears = targets.fileless.isNotEmpty() || (index >= fileless.size && targets.partial.isNotEmpty())
        val feedback = if (clears) {
            execute(DvrMutationAction.Delete(openingSession, id)).toDvrMutationFeedback()
        } else {
            null
        }
        if (clears) attempted++
        if (feedback?.isFailure == true) {
            failed++
            failure = failure ?: feedback
        }
    }
    return failure?.let { Notice.DvrProblemsClearFailed(failed, attempted, it) }
}

/** A lost connection or write permission ends the clear and fails what is left. */
private fun clearStopReason(current: SessionObservation, openingSession: CurrentSessionObservation) = when {
    current.currentSession !== openingSession -> DvrMutationFeedback.CONNECTION_UNAVAILABLE
    (current.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite != CapabilityAccess.ALLOWED ->
        DvrMutationFeedback.PERMISSION_DENIED
    else -> null
}
