package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

enum class RecordingEndKind { CHAIN_PART, NEXT_EPISODE, OTHER_EPISODE }

sealed interface RecordingEndStart {
    data object FromBeginning : RecordingEndStart
    data class Resume(val position: Duration) : RecordingEndStart
    data class AtTime(val at: Instant) : RecordingEndStart
}

sealed interface RecordingEndOffer {
    data class Recording(val entry: DvrEntry, val kind: RecordingEndKind, val start: RecordingEndStart) : RecordingEndOffer {
        /** Display hint from this offer's file metadata; null means there is no overlap time to show. */
        val displayContinuationTime: Instant?
            get() {
                val at = (start as? RecordingEndStart.AtTime)?.at ?: return null
                val fileStart = recordingEndFileStart(entry) ?: return null
                return at.takeIf { it > fileStart }
            }
    }
    data class WatchLive(val channelId: ChannelId, val event: EpgEvent?) : RecordingEndOffer
}

/**
 * Ordered alternatives at [endedAt], the broadcast time reached in [ended] (not the wall clock).
 * Measure the chain gap from the stop of [ended]'s last file, or its padded stop if unavailable.
 * A still-running [ended] uses at least [endedAt] for the gap. Otherwise [endedAt] determines
 * where to continue, or proves overlap when a finished recording has no known end.
 * Use one session observation for the entries, EPG and [admission]. Play must revalidate its target.
 * Only [RecordingEndOffer.Recording] can be an automatic target; a corner offer uses only chain
 * parts or the next episode and can recalculate at the actual broadcast time when accepted.
 */
fun recordingEndOffers(
    ended: DvrEntry,
    endedAt: Instant?,
    recordings: List<DvrEntry>,
    epgEvent: (EventId) -> EpgEvent?,
    admission: (DvrEntry) -> RecordingPlaybackAdmission?,
    liveEvent: EpgEvent?,
    now: Instant,
): List<RecordingEndOffer> {
    val channel = ended.channelId
    val endedIsRunning = recordingIsRunning(ended)
    val lastStop = ended.files?.lastOrNull()?.stop
    val recordingEnd = if (endedIsRunning && endedAt != null) maxOf(lastStop ?: endedAt, endedAt)
        else lastStop ?: recordingPaddedStop(ended)
    val latestStart = recordingEnd?.plus(2.minutes)
    val playable = recordings.mapNotNull { entry ->
        if (entry.id == ended.id || sameRecordingBroadcast(ended, entry, epgEvent)) return@mapNotNull null
        val targetAdmission = admission(entry)
        if (!recordingPlaybackOffered(targetAdmission)) return@mapNotNull null
        entry to targetAdmission
    }
    val chainCandidates = if (channel == null || endedAt == null) emptyList() else playable.mapNotNull { (entry, _) ->
        if (entry.channelId != channel || entry.state == DvrEntryState.MISSED) return@mapNotNull null
        val start = recordingEndFileStart(entry) ?: return@mapNotNull null
        val file = entry.files?.lastOrNull() ?: return@mapNotNull null
        val stop = file.stop ?: (if (recordingIsRunning(entry)) now else recordingPaddedStop(entry))
            ?: return@mapNotNull null
        val startsInTime = latestStart?.let { start <= it } ?: (start < endedAt)
        if (!startsInTime || stop <= maxOf(start, endedAt)) return@mapNotNull null
        start to RecordingEndOffer.Recording(entry, RecordingEndKind.CHAIN_PART,
            RecordingEndStart.AtTime(endedAt))
    }.sortedWith(compareBy({ it.first }, { it.second.entry.id.value }))
    val chain = buildList<RecordingEndOffer.Recording> {
        for ((_, candidate) in chainCandidates) {
            if (none { sameRecordingBroadcast(it.entry, candidate.entry, epgEvent) }) add(candidate)
            if (size == MAX_RECORDING_END_OFFERS) break
        }
    }
    if (chain.size == MAX_RECORDING_END_OFFERS) return chain

    val endedEvent = ended.eventId?.let(epgEvent)
    val rule = ended.autorecRuleId
    val series = endedEvent?.seriesLinkUri?.takeIf(String::isNotBlank)
    val endedAiring = endedEvent?.start ?: ended.start
    val related = if (rule == null && series == null) emptyList() else playable.mapNotNull { (entry, admission) ->
        if (chain.any { it.entry.id == entry.id || sameRecordingBroadcast(it.entry, entry, epgEvent) }) {
            return@mapNotNull null
        }
        val event = entry.eventId?.let(epgEvent)
        if ((rule == null || entry.autorecRuleId != rule) && (series == null || event?.seriesLinkUri != series)) {
            return@mapNotNull null
        }
        val resume = offeredResumePosition(admission)
        if (recordingWatchState(entry, resume, now.epochSeconds) == RecordingWatchState.Watched) return@mapNotNull null
        val airing = event?.start ?: entry.start ?: return@mapNotNull null
        airing to RecordingEndOffer.Recording(entry, RecordingEndKind.OTHER_EPISODE,
            if (resume != null) RecordingEndStart.Resume(resume) else RecordingEndStart.FromBeginning)
    }.sortedWith(compareBy({ it.first }, { it.second.entry.id.value }))
    val next = endedAiring?.let { start -> related.firstOrNull { it.first > start } }?.second
    return buildList<RecordingEndOffer> {
        addAll(chain)
        next?.let { add(it.copy(kind = RecordingEndKind.NEXT_EPISODE)) }
        addAll(related.asReversed().asSequence().map { it.second }.filter { it.entry.id != next?.entry?.id }.take(2))
        if (endedAt != null && !endedIsRunning && channel != null && recordingEnd != null &&
            now - recordingEnd in Duration.ZERO..3.minutes
        ) {
            val onAir = liveEvent?.takeIf { (it.channelId == null || it.channelId == channel) && now >= it.start && now < it.stop }
            if (none { offer ->
                offer is RecordingEndOffer.Recording && with(offer.entry) {
                    if (onAir != null) {
                        eventId == onAir.id || id == onAir.dvrEntryId ||
                            (channelId == channel && start == onAir.start && stop == onAir.stop)
                    } else {
                        val airing = eventId?.let(epgEvent)
                        val airingStart = airing?.start ?: start
                        val airingStop = airing?.stop ?: stop
                        (airing?.channelId ?: channelId) == channel && airingStart != null && airingStop != null &&
                            now >= airingStart && now < airingStop
                    }
                }
            }) add(RecordingEndOffer.WatchLive(channel, onAir))
        }
    }.take(MAX_RECORDING_END_OFFERS)
}

private const val MAX_RECORDING_END_OFFERS = 4

/** Tvheadend opens the last listed file, not a concatenation of all file observations. */
fun recordingEndFileStart(entry: DvrEntry): Instant? {
    val files = entry.files ?: return null
    return files.lastOrNull()?.start ?: if (files.size == 1) recordingPaddedStart(entry) else null
}

private fun sameRecordingBroadcast(first: DvrEntry, second: DvrEntry, epgEvent: (EventId) -> EpgEvent?): Boolean {
    if (first.eventId != null && first.eventId == second.eventId) return true
    val firstAiring = first.eventId?.let(epgEvent)
    val secondAiring = second.eventId?.let(epgEvent)
    if (firstAiring != null && secondAiring != null) return firstAiring.id == secondAiring.id
    return first.channelId != null && first.channelId == second.channelId && first.start != null && first.stop != null &&
        first.start == second.start && first.stop == second.stop
}
