package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import at.bernhardberger.tvhplayer.core.resolveChannelScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelsGroupsTest {
    private val news = ChannelTagId(1)
    private val sport = ChannelTagId(2)
    private val radio = ChannelTagId(3)
    private val channels = listOf(
        Channel.create(id = ChannelId(1), name = "One", number = 1, tagIds = listOf(news)),
        Channel.create(id = ChannelId(2), name = "Two", number = 2, tagIds = listOf(sport, news)),
        Channel.create(id = ChannelId(3), name = "Three", number = 3, tagIds = listOf(sport)),
    )
    private val tags = listOf(
        ChannelTag.create(sport, name = "Sport", channelIds = listOf(ChannelId(2), ChannelId(3))),
        ChannelTag.create(news, name = "News", channelIds = listOf(ChannelId(1), ChannelId(2))),
        ChannelTag.create(radio, name = "Radio", channelIds = emptyList()),
    )

    private fun ids(group: ChannelsGroup) = group.channels.map { it.id.value }

    @Test
    fun allChannelsLeadsThenTheVisibleTagsInChannelsOrder() {
        val groups = channelsGroups(resolveChannelScope(channels, tags, requestedTagId = null))
        assertEquals(listOf(null, sport, news, radio), groups.map { it.tagId })
        assertEquals(listOf(1L, 2L, 3L), ids(groups[0]))
        assertEquals(listOf(2L, 3L), ids(groups[1]))
        assertEquals(listOf(1L, 2L), ids(groups[2]))
        assertEquals(emptyList<Long>(), ids(groups[3]))
    }

    @Test
    fun hiddenAllChannelsAndHiddenTagsAreLeftOut() {
        val visibility = ChannelScopeVisibility(configured = true, allChannelsVisible = false, visibleTagIds = setOf(news))
        val groups = channelsGroups(resolveChannelScope(channels, tags, requestedTagId = null, visibility))
        assertEquals(listOf(news), groups.map { it.tagId })
    }

    @Test
    fun rowsSkipEmptyGroupsAndStepWithinThem() {
        val rows = channelsRows(channelsGroups(resolveChannelScope(channels, tags, requestedTagId = null)))
        assertEquals(listOf(null, sport, news), rows.map { it.tagId })
        assertEquals(sport, adjacentChannelsRow(rows, null, 1)?.tagId)
        assertEquals(null, adjacentChannelsRow(rows, sport, -1)?.tagId)
        assertEquals(rows.first(), adjacentChannelsRow(rows, sport, -1))
        assertNull(adjacentChannelsRow(rows, null, -1))
        assertNull(adjacentChannelsRow(rows, news, 1))
        assertNull("An empty group is no row to step from", adjacentChannelsRow(rows, radio, 1))
    }
}
