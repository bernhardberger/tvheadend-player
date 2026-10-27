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

const val GUIDE_VISIBLE_WINDOW_SEC = 3 * 3600L
val GUIDE_EPG_COVERAGE_POLICY = EpgCoveragePolicy.create(7.days)

/** Destination entry keeps the active group and remembers only the channel, not its old time. */
fun guideEntryChannelId(
    channelIds: List<ChannelId>,
    playingChannelId: ChannelId?,
    rememberedChannelId: ChannelId?,
    lastPlayedChannelId: ChannelId?,
    selectedChannelId: ChannelId?,
): ChannelId? = listOf(playingChannelId, rememberedChannelId, lastPlayedChannelId, selectedChannelId)
    .firstOrNull { it != null && it in channelIds } ?: channelIds.firstOrNull()

/** Display-only archive is merged once by the Guide snapshot owner. Live identity wins. */
fun guideDisplayEvents(snapshot: EpgSnapshot?): List<EpgEventEntry> =
    if (snapshot == null) emptyList() else (snapshot.events + snapshot.historicalEvents).distinctBy { it.id }

data class GuideWindowBounds(
    val earliestStartSec: Long,
    val latestStartSec: Long,
) {
    init {
        require(latestStartSec >= earliestStartSec)
    }

    fun constrain(startSec: Long): Long = startSec.coerceIn(earliestStartSec, latestStartSec)
}

fun guideWindowBounds(openedAtSec: Long, zoneId: ZoneId): GuideWindowBounds {
    val earliestStartSec = floorGuideWindowToHour(openedAtSec, zoneId) - 6 * 3600L
    val latestStartSec = floorGuideWindowToHour(
        openedAtSec + GUIDE_EPG_COVERAGE_POLICY.futureHorizon.inWholeSeconds -
            GUIDE_VISIBLE_WINDOW_SEC,
        zoneId,
    )
    return GuideWindowBounds(earliestStartSec, latestStartSec)
}

fun moveGuideWindowByDays(
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

fun floorGuideWindowToHour(epochSec: Long, zoneId: ZoneId): Long =
    JavaInstant.ofEpochSecond(epochSec)
        .atZone(zoneId)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
        .toEpochSecond()

/**
 * The window start that opens on [nowSec]: half an hour earlier, down to the half hour, so the
 * programme on air starts in view while the rest of the window shows what comes next. The server
 * drops programmes once they end, so a window further back would mostly be empty.
 */
fun guideNowWindowStart(nowSec: Long, bounds: GuideWindowBounds, zoneId: ZoneId): Long {
    val start = JavaInstant.ofEpochSecond(nowSec - GuideNowLeadSec).atZone(zoneId)
    return bounds.constrain(start.withMinute(start.minute / 30 * 30).withSecond(0).withNano(0).toEpochSecond())
}

private const val GuideNowLeadSec = 30 * 60L

fun shouldWaitForGuideCoverage(
    connectionReady: Boolean,
    hasCurrentSnapshot: Boolean,
    acquisitionPending: Boolean,
    coverageSettled: Boolean,
): Boolean = connectionReady && acquisitionPending && (!hasCurrentSnapshot || !coverageSettled)

enum class GuidePendingNavigationAction { WAIT, RESTORE_ORIGIN, HEADER, MOVE }

/** Cancels navigation intent, never grants coverage to an unknown destination. */
fun guidePendingNavigationAction(
    direction: EpgFocusDirection,
    frontierDirection: Int?,
    channelDirection: Int?,
    windowPending: Boolean,
    originPending: Boolean,
): GuidePendingNavigationAction {
    if (frontierDirection == null && channelDirection == null && !windowPending && !originPending) {
        return GuidePendingNavigationAction.MOVE
    }
    if (frontierDirection != null && (
        direction == EpgFocusDirection.LEFT && frontierDirection > 0 ||
            direction == EpgFocusDirection.RIGHT && frontierDirection < 0
        )
    ) return GuidePendingNavigationAction.RESTORE_ORIGIN
    if (channelDirection != null && (
        direction == EpgFocusDirection.UP && channelDirection > 0 ||
            direction == EpgFocusDirection.DOWN && channelDirection < 0
        )
    ) return GuidePendingNavigationAction.MOVE
    return if (direction == EpgFocusDirection.UP) {
        GuidePendingNavigationAction.HEADER
    } else {
        GuidePendingNavigationAction.WAIT
    }
}

fun firstUnsettledGuidePageIndex(
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
)

/** [ensureActive] runs once per event so a superseded preparation can stop mid-loop. */
fun indexTimelineEventsByChannel(
    events: List<EpgEventEntry>,
    windowStartSec: Long,
    windowEndSec: Long,
    ensureActive: () -> Unit = {},
): TimelineEpgEventIndex {
    require(windowEndSec > windowStartSec)
    val visibleEvents = mutableMapOf<ChannelId, MutableList<EpgEventEntry>>()
    val channelsWithEvents = mutableSetOf<ChannelId>()
    events.forEach { event ->
        ensureActive()
        event.channelId?.let { channelId ->
            channelsWithEvents += channelId
            if (
                event.stop.epochSeconds > windowStartSec &&
                event.start.epochSeconds < windowEndSec
            ) {
                visibleEvents.getOrPut(channelId) { mutableListOf() }.add(event)
            }
        }
    }
    return TimelineEpgEventIndex(
        visibleEventsByChannel = visibleEvents,
        channelsWithEvents = channelsWithEvents,
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

/**
 * Left and Right step programme by programme. Up and Down pick the adjacent row's programme
 * at [anchorSec] or overlapping [anchorSpanSec]. Without an anchor, use the focused programme's
 * readable span. Callers keep the anchor across vertical moves so they never drift in time.
 */
fun moveTimelineEpgFocus(
    rows: List<EpgFocusColumn>,
    current: EpgFocusTarget,
    direction: EpgFocusDirection,
    visibleChannelRange: IntRange = rows.indices,
    anchorSec: Long? = null,
    readableRangeSec: LongRange? = null,
    minReadableOverlapSec: Long = 1,
    anchorSpanSec: LongRange? = null,
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

    val span = anchorSpanSec ?: readableRangeSec?.takeIf { anchorSec == null }
        ?.let { timelineFocusAnchorRange(currentEvent, it) }
    val step = if (direction == EpgFocusDirection.UP) -1 else 1
    val channelIndex = generateSequence(current.channelIndex + step) { it + step }
        .takeWhile { it in rows.indices }
        .firstOrNull { timelineEventAt(rows[it].events, anchorSec ?: currentEvent.start.epochSeconds,
            readableRangeSec, minReadableOverlapSec, span) != null }
    if (channelIndex == null && direction == EpgFocusDirection.UP) {
        return TimelineEpgFocusMove(current, focusHeader = true)
    }
    channelIndex ?: return TimelineEpgFocusMove(current)
    val best = timelineEventAt(rows[channelIndex].events, anchorSec ?: currentEvent.start.epochSeconds,
        readableRangeSec, minReadableOverlapSec, span)
        ?: return TimelineEpgFocusMove(current)

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

/**
 * The row's nearest programme past [boundarySec] in [direction] that still overlaps the
 * Guide's range. An EPG gap can span the whole adjacent window; Left and Right then
 * continue to this programme instead of stopping.
 */
fun timelineProgrammeAcrossGap(
    events: List<EpgEventEntry>,
    originEventId: EventId,
    boundarySec: Long,
    direction: Int,
    rangeStartSec: Long,
    rangeEndSec: Long,
): EpgEventEntry? {
    require(direction == -1 || direction == 1)
    val inRange = events.filter {
        it.id != originEventId && it.stop.epochSeconds > rangeStartSec && it.start.epochSeconds < rangeEndSec
    }
    return if (direction < 0) {
        inRange.filter { it.start.epochSeconds < boundarySec }.maxByOrNull { it.start }
    } else {
        inRange.filter { it.start.epochSeconds >= boundarySec }.minByOrNull { it.start }
    }
}

fun timelinePageFocusTarget(
    rows: List<EpgFocusColumn>,
    current: EpgFocusTarget,
    preferredChannelIndex: Int,
    direction: Int,
    searchChannelIds: Set<ChannelId>? = null,
    anchorSec: Long? = null,
    readableRangeSec: LongRange? = null,
    minReadableOverlapSec: Long = 1,
    anchorSpanSec: LongRange? = null,
): EpgFocusTarget? {
    val currentEvent = rows.getOrNull(current.channelIndex)
        ?.events
        ?.firstOrNull { it.id == current.eventId }
        ?: return null
    val span = anchorSpanSec ?: readableRangeSec?.takeIf { anchorSec == null }
        ?.let { timelineFocusAnchorRange(currentEvent, it) }
    val step = if (direction < 0) -1 else 1
    val startIndex = preferredChannelIndex.coerceIn(rows.indices)
    val targetIndex = generateSequence(startIndex) { it + step }
        .takeWhile { it in rows.indices }
        .firstOrNull {
            timelineEventAt(rows[it].events, anchorSec ?: currentEvent.start.epochSeconds,
                readableRangeSec, minReadableOverlapSec, span) != null &&
                (searchChannelIds == null || rows[it].channelId in searchChannelIds)
        }
        ?: return null
    val targetEvent = timelineEventAt(rows[targetIndex].events, anchorSec ?: currentEvent.start.epochSeconds,
        readableRangeSec, minReadableOverlapSec, span)
        ?: return null
    return EpgFocusTarget(targetIndex, targetEvent.id)
}

/** Fully visible short programmes qualify; edge fragments must meet the readable overlap minimum. */
fun timelineEventIsReadable(event: EpgEventEntry, readableRangeSec: LongRange, minReadableOverlapSec: Long): Boolean {
    if (readableRangeSec.isEmpty()) return false
    val start = event.start.epochSeconds
    val end = event.stop.epochSeconds
    if (end <= start) return false
    val readableEnd = readableRangeSec.last + 1
    val overlap = minOf(end, readableEnd) - maxOf(start, readableRangeSec.first)
    return overlap > 0 && ((start >= readableRangeSec.first && end <= readableEnd) || overlap >= minReadableOverlapSec)
}

/** A readable on-air programme anchors at now; otherwise use its visible half-open span. */
fun timelineFocusAnchorRange(event: EpgEventEntry, readableRangeSec: LongRange, nowSec: Long? = null): LongRange {
    if (readableRangeSec.isEmpty()) return readableRangeSec
    if (nowSec != null && nowSec in readableRangeSec && nowSec in event.start.epochSeconds until event.stop.epochSeconds) {
        return nowSec..nowSec
    }
    val start = maxOf(event.start.epochSeconds, readableRangeSec.first)
    val end = minOf(event.stop.epochSeconds, readableRangeSec.last + 1)
    return if (start < end) start until end else event.start.epochSeconds.coerceIn(readableRangeSec).let { it..it }
}

/** Largest span overlap, then midpoint containment, then earlier start. Gaps use the nearest programme. */
fun timelineEventAt(
    events: List<EpgEventEntry>,
    anchorSec: Long,
    readableRangeSec: LongRange? = null,
    minReadableOverlapSec: Long = 1,
    anchorSpanSec: LongRange? = null,
): EpgEventEntry? {
    if (readableRangeSec?.isEmpty() == true) return null
    val span = anchorSpanSec?.takeUnless { it.isEmpty() }
    val anchor = span?.let { it.first + (it.last - it.first + 1) / 2 }
        ?: readableRangeSec?.let { anchorSec.coerceIn(it) } ?: anchorSec
    val candidates = events.filter {
        readableRangeSec == null || timelineEventIsReadable(it, readableRangeSec, minReadableOverlapSec)
    }
    if (span != null) {
        candidates.filter { it.start.epochSeconds <= span.last && it.stop.epochSeconds > span.first }
            .minWithOrNull(compareBy<EpgEventEntry>(
                { -(minOf(it.stop.epochSeconds, span.last + 1) - maxOf(it.start.epochSeconds, span.first)) },
                { anchor !in it.start.epochSeconds until it.stop.epochSeconds },
                { it.start.epochSeconds },
            ))?.let { return it }
    }
    return candidates.minWithOrNull(
        compareBy<EpgEventEntry>(
            { maxOf(it.start.epochSeconds - anchor, anchor - it.stop.epochSeconds + 1, 0L) },
            { -it.start.epochSeconds },
        ),
    )
}

/** Logical leading-edge pixels work identically with relative placement in LTR and RTL. */
fun shouldComposeTimelineCell(
    startPx: Int,
    widthPx: Int,
    visibleWidthPx: Int?,
    isFocusTarget: Boolean,
): Boolean = isFocusTarget || visibleWidthPx == null ||
    (visibleWidthPx > 0 && widthPx > 0 && startPx < visibleWidthPx && startPx.toLong() + widthPx > 0)

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

fun epgFrontierSettled(coverage: EpgCoverage?, requestedThrough: Instant): Boolean =
    coverage?.knownTo?.let { it >= requestedThrough } == true

fun guideChannelPageCoverageSettled(
    channelIds: List<ChannelId>,
    coverages: List<EpgCoverage>,
    requestedThrough: Instant,
): Boolean = channelIds.all { channelId ->
    epgFrontierSettled(
        coverages.firstOrNull { it.channelId == channelId },
        requestedThrough,
    )
}

sealed interface GuideCoverageFocusResolution {
    data object Wait : GuideCoverageFocusResolution
    data object Release : GuideCoverageFocusResolution
    data class Select(val target: EpgFocusTarget) : GuideCoverageFocusResolution
}

fun resolveGuideWindowFocus(
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

sealed interface GuideDeferredOriginResolution {
    data object Wait : GuideDeferredOriginResolution
    data object Release : GuideDeferredOriginResolution
    data class Restore(val target: EpgFocusTarget) : GuideDeferredOriginResolution
}

fun resolveGuideFrontierOrigin(
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
    /** Nothing shown and nothing proves the window empty, though the request has settled. */
    NOT_LOADED,
    PARTIAL,
    STALE,
    PERMISSION_DENIED,
    RECONNECTING,
    SERVER_FAILURE,
}

/**
 * Whether a row's window is known: an ended window shows the retained programmes as they are,
 * and a later one is known once the channel's coverage reaches its end.
 */
fun epgWindowCoverageProven(coverage: EpgCoverage?, windowEndSec: Long, nowSec: Long): Boolean =
    windowEndSec <= nowSec || epgFrontierSettled(coverage, Instant.fromEpochSeconds(windowEndSec))

fun epgColumnDataState(
    visibleEvents: List<EpgEventEntry>,
    windowStartSec: Long,
    windowEndSec: Long,
    connectionState: ConnectionUiState,
    coveragePending: Boolean = false,
    hasCachedEvents: Boolean = visibleEvents.isNotEmpty(),
    windowCoverageProven: Boolean = false,
): EpgColumnDataState = when {
    connectionState is ConnectionUiState.Error &&
        connectionState.kind == ConnectionFailureKind.PERMISSION_DENIED ->
        EpgColumnDataState.PERMISSION_DENIED
    connectionState is ConnectionUiState.Error ->
        EpgColumnDataState.SERVER_FAILURE
    connectionState == ConnectionUiState.Reconnecting && hasCachedEvents ->
        EpgColumnDataState.STALE
    connectionState == ConnectionUiState.Reconnecting -> EpgColumnDataState.RECONNECTING
    connectionState == ConnectionUiState.Connecting ||
        connectionState == ConnectionUiState.SyncingChannels ->
        if (!hasCachedEvents) EpgColumnDataState.LOADING else EpgColumnDataState.STALE
    coveragePending -> EpgColumnDataState.LOADING
    visibleEvents.isEmpty() && !hasCachedEvents -> EpgColumnDataState.NO_DATA
    // A request that timed out proves nothing about the window.
    visibleEvents.isEmpty() && !windowCoverageProven -> EpgColumnDataState.NOT_LOADED
    visibleEvents.isEmpty() -> EpgColumnDataState.EMPTY_DAY
    visibleEvents.minOf { it.start.epochSeconds } > windowStartSec ||
        visibleEvents.maxOf { it.stop.epochSeconds } < windowEndSec ->
        EpgColumnDataState.PARTIAL
    else -> EpgColumnDataState.READY
}
