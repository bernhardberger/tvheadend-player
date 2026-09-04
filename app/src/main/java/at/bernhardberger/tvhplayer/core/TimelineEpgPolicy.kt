package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgCoveragePolicy
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.RetainedMetadataAuthority
import at.bernhardberger.tvheadend.sdk.core.epgSnapshotAuthority
import at.bernhardberger.tvheadend.sdk.core.epgSnapshotForDisplay
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import java.time.Instant as JavaInstant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal const val GUIDE_VISIBLE_WINDOW_SEC = 3 * 3600L
internal val GUIDE_EPG_COVERAGE_POLICY = EpgCoveragePolicy.create(7.days)

internal data class GuideWindowBounds(
    val earliestStartSec: Long,
    val latestStartSec: Long,
) {
    init {
        require(latestStartSec >= earliestStartSec)
    }

    fun constrain(startSec: Long): Long = startSec.coerceIn(earliestStartSec, latestStartSec)
}

internal fun guideWindowBounds(openedAtSec: Long, zoneId: ZoneId): GuideWindowBounds {
    val earliestStartSec = floorGuideWindowToHour(openedAtSec, zoneId)
    val latestStartSec = floorGuideWindowToHour(
        openedAtSec + GUIDE_EPG_COVERAGE_POLICY.futureHorizon.inWholeSeconds -
            GUIDE_VISIBLE_WINDOW_SEC,
        zoneId,
    )
    return GuideWindowBounds(earliestStartSec, latestStartSec)
}

internal fun moveGuideWindowByDays(
    windowStartSec: Long,
    dayDelta: Int,
    bounds: GuideWindowBounds,
    zoneId: ZoneId,
): Long = bounds.constrain(
    JavaInstant.ofEpochSecond(windowStartSec)
        .atZone(zoneId)
        .plusDays(dayDelta.toLong())
        .toEpochSecond(),
)

internal fun floorGuideWindowToHour(epochSec: Long, zoneId: ZoneId): Long =
    JavaInstant.ofEpochSecond(epochSec)
        .atZone(zoneId)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
        .toEpochSecond()

internal fun shouldWaitForGuideCoverage(
    connectionReady: Boolean,
    hasCurrentSnapshot: Boolean,
    acquisitionPending: Boolean,
    coverageSettled: Boolean,
): Boolean = connectionReady && acquisitionPending && (!hasCurrentSnapshot || !coverageSettled)

internal fun firstUnsettledGuidePageIndex(
    currentChannelIndex: Int,
    targetChannelIndex: Int,
    channelCount: Int,
    pageSize: Int,
    coverageSettled: (Int) -> Boolean,
): Int? {
    require(channelCount > 0)
    require(pageSize > 0)
    require(currentChannelIndex in 0 until channelCount)
    require(targetChannelIndex in 0 until channelCount)
    val currentPageStart = currentChannelIndex / pageSize * pageSize
    val targetPageStart = targetChannelIndex / pageSize * pageSize
    if (currentPageStart == targetPageStart) return null
    val pageIndices = if (targetPageStart > currentPageStart) {
        generateSequence(currentPageStart + pageSize) { it + pageSize }
            .takeWhile { it <= targetPageStart }
    } else {
        generateSequence(currentPageStart - 1) { index ->
            index / pageSize * pageSize - 1
        }.takeWhile { it >= targetPageStart }
    }
    return pageIndices.firstOrNull { !coverageSettled(it) }
}

data class EpgFocusColumn(
    val channelId: ChannelId,
    val events: List<EpgEventEntry>,
)

data class EpgFocusTarget(
    val channelIndex: Int,
    val eventId: EventId,
)

data class TimelineEpgEventIndex(
    val visibleEventsByChannel: Map<ChannelId, List<EpgEventEntry>>,
    val channelsWithEvents: Set<ChannelId>,
    val channelsWithMatchingEvents: Set<ChannelId>,
)

fun indexTimelineEventsByChannel(
    events: List<EpgEventEntry>,
    windowStartSec: Long,
    windowEndSec: Long,
    matches: (EpgEventEntry) -> Boolean = { true },
): TimelineEpgEventIndex {
    require(windowEndSec > windowStartSec)
    val visibleEvents = mutableMapOf<ChannelId, MutableList<EpgEventEntry>>()
    val channelsWithEvents = mutableSetOf<ChannelId>()
    val channelsWithMatchingEvents = mutableSetOf<ChannelId>()
    events.forEach { event ->
        event.channelId?.let { channelId ->
            channelsWithEvents += channelId
            if (matches(event)) {
                channelsWithMatchingEvents += channelId
                if (
                    event.stop.epochSeconds > windowStartSec &&
                    event.start.epochSeconds < windowEndSec
                ) {
                    visibleEvents.getOrPut(channelId) { mutableListOf() }.add(event)
                }
            }
        }
    }
    return TimelineEpgEventIndex(
        visibleEventsByChannel = visibleEvents,
        channelsWithEvents = channelsWithEvents,
        channelsWithMatchingEvents = channelsWithMatchingEvents,
    )
}

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
    val timeFrontierDirection: Int = 0,
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
    searchChannelIds: Set<ChannelId>? = null,
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
        if (searchChannelIds != null && rows[channelIndex].channelId !in searchChannelIds) {
            return@forEach
        }
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
            timeFrontierDirection = if (direction == EpgFocusDirection.LEFT) -1 else 1,
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

fun timelineFrontierFocus(
    rows: List<EpgFocusColumn>,
    channelId: ChannelId,
    originEventId: EventId,
    boundarySec: Long,
    direction: Int,
): EpgFocusTarget? {
    require(direction == -1 || direction == 1)
    val channelIndex = rows.indexOfFirst { it.channelId == channelId }
    if (channelIndex < 0) return null
    val events = rows[channelIndex].events.sortedBy { it.start }
    val originIndex = events.indexOfFirst { it.id == originEventId }
    val event = if (originIndex >= 0) {
        events.getOrNull(originIndex + direction)
    } else if (direction > 0) {
        events.firstOrNull { it.start.epochSeconds >= boundarySec }
    } else {
        events.lastOrNull { it.stop.epochSeconds <= boundarySec }
    }
    return event?.let { EpgFocusTarget(channelIndex, it.id) }
}

fun timelinePageFocusTarget(
    rows: List<EpgFocusColumn>,
    current: EpgFocusTarget,
    preferredChannelIndex: Int,
    direction: Int,
    searchChannelIds: Set<ChannelId>? = null,
): EpgFocusTarget? {
    val currentEvent = rows.getOrNull(current.channelIndex)
        ?.events
        ?.firstOrNull { it.id == current.eventId }
        ?: return null
    val step = if (direction < 0) -1 else 1
    val startIndex = preferredChannelIndex.coerceIn(rows.indices)
    val targetIndex = generateSequence(startIndex) { it + step }
        .takeWhile { it in rows.indices }
        .firstOrNull {
            rows[it].events.isNotEmpty() &&
                (searchChannelIds == null || rows[it].channelId in searchChannelIds)
        }
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

internal fun guideChannelPageCoverageSettled(
    channelIds: List<ChannelId>,
    coverages: List<EpgCoverage>,
    requestedThrough: Instant,
): Boolean = channelIds.all { channelId ->
    epgFrontierSettled(
        coverages.firstOrNull { it.channelId == channelId },
        requestedThrough,
    )
}

internal sealed interface GuideCoverageFocusResolution {
    data object Wait : GuideCoverageFocusResolution
    data object Release : GuideCoverageFocusResolution
    data class Select(val target: EpgFocusTarget) : GuideCoverageFocusResolution
}

internal fun resolveGuideWindowFocus(
    rows: List<EpgFocusColumn>,
    preferredChannelId: ChannelId,
    targetSec: Long,
    requestedChannelIds: Set<ChannelId>,
    coverages: List<EpgCoverage>,
    requestedThrough: Instant,
    connectionReady: Boolean,
    hasCurrentSnapshot: Boolean,
    acquisitionPending: Boolean,
): GuideCoverageFocusResolution {
    val coverageSettled = hasCurrentSnapshot && guideChannelPageCoverageSettled(
        channelIds = requestedChannelIds.toList(),
        coverages = coverages,
        requestedThrough = requestedThrough,
    )
    if (!coverageSettled) {
        return if (
            shouldWaitForGuideCoverage(
                connectionReady = connectionReady,
                hasCurrentSnapshot = hasCurrentSnapshot,
                acquisitionPending = acquisitionPending,
                coverageSettled = false,
            )
        ) {
            GuideCoverageFocusResolution.Wait
        } else {
            GuideCoverageFocusResolution.Release
        }
    }
    val preferredChannelIndex = rows.indexOfFirst { it.channelId == preferredChannelId }
    if (preferredChannelIndex < 0) return GuideCoverageFocusResolution.Release
    val target = initialTimelineEpgFocus(
        rows = rows,
        preferredChannelIndex = preferredChannelIndex,
        targetSec = targetSec,
        searchChannelIds = requestedChannelIds,
    ) ?: return GuideCoverageFocusResolution.Release
    return GuideCoverageFocusResolution.Select(target)
}

internal sealed interface GuideDeferredOriginResolution {
    data object Wait : GuideDeferredOriginResolution
    data object Release : GuideDeferredOriginResolution
    data class Restore(val target: EpgFocusTarget) : GuideDeferredOriginResolution
}

internal fun resolveGuideFrontierOrigin(
    rows: List<EpgFocusColumn>,
    channelId: ChannelId,
    eventId: EventId,
    connectionReady: Boolean,
    hasCurrentSnapshot: Boolean,
    timedOut: Boolean,
): GuideDeferredOriginResolution {
    if (timedOut) return GuideDeferredOriginResolution.Release
    if (!connectionReady || !hasCurrentSnapshot) return GuideDeferredOriginResolution.Wait
    val channelIndex = rows.indexOfFirst { it.channelId == channelId }
    if (channelIndex < 0) return GuideDeferredOriginResolution.Wait
    if (rows[channelIndex].events.none { it.id == eventId }) {
        return GuideDeferredOriginResolution.Wait
    }
    return GuideDeferredOriginResolution.Restore(EpgFocusTarget(channelIndex, eventId))
}

fun EpgRepositoryState.currentEpgSnapshot(): EpgSnapshot? =
    epgSnapshotForDisplay?.takeIf {
        epgSnapshotAuthority == RetainedMetadataAuthority.CURRENT
    }

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
    visibleEvents: List<EpgEventEntry>,
    windowStartSec: Long,
    windowEndSec: Long,
    connectionState: ConnectionUiState,
    filterActive: Boolean = false,
    coveragePending: Boolean = false,
    hasCachedEvents: Boolean = visibleEvents.isNotEmpty(),
    hasMatchingCachedEvents: Boolean = hasCachedEvents,
): EpgColumnDataState = when {
    connectionState is ConnectionUiState.Error &&
        connectionState.kind == ConnectionFailureKind.PERMISSION_DENIED ->
        EpgColumnDataState.PERMISSION_DENIED
    connectionState is ConnectionUiState.Error ||
        connectionState is ConnectionUiState.SubscriptionError ->
        EpgColumnDataState.SERVER_FAILURE
    connectionState == ConnectionUiState.Reconnecting && hasCachedEvents ->
        EpgColumnDataState.STALE
    connectionState == ConnectionUiState.Reconnecting -> EpgColumnDataState.RECONNECTING
    connectionState == ConnectionUiState.Connecting ||
        connectionState == ConnectionUiState.SyncingChannels ->
        if (!hasCachedEvents) EpgColumnDataState.LOADING else EpgColumnDataState.STALE
    coveragePending -> EpgColumnDataState.LOADING
    filterActive && hasCachedEvents && !hasMatchingCachedEvents ->
        EpgColumnDataState.FILTER_EMPTY
    visibleEvents.isEmpty() && !hasCachedEvents -> EpgColumnDataState.NO_DATA
    visibleEvents.isEmpty() -> EpgColumnDataState.EMPTY_DAY
    visibleEvents.minOf { it.start.epochSeconds } > windowStartSec ||
        visibleEvents.maxOf { it.stop.epochSeconds } < windowEndSec ->
        EpgColumnDataState.PARTIAL
    else -> EpgColumnDataState.READY
}
