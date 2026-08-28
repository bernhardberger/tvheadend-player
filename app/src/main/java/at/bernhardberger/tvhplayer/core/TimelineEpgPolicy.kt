package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Instant

data class EpgFocusColumn(
    val channelId: ChannelId,
    val events: List<EpgEventEntry>,
)

data class EpgFocusTarget(
    val channelIndex: Int,
    val eventId: EventId,
)

enum class EpgFocusDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

data class TimelineEpgFocusMove(
    val target: EpgFocusTarget,
    val focusHeader: Boolean = false,
    val pageChannels: Boolean = false,
    val extendTimeFrontier: Boolean = false,
)

data class TimelineEventSpan(
    val startFraction: Float,
    val endFraction: Float,
)

fun initialTimelineEpgFocus(
    rows: List<EpgFocusColumn>,
    preferredChannelIndex: Int,
    targetSec: Long,
    preferredEventId: EventId? = null,
): EpgFocusTarget? {
    if (rows.isEmpty()) return null
    val preferredIndex = preferredChannelIndex.coerceIn(rows.indices)
    val candidateIndices = buildList {
        add(preferredIndex)
        for (distance in 1..rows.size) {
            (preferredIndex + distance).takeIf { it in rows.indices }?.let(::add)
            (preferredIndex - distance).takeIf { it in rows.indices }?.let(::add)
        }
    }
    candidateIndices.forEach { channelIndex ->
        val events = rows[channelIndex].events.sortedBy { it.start }
        if (events.isEmpty()) return@forEach
        val event = preferredEventId?.let { id -> events.firstOrNull { it.id == id } }
            ?: events.firstOrNull { it.start.epochSeconds <= targetSec && targetSec < it.stop.epochSeconds }
            ?: events.minByOrNull { abs(it.start.epochSeconds - targetSec) }
        if (event != null) return EpgFocusTarget(channelIndex, event.id)
    }
    return null
}

fun reconcileTimelineEpgFocus(
    rows: List<EpgFocusColumn>,
    current: EpgFocusTarget?,
    preferredChannelIndex: Int,
    targetSec: Long,
): EpgFocusTarget? {
    current?.let { target ->
        rows.forEachIndexed { channelIndex, row ->
            if (row.events.any { it.id == target.eventId }) {
                return EpgFocusTarget(channelIndex, target.eventId)
            }
        }
    }
    return initialTimelineEpgFocus(
        rows = rows,
        preferredChannelIndex = preferredChannelIndex,
        targetSec = targetSec,
    )
}

fun moveTimelineEpgFocus(
    rows: List<EpgFocusColumn>,
    current: EpgFocusTarget,
    direction: EpgFocusDirection,
    visibleChannelRange: IntRange = rows.indices,
): TimelineEpgFocusMove {
    val row = rows.getOrNull(current.channelIndex)
        ?: return TimelineEpgFocusMove(current)
    val events = row.events.sortedBy { it.start }
    val currentIndex = events.indexOfFirst { it.id == current.eventId }
    val currentEvent = events.getOrNull(currentIndex)
        ?: return TimelineEpgFocusMove(current)

    if (direction == EpgFocusDirection.LEFT || direction == EpgFocusDirection.RIGHT) {
        val eventIndex = currentIndex + if (direction == EpgFocusDirection.LEFT) -1 else 1
        val event = events.getOrNull(eventIndex) ?: return TimelineEpgFocusMove(
            target = current,
            extendTimeFrontier = direction == EpgFocusDirection.RIGHT,
        )
        return TimelineEpgFocusMove(current.copy(eventId = event.id))
    }

    val step = if (direction == EpgFocusDirection.UP) -1 else 1
    val channelIndex = generateSequence(current.channelIndex + step) { it + step }
        .takeWhile { it in rows.indices }
        .firstOrNull { rows[it].events.isNotEmpty() }
    if (channelIndex == null && direction == EpgFocusDirection.UP) {
        return TimelineEpgFocusMove(current, focusHeader = true)
    }
    channelIndex ?: return TimelineEpgFocusMove(current)
    val adjacent = rows[channelIndex]
    val best = adjacent.events.maxWithOrNull(
        compareBy<EpgEventEntry>(
            { overlapSeconds(currentEvent, it) },
            { -abs(midpoint(currentEvent) - midpoint(it)) },
        )
    ) ?: return TimelineEpgFocusMove(current)

    return TimelineEpgFocusMove(
        target = EpgFocusTarget(channelIndex, best.id),
        pageChannels = channelIndex !in visibleChannelRange,
    )
}

fun timelinePageFocusTarget(
    rows: List<EpgFocusColumn>,
    current: EpgFocusTarget,
    preferredChannelIndex: Int,
    direction: Int,
): EpgFocusTarget? {
    val currentEvent = rows.getOrNull(current.channelIndex)
        ?.events
        ?.firstOrNull { it.id == current.eventId }
        ?: return null
    val step = if (direction < 0) -1 else 1
    val startIndex = preferredChannelIndex.coerceIn(rows.indices)
    val targetIndex = generateSequence(startIndex) { it + step }
        .takeWhile { it in rows.indices }
        .firstOrNull { rows[it].events.isNotEmpty() }
        ?: return null
    val targetEvent = rows[targetIndex].events.maxWithOrNull(
        compareBy<EpgEventEntry>(
            { overlapSeconds(currentEvent, it) },
            { -abs(midpoint(currentEvent) - midpoint(it)) },
        ),
    ) ?: return null
    return EpgFocusTarget(targetIndex, targetEvent.id)
}

fun timelineEventSpan(
    eventStartSec: Long,
    eventEndSec: Long,
    windowStartSec: Long,
    windowEndSec: Long,
): TimelineEventSpan? {
    val windowDuration = windowEndSec - windowStartSec
    if (windowDuration <= 0L || eventEndSec <= windowStartSec || eventStartSec >= windowEndSec) {
        return null
    }
    val visibleStart = max(eventStartSec, windowStartSec)
    val visibleEnd = min(eventEndSec, windowEndSec)
    return TimelineEventSpan(
        startFraction = (visibleStart - windowStartSec).toFloat() / windowDuration,
        endFraction = (visibleEnd - windowStartSec).toFloat() / windowDuration,
    )
}

private fun overlapSeconds(left: EpgEventEntry, right: EpgEventEntry): Long =
    max(
        0L,
        min(left.stop.epochSeconds, right.stop.epochSeconds) -
            max(left.start.epochSeconds, right.start.epochSeconds),
    )

private fun midpoint(event: EpgEventEntry): Long =
    event.start.epochSeconds + (event.stop.epochSeconds - event.start.epochSeconds) / 2

fun epgFrontierSettled(coverage: EpgCoverage?, requestedThrough: Instant): Boolean =
    coverage?.knownTo?.let { it >= requestedThrough } == true

fun EpgRepositoryState.currentEpgSnapshot(): EpgSnapshot? =
    (this as? EpgRepositoryState.Current)?.snapshot

enum class EpgColumnDataState {
    READY,
    LOADING,
    NO_DATA,
    EMPTY_DAY,
    PARTIAL,
    STALE,
    PERMISSION_DENIED,
    RECONNECTING,
    SERVER_FAILURE,
    FILTER_EMPTY,
}

fun epgColumnDataState(
    cachedEvents: List<EpgEventEntry>,
    visibleEvents: List<EpgEventEntry>,
    windowStartSec: Long,
    windowEndSec: Long,
    connectionState: ConnectionUiState,
    filterActive: Boolean = false,
    matchingCachedEvents: List<EpgEventEntry> = cachedEvents,
): EpgColumnDataState = when {
    connectionState is ConnectionUiState.Error &&
        connectionState.kind == ConnectionFailureKind.PERMISSION_DENIED ->
        EpgColumnDataState.PERMISSION_DENIED
    connectionState is ConnectionUiState.Error ||
        connectionState is ConnectionUiState.SubscriptionError ->
        EpgColumnDataState.SERVER_FAILURE
    connectionState == ConnectionUiState.Reconnecting && cachedEvents.isNotEmpty() ->
        EpgColumnDataState.STALE
    connectionState == ConnectionUiState.Reconnecting -> EpgColumnDataState.RECONNECTING
    connectionState == ConnectionUiState.Connecting ||
        connectionState == ConnectionUiState.SyncingChannels ->
        if (cachedEvents.isEmpty()) EpgColumnDataState.LOADING else EpgColumnDataState.STALE
    filterActive && cachedEvents.isNotEmpty() && matchingCachedEvents.isEmpty() ->
        EpgColumnDataState.FILTER_EMPTY
    visibleEvents.isEmpty() && cachedEvents.isEmpty() -> EpgColumnDataState.NO_DATA
    visibleEvents.isEmpty() -> EpgColumnDataState.EMPTY_DAY
    visibleEvents.minOf { it.start.epochSeconds } > windowStartSec ||
        visibleEvents.maxOf { it.stop.epochSeconds } < windowEndSec ->
        EpgColumnDataState.PARTIAL
    else -> EpgColumnDataState.READY
}
