package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.ChannelTagUi
import at.bernhardberger.tvhplayer.htsp.ChannelUi

enum class TagScopeFallback {
    TAG_UNAVAILABLE,
}

data class ChannelBrowsingScope(
    val allChannels: List<ChannelUi>,
    val visibleChannels: List<ChannelUi>,
    val tags: List<ChannelTagUi>,
    val activeTagId: Int?,
    val fallback: TagScopeFallback? = null,
)

fun resolveChannelScope(
    channels: List<ChannelUi>,
    tags: List<ChannelTagUi>,
    requestedTagId: Int?,
): ChannelBrowsingScope {
    if (requestedTagId == null) {
        return ChannelBrowsingScope(
            allChannels = channels,
            visibleChannels = channels,
            tags = tags,
            activeTagId = null,
        )
    }

    val activeTag = tags.firstOrNull { it.id == requestedTagId }
        ?: return ChannelBrowsingScope(
            allChannels = channels,
            visibleChannels = channels,
            tags = tags,
            activeTagId = null,
            fallback = TagScopeFallback.TAG_UNAVAILABLE,
        )

    return ChannelBrowsingScope(
        allChannels = channels,
        visibleChannels = channels.filter { requestedTagId in it.tagIds },
        tags = tags,
        activeTagId = activeTag.id,
    )
}

fun browsingFocusChannelId(
    visibleChannels: List<ChannelUi>,
    currentFocusId: Int,
): Int? = visibleChannels.firstOrNull { it.id == currentFocusId }?.id
    ?: visibleChannels.firstOrNull()?.id

fun adjacentTagId(
    tags: List<ChannelTagUi>,
    activeTagId: Int?,
    direction: Int,
): Int? {
    val orderedIds = listOf<Int?>(null) + tags.map { it.id }
    val currentIndex = orderedIds.indexOf(activeTagId).coerceAtLeast(0)
    val targetIndex = (currentIndex + direction.coerceIn(-1, 1))
        .coerceIn(orderedIds.indices)
    return orderedIds[targetIndex]
}
