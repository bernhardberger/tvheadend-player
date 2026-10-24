package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.components.ClosedDrawerWidth
import at.bernhardberger.tvhplayer.ui.components.CompactZapRowOffscreen
import at.bernhardberger.tvhplayer.ui.components.OpenDrawerWidth
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import org.junit.Rule
import org.junit.Test
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
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
    private val grid = ChannelsView(ChannelsLayout.GRID).toggledProgramArt()
    private val immersive = ChannelsView(ChannelsLayout.IMMERSIVE).toggledProgramArt()
    private val tagsButton = hasTestTag("channels-tags-button")
    private val viewButton = hasTestTag("channels-view-button")

    private fun card(id: Int) = hasTestTag("channel-card-$id")
    private fun row(id: Int) = hasTestTag("channel-row-$id")
    private fun group(id: Int?) = hasTestTag("channels-tags-${id ?: "all"}")
    private fun option(key: String) = hasTestTag("channels-view-$key")

    private fun awaitRail() = compose.waitUntil(10_000) {
        compose.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag("global-drawer-surface")))
            .fetchSemanticsNodes().isNotEmpty()
    }

    /** The Tags button names the active [tag], "All channels" while none narrows the list, and is read with it. */
    private fun assertTag(tag: String) {
        compose.onNode(tagsButton and hasText(tag) and hasAnyAncestor(hasTestTag("channels-header"))).assertExists()
        compose.onNode(tagsButton).assertContentDescriptionEquals("Tags, $tag")
    }

    /** Opens the picker from the Tags button and moves [steps] rows from the checked group, Up when negative. */
    private fun pickGroup(target: SemanticsMatcher, steps: Int) {
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(hasAnyAncestor(hasTestTag("channels-tags-panel")))
        repeat(kotlin.math.abs(steps)) { fixture.key(if (steps < 0) Key.DirectionUp else Key.DirectionDown) }
        fixture.awaitFocus(target)
        fixture.key(Key.DirectionCenter)
    }

    @Test fun gridEntersOnTheFirstCardAndStaysAtTheLastColumn() {
        fixture.show(grid)
        fixture.awaitFocus(card(1))
        assertTag("All channels")
        repeat(2) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(3))
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(card(3))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(6))
        assertEquals(ChannelId(6), fixture.selected)
        assertEquals("Focus alone never tunes", 0, fixture.plays)
    }

    @Test fun upFromTheFirstRowReachesTheHeaderAndDownReturnsToTheRememberedCard() {
        fixture.show(grid)
        fixture.awaitFocus(card(1))
        repeat(2) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(3))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
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
        fixture.awaitFocus(tagsButton)
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
        fixture.awaitFocus(tagsButton)
        pickGroup(group(1), steps = 1)
        compose.onNodeWithTag("channels-tags-panel").assertDoesNotExist()
        fixture.awaitFocus(card(1))
        assertTag("Favorites")
        repeat(2) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(3))
        fixture.key(Key.DirectionUp)
        pickGroup(group(2), steps = 1)
        fixture.awaitFocus(card(7))
        assertTag("Entertainment")
        fixture.key(Key.DirectionUp)
        pickGroup(group(1), steps = -1)
        fixture.awaitFocus(card(3))
        assertEquals("Focus alone never tunes", 0, fixture.plays)
    }

    @Test fun groupPickerOpensOnTheCurrentGroupAndBackClosesWithoutChange() {
        fixture.show(list)
        fixture.awaitFocus(row(1))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(group(null))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(group(1))
        fixture.key(Key.Back)
        compose.onNodeWithTag("channels-tags-panel").assertDoesNotExist()
        fixture.awaitFocus(tagsButton)
        assertTag("All channels")
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(row(1))
    }

    @Test fun tagsPanelOpensOnTheActiveTagAndBackReturnsToTheNamedButton() {
        fixture.show(list)
        fixture.awaitFocus(row(1))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
        assertTag("All channels")
        pickGroup(group(2), steps = 2)
        fixture.awaitFocus(row(7))
        assertTag("Entertainment")
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(group(2))
        fixture.key(Key.Back)
        compose.onNodeWithTag("channels-tags-panel").assertDoesNotExist()
        fixture.awaitFocus(tagsButton)
        assertTag("Entertainment")
    }

    @Test fun rightFromAListRowReachesTheHeaderAndDownReturnsToTheRow() {
        fixture.show(list)
        fixture.awaitFocus(row(1))
        repeat(3) { fixture.key(Key.DirectionDown) }
        fixture.awaitFocus(row(4))
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(tagsButton)
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(row(4))
        // Left undoes Right; the rail is the row's own start edge.
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(tagsButton)
        fixture.key(Key.DirectionLeft)
        fixture.awaitFocus(row(4))
        fixture.key(Key.DirectionLeft)
        awaitRail()
        assertEquals("Focus alone never tunes", 0, fixture.plays)
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

    @Test fun viewOptionsOfferProgramArtForEachLayoutAndEachKeepsItsValue() {
        fixture.show(list)
        fixture.awaitFocus(row(1))
        fixture.key(Key.DirectionUp)
        fixture.key(Key.DirectionRight)
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(option("layout-list"))
        // The list starts with the focused programme's picture behind it.
        compose.onNode(option("program-art")).assertIsOn()
        repeat(2) { fixture.key(Key.DirectionDown) }
        fixture.awaitFocus(option("program-art"))
        fixture.key(Key.DirectionCenter)
        compose.onNode(option("program-art")).assertIsOff()
        repeat(3) { fixture.key(Key.DirectionUp) }
        fixture.awaitFocus(option("layout-immersive"))
        fixture.key(Key.DirectionCenter)
        compose.onNode(option("program-art")).assertIsOff()
        repeat(3) { fixture.key(Key.DirectionDown) }
        fixture.awaitFocus(option("program-art"))
        fixture.key(Key.DirectionCenter)
        compose.onNode(option("program-art")).assertIsOn()
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(option("layout-grid"))
        fixture.key(Key.DirectionCenter)
        // Immersive's art does not reach the grid.
        compose.onNode(option("program-art")).assertIsOff()
        assertEquals(ChannelsView(ChannelsLayout.GRID, setOf(ChannelsLayout.IMMERSIVE)), fixture.channelsView)
    }

    @Test fun cardPictureLeadsWithTheProgrammeAndFallsBackToTheLogo() {
        fixture.show(grid, imageLoader = fixture.fixtureArt())
        fixture.awaitFocus(card(1))
        compose.onNodeWithTag("channels-grid-1-art", useUnmergedTree = true).assertExists()
        // The Kettle Hour has no programme picture, so its card shows the logo.
        compose.onNodeWithTag("channels-grid-6-picon", useUnmergedTree = true).assertExists()
        compose.runOnIdle { fixture.channelsView = grid.toggledProgramArt() }
        compose.onNodeWithTag("channels-grid-1-art", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("channels-grid-1-picon", useUnmergedTree = true).assertExists()
    }

    @Test fun gridCardsLeadWithTheChannelAndStartTheProgrammeLineWithItsTime() {
        fixture.show(grid)
        fixture.awaitFocus(card(1))
        compose.onNodeWithTag("channels-grid-1-identity", useUnmergedTree = true).assertTextEquals("1 Ridge Earth")
        val programmeLine = hasAnyAncestor(hasTestTag("channels-grid-1-now"))
        compose.onNode(hasTestTag("channel-programme-title") and programmeLine, useUnmergedTree = true)
            .assertTextEquals("Ridge Light")
        compose.onNode(hasTestTag("channel-programme-start") and programmeLine, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("channels-grid-1-left", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("channels-grid-1-start", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun singleGroupHidesTheGroupButtonAndUpReachesViewOptions() {
        fixture.show(list, tags = false)
        fixture.awaitFocus(row(1))
        compose.onNode(tagsButton).assertDoesNotExist()
        compose.onNodeWithTag("channels-list").assertExists()
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(viewButton)
        // On the list the header's start edge leads back to the row, whose own start edge is the rail.
        fixture.key(Key.DirectionLeft)
        fixture.awaitFocus(row(1))
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
        fixture.awaitFocus(tagsButton)
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(hasTestTag("channels-show-all"))
        fixture.key(Key.DirectionCenter)
        fixture.awaitFocus(card(1))
        assertTag("All channels")
    }

    @Test fun emptyGroupWithoutAllChannelsLeavesFocusOnTheGroupButton() {
        fixture.show(grid, emptyGroup = true, allChannels = false)
        fixture.awaitFocus(card(1))
        assertTag("Favorites")
        fixture.key(Key.DirectionUp)
        pickGroup(group(9), steps = 2)
        fixture.awaitFocus(tagsButton)
        compose.onNodeWithTag("channels-empty-group").assertExists()
        compose.onNodeWithTag("channels-show-all").assertDoesNotExist()
    }

    @Test fun listHeaderStartEdgeReachesTheRailInAnEmptyGroup() {
        fixture.show(list, emptyGroup = true, allChannels = false)
        fixture.awaitFocus(row(1))
        fixture.key(Key.DirectionUp)
        pickGroup(group(9), steps = 2)
        fixture.awaitFocus(tagsButton)
        // No row to go back to, so the start edge leads to the rail as on the other layouts.
        fixture.key(Key.DirectionLeft)
        awaitRail()
    }

    @Test fun listHeaderStartEdgeSkipsAllChannelsInAnEmptyGroup() {
        fixture.show(list, emptyGroup = true)
        fixture.awaitFocus(row(1))
        fixture.key(Key.DirectionUp)
        pickGroup(group(9), steps = 3)
        fixture.awaitFocus(hasTestTag("channels-show-all"))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
        fixture.key(Key.DirectionLeft)
        awaitRail()
    }

    @Test fun gridReturnsFromThePlayerOnThePlayingCardAndStaysAtTheLastRow() {
        fixture.show(grid, playerReturn = PlayerReturnFocus(ChannelId(17), ChannelId(3)))
        fixture.awaitFocus(card(17))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(17))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(card(14))
    }

    @Test fun gridChannelKeysPageByVisibleRowsAndKeepTheColumn() {
        fixture.show(grid, playerReturn = PlayerReturnFocus(ChannelId(2), ChannelId(2)))
        fixture.awaitFocus(card(2))
        fixture.key(Key.ChannelDown)
        fixture.awaitFocus(card(11))
        fixture.key(Key.ChannelUp)
        fixture.awaitFocus(card(2))
    }

    @Test fun gridHoldsTheFocusedRowBelowHalfOfTheRowAbove() {
        fixture.show(grid)
        fixture.awaitFocus(card(1))
        val rowHeight = compose.onNode(card(2)).fetchSemanticsNode().boundsInRoot.height
        val rowGap = compose.onNode(card(5)).fetchSemanticsNode().positionInRoot.y -
            compose.onNode(card(2)).fetchSemanticsNode().boundsInRoot.bottom
        // The grid reaches up to the screen's top, where the header scrolls away.
        val gridTop = compose.onNodeWithTag("channels-grid").fetchSemanticsNode().positionInRoot.y
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(4))
        compose.waitForIdle()
        val focusLine = compose.onNode(card(4)).fetchSemanticsNode().boundsInRoot.top
        assertEquals(
            "the lower half of the first row stays in view at the top",
            gridTop + with(compose.density) { ChannelsGridEdgeInset.toPx() } + rowHeight * ChannelsGridPeekFraction + rowGap,
            compose.onNode(card(5)).fetchSemanticsNode().positionInRoot.y, 1f,
        )
        repeat(2) { fixture.key(Key.DirectionDown) }
        fixture.awaitFocus(card(10))
        compose.waitForIdle()
        assertEquals("the rows move under the focus", focusLine, compose.onNode(card(10)).fetchSemanticsNode().boundsInRoot.top, .5f)
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(card(7))
        compose.waitForIdle()
        assertEquals("Up holds the place too", focusLine, compose.onNode(card(7)).fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun gridHeaderScrollsAwayWithTheRowsAndComesBackWithTheFirstRow() {
        val header = hasTestTag("channels-header")
        fixture.show(grid)
        fixture.awaitFocus(card(1))
        // Positions, not bounds: bounds clip to the parents the header leaves.
        val headerTop = compose.onNode(header).fetchSemanticsNode().positionInRoot.y
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(4))
        compose.waitForIdle()
        val scrolledHeader = compose.onNode(header).fetchSemanticsNode()
        assertTrue("the header leaves with the first row", scrolledHeader.positionInRoot.y + scrolledHeader.size.height <= 0.5f)
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(card(1))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
        compose.waitForIdle()
        assertEquals(headerTop, compose.onNode(header).fetchSemanticsNode().positionInRoot.y, 1f)
    }

    @Test fun immersiveDownStaysAndUpReachesTheHeader() {
        fixture.show(immersive)
        fixture.awaitFocus(card(1))
        assertTag("All channels")
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(card(2))
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(2))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
        fixture.key(Key.DirectionDown)
        fixture.awaitFocus(card(2))
        assertEquals("Focus alone never tunes", 0, fixture.plays)
    }

    @Test fun immersiveBodyStartsAtTheTopPaddingAndItsHeaderButtonsStayInTheTopSlot() {
        fixture.show(immersive)
        fixture.awaitFocus(card(1))
        val header = compose.onNodeWithTag("channels-header").fetchSemanticsNode()
        // The body starts at the top content padding, where the header sits, instead of below it.
        val body = header.parent!!.children.first { it.id != header.id }
        assertEquals("the immersive body starts at the top padding", header.positionInRoot.y, body.boundsInRoot.top, .5f)
        // The buttons sit in the 40dp slot at the top content padding, as the list's header has them.
        val top = header.positionInRoot.y
        val slot = with(compose.density) { TvBrowseHeaderHeight.toPx() }
        listOf(tagsButton, viewButton).forEach {
            val bounds = compose.onNode(it).fetchSemanticsNode().boundsInRoot
            assertTrue("$it starts in the slot", bounds.top >= top - .5f)
            assertTrue("$it ends in the slot", bounds.bottom <= top + slot + .5f)
        }
        // The row below still reaches the buttons with Up.
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
    }

    @Test fun headerNamesTheScreenAsItsPaneTitleAndTheTagsButtonShowsTheGroup() {
        fixture.show(list)
        fixture.awaitFocus(row(1))
        compose.onNodeWithTag("channels-header").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Channels"))
        compose.onNode(hasText("Channels") and hasAnyAncestor(hasTestTag("channels-header")), useUnmergedTree = true).assertDoesNotExist()
        assertTag("All channels")
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
        pickGroup(group(1), steps = 1)
        assertTag("Favorites")
    }

    @Test fun immersiveRowRunsUnderTheRailAndKeepsItsKeyline() {
        fixture.show(immersive)
        fixture.awaitFocus(card(1))
        val keyline = compose.onNode(card(1)).getUnclippedBoundsInRoot().left
        repeat(2) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(3))
        compose.waitForIdle()
        assertEquals(keyline.value, compose.onNode(card(3)).getUnclippedBoundsInRoot().left.value, 1f)
        // The passed card is not clipped at the content's start: the row reaches under the drawer at
        // its widest, beyond the screen edge while the drawer is closed.
        val row = compose.onNodeWithTag("channels-immersive-cards").getUnclippedBoundsInRoot()
        assertEquals(-(OpenDrawerWidth - ClosedDrawerWidth + CompactZapRowOffscreen).value, row.left.value, 1f)
    }

    @Test fun openingTheDrawerMovesTheRowAndTheLogoWhileTheRowStillReachesTheScreenEdge() {
        fixture.show(immersive.toggledProgramArt())
        fixture.awaitFocus(card(1))
        // The logo comes with its glow, which is baked off the main thread.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("channels-immersive-picon").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val logo = compose.onNodeWithTag("channels-immersive-picon").getUnclippedBoundsInRoot().left
        repeat(2) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(3))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
        fixture.key(Key.DirectionLeft)
        awaitRail()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val opened = (OpenDrawerWidth - ClosedDrawerWidth).value
        assertEquals(logo.value + opened, compose.onNodeWithTag("channels-immersive-picon").getUnclippedBoundsInRoot().left.value, 1f)
        // Moved by the drawer, the row still begins at the screen edge.
        val row = compose.onNodeWithTag("channels-immersive-cards").getUnclippedBoundsInRoot()
        assertEquals(-CompactZapRowOffscreen.value, row.left.value, 1f)
    }

    @Test fun immersiveLeftAtTheRowStartReachesTheRailAndTheRightEdgeStays() {
        fixture.show(immersive, playerReturn = PlayerReturnFocus(ChannelId(18), ChannelId(3)))
        fixture.awaitFocus(card(18))
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(card(18))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
        pickGroup(group(1), steps = 1)
        fixture.awaitFocus(card(1) and hasAnyAncestor(hasTestTag("channels-immersive-cards")))
        assertTag("Favorites")
        fixture.key(Key.DirectionLeft)
        awaitRail()
        // The header is nearer the rail than the bottom row; Right still returns to the card.
        fixture.key(Key.DirectionRight)
        fixture.awaitFocus(card(1))
    }

    @Test fun immersiveGroupPickerReturnsToEachGroupsCard() {
        fixture.show(immersive)
        fixture.awaitFocus(card(1))
        repeat(3) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(4))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
        pickGroup(group(2), steps = 2)
        fixture.awaitFocus(card(7))
        assertTag("Entertainment")
        repeat(3) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(10))
        fixture.key(Key.DirectionUp)
        fixture.awaitFocus(tagsButton)
        pickGroup(group(null), steps = -2)
        fixture.awaitFocus(card(4))
    }

    @Test fun immersiveChannelKeysPageByTheCardsOnScreen() {
        fixture.show(immersive, playerReturn = PlayerReturnFocus(ChannelId(2), ChannelId(2)))
        fixture.awaitFocus(card(2))
        fixture.key(Key.ChannelDown)
        fixture.awaitFocus(card(5))
        fixture.key(Key.ChannelUp)
        fixture.awaitFocus(card(2))
    }

    @Test fun immersiveBackgroundShowsArtThenTheLogoWithoutArt() {
        fixture.show(immersive, playerReturn = PlayerReturnFocus(ChannelId(1), ChannelId(1)))
        fixture.awaitFocus(card(1))
        compose.onNodeWithTag("channels-immersive-art").assertExists()
        compose.onNodeWithTag("channels-immersive-subtitle").assertTextEquals("Winter on the ridge")
        // The picture brings its colour; without one the logo's colour glows instead.
        compose.onNodeWithTag("channels-backdrop").assertExists()
        repeat(7) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(8))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("channels-immersive-picon").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onNodeWithTag("channels-immersive-art").assertDoesNotExist()
        compose.onNodeWithTag("channels-logo-glow").assertExists()
        compose.onNodeWithTag("channels-immersive-picon").assertExists()
        compose.onNodeWithTag("channels-immersive-title").assertTextEquals("No program information")
        // Without programme lines the title still sits on the row instead of leaving a gap.
        val hero = compose.onNodeWithTag("channels-immersive-hero").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag("channels-immersive-title").getUnclippedBoundsInRoot()
        assertEquals(hero.bottom.value, title.bottom.value, 1f)
    }

    @Test fun immersiveLogoWithoutArtComesOnlyOnceItHasLoaded() {
        val logos = CompletableDeferred<Unit>()
        fixture.show(immersive, playerReturn = PlayerReturnFocus(ChannelId(1), ChannelId(1)),
            imageLoader = ImageLoader.Builder(fixture.context).components {
                add(Interceptor { chain ->
                    // Logos wait; the samples of their colour and the programme pictures load at once.
                    val id = (chain.request.data as? AppArtworkSource)?.id
                    if (id != null && id.value <= 18 && chain.request.memoryCachePolicy.writeEnabled) logos.await()
                    SuccessResult(Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888).asImage(), chain.request, DataSource.MEMORY)
                })
            }.build())
        fixture.awaitFocus(card(1))
        repeat(7) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(8))
        compose.mainClock.advanceTimeBy(800)
        // The glow bakes off the main thread; the backdrop still waits for the logo, well within its timeout.
        Thread.sleep(500)
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("channels-logo-glow").assertExists()
        compose.onNodeWithTag("channels-immersive-picon").assertDoesNotExist()
        logos.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("channels-immersive-picon").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun aGuideStillOnItsWayShowsPlaceholderLinesInsteadOfNoEpg() {
        fixture.show(immersive, guidePending = true)
        fixture.awaitFocus(card(1))
        compose.onNodeWithTag("channels-immersive-loading").assertExists()
        compose.onNodeWithTag("channels-immersive-1-loading", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("No program information", useUnmergedTree = true).assertCountEquals(0)
        fixture.channelsView = ChannelsView(ChannelsLayout.LIST)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("channel-programme-loading", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodesWithText("No program information", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun immersiveBackgroundFollowsFocusAfterAnOrdinaryEntry() {
        fixture.show(immersive)
        fixture.awaitFocus(card(1))
        compose.onNodeWithTag("channels-immersive-art").assertExists()
        repeat(7) { fixture.key(Key.DirectionRight) }
        fixture.awaitFocus(card(8))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("channels-immersive-picon").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onNodeWithTag("channels-immersive-art").assertDoesNotExist()
        compose.onNodeWithTag("channels-immersive-picon").assertExists()
    }

    @Test fun immersiveBackgroundMatchesLogoCardsWithTheLogo() {
        fixture.show(immersive.toggledProgramArt(), playerReturn = PlayerReturnFocus(ChannelId(1), ChannelId(1)))
        fixture.awaitFocus(card(1))
        // The logo comes with its backdrop, once that has loaded.
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onNodeWithTag("channels-immersive-art").assertDoesNotExist()
        compose.onNodeWithTag("channels-immersive-picon").assertExists()
    }

    @Test fun gridAndListDrawNoBackdrop() {
        fixture.show(grid, playerReturn = PlayerReturnFocus(ChannelId(1), ChannelId(1)))
        fixture.awaitFocus(card(1))
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onNodeWithTag("channels-immersive-background").assertDoesNotExist()
        fixture.channelsView = list
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onNodeWithTag("channels-immersive-background").assertDoesNotExist()
        compose.onNodeWithTag("channels-backdrop").assertDoesNotExist()
        fixture.channelsView = immersive
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onNodeWithTag("channels-immersive-background").assertExists()
    }

    @Test fun immersiveBackgroundDrawsNothingOverWarmPlayback() {
        fixture.show(immersive, playerReturn = PlayerReturnFocus(ChannelId(1), ChannelId(1)), warmPlayback = true)
        fixture.awaitFocus(card(1))
        compose.onNodeWithTag("channels-immersive-background").assertDoesNotExist()
        compose.onNodeWithTag("channels-immersive-art").assertDoesNotExist()
    }
}
