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

data class EpgFocusMove(
    val target: EpgFocusTarget,
    val focusDayStrip: Boolean = false,
    val extendTimeFrontier: Boolean = false,
    val pageColumns: Boolean = false,
)

fun moveMagazineEpgFocus(
    columns: List<EpgFocusColumn>,
    current: EpgFocusTarget,
    direction: EpgFocusDirection,
    visibleColumnRange: IntRange = columns.indices,
): EpgFocusMove {
    val column = columns.getOrNull(current.channelIndex)
        ?: return EpgFocusMove(current)
    val sorted = column.events.sortedBy { it.start }
    val currentIndex = sorted.indexOfFirst { it.eventId == current.eventId }
    val currentEvent = sorted.getOrNull(currentIndex) ?: return EpgFocusMove(current)

    if (direction == EpgFocusDirection.UP || direction == EpgFocusDirection.DOWN) {
        val eventIndex = currentIndex + if (direction == EpgFocusDirection.UP) -1 else 1
        val event = sorted.getOrNull(eventIndex)
        return when {
            event != null -> EpgFocusMove(current.copy(eventId = event.eventId))
            direction == EpgFocusDirection.UP ->
                EpgFocusMove(current, focusDayStrip = true)
            else -> EpgFocusMove(current, extendTimeFrontier = true)
        }
    }

    val channelIndex = current.channelIndex +
        if (direction == EpgFocusDirection.LEFT) -1 else 1
    val adjacent = columns.getOrNull(channelIndex) ?: return EpgFocusMove(current)
    val best = adjacent.events.maxWithOrNull(
        compareBy<EpgEventEntry>(
            { overlapSeconds(currentEvent, it) },
            { -abs(midpoint(currentEvent) - midpoint(it)) },
        )
    ) ?: return EpgFocusMove(current)

    return EpgFocusMove(
        target = EpgFocusTarget(channelIndex, best.eventId),
        pageColumns = channelIndex !in visibleColumnRange,
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
