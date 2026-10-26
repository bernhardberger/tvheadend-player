package at.bernhardberger.tvhplayer.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsViewTest {
    @Test
    fun theListAlwaysShowsArtImmersiveStartsWithArtWithoutNamesAndTheGridWithLogos() {
        assertTrue(ChannelsView(ChannelsLayout.LIST).showsProgramArt)
        assertTrue(ChannelsView(ChannelsLayout.IMMERSIVE).showsProgramArt)
        assertFalse(ChannelsView(ChannelsLayout.IMMERSIVE).channelNames)
        assertFalse(ChannelsView(ChannelsLayout.GRID).showsProgramArt)
    }

    @Test
    fun channelNamesToggleWithoutTouchingTheArt() {
        val named = ChannelsView().toggledChannelNames()
        assertTrue(named.channelNames)
        assertTrue(named.showsProgramArt)
        assertEquals(ChannelsView(), named.toggledChannelNames())
    }

    @Test
    fun theSwitchChangesOnlyItsOwnLayout() {
        val grid = ChannelsView(ChannelsLayout.GRID).toggledProgramArt()
        assertTrue(grid.showsProgramArt)
        assertTrue("Immersive keeps its art", grid.copy(layout = ChannelsLayout.IMMERSIVE).showsProgramArt)
        assertEquals(ChannelsView(ChannelsLayout.GRID), grid.toggledProgramArt())
    }

    @Test
    fun missingOrUnknownStoredValuesFallBackToTheDefaults() {
        assertEquals(ChannelsView(ChannelsLayout.IMMERSIVE, setOf(ChannelsLayout.IMMERSIVE), channelNames = false), ChannelsView())
        assertEquals(ChannelsView(), resolveChannelsView(null) { null })
        // Round 3's ROWS is an unknown value.
        assertEquals(ChannelsView(), resolveChannelsView("ROWS") { null })
        assertEquals(
            ChannelsView(ChannelsLayout.GRID, setOf(ChannelsLayout.IMMERSIVE)),
            resolveChannelsView("GRID", programArt = mapOf(ChannelsLayout.IMMERSIVE to true, ChannelsLayout.LIST to false)::get),
        )
        // A stored List value from before the list lost its switch is ignored.
        assertEquals(
            ChannelsView(ChannelsLayout.GRID),
            resolveChannelsView("GRID", programArt = mapOf(ChannelsLayout.GRID to false, ChannelsLayout.LIST to true)::get),
        )
    }

    @Test
    fun theStoreKeepsEveryValueOfTheView() = runTest {
        val store = UiSettingsStore(InMemoryPreferencesDataStore())
        assertEquals(ChannelsView(), store.settings.first().channelsView)
        val view = ChannelsView(ChannelsLayout.GRID, setOf(ChannelsLayout.IMMERSIVE, ChannelsLayout.GRID))
        store.setChannelsView(view)
        assertEquals(view, store.settings.first().channelsView)
        val logosWithNames = ChannelsView(ChannelsLayout.IMMERSIVE, emptySet(), channelNames = true)
        store.setChannelsView(logosWithNames)
        assertEquals(logosWithNames, store.settings.first().channelsView)
    }
}
