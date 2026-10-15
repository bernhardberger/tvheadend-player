package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.ChannelId
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
    stacked: Boolean = false,
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
    if ((!fromGuide || stacked) && canWatchChannel) add(BrowseContextAction.SHOW_GUIDE)
}

fun recordingDetailsActions(
    recording: DvrEntry,
    playbackEligible: Boolean,
    canModifyRecordings: Boolean,
    canWatchChannel: Boolean,
): List<BrowseContextAction> = buildList {
    if (recording.state == DvrEntryState.SCHEDULED && canWatchChannel) add(BrowseContextAction.WATCH)
    addAll(recordingContextActions(recording, playbackEligible, canModifyRecordings).filter {
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

fun recordingDetailsFacts(recording: DvrEntry): List<DetailsFact> = buildList {
    recording.start?.let { add(DetailsFact(DetailsFactKind.RECORDED, it.epochSeconds)) }
    val length = recording.start?.let { start -> recording.stop?.let { (it - start).inWholeSeconds } }
        ?.takeIf { it > 0 } ?: return@buildList
    add(DetailsFact(DetailsFactKind.LENGTH, length))
    recording.playPosition?.inWholeSeconds?.takeIf { it > 0 && it < length }?.let {
        add(DetailsFact(DetailsFactKind.LEFT, length - it))
    }
}
