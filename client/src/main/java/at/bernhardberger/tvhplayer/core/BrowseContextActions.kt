package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi

enum class BrowseContextAction {
    WATCH, DETAILS, RECORD, STOP, CANCEL, SHOW_GUIDE,
    RESUME, START_OVER, PLAY, DELETE,
}

fun channelContextActions(
    event: EpgEvent?,
    nowSec: Long,
    recording: DvrEntry?,
    canModifyRecordings: Boolean,
): List<BrowseContextAction> = buildList {
    add(BrowseContextAction.WATCH)
    if (event != null) {
        add(BrowseContextAction.DETAILS)
        addAll(programmeContextRecordingActions(event, nowSec, recording, canModifyRecordings))
    }
    add(BrowseContextAction.SHOW_GUIDE)
}

fun guideContextActions(
    event: EpgEvent,
    nowSec: Long,
    recording: DvrEntry?,
    canModifyRecordings: Boolean,
): List<BrowseContextAction> = buildList {
    add(BrowseContextAction.DETAILS)
    addAll(programmeContextRecordingActions(event, nowSec, recording, canModifyRecordings))
    add(BrowseContextAction.WATCH)
}

private fun programmeContextRecordingActions(
    event: EpgEvent,
    nowSec: Long,
    recording: DvrEntry?,
    canModifyRecordings: Boolean,
): List<BrowseContextAction> = programmeActions(
    event, nowSec, recording, canModifyRecordings = canModifyRecordings,
).mapNotNull {
    when (it) {
        ProgrammeAction.RECORD -> BrowseContextAction.RECORD
        ProgrammeAction.STOP_RECORDING -> BrowseContextAction.STOP
        ProgrammeAction.CANCEL_RECORDING -> BrowseContextAction.CANCEL
        ProgrammeAction.WATCH, ProgrammeAction.WATCH_FROM_START -> null
    }
}

/** [admission] is the SDK's admission of [entry] from the observation it was read from, null without a session. */
@OptIn(ExperimentalTvheadendApi::class)
fun recordingContextActions(
    entry: DvrEntry,
    admission: RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
): List<BrowseContextAction> = buildList {
    if (recordingPlaybackOffered(admission)) {
        val resumable = offeredResumePosition(admission) != null
        if (admission is RecordingPlaybackAdmission.Growing) {
            add(BrowseContextAction.WATCH)
            if (resumable) add(BrowseContextAction.START_OVER)
        } else if (resumable) {
            add(BrowseContextAction.RESUME)
            add(BrowseContextAction.START_OVER)
        } else add(BrowseContextAction.PLAY)
    }
    add(BrowseContextAction.DETAILS)
    if (canModifyRecordings) when (entry.state) {
        DvrEntryState.RECORDING -> add(BrowseContextAction.STOP)
        DvrEntryState.SCHEDULED -> add(BrowseContextAction.CANCEL)
        DvrEntryState.COMPLETED, DvrEntryState.MISSED, DvrEntryState.INVALID,
        DvrEntryState.RECORDING_ERROR, DvrEntryState.COMPLETED_ERROR,
        DvrEntryState.FILE_MISSING -> add(BrowseContextAction.DELETE)
        else -> Unit
    }
}
