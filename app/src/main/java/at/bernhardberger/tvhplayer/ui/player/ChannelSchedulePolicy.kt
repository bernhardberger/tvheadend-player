package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftWallClockMapping
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.awaitsGuide
import at.bernhardberger.tvhplayer.core.isDegraded
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState

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
