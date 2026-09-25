package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrRepository
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget

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

internal enum class DvrMutationFeedback(val isFailure: Boolean) {
    CONFIRMED(false),
    ACCEPTED_UNCONFIRMED(false),
    PERMISSION_DENIED(true),
    CONNECTION_LIMIT(true),
    REJECTED(true),
    NOT_SUPPORTED(true),
    TIMEOUT(true),
    CONNECTION_UNAVAILABLE(true),
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

@Composable
internal fun DvrMutationFeedback.label(): String = stringResource(
    when (this) {
        DvrMutationFeedback.CONFIRMED -> R.string.recording_action_confirmed
        DvrMutationFeedback.ACCEPTED_UNCONFIRMED -> R.string.recording_action_accepted
        DvrMutationFeedback.PERMISSION_DENIED -> R.string.recording_action_permission
        DvrMutationFeedback.CONNECTION_LIMIT -> R.string.recording_action_conn_limit
        DvrMutationFeedback.REJECTED -> R.string.recording_action_rejected
        DvrMutationFeedback.NOT_SUPPORTED -> R.string.recording_action_not_supported
        DvrMutationFeedback.TIMEOUT -> R.string.recording_action_timeout
        DvrMutationFeedback.CONNECTION_UNAVAILABLE -> R.string.recording_action_connection
    },
)
