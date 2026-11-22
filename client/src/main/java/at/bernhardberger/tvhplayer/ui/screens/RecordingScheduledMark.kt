package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.linkedRecordingForEvent
import at.bernhardberger.tvhplayer.core.recordingIsRunning

/** The scheduled or already running recording to mark on a Next line. */
fun SessionObservation.nextRecordingForEvent(event: EpgEvent?): DvrEntry? =
    linkedRecordingForEvent(event)?.takeIf { it.state == DvrEntryState.SCHEDULED || recordingIsRunning(it) }

/** Whether a recording is scheduled for [event], the mark on a headline's Next line. */
fun SessionObservation.isScheduled(event: EpgEvent?): Boolean =
    nextRecordingForEvent(event)?.state == DvrEntryState.SCHEDULED
