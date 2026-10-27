package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftWallClockMapping
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.awaitsGuide
import at.bernhardberger.tvhplayer.core.isDegraded
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvhplayer.core.GUIDE_VISIBLE_WINDOW_SEC
import at.bernhardberger.tvhplayer.core.currentEpgSnapshot
import at.bernhardberger.tvhplayer.core.epgFrontierSettled
import at.bernhardberger.tvhplayer.core.guideWindowBounds
import at.bernhardberger.tvhplayer.core.shouldWaitForGuideCoverage
import java.time.ZoneId
import kotlin.time.Instant

/** Display-only history boundary; never turn a wall-clock estimate into a seek grant. */
internal fun scheduleHistoryStart(state: AppTimeshiftState): Long? {
    if (!state.available) return null
    val timeline = state.timeline ?: return null
    val mapping = (state.historyStartTimeline?.takeIf { it.describesSameSegment(timeline) }
        ?: timeline).wallClockMapping as? TimeshiftWallClockMapping.Estimate ?: return null
    return timeline.select(timeline.start)?.let(mapping::estimate)?.epochSeconds
}

internal fun channelScheduleEvents(
    events: List<EpgEvent>, channelId: ChannelId, nowSec: Long, historyStartSec: Long?,
): List<EpgEvent> = events.filter {
    it.channelId == channelId && it.stop > it.start &&
        it.stop.epochSeconds > (historyStartSec ?: nowSec)
}.distinctBy { it.id }.sortedBy { it.start }

internal fun scheduleOffline(connection: ConnectionUiState, epg: EpgRepositoryState): Boolean =
    epg is EpgRepositoryState.Stale || connection.isDegraded(connection == ConnectionUiState.Reconnecting)

internal fun scheduleLoading(connection: ConnectionUiState, epg: EpgRepositoryState): Boolean =
    !scheduleOffline(connection, epg) && connection.awaitsGuide(epg)

/** The schedule asks for the Guide's seven-day reach; the server answers with what it holds. */
internal fun scheduleCoverageThroughSec(openedAtSec: Long, zone: ZoneId): Long =
    guideWindowBounds(openedAtSec, zone).latestStartSec + GUIDE_VISIBLE_WINDOW_SEC

/** Later days are still arriving while the request runs and the channel's live coverage stops short of it. */
internal fun scheduleCoveragePending(
    connection: ConnectionUiState, epg: EpgRepositoryState, channelId: ChannelId,
    throughSec: Long, requestPending: Boolean,
): Boolean {
    val snapshot = epg.currentEpgSnapshot()
    return shouldWaitForGuideCoverage(
        connectionReady = connection == ConnectionUiState.Ready,
        hasCurrentSnapshot = snapshot != null,
        acquisitionPending = requestPending,
        coverageSettled = epgFrontierSettled(snapshot?.coverages?.firstOrNull { it.channelId == channelId },
            Instant.fromEpochSeconds(throughSec)),
    )
}
