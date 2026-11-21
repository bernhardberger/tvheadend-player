package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.linkedRecordingForEvent

/** Whether a recording is scheduled for [event], the mark on a headline's Next line. */
fun SessionObservation.isScheduled(event: EpgEvent?): Boolean =
    linkedRecordingForEvent(event)?.state == DvrEntryState.SCHEDULED
