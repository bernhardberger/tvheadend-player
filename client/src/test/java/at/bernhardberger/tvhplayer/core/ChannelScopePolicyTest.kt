package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelScopePolicyTest {
    @Test
    fun browseOrderUsesMajorMinorNameAndIdentityWithUnnumberedLast() {
        val channels = listOf(
            channel(id = 90, number = 90),
            channel(id = 99, number = null, name = "Alpha"),
            channel(id = 98, number = 0, name = "Beta"),
            channel(id = 12, number = 2, numberMinor = 1),
            channel(id = 13, number = 2, numberMinor = 0),
            channel(id = 11, number = 2, name = "Alpha"),
            channel(id = 10, number = 2, name = "Zulu"),
            channel(id = 14, number = 2, name = "Alpha"),
            channel(id = 1, number = 1),
        )

        val ordered = orderBrowseChannels(channels)

        assertEquals(listOf(1L, 11L, 14L, 10L, 13L, 12L, 90L, 99L, 98L), ordered.map { it.id.value })
    }

    @Test
    fun numericOrderingPreservesTheActiveTagFilterAndChannelIdentity() {
        val tagId = ChannelTagId(5)
        val twenty = channel(id = 20, number = 20, tagId = tagId)
        val excluded = channel(id = 2, number = 2)
        val three = channel(id = 3, number = 3, tagId = tagId)

        val scope = resolveChannelScope(
            channels = listOf(twenty, excluded, three),
            tags = listOf(ChannelTag.create(id = tagId, name = "News", index = 1)),
            requestedTagId = tagId,
        )

        assertEquals(tagId, scope.activeTagId)
        assertEquals(listOf(three, twenty), scope.visibleChannels)
    }

    @Test
    fun unnumberedChannelsIgnoreMinorNumbersAndSortByNameWithBlankNamesLast() {
        val ordered = orderBrowseChannels(
            listOf(
                channel(id = 10, number = null, numberMinor = 9, name = "Alpha"),
                channel(id = 20, number = 0, numberMinor = 2, name = "Zulu"),
                channel(id = 30, number = null, name = "Beta"),
                channel(id = 40, number = 0, name = "Alpha"),
                channel(id = 2, number = 0, name = ""),
                channel(id = 1, number = null, name = ""),
            ),
        )

        assertEquals(listOf(10L, 40L, 30L, 20L, 1L, 2L), ordered.map { it.id.value })
    }

    @Test
    fun duplicateNumberedChannelsPutBlankNamesLast() {
        val ordered = orderBrowseChannels(
            listOf(
                channel(id = 1, number = 7, name = ""),
                channel(id = 2, number = 7, name = "Zulu"),
                channel(id = 3, number = 7, name = "Alpha"),
            ),
        )

        assertEquals(listOf(3L, 2L, 1L), ordered.map { it.id.value })
    }

    private fun channel(
        id: Long,
        number: Long?,
        numberMinor: Long? = null,
        tagId: ChannelTagId? = null,
        name: String = "Channel $id",
    ) = Channel.create(
        id = ChannelId(id),
        name = name,
        number = number,
        numberMinor = numberMinor,
        tagIds = tagId?.let(::listOf),
    )
}
