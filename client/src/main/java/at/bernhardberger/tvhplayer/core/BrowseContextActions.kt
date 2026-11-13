package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi

enum class BrowseContextAction {
    CONTINUE_WATCHING, WATCH_LIVE,
    EDIT_RULE, SKIP, UNSKIP,
    WATCH, DETAILS, RECORD, RECORD_SERIES, RECORD_TITLE, STOP, CANCEL, SHOW_GUIDE,
    RESUME, START_OVER, PLAY, MARK_WATCHED, MARK_UNWATCHED, DELETE, REMOVE_ENTRY,
    MARK_ALL_WATCHED, MARK_ALL_UNWATCHED, DELETE_WATCHED, DELETE_ALL,
}

/** Actions a programme's existing recording entry answers the Record key with. */
val programmeRecordingRecordKeyActions: Set<BrowseContextAction> = setOf(
    BrowseContextAction.STOP, BrowseContextAction.CANCEL, BrowseContextAction.SKIP, BrowseContextAction.UNSKIP,
)

/**
 * One order for every channel and programme menu, so a held OK always lands on Watch.
 * [recordingAdmission] is the SDK's admission of [recording]: an ended programme it admits plays
 * its recording right after Watch, as on the details page. The [playing] programme or channel
 * offers Continue watching in place of Watch, and [behindLive] adds Watch live right after it.
 */
fun channelContextActions(
    event: EpgEvent?,
    nowSec: Long,
    recording: DvrEntry?,
    recordingAdmission: RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
    playing: Boolean = false,
    behindLive: Boolean = false,
): List<BrowseContextAction> = buildList {
    if (playing) {
        add(BrowseContextAction.CONTINUE_WATCHING)
        if (behindLive) add(BrowseContextAction.WATCH_LIVE)
    } else add(BrowseContextAction.WATCH)
    if (event != null) {
        val actions = programmeActions(event, nowSec, recording, canModifyRecordings = canModifyRecordings,
            recordingAdmission = recordingAdmission)
        if (recording != null && ProgrammeAction.WATCH_FROM_START in actions) {
            add(if (nowSec < event.stop.epochSeconds) BrowseContextAction.START_OVER else BrowseContextAction.PLAY)
        }
        add(BrowseContextAction.DETAILS)
        actions.mapNotNullTo(this) {
            when (it) {
                ProgrammeAction.RECORD -> BrowseContextAction.RECORD
                ProgrammeAction.STOP_RECORDING -> BrowseContextAction.STOP
                ProgrammeAction.CANCEL_RECORDING -> BrowseContextAction.CANCEL.takeUnless {
                    recording?.isRuleRecording == true
                }
                ProgrammeAction.WATCH, ProgrammeAction.WATCH_FROM_START,
                ProgrammeAction.RECORD_SERIES, ProgrammeAction.RECORD_TITLE -> null
            }
        }
    }
    add(BrowseContextAction.SHOW_GUIDE)
}

/**
 * [admission] is the SDK's admission of [entry] from the observation it was read from, null without a session.
 * The [playing] recording offers Continue watching in place of Watch, Resume or Play.
 */
@OptIn(ExperimentalTvheadendApi::class)
fun recordingContextActions(
    entry: DvrEntry,
    admission: RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
    playing: Boolean = false,
    canWatchChannel: Boolean = false,
    ruleAvailable: Boolean = false,
): List<BrowseContextAction> = buildList {
    if (playing) add(BrowseContextAction.CONTINUE_WATCHING)
    if (recordingPlaybackOffered(admission)) {
        val resumable = offeredResumePosition(admission) != null
        when {
            playing -> if (resumable) add(BrowseContextAction.START_OVER)
            admission is RecordingPlaybackAdmission.Growing -> {
                add(BrowseContextAction.WATCH)
                if (resumable) add(BrowseContextAction.START_OVER)
            }
            resumable -> {
                add(BrowseContextAction.RESUME)
                add(BrowseContextAction.START_OVER)
            }
            else -> add(BrowseContextAction.PLAY)
        }
    }
    if (canWatchChannel && recordingIsRunning(entry)) add(BrowseContextAction.WATCH_LIVE)
    add(BrowseContextAction.DETAILS)
    val ruleActions = recordingRuleActions(entry, canModifyRecordings, ruleAvailable)
    recordingWatchStateAction(entry, admission, canModifyRecordings)?.let(::add)
    if (canModifyRecordings) when (entry.state) {
        DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR -> add(BrowseContextAction.STOP)
        DvrEntryState.SCHEDULED -> if (entry.isRuleRecording) {
            addAll(ruleActions.filter { it != BrowseContextAction.EDIT_RULE })
        } else add(BrowseContextAction.CANCEL)
        DvrEntryState.COMPLETED, DvrEntryState.MISSED, DvrEntryState.INVALID,
        DvrEntryState.COMPLETED_ERROR -> add(
            if (deleteRemovesEntryOnly(entry)) BrowseContextAction.REMOVE_ENTRY else BrowseContextAction.DELETE)
        else -> Unit
    }
    if (BrowseContextAction.EDIT_RULE in ruleActions) add(BrowseContextAction.EDIT_RULE)
}

val DvrEntry.isRuleRecording: Boolean get() = autorecRuleId != null || timerecRuleId != null

fun recordingRuleActions(
    entry: DvrEntry?,
    canModifyRecordings: Boolean,
    ruleAvailable: Boolean,
): List<BrowseContextAction> = buildList {
    if (!canModifyRecordings || entry?.isRuleRecording != true || entry.state !in listOf(
            DvrEntryState.SCHEDULED, DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) return@buildList
    if (entry.state == DvrEntryState.SCHEDULED) {
        add(if (entry.enabled == false) BrowseContextAction.UNSKIP else BrowseContextAction.SKIP)
    }
    if (ruleAvailable) add(BrowseContextAction.EDIT_RULE)
}

fun SessionObservation.recordingRuleAvailable(entry: DvrEntry): Boolean {
    val snapshot = (dvrState as? DvrRepositoryState.Current)?.snapshot ?: return false
    return snapshot.autorecRules.any { it.id == entry.autorecRuleId } ||
        snapshot.timerecRules.any { it.id == entry.timerecRuleId }
}

/** Missing automatic-rule metadata still truthfully identifies the entry as automatically recorded. */
fun SessionObservation.recordingRuleKind(entry: DvrEntry): AutomaticRecordingKind? = when {
    entry.autorecRuleId != null -> dvrSnapshotForDisplay?.autorecRules
        ?.firstOrNull { it.id == entry.autorecRuleId }?.let(::autorecKind) ?: AutomaticRecordingKind.TITLE
    entry.timerecRuleId != null -> AutomaticRecordingKind.TIMER
    else -> null
}

/**
 * True when deleting [entry] only removes its row from the server's list: a missed, invalid or
 * failed entry that never produced a file. An entry with a file, also a partial one, deletes the
 * recording. A completed entry always has one, whatever its file list currently says.
 */
fun deleteRemovesEntryOnly(entry: DvrEntry): Boolean =
    entry.state in setOf(DvrEntryState.MISSED, DvrEntryState.INVALID, DvrEntryState.COMPLETED_ERROR) &&
        entry.files.isNullOrEmpty()

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
