package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.ChannelTagUi
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelScopePolicyTest {
    private val news = ChannelTagUi(id = 10, name = "News", index = 1)
    private val sport = ChannelTagUi(id = 20, name = "Sport", index = 2)
    private val channels = listOf(
        channel(id = 1, number = 1, tags = setOf(10)),
        channel(id = 2, number = 2, tags = setOf(10, 20)),
        channel(id = 3, number = 3, tags = emptySet()),
    )

    @Test
    fun allChannelsIsAlwaysAvailableAndPreservesServerOrder() {
        val scope = resolveChannelScope(channels, listOf(news, sport), requestedTagId = null)

        assertNull(scope.activeTagId)
        assertEquals(listOf(1, 2, 3), scope.visibleChannels.map { it.id })
        assertNull(scope.fallback)
    }

    @Test
    fun selectedTagFiltersOnceWithoutChangingChannelOrderOrNumbers() {
        val scope = resolveChannelScope(channels, listOf(news, sport), requestedTagId = 20)

        assertEquals(20, scope.activeTagId)
        assertEquals(listOf(2), scope.visibleChannels.map { it.id })
        assertEquals(2, scope.visibleChannels.single().number)
    }

    @Test
    fun emptyTagRemainsSelectedAndRecoverable() {
        val emptyTag = ChannelTagUi(id = 30, name = "Empty", index = 3)

        val scope = resolveChannelScope(channels, listOf(news, sport, emptyTag), requestedTagId = 30)

        assertEquals(30, scope.activeTagId)
        assertEquals(emptyList<ChannelUi>(), scope.visibleChannels)
        assertNull(scope.fallback)
    }

    @Test
    fun removedOrRestrictedTagFallsBackToAllWithExplanation() {
        val scope = resolveChannelScope(channels, listOf(news), requestedTagId = 20)

        assertNull(scope.activeTagId)
        assertEquals(listOf(1, 2, 3), scope.visibleChannels.map { it.id })
        assertEquals(TagScopeFallback.TAG_UNAVAILABLE, scope.fallback)
    }

    @Test
    fun allChannelsOnlyHidesTagNavigationWithoutFilteringChannels() {
        val scope = resolveChannelScope(
            channels = channels,
            tags = listOf(news, sport),
            requestedTagId = null,
            visibility = ChannelScopeVisibility(
                configured = true,
                allChannelsVisible = true,
                visibleTagIds = emptySet(),
            ),
        )

        assertEquals(true, scope.allChannelsVisible)
        assertEquals(emptyList<ChannelTagUi>(), scope.tags)
        assertNull(scope.activeTagId)
        assertEquals(listOf(1, 2, 3), scope.visibleChannels.map { it.id })
    }

    @Test
    fun hidingAllChannelsSelectsFirstVisibleTag() {
        val scope = resolveChannelScope(
            channels = channels,
            tags = listOf(news, sport),
            requestedTagId = null,
            visibility = ChannelScopeVisibility(
                configured = true,
                allChannelsVisible = false,
                visibleTagIds = setOf(20),
            ),
        )

        assertEquals(false, scope.allChannelsVisible)
        assertEquals(listOf(sport), scope.tags)
        assertEquals(20, scope.activeTagId)
        assertEquals(listOf(2), scope.visibleChannels.map { it.id })
        assertEquals(TagScopeFallback.SCOPE_HIDDEN, scope.fallback)
    }

    @Test
    fun customVisibilityStartsFromAllAvailableScopesAndBecomesAnAllowlist() {
        val visibility = updateChannelScopeVisibility(
            current = ChannelScopeVisibility(),
            availableTagIds = setOf(10, 20),
            tagId = 20,
            visible = false,
        )

        assertEquals(true, visibility.configured)
        assertEquals(true, visibility.allChannelsVisible)
        assertEquals(setOf(10), visibility.visibleTagIds)
        assertEquals(false, visibility.isTagVisible(30))
    }

    @Test
    fun finalVisibleScopeCannotBeDisabled() {
        val allOnly = ChannelScopeVisibility(
            configured = true,
            allChannelsVisible = true,
            visibleTagIds = emptySet(),
        )
        val sportOnly = ChannelScopeVisibility(
            configured = true,
            allChannelsVisible = false,
            visibleTagIds = setOf(20),
        )

        assertEquals(
            allOnly,
            updateChannelScopeVisibility(allOnly, setOf(10, 20), tagId = null, visible = false),
        )
        assertEquals(
            sportOnly,
            updateChannelScopeVisibility(sportOnly, setOf(10, 20), tagId = 20, visible = false),
        )
    }

    @Test
    fun enablingAllChannelsRecoversFromMissingConfiguredTags() {
        val staleTagOnly = ChannelScopeVisibility(
            configured = true,
            allChannelsVisible = false,
            visibleTagIds = setOf(99),
        )

        assertEquals(
            ChannelScopeVisibility(
                configured = true,
                allChannelsVisible = true,
                visibleTagIds = emptySet(),
            ),
            updateChannelScopeVisibility(
                current = staleTagOnly,
                availableTagIds = setOf(10, 20),
                tagId = null,
                visible = true,
            ),
        )
    }

    @Test
    fun browsingFocusMovesToFirstVisibleWithoutChangingPlayback() {
        assertEquals(2, browsingFocusChannelId(listOf(channels[1]), currentFocusId = 1))
        assertEquals(2, browsingFocusChannelId(listOf(channels[1]), currentFocusId = 2))
        assertNull(browsingFocusChannelId(emptyList(), currentFocusId = 1))
    }

    @Test
    fun tagNavigationMovesBetweenAllChannelsAndServerTags() {
        val tags = listOf(news, sport)

        assertEquals(10, adjacentTagId(tags, activeTagId = null, direction = 1))
        assertEquals(20, adjacentTagId(tags, activeTagId = 10, direction = 1))
        assertEquals(10, adjacentTagId(tags, activeTagId = 20, direction = -1))
        assertNull(adjacentTagId(tags, activeTagId = 10, direction = -1))
    }

    @Test
    fun tagNavigationStopsAtEndpoints() {
        val tags = listOf(news, sport)

        assertNull(adjacentTagId(tags, activeTagId = null, direction = -1))
        assertEquals(20, adjacentTagId(tags, activeTagId = 20, direction = 1))
        assertNull(adjacentTagId(emptyList(), activeTagId = null, direction = 1))
    }

    @Test
    fun tagNavigationCanExcludeAllChannels() {
        val tags = listOf(news, sport)

        assertEquals(
            20,
            adjacentTagId(
                tags = tags,
                activeTagId = 10,
                direction = 1,
                allChannelsVisible = false,
            ),
        )
        assertEquals(
            10,
            adjacentTagId(
                tags = tags,
                activeTagId = 10,
                direction = -1,
                allChannelsVisible = false,
            ),
        )
    }

    private fun channel(id: Int, number: Int, tags: Set<Int>) = ChannelUi(
        id = id,
        name = "Channel $id",
        number = number,
        icon = null,
        tagIds = tags,
    )
}
