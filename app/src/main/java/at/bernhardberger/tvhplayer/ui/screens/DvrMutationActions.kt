package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrRepository
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.toDvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
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
    is DvrMutationAction.Stop -> dvrMutationStateIsCurrent(
        currentSession, recordingId, DvrEntryState.RECORDING, observation,
    )
    is DvrMutationAction.CreateProgramme,
    is DvrMutationAction.Delete -> true
}

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
) {
    constructor(repository: DvrRepository) : this(
        scheduleEntry = repository::scheduleEntry,
        stopEntry = repository::stopEntry,
        cancelEntry = repository::cancelEntry,
        deleteEntry = repository::deleteEntry,
    )

    suspend fun execute(action: DvrMutationAction?): DvrMutationFeedback {
        val result = when (action) {
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
            null -> DvrMutationResult.NotReady
        }
        return result.toDvrMutationFeedback()
    }
}

internal fun NoticeCenter.postDvrFailure(action: DvrMutationAction, feedback: DvrMutationFeedback, context: NoticeContext) {
    if (!feedback.isFailure) return
    val kind = when (action) {
        is DvrMutationAction.CreateProgramme -> DvrMutationKind.SCHEDULE
        is DvrMutationAction.Stop -> DvrMutationKind.STOP
        is DvrMutationAction.Cancel -> DvrMutationKind.CANCEL
        is DvrMutationAction.Delete -> DvrMutationKind.DELETE
    }
    post(Notice.DvrActionFailed(kind, feedback), context)
}
