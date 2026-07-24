package at.bernhardberger.tvhplayer.core

object LastPlayedChannelPolicy {
    fun resolve(orderedIds: List<Int>, persistedId: Int?): Int? {
        if (orderedIds.isEmpty()) return null
        return persistedId?.takeIf(orderedIds::contains) ?: orderedIds.first()
    }
}
