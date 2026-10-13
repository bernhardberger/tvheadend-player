package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent

enum class DetailsStatus { ON_NOW, RECORDING, SCHEDULED, FAILED }

fun detailsStatus(event: EpgEvent?, recording: DvrEntry?, nowSec: Long): DetailsStatus? =
    when (recording?.state) {
        DvrEntryState.RECORDING -> DetailsStatus.RECORDING
        DvrEntryState.SCHEDULED -> DetailsStatus.SCHEDULED
        DvrEntryState.MISSED, DvrEntryState.INVALID, DvrEntryState.RECORDING_ERROR,
        DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING -> DetailsStatus.FAILED
        else -> DetailsStatus.ON_NOW.takeIf {
            event != null && nowSec >= event.start.epochSeconds && nowSec < event.stop.epochSeconds
        }
    }

fun programmeDetailsActions(
    event: EpgEvent,
    nowSec: Long,
    recording: DvrEntry?,
    canModifyRecordings: Boolean,
    canWatchChannel: Boolean,
    fromGuide: Boolean,
    liveProgramme: Boolean = true,
): List<BrowseContextAction> = buildList {
    if (canWatchChannel) add(BrowseContextAction.WATCH)
    programmeActions(event, nowSec, recording, canModifyRecordings = canModifyRecordings).forEach {
        when (it) {
            ProgrammeAction.WATCH -> Unit
            ProgrammeAction.RECORD -> if (liveProgramme) add(BrowseContextAction.RECORD)
            ProgrammeAction.STOP_RECORDING -> add(BrowseContextAction.STOP)
            ProgrammeAction.CANCEL_RECORDING -> add(BrowseContextAction.CANCEL)
            ProgrammeAction.WATCH_FROM_START -> if (recording != null) add(BrowseContextAction.PLAY)
        }
    }
    if (!fromGuide && canWatchChannel) add(BrowseContextAction.SHOW_GUIDE)
}

fun recordingDetailsActions(
    recording: DvrEntry,
    playbackEligible: Boolean,
    canModifyRecordings: Boolean,
    canWatchChannel: Boolean,
): List<BrowseContextAction> = buildList {
    if (recording.state == DvrEntryState.SCHEDULED && canWatchChannel) add(BrowseContextAction.WATCH)
    addAll(recordingContextActions(recording, playbackEligible, canModifyRecordings).filter {
        it != BrowseContextAction.DETAILS &&
            (it != BrowseContextAction.DELETE || recording.state != DvrEntryState.COMPLETED)
    })
}

enum class DetailsFactKind { STARTS, STARTED, RECORDED, LENGTH, LEFT }
data class DetailsFact(val kind: DetailsFactKind, val seconds: Long)

fun programmeDetailsFacts(event: EpgEvent, nowSec: Long): List<DetailsFact> = buildList {
    add(DetailsFact(if (nowSec >= event.stop.epochSeconds) DetailsFactKind.STARTED else DetailsFactKind.STARTS,
        event.start.epochSeconds))
    add(DetailsFact(DetailsFactKind.LENGTH, (event.stop - event.start).inWholeSeconds.coerceAtLeast(0)))
    if (nowSec >= event.start.epochSeconds && nowSec < event.stop.epochSeconds) {
        add(DetailsFact(DetailsFactKind.LEFT, event.stop.epochSeconds - nowSec))
    }
}

fun recordingDetailsFacts(recording: DvrEntry): List<DetailsFact> = buildList {
    recording.start?.let { add(DetailsFact(DetailsFactKind.RECORDED, it.epochSeconds)) }
    val length = recording.start?.let { start -> recording.stop?.let { (it - start).inWholeSeconds } }
        ?.takeIf { it > 0 } ?: return@buildList
    add(DetailsFact(DetailsFactKind.LENGTH, length))
    recording.playPosition?.inWholeSeconds?.takeIf { it > 0 && it < length }?.let {
        add(DetailsFact(DetailsFactKind.LEFT, length - it))
    }
}
