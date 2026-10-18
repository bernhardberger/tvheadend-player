package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope

/** A browsable group of Channels: All channels ([tagId] null) or one channel tag. */
internal data class ChannelsGroup(
    val tagId: ChannelTagId?,
    val name: String?,
    val channels: List<Channel>,
)

/**
 * The groups Channels offers, in its order: All channels first when it is visible (it is opt-in
 * under Settings), then the visible tags. Each group lists its channels in browse order.
 */
internal fun channelsGroups(scope: ChannelBrowsingScope): List<ChannelsGroup> = buildList {
    if (scope.allChannelsVisible) add(ChannelsGroup(null, null, scope.allChannels))
    scope.tags.forEach { tag ->
        add(ChannelsGroup(tag.id, tag.name, scope.allChannels.filter { tag.id in it.tagIds.orEmpty() }))
    }
}

/** The Rows layout shows one row per group that has channels. */
internal fun channelsRows(groups: List<ChannelsGroup>): List<ChannelsGroup> = groups.filter { it.channels.isNotEmpty() }

/** The row [step] rows from [current]'s, or null past either end or without a current row. */
internal fun adjacentChannelsRow(rows: List<ChannelsGroup>, current: ChannelTagId?, step: Int): ChannelsGroup? {
    val index = rows.indexOfFirst { it.tagId == current }
    return if (index < 0) null else rows.getOrNull(index + step)
}
