package at.bernhardberger.tvhplayer.core

const val RECENT_CHANNEL_LIMIT = 8

object LastPlayedChannelPolicy {
    fun resolve(orderedIds: List<Int>, persistedId: Int?): Int? {
        if (orderedIds.isEmpty()) return null
        return persistedId?.takeIf(orderedIds::contains) ?: orderedIds.first()
    }
}

fun pushRecentChannelId(
    current: List<Int>,
    channelId: Int,
    limit: Int = RECENT_CHANNEL_LIMIT,
): List<Int> = (listOf(channelId) + current.filterNot { it == channelId }).take(limit)
