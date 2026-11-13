package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.AutorecRule
import at.bernhardberger.tvheadend.sdk.core.TimerecRule
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState

/** A confirmation authorizes only the captured session, entry and recording state. */
fun dvrMutationStateIsCurrent(
    capturedSession: CurrentSessionObservation,
    recordingId: DvrEntryId,
    expectedState: DvrEntryState,
    observation: SessionObservation,
): Boolean = observation.currentSession === capturedSession &&
    observation.dvrEntry(recordingId)?.state == expectedState

/** Rule edits authorize the captured rule, not a same-id rule changed by another client. */
fun dvrMutationTargetIsCurrent(
    capturedSession: CurrentSessionObservation,
    target: AutorecRule,
    observation: SessionObservation,
): Boolean = observation.currentSession === capturedSession &&
    (observation.dvrState as? DvrRepositoryState.Current)?.snapshot?.autorecRules?.any { it == target } == true

fun dvrMutationTargetIsCurrent(
    capturedSession: CurrentSessionObservation,
    target: TimerecRule,
    observation: SessionObservation,
): Boolean = observation.currentSession === capturedSession &&
    (observation.dvrState as? DvrRepositoryState.Current)?.snapshot?.timerecRules?.any { it == target } == true
