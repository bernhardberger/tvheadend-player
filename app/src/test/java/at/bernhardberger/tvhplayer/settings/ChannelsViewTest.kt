package at.bernhardberger.tvhplayer.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsViewTest {
    @Test
    fun theListAlwaysShowsArtAndNamesImmersiveStartsWithArtTheGridWithLogosBothWithoutNames() {
        assertTrue(ChannelsView(ChannelsLayout.LIST).showsProgramArt)
        assertTrue(ChannelsView(ChannelsLayout.LIST).showsChannelNames)
        assertTrue(ChannelsView(ChannelsLayout.IMMERSIVE).showsProgramArt)
        assertFalse(ChannelsView(ChannelsLayout.IMMERSIVE).showsChannelNames)
        assertFalse(ChannelsView(ChannelsLayout.GRID).showsProgramArt)
        assertFalse(ChannelsView(ChannelsLayout.GRID).showsChannelNames)
    }

    @Test
    fun channelNamesToggleWithoutTouchingTheArt() {
        val named = ChannelsView().toggledChannelNames()
        assertTrue(named.showsChannelNames)
        assertTrue(named.showsProgramArt)
        assertEquals(ChannelsView(), named.toggledChannelNames())
    }

    @Test
    fun theSwitchesChangeOnlyTheirOwnLayout() {
        val grid = ChannelsView(ChannelsLayout.GRID).toggledProgramArt().toggledChannelNames()
        assertTrue(grid.showsProgramArt)
        assertTrue(grid.showsChannelNames)
        val immersive = grid.copy(layout = ChannelsLayout.IMMERSIVE)
        assertTrue("Immersive keeps its art", immersive.showsProgramArt)
        assertFalse("Immersive keeps its names off", immersive.showsChannelNames)
        assertEquals(ChannelsView(ChannelsLayout.GRID), grid.toggledProgramArt().toggledChannelNames())
    }

    @Test
    fun missingOrUnknownStoredValuesFallBackToTheDefaults() {
        assertEquals(ChannelsView(ChannelsLayout.IMMERSIVE, setOf(ChannelsLayout.IMMERSIVE), emptySet()), ChannelsView())
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
        assertEquals(
            ChannelsView(ChannelsLayout.GRID, channelNames = setOf(ChannelsLayout.GRID)),
            resolveChannelsView("GRID", channelNames = mapOf(ChannelsLayout.GRID to true, ChannelsLayout.LIST to true)::get) { null },
        )
    }

    @Test
    fun theStoreKeepsEveryValueOfTheView() = runTest {
        val store = UiSettingsStore(InMemoryPreferencesDataStore())
        assertEquals(ChannelsView(), store.settings.first().channelsView)
        val view = ChannelsView(ChannelsLayout.GRID, setOf(ChannelsLayout.IMMERSIVE, ChannelsLayout.GRID))
        store.setChannelsView(view)
        assertEquals(view, store.settings.first().channelsView)
        val namedGrid = ChannelsView(ChannelsLayout.GRID, emptySet(), setOf(ChannelsLayout.GRID))
        store.setChannelsView(namedGrid)
        assertEquals(namedGrid, store.settings.first().channelsView)
    }
}
