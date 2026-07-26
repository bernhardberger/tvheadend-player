package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry

const val HOME_RECENT_CHANNEL_LIMIT = 8
const val HOME_ON_NOW_LIMIT = 6
const val HOME_RECORDING_LIMIT = 5

data class HomeNowPlaying(
    val channelId: Int,
    val channelName: String,
    val programmeTitle: String?,
    val isRecording: Boolean,
)

data class HomeRecentChannel(
    val channelId: Int,
    val channelName: String,
    val programmeTitle: String?,
)

data class HomeRecordingItem(
    val id: Int,
    val channelId: Int,
    val title: String,
    val subtitle: String?,
    val playable: Boolean,
)

data class HomeDashboardModel(
    val nowPlaying: HomeNowPlaying?,
    val recentChannels: List<HomeRecentChannel>,
    val onNow: List<HomeRecentChannel>,
    val latestRecordings: List<HomeRecordingItem>,
    val recordingNow: List<HomeRecordingItem>,
    val upcomingRecordings: List<HomeRecordingItem>,
)

enum class HomeFocusTarget {
    NOW_PLAYING,
    RECENT_CHANNEL,
    ON_NOW,
    RECORDING_NOW,
    LATEST_RECORDING,
    UPCOMING_RECORDING,
    STATUS_ACTION,
}

fun homeInitialFocusTarget(model: HomeDashboardModel): HomeFocusTarget = when {
    model.nowPlaying != null -> HomeFocusTarget.NOW_PLAYING
    model.recentChannels.isNotEmpty() -> HomeFocusTarget.RECENT_CHANNEL
    model.onNow.isNotEmpty() -> HomeFocusTarget.ON_NOW
    model.recordingNow.isNotEmpty() -> HomeFocusTarget.RECORDING_NOW
    model.latestRecordings.isNotEmpty() -> HomeFocusTarget.LATEST_RECORDING
    model.upcomingRecordings.isNotEmpty() -> HomeFocusTarget.UPCOMING_RECORDING
    else -> HomeFocusTarget.STATUS_ACTION
}

fun buildHomeDashboard(
    channelsById: Map<Int, ChannelUi>,
    activeServiceId: Int?,
    activeRecordingId: Int?,
    activeProgrammeTitle: String?,
    recentChannelIds: List<Int>,
    onNowEvents: List<Pair<ChannelUi, EpgEventEntry>>,
    recordings: List<DvrEntry>,
    nowSec: Long,
): HomeDashboardModel {
    val nowPlaying = when {
        activeServiceId != null -> {
            val channel = channelsById[activeServiceId]
            HomeNowPlaying(
                channelId = activeServiceId,
                channelName = channel?.name.orEmpty(),
                programmeTitle = activeProgrammeTitle,
                isRecording = false,
            )
        }
        activeRecordingId != null -> {
            val entry = recordings.firstOrNull { it.id == activeRecordingId }
            HomeNowPlaying(
                channelId = entry?.channelId ?: -1,
                channelName = entry?.channelName.orEmpty(),
                programmeTitle = entry?.title,
                isRecording = true,
            )
        }
        else -> null
    }

    val recent = recentChannelIds
        .asSequence()
        .mapNotNull { id ->
            val channel = channelsById[id] ?: return@mapNotNull null
            HomeRecentChannel(
                channelId = id,
                channelName = channel.name,
                programmeTitle = onNowEvents.firstOrNull { it.first.id == id }?.second?.title,
            )
        }
        .take(HOME_RECENT_CHANNEL_LIMIT)
        .toList()

    val onNow = onNowEvents
        .asSequence()
        .filter { (_, event) -> event.start <= nowSec && nowSec < event.stop }
        .take(HOME_ON_NOW_LIMIT)
        .map { (channel, event) ->
            HomeRecentChannel(
                channelId = channel.id,
                channelName = channel.name,
                programmeTitle = event.title,
            )
        }
        .toList()

    val playable = recordings
        .asSequence()
        .filter { it.state == DvrState.COMPLETED }
        .filter { recordingPlaybackAvailability(it) is RecordingPlaybackAvailability.Ready }
        .sortedByDescending { it.start }
        .take(HOME_RECORDING_LIMIT)
        .map {
            HomeRecordingItem(
                id = it.id,
                channelId = it.channelId,
                title = it.title,
                subtitle = it.channelName,
                playable = true,
            )
        }
        .toList()

    val recordingNow = recordings
        .asSequence()
        .filter { it.state == DvrState.RECORDING }
        .sortedByDescending { it.start }
        .take(HOME_RECORDING_LIMIT)
        .map {
            HomeRecordingItem(
                id = it.id,
                channelId = it.channelId,
                title = it.title,
                subtitle = it.channelName,
                playable = true,
            )
        }
        .toList()

    val upcoming = recordings
        .asSequence()
        .filter { it.state == DvrState.SCHEDULED && it.start >= nowSec }
        .sortedBy { it.start }
        .take(HOME_RECORDING_LIMIT)
        .map {
            HomeRecordingItem(
                id = it.id,
                channelId = it.channelId,
                title = it.title,
                subtitle = it.channelName,
                playable = false,
            )
        }
        .toList()

    return HomeDashboardModel(
        nowPlaying = nowPlaying,
        recentChannels = recent,
        onNow = onNow,
        latestRecordings = playable,
        recordingNow = recordingNow,
        upcomingRecordings = upcoming,
    )
}

fun pushRecentChannelId(
    current: List<Int>,
    channelId: Int,
    limit: Int = HOME_RECENT_CHANNEL_LIMIT,
): List<Int> = (listOf(channelId) + current.filterNot { it == channelId }).take(limit)
