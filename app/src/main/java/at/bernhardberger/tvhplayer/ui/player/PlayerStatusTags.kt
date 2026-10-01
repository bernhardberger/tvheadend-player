package at.bernhardberger.tvhplayer.ui.player

internal fun currentRecordingIsGrowing(
    observation: at.bernhardberger.tvheadend.sdk.core.SessionObservation,
    selection: at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection?,
): Boolean = selection != null && observation.currentSession === selection.currentSession &&
    observation.dvrEntry(selection.recordingId)?.state == at.bernhardberger.tvheadend.sdk.core.DvrEntryState.RECORDING
