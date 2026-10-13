package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent

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

fun recordingContextActions(
    entry: DvrEntry,
    playbackEligible: Boolean,
    canModifyRecordings: Boolean,
): List<BrowseContextAction> = buildList {
    if (playbackEligible) when (entry.state) {
        DvrEntryState.RECORDING -> {
            add(BrowseContextAction.WATCH)
            if ((entry.playPosition?.inWholeSeconds ?: 0L) > 0L) add(BrowseContextAction.START_OVER)
        }
        DvrEntryState.COMPLETED -> {
            if ((entry.playPosition?.inWholeSeconds ?: 0L) > 0L) {
                add(BrowseContextAction.RESUME)
                add(BrowseContextAction.START_OVER)
            } else add(BrowseContextAction.PLAY)
        }
        else -> Unit
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
