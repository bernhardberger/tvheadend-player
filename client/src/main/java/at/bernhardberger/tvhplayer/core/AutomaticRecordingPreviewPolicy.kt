package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.AutorecTime
import at.bernhardberger.tvheadend.sdk.core.BroadcastType
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgSearchRequest
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Instant

enum class AutorecEpisodeState { MATCH, SCHEDULED, DUPLICATE, SKIPPED, LEFT_OUT, REMOVED }

private val textMatchFields = setOf(
    RecordingDraftField.WORDS, RecordingDraftField.SEARCH_IN, RecordingDraftField.MERGE_TEXT,
)

/** An app projection of either guide data or a real recording, never a fabricated guide event. */
data class AutorecPreviewRow(
    val eventId: EventId?,
    val recordingId: DvrEntryId?,
    val title: String?,
    val subtitle: String?,
    val channelId: ChannelId?,
    val start: Instant,
    val stop: Instant?,
    val state: AutorecEpisodeState,
)

sealed interface AutorecPreviewState {
    data object Loading : AutorecPreviewState
    data object NothingToSearch : AutorecPreviewState
    data class Failed(val failure: TvheadendFailure) : AutorecPreviewState
    data class Matches(
        val rows: List<AutorecPreviewRow>,
        val willRecordCount: Int,
        val broadcastFilterUnavailable: Boolean,
    ) : AutorecPreviewState {
        val matchCount: Int get() = rows.size
    }
}

/**
 * Series on one channel uses an empty merged-text regex: epg.c:2463-2475 also admits titleless events.
 * All-channel series uses a literal title/name query to avoid a whole-guide search; it can miss renamed
 * or titleless episodes. Both are limited to the server's visible guide horizon and permissions.
 * Existing rules need no search unless matching changed; an off draft cannot create replacement entries.
 */
fun AutorecDraft.previewSearchRequest(baseline: AutorecDraft? = null): EpgSearchRequest? {
    if (!enabled || (baseline != null && !matchingChanged(changedFields(baseline)))) {
        return null
    }
    val channelId = (channel as? RecordingRuleChannel.SpecificChannel)?.channelId
    return when {
        kind == AutomaticRecordingKind.SERIES && channelId != null ->
            EpgSearchRequest.create("", mergeText = true, channelId = channelId)
        kind == AutomaticRecordingKind.SERIES -> {
            val title = pattern.words.takeUnless(String::isBlank) ?: name.takeUnless(String::isBlank)
            title?.let { EpgSearchRequest.create(RecordingPattern(it).stored(AutomaticRecordingKind.KEYWORD)) }
        }
        pattern.words.isBlank() -> null
        else -> EpgSearchRequest.create(pattern.stored(kind), fullText, mergeText, channelId)
    }
}

/**
 * The caller's zone is assumed to equal the server's. Upcoming own entries are authoritative even
 * outside guide results. Unchanged matching uses only those entries; a matching edit previews their
 * replacement after saving (dvr_autorec.c:596-603,1610-1634), including removals and new guide matches.
 * HTSP exposes isNew but not is_repeat (htsp_server.c:1446-1447), so policies other than ALL/NEW
 * cannot reproduce dvr_autorec.c:230-236 and are flagged rather than silently guessed.
 */
fun AutorecDraft.previewMatches(
    events: List<EpgEvent>,
    entries: List<DvrEntry>,
    ruleId: AutorecRuleId?,
    now: Instant,
    zone: ZoneId,
    baseline: AutorecDraft? = null,
): AutorecPreviewState.Matches {
    require(ruleId == null || baseline != null) { "An existing rule preview requires its baseline draft" }
    val changed = baseline?.let(::changedFields).orEmpty()
    val rematching = ruleId == null || matchingChanged(changed)
    val matches = if (rematching) events.distinctBy { it.id }.filter { matchesPreviewEvent(it, now, zone) }
        else emptyList()
    val matchingIds = matches.map { it.id }.toSet()
    val own = entries.filter {
        ruleId != null && it.autorecRuleId == ruleId && it.start?.let { time -> time > now } == true
    }
    val ownEvents = own.mapNotNull { it.eventId }.toSet()
    val guideRows = matches.asSequence()
        .filter { it.id !in ownEvents }
        .map { event ->
            AutorecPreviewRow(event.id, null, event.title, event.subtitle, event.channelId, event.start, event.stop,
                if (event.id in excludedEventIds) AutorecEpisodeState.LEFT_OUT else AutorecEpisodeState.MATCH)
        }
    val entryRows = own.map { entry ->
        AutorecPreviewRow(entry.eventId, entry.id, entry.title, entry.subtitle, entry.channelId,
            requireNotNull(entry.start), entry.stop,
            if (entryWillBeRemoved(entry, matchingIds, changed, events, zone)) AutorecEpisodeState.REMOVED
            else entry.previewState())
    }
    val rows = (guideRows.toList() + entryRows).sortedBy { it.start }
    val willRecord = rows.count { it.state == AutorecEpisodeState.MATCH || it.state == AutorecEpisodeState.SCHEDULED }
    return AutorecPreviewState.Matches(rows, willRecord, broadcastType !in setOf(BroadcastType.ALL, BroadcastType.NEW))
}

private fun AutorecDraft.entryWillBeRemoved(
    entry: DvrEntry,
    matchingIds: Set<EventId>,
    changed: Set<RecordingDraftField>,
    events: List<EpgEvent>,
    zone: ZoneId,
): Boolean {
    // Every real update rebuilds scheduled spawns from guide events (dvr_autorec.c:1610-1631).
    val eventlessSpawn = entry.eventId == null && entry.state == DvrEntryState.SCHEDULED
    val knownNotNew = broadcastType == BroadcastType.NEW &&
        events.any { it.id == entry.eventId && it.isNew != true }
    val criteriaExclude = !enabled || !matchesChannelAndTime(entry.channelId, requireNotNull(entry.start), zone) ||
        (textMatchingChanged(changed) && entry.eventId?.let { it !in matchingIds } == true)
    return changed.isNotEmpty() && (eventlessSpawn || knownNotNew || (matchingChanged(changed) && criteriaExclude))
}

private fun AutorecDraft.matchingChanged(changed: Set<RecordingDraftField>): Boolean =
    changed.any {
        it in autorecMatchAffectingFields && (kind != AutomaticRecordingKind.SERIES || it !in textMatchFields)
    }

// A series link bypasses the server's text checks (dvr_autorec.c:356-358).
private fun AutorecDraft.textMatchingChanged(changed: Set<RecordingDraftField>): Boolean =
    kind != AutomaticRecordingKind.SERIES && changed.any { it in textMatchFields }

private fun DvrEntry.previewState(): AutorecEpisodeState = when {
    enabled == false -> AutorecEpisodeState.SKIPPED
    duplicate == true -> AutorecEpisodeState.DUPLICATE
    else -> AutorecEpisodeState.SCHEDULED
}

private fun AutorecDraft.matchesPreviewEvent(event: EpgEvent, now: Instant, zone: ZoneId): Boolean {
    return enabled && event.start > now && matchesChannelAndTime(event.channelId, event.start, zone) &&
        (kind != AutomaticRecordingKind.SERIES || seriesLinkUri == event.seriesLinkUri) &&
        (broadcastType != BroadcastType.NEW || event.isNew == true)
}

private fun AutorecDraft.matchesChannelAndTime(channelId: ChannelId?, start: Instant, zone: ZoneId): Boolean {
    val local = java.time.Instant.ofEpochSecond(start.epochSeconds).atZone(zone)
    val channelMatches = channel == RecordingRuleChannel.AllChannels ||
        (channel as? RecordingRuleChannel.SpecificChannel)?.channelId == channelId
    return channelMatches && local.dayOfWeek in days && startWindow.containsMinute(local.toLocalTime())
}

/**
 * Minute comparison mirrors retained tm_sec at dvr_autorec.c:287-290; DST-edge mktime quirks are not modelled.
 */
private fun RecordingStartWindow.containsMinute(time: LocalTime): Boolean {
    val (start, end) = bounds()
    val from = (start as? AutorecTime.At)?.time
    val until = (end as? AutorecTime.At)?.time
    if (from == null || until == null) return true
    val minute = time.withSecond(0).withNano(0)
    return if (from <= until) minute >= from && minute <= until else minute >= from || minute <= until
}

data class TimerOccurrence(val start: Instant, val stop: Instant)

/** Calendar-day arithmetic in the caller's server-equivalent zone, including DST gaps/overlaps. */
fun TimerDraft.nextOccurrences(now: Instant, zone: ZoneId, count: Int): List<TimerOccurrence> {
    require(count >= 0)
    if (start == end) return emptyList()
    val firstDate = java.time.Instant.ofEpochSecond(now.epochSeconds).atZone(zone).toLocalDate()
    val dates = if (repeating) {
        if (days.isEmpty()) emptySequence()
        else generateSequence(firstDate) { it.plusDays(1) }.filter { it.dayOfWeek in days }
    } else sequenceOf(date)
    return dates.map { resolveOccurrenceOn(it, zone) }.filter { it.start > now && it.stop > it.start }
        .take(count).toList()
}
