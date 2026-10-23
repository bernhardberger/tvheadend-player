package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry

data class ChannelNowStatus(
    val playingNow: Boolean,
    val recordingNow: Boolean,
)

fun activeRecordingChannelIds(entries: List<DvrEntry>): Set<ChannelId> = entries
    .asSequence()
    .filter(::recordingIsRunning)
    .mapNotNull { it.channelId }
    .toSet()

fun channelNowStatus(
    channelId: ChannelId,
    playingChannelId: ChannelId?,
    recordingChannelIds: Set<ChannelId>,
): ChannelNowStatus = ChannelNowStatus(
    playingNow = channelId == playingChannelId,
    recordingNow = channelId in recordingChannelIds,
)
