package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry

fun EpgEventEntry.programmeRecordingTarget(
    currentSession: CurrentSessionObservation,
): ProgrammeRecordingTarget = ProgrammeRecordingTarget.from(this, currentSession)

sealed interface LiveInfoRecordingState {
    data object Idle : LiveInfoRecordingState

    data class Confirming(
        val target: ProgrammeRecordingTarget,
    ) : LiveInfoRecordingState

    data class Dispatching(
        val target: ProgrammeRecordingTarget,
    ) : LiveInfoRecordingState

    data class Succeeded(
        val target: ProgrammeRecordingTarget,
    ) : LiveInfoRecordingState

    data class Failed(
        val target: ProgrammeRecordingTarget,
        val result: DvrMutationResult<*>,
    ) : LiveInfoRecordingState
}

sealed interface LiveInfoRecordingDecision {
    data class Dispatch(
        val target: ProgrammeRecordingTarget,
    ) : LiveInfoRecordingDecision

    data object Invalidate : LiveInfoRecordingDecision
    data object Ignore : LiveInfoRecordingDecision
}

fun liveInfoRecordingDecision(
    state: LiveInfoRecordingState,
    currentEvent: EpgEventEntry?,
    actionEligible: Boolean,
): LiveInfoRecordingDecision {
    val target = when (state) {
        is LiveInfoRecordingState.Confirming -> state.target
        is LiveInfoRecordingState.Failed -> state.target
        LiveInfoRecordingState.Idle,
        is LiveInfoRecordingState.Dispatching,
        is LiveInfoRecordingState.Succeeded -> return LiveInfoRecordingDecision.Ignore
    }
    if (!actionEligible) return LiveInfoRecordingDecision.Invalidate
    return if (
        currentEvent?.id == target.eventId &&
        currentEvent.channelId == target.channelId &&
        currentEvent.start.epochSeconds == target.start &&
        currentEvent.stop.epochSeconds == target.stop &&
        currentEvent.title.orEmpty() == target.title
    ) {
        LiveInfoRecordingDecision.Dispatch(target)
    } else {
        LiveInfoRecordingDecision.Invalidate
    }
}

fun liveInfoRecordingCompleted(
    state: LiveInfoRecordingState,
    result: DvrMutationResult<*>,
): LiveInfoRecordingState {
    val target = (state as? LiveInfoRecordingState.Dispatching)?.target ?: return state
    return when (result) {
        is DvrMutationResult.Confirmed,
        is DvrMutationResult.AcceptedButUnconfirmed -> LiveInfoRecordingState.Succeeded(target)
        else -> LiveInfoRecordingState.Failed(target, result)
    }
}

data class LiveInfoRecordingCompletion(
    val state: LiveInfoRecordingState,
    val showResult: Boolean,
)

fun liveInfoRecordingCompletion(
    state: LiveInfoRecordingState,
    result: DvrMutationResult<*>,
    infoOpen: Boolean,
): LiveInfoRecordingCompletion = LiveInfoRecordingCompletion(
    state = liveInfoRecordingCompleted(state, result),
    showResult = infoOpen && state is LiveInfoRecordingState.Dispatching,
)

fun liveInfoRecordingDismissed(
    state: LiveInfoRecordingState,
): LiveInfoRecordingState = when (state) {
    is LiveInfoRecordingState.Dispatching,
    is LiveInfoRecordingState.Succeeded -> state
    LiveInfoRecordingState.Idle,
    is LiveInfoRecordingState.Confirming,
    is LiveInfoRecordingState.Failed -> LiveInfoRecordingState.Idle
}
