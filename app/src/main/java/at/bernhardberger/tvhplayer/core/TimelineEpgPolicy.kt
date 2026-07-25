package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class EpgFocusColumn(
    val channelId: Int,
    val events: List<EpgEventEntry>,
)

data class EpgFocusTarget(
    val channelIndex: Int,
    val eventId: Int,
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

fun moveTimelineEpgFocus(
    rows: List<EpgFocusColumn>,
    current: EpgFocusTarget,
    direction: EpgFocusDirection,
    visibleChannelRange: IntRange = rows.indices,
): TimelineEpgFocusMove {
    val row = rows.getOrNull(current.channelIndex)
        ?: return TimelineEpgFocusMove(current)
    val events = row.events.sortedBy { it.start }
    val currentIndex = events.indexOfFirst { it.eventId == current.eventId }
    val currentEvent = events.getOrNull(currentIndex)
        ?: return TimelineEpgFocusMove(current)

    if (direction == EpgFocusDirection.LEFT || direction == EpgFocusDirection.RIGHT) {
        val eventIndex = currentIndex + if (direction == EpgFocusDirection.LEFT) -1 else 1
        val event = events.getOrNull(eventIndex) ?: return TimelineEpgFocusMove(
            target = current,
            extendTimeFrontier = direction == EpgFocusDirection.RIGHT,
        )
        return TimelineEpgFocusMove(current.copy(eventId = event.eventId))
    }

    val channelIndex = current.channelIndex +
        if (direction == EpgFocusDirection.UP) -1 else 1
    if (channelIndex < 0) {
        return TimelineEpgFocusMove(current, focusHeader = true)
    }
    val adjacent = rows.getOrNull(channelIndex) ?: return TimelineEpgFocusMove(current)
    val best = adjacent.events.maxWithOrNull(
        compareBy<EpgEventEntry>(
            { overlapSeconds(currentEvent, it) },
            { -abs(midpoint(currentEvent) - midpoint(it)) },
        )
    ) ?: return TimelineEpgFocusMove(current)

    return TimelineEpgFocusMove(
        target = EpgFocusTarget(channelIndex, best.eventId),
        pageChannels = channelIndex !in visibleChannelRange,
    )
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
    max(0L, min(left.stop, right.stop) - max(left.start, right.start))

private fun midpoint(event: EpgEventEntry): Long = event.start + (event.stop - event.start) / 2

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
}

fun epgColumnDataState(
    cachedEvents: List<EpgEventEntry>,
    visibleEvents: List<EpgEventEntry>,
    windowStartSec: Long,
    windowEndSec: Long,
    connectionState: ConnectionUiState,
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
    visibleEvents.isEmpty() && cachedEvents.isEmpty() -> EpgColumnDataState.NO_DATA
    visibleEvents.isEmpty() -> EpgColumnDataState.EMPTY_DAY
    visibleEvents.minOf { it.start } > windowStartSec ||
        visibleEvents.maxOf { it.stop } < windowEndSec ->
        EpgColumnDataState.PARTIAL
    else -> EpgColumnDataState.READY
}
