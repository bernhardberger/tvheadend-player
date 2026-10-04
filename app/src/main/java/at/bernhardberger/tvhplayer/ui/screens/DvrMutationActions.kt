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
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import at.bernhardberger.tvhplayer.core.DvrLocalIntentKind
import at.bernhardberger.tvhplayer.core.RecentDvrIntents
import at.bernhardberger.tvhplayer.core.dvrMutationStateIsCurrent
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticeQueue
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticeKind
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticeIcon

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

internal enum class DvrMutationFeedback(val isFailure: Boolean, @param:androidx.annotation.StringRes val message: Int?) {
    CONFIRMED(false, null),
    ACCEPTED_UNCONFIRMED(false, null),
    PERMISSION_DENIED(true, R.string.recording_action_permission),
    CONNECTION_LIMIT(true, R.string.recording_action_conn_limit),
    REJECTED(true, R.string.recording_action_rejected),
    NOT_SUPPORTED(true, R.string.recording_action_not_supported),
    TIMEOUT(true, R.string.recording_action_timeout),
    CONNECTION_UNAVAILABLE(true, R.string.recording_action_connection),
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
    private val localIntents: RecentDvrIntents = RecentDvrIntents(),
) {
    constructor(repository: DvrRepository, localIntents: RecentDvrIntents) : this(
        scheduleEntry = repository::scheduleEntry,
        stopEntry = repository::stopEntry,
        cancelEntry = repository::cancelEntry,
        deleteEntry = repository::deleteEntry,
        localIntents = localIntents,
    )

    suspend fun execute(action: DvrMutationAction?): DvrMutationFeedback {
        val ticket = when (action) {
            is DvrMutationAction.Stop -> localIntents.begin(
                action.currentSession.generationIdentity, action.recordingId, DvrLocalIntentKind.STOP)
            is DvrMutationAction.Delete -> localIntents.begin(
                action.currentSession.generationIdentity, action.recordingId, DvrLocalIntentKind.DELETE)
            else -> null
        }
        var accepted = false
        try {
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
            return result.toDvrMutationFeedback().also { accepted = !it.isFailure }
        } finally {
            if (ticket != null) localIntents.finish(ticket, accepted)
        }
    }
}

internal fun DvrMutationResult<*>.toDvrMutationFeedback(): DvrMutationFeedback = when (this) {
    is DvrMutationResult.Confirmed -> DvrMutationFeedback.CONFIRMED
    is DvrMutationResult.AcceptedButUnconfirmed -> DvrMutationFeedback.ACCEPTED_UNCONFIRMED
    DvrMutationResult.AccessDenied -> DvrMutationFeedback.PERMISSION_DENIED
    DvrMutationResult.ConnectionLimit -> DvrMutationFeedback.CONNECTION_LIMIT
    DvrMutationResult.ServerRejected -> DvrMutationFeedback.REJECTED
    DvrMutationResult.NotSupported -> DvrMutationFeedback.NOT_SUPPORTED
    DvrMutationResult.Timeout -> DvrMutationFeedback.TIMEOUT
    DvrMutationResult.NotReady,
    DvrMutationResult.ObservationExpired,
    DvrMutationResult.TransportUnavailable -> DvrMutationFeedback.CONNECTION_UNAVAILABLE
}

internal fun AppNoticeQueue.postDvrFailure(feedback: DvrMutationFeedback, context: Any) {
    if (!feedback.isFailure) return
    post("dvr-action", R.string.recording_action_failed, AppNoticeKind.FAILURE, context,
        icon = AppNoticeIcon.WARNING, detailMessage = feedback.message)
}
