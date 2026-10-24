package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi

enum class BrowseContextAction {
    WATCH, DETAILS, RECORD, STOP, CANCEL, SHOW_GUIDE,
    RESUME, START_OVER, PLAY, MARK_WATCHED, MARK_UNWATCHED, DELETE,
    MARK_ALL_WATCHED, MARK_ALL_UNWATCHED, DELETE_WATCHED, DELETE_ALL,
}

/**
 * One order for every channel and programme menu, so a held OK always lands on Watch.
 * [recordingAdmission] is the SDK's admission of [recording]: an ended programme it admits plays
 * its recording right after Watch, as on the details page.
 */
fun channelContextActions(
    event: EpgEvent?,
    nowSec: Long,
    recording: DvrEntry?,
    recordingAdmission: RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
): List<BrowseContextAction> = buildList {
    add(BrowseContextAction.WATCH)
    if (event != null) {
        val actions = programmeActions(event, nowSec, recording, canModifyRecordings = canModifyRecordings,
            recordingAdmission = recordingAdmission)
        if (recording != null && ProgrammeAction.WATCH_FROM_START in actions) add(BrowseContextAction.PLAY)
        add(BrowseContextAction.DETAILS)
        actions.mapNotNullTo(this) {
            when (it) {
                ProgrammeAction.RECORD -> BrowseContextAction.RECORD
                ProgrammeAction.STOP_RECORDING -> BrowseContextAction.STOP
                ProgrammeAction.CANCEL_RECORDING -> BrowseContextAction.CANCEL
                ProgrammeAction.WATCH, ProgrammeAction.WATCH_FROM_START -> null
            }
        }
    }
    add(BrowseContextAction.SHOW_GUIDE)
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

/**
 * Watch-state commands are only useful for playable, ended recordings with write authority. The
 * command follows the shown watch state: only a watched recording offers unwatched, and a partly
 * watched one offers watched even when an earlier viewing already counted.
 */
@OptIn(ExperimentalTvheadendApi::class)
fun recordingWatchStateAction(
    entry: DvrEntry,
    admission: RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
): BrowseContextAction? {
    if (!canModifyRecordings || !recordingPlaybackOffered(admission) ||
        admission is RecordingPlaybackAdmission.Growing || entry.state == DvrEntryState.MISSED
    ) return null
    val watched = recordingWatchState(entry, offeredResumePosition(admission), nowSec = 0L) == RecordingWatchState.Watched
    return if (watched) BrowseContextAction.MARK_UNWATCHED else BrowseContextAction.MARK_WATCHED
}

private val FolderContextActions = listOf(
    BrowseContextAction.MARK_ALL_WATCHED, BrowseContextAction.MARK_ALL_UNWATCHED,
    BrowseContextAction.DELETE_WATCHED, BrowseContextAction.DELETE_ALL,
)

/** An Archive folder's menu offers only the actions that would change at least one recording under it. */
@OptIn(ExperimentalTvheadendApi::class)
fun folderContextActions(
    folder: DvrArchiveFolder,
    admission: (DvrEntry) -> RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
): List<BrowseContextAction> = FolderContextActions.filter {
    folderActionTargets(folder, it, admission, canModifyRecordings).isNotEmpty()
}

/** The recordings under [folder] that a folder [action] changes, newest first. */
@OptIn(ExperimentalTvheadendApi::class)
fun folderActionTargets(
    folder: DvrArchiveFolder,
    action: BrowseContextAction,
    admission: (DvrEntry) -> RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
): List<DvrEntry> = folder.allRecordings().filter {
    folderActionApplies(it, action, admission(it), canModifyRecordings)
}

/**
 * Whether a folder [action] changes [entry]: each recording follows its own menu's rules, and one
 * that is still running is left alone, since it is neither finished to watch nor safe to delete.
 */
@OptIn(ExperimentalTvheadendApi::class)
fun folderActionApplies(
    entry: DvrEntry,
    action: BrowseContextAction,
    admission: RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
): Boolean {
    if (!canModifyRecordings || entry.state != DvrEntryState.COMPLETED) return false
    return when (action) {
        BrowseContextAction.MARK_ALL_WATCHED ->
            recordingWatchStateAction(entry, admission, canModifyRecordings) == BrowseContextAction.MARK_WATCHED
        BrowseContextAction.MARK_ALL_UNWATCHED ->
            recordingWatchStateAction(entry, admission, canModifyRecordings) == BrowseContextAction.MARK_UNWATCHED
        BrowseContextAction.DELETE_WATCHED ->
            recordingWatchState(entry, offeredResumePosition(admission), nowSec = 0L) == RecordingWatchState.Watched
        BrowseContextAction.DELETE_ALL -> true
        else -> false
    }
}
