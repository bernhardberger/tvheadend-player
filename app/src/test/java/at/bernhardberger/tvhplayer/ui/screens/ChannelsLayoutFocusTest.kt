package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.settings.ChannelsCardPicture
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import org.junit.Assert.assertEquals
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D-pad contract of the Channels layouts: entry, header, panels, edges, paging and return focus. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class ChannelsLayoutFocusTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private val fixture = ChannelsLayoutFixture(compose, images)

    private val list = ChannelsView(ChannelsLayout.LIST)
    private val grid = ChannelsView(ChannelsLayout.GRID)
    private val rows = ChannelsView(ChannelsLayout.ROWS)
    private val groupButton = hasTestTag("channels-group-button")
    private val viewButton = hasTestTag("channels-view-button")

    private fun card(id: Int) = hasTestTag("channel-card-$id")
    private fun row(id: Int) = hasTestTag("channel-row-$id")
    private fun group(id: Int?) = hasTestTag("channels-group-${id ?: "all"}")
    private fun option(key: String) = hasTestTag("channels-view-$key")

    private fun awaitRail() = compose.waitUntil(10_000) {
        compose.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag("global-drawer-surface")))
            .fetchSemanticsNodes().isNotEmpty()
    }

    private fun assertTitle(title: String) =
        compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag("channels-header"))).assertExists()

    /** Opens the picker from the group button and moves [steps] rows from the checked group, Up when negative. */
    private fun pickGroup(target: SemanticsMatcher, steps: Int) {
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(hasAnyAncestor(hasTestTag("channels-group-panel")))
        repeat(kotlin.math.abs(steps)) { fixture.key(if (steps < 0) Key.DirectionUp else Key.DirectionDown) }
        fixture.awaitFocus(target)
        fixture.key(Key.DirectionCenter)
    }

    @Test fun gridEntersOnTheFirstCardAndStaysAtTheLastColumn() {
        fixture.show(grid)
        fixture.awaitFocus(card(1))
        assertTitle("All channels")
        repeat(3) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(4))
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(card(4))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(8))
        assertEquals(ChannelId(8), fixture.selected)
        assertEquals("Focus alone never tunes", 0, fixture.plays)
    }

    @Test fun upFromTheFirstRowReachesTheHeaderAndDownReturnsToTheRememberedCard() {
        fixture.show(grid)
        fixture.awaitFocus(card(1))
        repeat(2) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(3))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(groupButton)
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(viewButton)
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(viewButton)
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(viewButton)
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(3))
    }

    @Test fun leftFromTheFirstHeaderButtonAndTheFirstColumnReachTheRail() {
        fixture.show(grid)
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(groupButton)
        fixture.key(Key.DirectionLeft)
        awaitRail()
        // Right from the rail re-enters the content on its remembered card, not the header.
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionLeft)
        awaitRail()
    }

    @Test fun groupPickerAppliesOnOkAndEachGroupKeepsItsCard() {
        fixture.show(grid)
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(groupButton)
        pickGroup(group(1), steps = 1)
        compose.onNodeWithTag("channels-group-panel").assertDoesNotExist()
        fixture.awaitFocus(card(1))
        assertTitle("Favorites")
        repeat(2) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(3))
        fixture.key(Key.DirectionUp)
        pickGroup(group(2), steps = 1)
        fixture.awaitFocus(card(7))
        assertTitle("Entertainment")
        fixture.key(Key.DirectionUp)
        pickGroup(group(1), steps = -1)
        fixture.awaitFocus(card(3))
        assertEquals("Focus alone never tunes", 0, fixture.plays)
    }

    @Test fun groupPickerOpensOnTheCurrentGroupAndBackClosesWithoutChange() {
        fixture.show(list)
        fixture.awaitFocus(row(1))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(groupButton)
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(group(null))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(group(1))
        fixture.key(Key.Back)
        compose.onNodeWithTag("channels-group-panel").assertDoesNotExist()
        fixture.awaitFocus(groupButton)
        assertTitle("All channels")
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(row(1))
    }

    @Test fun viewOptionsApplyLiveKeepTheFocusedChannelAndBackReturnsToTheButton() {
        fixture.show(grid)
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(card(2))
        fixture.key(Key.DirectionUp)
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(viewButton)
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(option("layout-grid"))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(option("layout-list"))
        fixture.key(Key.DirectionCenter)
        compose.onNodeWithTag("channels-list").assertExists()
        compose.onNodeWithTag("channels-view-panel").assertExists()
        fixture.awaitFocus(option("layout-list"))
        fixture.key(Key.Back)
        fixture.awaitFocus(viewButton)
        assertEquals(ChannelsLayout.LIST, fixture.channelsView.layout)
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(row(2))
    }

    @Test fun viewOptionsOfferOnlyTheCurrentLayoutsOptionsAndKeepTheOthers() {
        fixture.show(list)
        fixture.awaitFocus(row(1))
        fixture.key(Key.DirectionUp)
        fixture.key(Key.DirectionRight)
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(option("layout-list"))
        compose.onNode(option("columns-3")).assertDoesNotExist()
        compose.onNode(option("picture-logo")).assertDoesNotExist()
        fixture.key(Key.DirectionDown)
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(option("layout-grid"))
        repeat(2) { fixture.key(Key.DirectionDown) }
        fixture.awaitFocus(option("columns-3"))
        fixture.key(Key.DirectionCenter)
        repeat(3) { fixture.key(Key.DirectionDown) }
        fixture.awaitFocus(option("picture-logo"))
        fixture.key(Key.DirectionCenter)
        assertEquals(ChannelsView(ChannelsLayout.GRID, 3, ChannelsCardPicture.LOGO), fixture.channelsView)
        repeat(4) { fixture.key(Key.DirectionUp) }
        fixture.awaitFocus(option("layout-rows"))
        fixture.key(Key.DirectionCenter)
        compose.onNode(option("columns-3")).assertDoesNotExist()
        compose.onNode(option("picture-logo")).assertExists()
        assertEquals("Rows keep the grid's columns", ChannelsView(ChannelsLayout.ROWS, 3, ChannelsCardPicture.LOGO), fixture.channelsView)
    }

    @Test fun cardPictureLeadsWithTheProgrammeAndFallsBackToTheLogo() {
        fixture.show(grid, imageLoader = fixture.fixtureArt())
        fixture.awaitFocus(card(1))
        compose.onNodeWithTag("channels-grid-1-art", useUnmergedTree = true).assertExists()
        // The Kettle Hour has no programme picture, so its card shows the logo.
        compose.onNodeWithTag("channels-grid-6-picon", useUnmergedTree = true).assertExists()
        compose.runOnIdle { fixture.channelsView = grid.copy(cardPicture = ChannelsCardPicture.LOGO) }
        compose.onNodeWithTag("channels-grid-1-art", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("channels-grid-1-picon", useUnmergedTree = true).assertExists()
    }

    @Test fun singleGroupHidesTheGroupButtonAndUpReachesViewOptions() {
        fixture.show(list, tags = false)
        fixture.awaitFocus(row(1))
        compose.onNode(groupButton).assertDoesNotExist()
        compose.onNodeWithTag("channels-list").assertExists()
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(viewButton)
        fixture.key(Key.DirectionLeft)
        awaitRail()
    }

    @Test fun emptyGroupOffersAllChannels() {
        fixture.show(grid, emptyGroup = true)
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionUp)
        pickGroup(group(9), steps = 3)
        fixture.awaitFocus(hasTestTag("channels-show-all"))
        compose.onNodeWithTag("channels-empty-group").assertExists()
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(groupButton)
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(hasTestTag("channels-show-all"))
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(card(1))
        assertTitle("All channels")
    }

    @Test fun emptyGroupWithoutAllChannelsLeavesFocusOnTheGroupButton() {
        fixture.show(grid, emptyGroup = true, allChannels = false)
        fixture.awaitFocus(card(1))
        assertTitle("Favorites")
        fixture.key(Key.DirectionUp)
        pickGroup(group(9), steps = 2)
        fixture.awaitFocus(groupButton)
        compose.onNodeWithTag("channels-empty-group").assertExists()
        compose.onNodeWithTag("channels-show-all").assertDoesNotExist()
    }

    @Test fun gridReturnsFromThePlayerOnThePlayingCardAndStaysAtTheLastRow() {
        fixture.show(grid, playerReturn = PlayerReturnFocus(ChannelId(17), ChannelId(3)))
        fixture.awaitFocus(card(17))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(17))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(card(13))
    }

    @Test fun gridChannelKeysPageByVisibleRowsAndKeepTheColumn() {
        fixture.show(grid, playerReturn = PlayerReturnFocus(ChannelId(2), ChannelId(2)))
        fixture.awaitFocus(card(2))
        fixture.key(Key.ChannelDown)
        fixture.awaitFocus(card(14))
        fixture.key(Key.ChannelUp)
        fixture.awaitFocus(card(2))
    }

    @Test fun gridOfThreeColumnsWrapsAfterTheThirdCard() {
        fixture.show(grid.copy(gridColumns = 3))
        fixture.awaitFocus(card(1))
        repeat(3) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(3))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(6))
    }

    @Test fun rowsMoveBetweenGroupsAndEachRowKeepsItsCard() {
        fixture.show(rows)
        fixture.awaitFocus(card(1))
        assertTitle("Channels")
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(card(2))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(1) and hasAnyAncestor(hasTestTag("channels-row-1-cards")))
        repeat(2) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(3))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(7) and hasAnyAncestor(hasTestTag("channels-row-2-cards")))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(7))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(card(3) and hasAnyAncestor(hasTestTag("channels-row-1-cards")))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(card(2) and hasAnyAncestor(hasTestTag("channels-row-all-cards")))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(groupButton)
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(2))
        assertEquals("Focus alone never tunes", 0, fixture.plays)
    }

    @Test fun rowsLeftAtTheRowStartReachesTheRailAndTheRightEdgeStays() {
        fixture.show(rows, playerReturn = PlayerReturnFocus(ChannelId(18), ChannelId(3)))
        fixture.awaitFocus(card(18))
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(card(18))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(1) and hasAnyAncestor(hasTestTag("channels-row-1-cards")))
        fixture.key(Key.DirectionLeft)
        awaitRail()
    }

    @Test fun rowsGroupPickerGoesToTheRowAndItsRememberedCard() {
        fixture.show(rows)
        fixture.awaitFocus(card(1))
        repeat(2) { fixture.key(Key.DirectionDown) }
        repeat(3) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(10))
        repeat(2) { fixture.key(Key.DirectionUp) }
        fixture.awaitFocus(card(1) and hasAnyAncestor(hasTestTag("channels-row-all-cards")))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(groupButton)
        pickGroup(group(2), steps = 2)
        fixture.awaitFocus(card(10) and hasAnyAncestor(hasTestTag("channels-row-2-cards")))
    }

    @Test fun rowsChannelKeysPageByTheCardsOnScreen() {
        fixture.show(rows, playerReturn = PlayerReturnFocus(ChannelId(2), ChannelId(2)))
        fixture.awaitFocus(card(2))
        fixture.key(Key.ChannelDown)
        fixture.awaitFocus(card(5))
        fixture.key(Key.ChannelUp)
        fixture.awaitFocus(card(2))
    }

    @Test fun rowsBackgroundShowsArtThenTheLogoWithoutArt() {
        fixture.show(rows, playerReturn = PlayerReturnFocus(ChannelId(1), ChannelId(1)))
        fixture.awaitFocus(card(1))
        compose.onNodeWithTag("channels-rows-art").assertExists()
        repeat(7) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(8))
        compose.onNodeWithTag("channels-rows-art").assertDoesNotExist()
        compose.onNodeWithTag("channels-rows-picon").assertExists()
        compose.onNodeWithTag("channels-rows-title").assertTextEquals("No EPG")
    }

    @Test fun rowsBackgroundDrawsNothingOverWarmPlayback() {
        fixture.show(rows, playerReturn = PlayerReturnFocus(ChannelId(1), ChannelId(1)), warmPlayback = true)
        fixture.awaitFocus(card(1))
        compose.onNodeWithTag("channels-rows-background").assertDoesNotExist()
        compose.onNodeWithTag("channels-rows-art").assertDoesNotExist()
    }
}
