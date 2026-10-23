package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi

enum class BrowseContextAction {
    WATCH, DETAILS, RECORD, STOP, CANCEL, SHOW_GUIDE,
    RESUME, START_OVER, PLAY, MARK_WATCHED, MARK_UNWATCHED, DELETE,
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
    recordingWatchStateAction(entry, admission, canModifyRecordings)?.let(::add)
    if (canModifyRecordings) when (entry.state) {
        DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR -> add(BrowseContextAction.STOP)
        DvrEntryState.SCHEDULED -> add(BrowseContextAction.CANCEL)
        DvrEntryState.COMPLETED, DvrEntryState.MISSED, DvrEntryState.INVALID,
        DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING -> add(BrowseContextAction.DELETE)
        else -> Unit
    }
}

/** Watch-state commands are only useful for playable, ended recordings with write authority. */
@OptIn(ExperimentalTvheadendApi::class)
fun recordingWatchStateAction(
    entry: DvrEntry,
    admission: RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
): BrowseContextAction? {
    if (!canModifyRecordings || !recordingPlaybackOffered(admission) ||
        admission is RecordingPlaybackAdmission.Growing || entry.state == DvrEntryState.MISSED
    ) return null
    return if ((entry.playCount ?: 0) > 0) BrowseContextAction.MARK_UNWATCHED else BrowseContextAction.MARK_WATCHED
}
