package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId

const val RECENT_CHANNEL_LIMIT = 8

object LastPlayedChannelPolicy {
    fun resolve(orderedIds: List<ChannelId>, persistedId: ChannelId?): ChannelId? {
        if (orderedIds.isEmpty()) return null
        return persistedId?.takeIf(orderedIds::contains) ?: orderedIds.first()
    }
}

fun pushRecentChannelId(
    current: List<ChannelId>,
    channelId: ChannelId,
    limit: Int = RECENT_CHANNEL_LIMIT,
): List<ChannelId> = (listOf(channelId) + current.filterNot { it == channelId }).take(limit)
