package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelScopePolicyTest {
    @Test
    fun channelsAreOrderedByTypedNumberWithDeterministicMinorDuplicateAndMissingTies() {
        val channels = listOf(
            channel(id = 90, number = 90),
            channel(id = 99, number = null),
            channel(id = 12, number = 2, numberMinor = 1),
            channel(id = 11, number = 2),
            channel(id = 10, number = 2),
            channel(id = 1, number = 1),
        )

        val scope = resolveChannelScope(channels, emptyList(), requestedTagId = null)

        assertEquals(listOf(1L, 10L, 11L, 12L, 90L, 99L), scope.visibleChannels.map { it.id.value })
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
    fun missingMajorNumbersStillUseMinorNumberBeforeTheIdentityTieBreak() {
        val scope = resolveChannelScope(
            channels = listOf(
                channel(id = 10, number = null, numberMinor = 9),
                channel(id = 20, number = null, numberMinor = 2),
                channel(id = 30, number = null),
            ),
            tags = emptyList(),
            requestedTagId = null,
        )

        assertEquals(listOf(30L, 20L, 10L), scope.visibleChannels.map { it.id.value })
    }

    private fun channel(
        id: Long,
        number: Long?,
        numberMinor: Long? = null,
        tagId: ChannelTagId? = null,
    ) = Channel.create(
        id = ChannelId(id),
        name = "Channel $id",
        number = number,
        numberMinor = numberMinor,
        tagIds = tagId?.let(::listOf),
    )
}
