package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry

fun EpgEventEntry.programmeRecordingTarget(
    currentSession: CurrentSessionObservation,
): ProgrammeRecordingTarget = ProgrammeRecordingTarget.from(this, currentSession)
