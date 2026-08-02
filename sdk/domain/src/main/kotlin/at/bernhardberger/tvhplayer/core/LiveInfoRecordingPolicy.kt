package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry

fun EpgEventEntry.programmeRecordingTarget(): ProgrammeRecordingTarget =
    ProgrammeRecordingTarget.from(this)

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
        val reason: DvrActionFailure,
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
    return if (currentEvent?.programmeRecordingTarget() == target) {
        LiveInfoRecordingDecision.Dispatch(target)
    } else {
        LiveInfoRecordingDecision.Invalidate
    }
}

fun liveInfoRecordingCompleted(
    state: LiveInfoRecordingState,
    result: DvrActionResult,
): LiveInfoRecordingState {
    val target = (state as? LiveInfoRecordingState.Dispatching)?.target ?: return state
    return when (result) {
        is DvrActionResult.Accepted -> LiveInfoRecordingState.Succeeded(target)
        is DvrActionResult.Failed -> LiveInfoRecordingState.Failed(target, result.reason)
    }
}

data class LiveInfoRecordingCompletion(
    val state: LiveInfoRecordingState,
    val showResult: Boolean,
)

fun liveInfoRecordingCompletion(
    state: LiveInfoRecordingState,
    result: DvrActionResult,
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
