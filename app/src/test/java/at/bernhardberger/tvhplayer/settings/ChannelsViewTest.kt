package at.bernhardberger.tvhplayer.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsViewTest {
    @Test
    fun eachLayoutDeclaresOnlyItsOwnOptionsAfterTheLayoutChoice() {
        assertEquals(listOf(ChannelsViewOption.LAYOUT), ChannelsView(ChannelsLayout.LIST).sections)
        assertEquals(
            listOf(ChannelsViewOption.LAYOUT, ChannelsViewOption.GRID_COLUMNS, ChannelsViewOption.CARD_PICTURE),
            ChannelsView(ChannelsLayout.GRID).sections,
        )
        assertEquals(
            listOf(ChannelsViewOption.LAYOUT, ChannelsViewOption.CARD_PICTURE),
            ChannelsView(ChannelsLayout.IMMERSIVE).sections,
        )
    }

    @Test
    fun optionsOfferTheirChoicesInPanelOrder() {
        assertEquals(ChannelsLayout.entries.map(ChannelsViewChoice::Layout), ChannelsViewOption.LAYOUT.choices)
        assertEquals(listOf(3, 4).map(ChannelsViewChoice::GridColumns), ChannelsViewOption.GRID_COLUMNS.choices)
        assertEquals(
            listOf(ChannelsCardPicture.PROGRAMME, ChannelsCardPicture.LOGO).map(ChannelsViewChoice::CardPicture),
            ChannelsViewOption.CARD_PICTURE.choices,
        )
    }

    @Test
    fun aChoiceChangesOnlyItsOwnValueAndUnusedValuesAreKept() {
        val grid = ChannelsView(ChannelsLayout.GRID, gridColumns = 3, cardPicture = ChannelsCardPicture.LOGO)
        val list = grid.with(ChannelsViewChoice.Layout(ChannelsLayout.LIST))
        assertEquals(ChannelsView(ChannelsLayout.LIST, 3, ChannelsCardPicture.LOGO), list)
        assertEquals(grid, list.with(ChannelsViewChoice.Layout(ChannelsLayout.GRID)))
        assertTrue(grid.isSelected(ChannelsViewChoice.GridColumns(3)))
        assertFalse(grid.isSelected(ChannelsViewChoice.GridColumns(4)))
        assertTrue(grid.isSelected(ChannelsViewChoice.CardPicture(ChannelsCardPicture.LOGO)))
    }

    @Test
    fun missingOrUnknownStoredValuesFallBackToTheDefaults() {
        assertEquals(ChannelsView(), resolveChannelsView(null, null, null))
        assertEquals(ChannelsView(ChannelsLayout.LIST, 4, ChannelsCardPicture.PROGRAMME), ChannelsView())
        // Round 3's ROWS and an unoffered column count are unknown values.
        assertEquals(ChannelsView(), resolveChannelsView("ROWS", 5, "POSTER"))
        assertEquals(
            ChannelsView(ChannelsLayout.IMMERSIVE, 3, ChannelsCardPicture.LOGO),
            resolveChannelsView("IMMERSIVE", 3, "LOGO"),
        )
        assertEquals(ChannelsView(ChannelsLayout.GRID), resolveChannelsView("GRID", null, "unknown"))
    }

    @Test
    fun theStoreKeepsEveryValueOfTheView() = runTest {
        val store = UiSettingsStore(InMemoryPreferencesDataStore())
        assertEquals(ChannelsView(), store.settings.first().channelsView)
        val view = ChannelsView(ChannelsLayout.IMMERSIVE, gridColumns = 3, cardPicture = ChannelsCardPicture.LOGO)
        store.setChannelsView(view)
        assertEquals(view, store.settings.first().channelsView)
    }
}
