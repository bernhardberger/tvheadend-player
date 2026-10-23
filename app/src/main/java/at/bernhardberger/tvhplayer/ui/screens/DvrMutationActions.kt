package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrProgressResult
import at.bernhardberger.tvheadend.sdk.core.DvrRepository
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.toDvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import at.bernhardberger.tvhplayer.core.folderActionApplies
import at.bernhardberger.tvhplayer.core.dvrMutationStateIsCurrent

internal sealed interface DvrMutationAction {
    data class CreateProgramme(
        val target: ProgrammeRecordingTarget,
        val configId: DvrConfigId?,
    ) : DvrMutationAction

    data class Stop(
        val currentSession: CurrentSessionObservation,
        val recordingId: DvrEntryId,
    ) : DvrMutationAction

    data class Cancel(
        val currentSession: CurrentSessionObservation,
        val recordingId: DvrEntryId,
    ) : DvrMutationAction

    data class Delete(
        val currentSession: CurrentSessionObservation,
        val recordingId: DvrEntryId,
    ) : DvrMutationAction
}

internal fun DvrMutationAction.recordingStateIsCurrent(observation: SessionObservation): Boolean = when (this) {
    is DvrMutationAction.Cancel -> dvrMutationStateIsCurrent(
        currentSession, recordingId, DvrEntryState.SCHEDULED, observation,
    )
    is DvrMutationAction.Stop -> listOf(DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR).any {
        dvrMutationStateIsCurrent(currentSession, recordingId, it, observation)
    }
    is DvrMutationAction.CreateProgramme,
    is DvrMutationAction.Delete -> true
}

@OptIn(ExperimentalTvheadendApi::class)
internal class DvrMutationActions(
    private val scheduleEntry: suspend (
        CurrentSessionObservation,
        DvrScheduleRequest,
    ) -> DvrMutationResult<DvrEntryId>,
    private val stopEntry: suspend (
        CurrentSessionObservation,
        DvrEntryId,
    ) -> DvrMutationResult<Unit>,
    private val cancelEntry: suspend (
        CurrentSessionObservation,
        DvrEntryId,
    ) -> DvrMutationResult<Unit>,
    private val deleteEntry: suspend (
        CurrentSessionObservation,
        DvrEntryId,
    ) -> DvrMutationResult<Unit>,
    private val updateWatchStateCommand: suspend (
        CurrentSessionObservation,
        DvrEntryId,
        Boolean,
    ) -> DvrProgressResult,
) {
    constructor(repository: DvrRepository) : this(
        scheduleEntry = repository::scheduleEntry,
        stopEntry = repository::stopEntry,
        cancelEntry = repository::cancelEntry,
        deleteEntry = repository::deleteEntry,
        updateWatchStateCommand = repository::updateWatchState,
    )

    suspend fun updateWatchState(
        currentSession: CurrentSessionObservation,
        recordingId: DvrEntryId,
        watched: Boolean,
    ): DvrProgressResult = updateWatchStateCommand(currentSession, recordingId, watched)

    suspend fun execute(action: DvrMutationAction?): DvrMutationResult<*> =
        when (action) {
            is DvrMutationAction.CreateProgramme -> scheduleEntry(
                action.target.currentSession,
                DvrScheduleRequest(
                    schedule = DvrSchedule.Programme(action.target.eventId),
                    configId = action.configId,
                    title = action.target.title,
                ),
            )
            is DvrMutationAction.Stop -> stopEntry(
                action.currentSession,
                action.recordingId,
            )
            is DvrMutationAction.Cancel -> cancelEntry(
                action.currentSession,
                action.recordingId,
            )
            is DvrMutationAction.Delete -> deleteEntry(
                action.currentSession,
                action.recordingId,
            )
            null -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
        }
}

@OptIn(ExperimentalTvheadendApi::class)
internal fun NoticeCenter.postDvrFailure(action: DvrMutationAction, result: DvrMutationResult<*>, context: NoticeContext) {
    val feedback = result.toDvrMutationFeedback()
    if (!feedback.isFailure) return
    val kind = when (action) {
        is DvrMutationAction.CreateProgramme -> DvrMutationKind.SCHEDULE
        is DvrMutationAction.Stop -> DvrMutationKind.STOP
        is DvrMutationAction.Cancel -> DvrMutationKind.CANCEL
        is DvrMutationAction.Delete -> DvrMutationKind.DELETE
    }
    // The server's own text belongs only to a refusal; any other failure keeps the app's wording.
    val serverMessage = (result as? DvrMutationResult.Failed)?.serverMessage
        ?.takeIf { feedback == DvrMutationFeedback.REJECTED }
    post(Notice.DvrActionFailed(kind, feedback, serverMessage), context)
}

/**
 * Applies a folder [action] to [recordingIds] one request at a time. Each recording is checked
 * against the latest observation first, so one that changed or left since the menu opened is
 * skipped; once the opening session is gone, the rest count as not done. Returns the notice to
 * post when any request failed.
 */
@OptIn(ExperimentalTvheadendApi::class)
internal suspend fun DvrMutationActions.runFolderAction(
    action: BrowseContextAction,
    openingSession: CurrentSessionObservation,
    recordingIds: List<DvrEntryId>,
    latestObservation: () -> SessionObservation,
): Notice.DvrFolderActionFailed? {
    val watched = when (action) {
        BrowseContextAction.MARK_ALL_WATCHED -> true
        BrowseContextAction.MARK_ALL_UNWATCHED -> false
        BrowseContextAction.DELETE_WATCHED, BrowseContextAction.DELETE_ALL -> null
        else -> error("Not a folder action: $action")
    }
    var attempted = 0
    var failed = 0
    var failure: DvrMutationFeedback? = null
    for ((index, id) in recordingIds.withIndex()) {
        val current = latestObservation()
        val canModify = (current.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
        // A lost connection or permission fails the rest; an entry someone else changed is just skipped.
        val stopped = when {
            current.currentSession !== openingSession -> DvrMutationFeedback.CONNECTION_UNAVAILABLE
            !canModify -> DvrMutationFeedback.PERMISSION_DENIED
            else -> null
        }
        if (stopped != null) {
            val rest = recordingIds.size - index
            attempted += rest
            failed += rest
            failure = failure ?: stopped
            break
        }
        val entry = current.dvrEntry(id) ?: continue
        if (!folderActionApplies(entry, action, current.recordingPlaybackAdmission(entry), canModify)) continue
        attempted++
        val feedback = if (watched != null) {
            when (val result = updateWatchState(openingSession, id, watched)) {
                DvrProgressResult.Accepted -> null
                is DvrProgressResult.Failed -> result.toDvrMutationFeedback()
            }
        } else execute(DvrMutationAction.Delete(openingSession, id)).toDvrMutationFeedback().takeIf { it.isFailure }
        if (feedback != null) {
            failed++
            failure = failure ?: feedback
        }
    }
    val kind = if (watched == null) DvrMutationKind.DELETE else DvrMutationKind.UPDATE
    return failure?.let { Notice.DvrFolderActionFailed(kind, failed, attempted, it) }
}
