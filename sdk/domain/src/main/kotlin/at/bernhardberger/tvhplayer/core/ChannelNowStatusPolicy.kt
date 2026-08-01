package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState

data class ChannelNowStatus(
    val playingNow: Boolean,
    val recordingNow: Boolean,
)

fun activeRecordingChannelIds(entries: List<DvrEntry>): Set<Int> = entries
    .asSequence()
    .filter { it.state == DvrState.RECORDING }
    .map { it.channelId }
    .toSet()

fun channelNowStatus(
    channelId: Int,
    playingChannelId: Int?,
    recordingChannelIds: Set<Int>,
): ChannelNowStatus = ChannelNowStatus(
    playingNow = channelId == playingChannelId,
    recordingNow = channelId in recordingChannelIds,
)
