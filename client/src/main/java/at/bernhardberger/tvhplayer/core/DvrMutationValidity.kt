package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation

/** A confirmation authorizes only the captured session, entry and recording state. */
fun dvrMutationStateIsCurrent(
    capturedSession: CurrentSessionObservation,
    recordingId: DvrEntryId,
    expectedState: DvrEntryState,
    observation: SessionObservation,
): Boolean = observation.currentSession === capturedSession &&
    observation.dvrEntry(recordingId)?.state == expectedState
