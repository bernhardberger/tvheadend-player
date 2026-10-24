package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission

enum class DetailsStatus { ON_NOW, RECORDING, RECORDING_ERRORS, SCHEDULED, MISSED, FAILED }

fun detailsStatus(event: EpgEvent?, recording: DvrEntry?, nowSec: Long): DetailsStatus? =
    when (recording?.state) {
        DvrEntryState.RECORDING -> DetailsStatus.RECORDING
        DvrEntryState.SCHEDULED -> DetailsStatus.SCHEDULED
        // Tvheadend is still recording; the errors so far do not make it a failure yet.
        DvrEntryState.RECORDING_ERROR -> DetailsStatus.RECORDING_ERRORS
        DvrEntryState.MISSED -> DetailsStatus.MISSED
        DvrEntryState.INVALID,
        DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING -> DetailsStatus.FAILED
        else -> DetailsStatus.ON_NOW.takeIf {
            event != null && nowSec >= event.start.epochSeconds && nowSec < event.stop.epochSeconds
        }
    }

fun programmeDetailsActions(
    event: EpgEvent,
    nowSec: Long,
    recording: DvrEntry?,
    recordingAdmission: RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
    canWatchChannel: Boolean,
    fromGuide: Boolean,
    liveProgramme: Boolean = true,
    stacked: Boolean = false,
): List<BrowseContextAction> = buildList {
    if (canWatchChannel) add(BrowseContextAction.WATCH)
    programmeActions(event, nowSec, recording, canModifyRecordings = canModifyRecordings,
        recordingAdmission = recordingAdmission).forEach {
        when (it) {
            ProgrammeAction.WATCH -> Unit
            ProgrammeAction.RECORD -> if (liveProgramme) add(BrowseContextAction.RECORD)
            ProgrammeAction.STOP_RECORDING -> add(BrowseContextAction.STOP)
            ProgrammeAction.CANCEL_RECORDING -> add(BrowseContextAction.CANCEL)
            ProgrammeAction.WATCH_FROM_START -> if (recording != null) add(BrowseContextAction.PLAY)
        }
    }
    if ((!fromGuide || stacked) && canWatchChannel) add(BrowseContextAction.SHOW_GUIDE)
}

fun recordingDetailsActions(
    recording: DvrEntry,
    admission: RecordingPlaybackAdmission?,
    canModifyRecordings: Boolean,
    canWatchChannel: Boolean,
): List<BrowseContextAction> = buildList {
    if (recording.state == DvrEntryState.SCHEDULED && canWatchChannel) add(BrowseContextAction.WATCH)
    addAll(recordingContextActions(recording, admission, canModifyRecordings).filter {
        it != BrowseContextAction.DETAILS
    })
}

/**
 * Other unended airings of [event] on any channel: series link URI,
 * otherwise the same title (trimmed, case-insensitive). Simulcasts remain separate, at most [limit].
 */
fun otherAirings(event: EpgEvent, events: List<EpgEvent>, nowSec: Long, limit: Int = 20,
    channelNumber: (ChannelId) -> Long? = { null }): List<EpgEvent> {
    val seriesLink = event.seriesLinkUri?.takeIf(String::isNotBlank)
    val title = event.title?.trim()?.takeIf(String::isNotEmpty)
    val matches: (EpgEvent) -> Boolean = when {
        seriesLink != null -> { other -> other.seriesLinkUri == seriesLink }
        title != null -> { other -> other.title?.trim().equals(title, ignoreCase = true) }
        else -> return emptyList()
    }
    return events.filter { it.id != event.id && it.stop.epochSeconds > nowSec && matches(it) }
        .sortedWith(compareBy<EpgEvent> { it.start }
            .thenBy { it.channelId?.let(channelNumber) ?: Long.MAX_VALUE })
        .take(limit)
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

/**
 * [admission] is the SDK's admission of [recording]; the time left follows the Resume it offers.
 * The length is what was recorded ([recordingLengthSeconds]), not the scheduled length.
 */
fun recordingDetailsFacts(recording: DvrEntry, admission: RecordingPlaybackAdmission?, nowSec: Long): List<DetailsFact> = buildList {
    recording.start?.let { add(DetailsFact(DetailsFactKind.RECORDED, it.epochSeconds)) }
    val length = recordingLengthSeconds(recording, nowSec)?.takeIf { it > 0 } ?: return@buildList
    add(DetailsFact(DetailsFactKind.LENGTH, length))
    offeredResumePosition(admission)?.inWholeSeconds?.takeIf { it > 0 && it < length }?.let {
        add(DetailsFact(DetailsFactKind.LEFT, length - it))
    }
}
