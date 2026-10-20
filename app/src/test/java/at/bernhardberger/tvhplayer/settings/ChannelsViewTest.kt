package at.bernhardberger.tvhplayer.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsViewTest {
    @Test
    fun eachArtSwitchNamesTheLayoutsThatUseIt() {
        assertEquals(setOf(ChannelsLayout.GRID, ChannelsLayout.IMMERSIVE), ChannelsArtwork.CARDS.layouts)
        assertEquals(setOf(ChannelsLayout.LIST, ChannelsLayout.GRID), ChannelsArtwork.BACKDROP.layouts)
    }

    @Test
    fun aSwitchChangesOnlyItsOwnValueAndALayoutChangeKeepsBoth() {
        val view = ChannelsView(ChannelsLayout.GRID)
        assertFalse(view.shows(ChannelsArtwork.CARDS))
        assertTrue(view.shows(ChannelsArtwork.BACKDROP))
        val cards = view.toggled(ChannelsArtwork.CARDS)
        assertEquals(ChannelsView(ChannelsLayout.GRID, cardArt = true), cards)
        assertEquals(ChannelsView(ChannelsLayout.GRID, cardArt = true, backdropArt = false), cards.toggled(ChannelsArtwork.BACKDROP))
        assertEquals(ChannelsView(ChannelsLayout.LIST, cardArt = true), cards.copy(layout = ChannelsLayout.LIST))
    }

    @Test
    fun missingOrUnknownStoredValuesFallBackToTheDefaults() {
        assertEquals(ChannelsView(ChannelsLayout.IMMERSIVE, cardArt = false, backdropArt = true), ChannelsView())
        assertEquals(ChannelsView(), resolveChannelsView(null, null, null))
        // Round 3's ROWS is an unknown value.
        assertEquals(ChannelsView(), resolveChannelsView("ROWS", null, null))
        assertEquals(ChannelsView(ChannelsLayout.IMMERSIVE, cardArt = true, backdropArt = false), resolveChannelsView("IMMERSIVE", true, false))
    }

    @Test
    fun theStoreKeepsEveryValueOfTheView() = runTest {
        val store = UiSettingsStore(InMemoryPreferencesDataStore())
        assertEquals(ChannelsView(), store.settings.first().channelsView)
        val view = ChannelsView(ChannelsLayout.IMMERSIVE, cardArt = true, backdropArt = false)
        store.setChannelsView(view)
        assertEquals(view, store.settings.first().channelsView)
    }
}
