package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.data.Channel
import at.bernhardberger.tvhplayer.data.ChannelTag

enum class TagScopeFallback {
    TAG_UNAVAILABLE,
    SCOPE_HIDDEN,
}

data class ChannelScopeVisibility(
    val configured: Boolean = false,
    val allChannelsVisible: Boolean = true,
    val visibleTagIds: Set<Int> = emptySet(),
) {
    fun isAllChannelsVisible(): Boolean = !configured || allChannelsVisible

    fun isTagVisible(tagId: Int): Boolean = !configured || tagId in visibleTagIds
}

data class ChannelBrowsingScope(
    val allChannels: List<Channel>,
    val visibleChannels: List<Channel>,
    val tags: List<ChannelTag>,
    val allChannelsVisible: Boolean,
    val activeTagId: Int?,
    val fallback: TagScopeFallback? = null,
)

fun resolveChannelScope(
    channels: List<Channel>,
    tags: List<ChannelTag>,
    requestedTagId: Int?,
    visibility: ChannelScopeVisibility = ChannelScopeVisibility(),
): ChannelBrowsingScope {
    val visibleTags = tags.filter { visibility.isTagVisible(it.id) }
    val allChannelsVisible = visibility.isAllChannelsVisible() || visibleTags.isEmpty()
    val requestedTag = requestedTagId?.let { id ->
        visibleTags.firstOrNull { it.id == id }
    }
    val activeTagId = when {
        requestedTag != null -> requestedTag.id
        requestedTagId == null && allChannelsVisible -> null
        allChannelsVisible -> null
        else -> visibleTags.first().id
    }
    val fallback = when {
        activeTagId == requestedTagId -> null
        requestedTagId != null && tags.none { it.id == requestedTagId } ->
            TagScopeFallback.TAG_UNAVAILABLE
        else -> TagScopeFallback.SCOPE_HIDDEN
    }

    return ChannelBrowsingScope(
        allChannels = channels,
        visibleChannels = if (activeTagId == null) {
            channels
        } else {
            channels.filter { activeTagId in it.tagIds }
        },
        tags = visibleTags,
        allChannelsVisible = allChannelsVisible,
        activeTagId = activeTagId,
        fallback = fallback,
    )
}

fun updateChannelScopeVisibility(
    current: ChannelScopeVisibility,
    availableTagIds: Set<Int>,
    tagId: Int?,
    visible: Boolean,
): ChannelScopeVisibility {
    val allChannelsVisible = if (current.configured) {
        current.allChannelsVisible
    } else {
        true
    }
    val visibleTagIds = if (current.configured) {
        current.visibleTagIds.intersect(availableTagIds)
    } else {
        availableTagIds
    }.toMutableSet()

    val updatedAllChannelsVisible = if (tagId == null) visible else allChannelsVisible
    if (tagId != null) {
        if (visible) visibleTagIds += tagId else visibleTagIds -= tagId
    }
    if (!updatedAllChannelsVisible && visibleTagIds.isEmpty()) return current

    return ChannelScopeVisibility(
        configured = true,
        allChannelsVisible = updatedAllChannelsVisible,
        visibleTagIds = visibleTagIds,
    )
}

fun browsingFocusChannelId(
    visibleChannels: List<Channel>,
    currentFocusId: Int,
): Int? = visibleChannels.firstOrNull { it.channelId == currentFocusId }?.channelId
    ?: visibleChannels.firstOrNull()?.channelId

fun adjacentTagId(
    tags: List<ChannelTag>,
    activeTagId: Int?,
    direction: Int,
    allChannelsVisible: Boolean = true,
): Int? {
    val orderedIds = buildList<Int?> {
        if (allChannelsVisible) add(null)
        addAll(tags.map { it.id })
    }
    if (orderedIds.isEmpty()) return null
    val currentIndex = orderedIds.indexOf(activeTagId).coerceAtLeast(0)
    val targetIndex = (currentIndex + direction.coerceIn(-1, 1))
        .coerceIn(orderedIds.indices)
    return orderedIds[targetIndex]
}
