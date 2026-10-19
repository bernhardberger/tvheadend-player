package at.bernhardberger.tvhplayer.ui.player

@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
internal fun currentRecordingIsGrowing(
    observation: at.bernhardberger.tvheadend.sdk.core.SessionObservation,
    selection: at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection?,
): Boolean = selection != null && observation.currentSession === selection.currentSession &&
    observation.dvrEntry(selection.recordingId)?.let(observation::recordingPlaybackAdmission) is
        at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission.Growing
