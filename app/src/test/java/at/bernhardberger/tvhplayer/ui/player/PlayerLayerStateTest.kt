package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerBackAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerLayerStateTest {
    /** The recording player's quick lists return to the timeline and, before any focus, to Pause. */
    @Test
    fun aQuickListReturnsToTheLastControlOnlyOverVisibleControls() = runTest {
        val state = PlayerLayerState(this, 5_000L, initialFocusedControl = "player-pause")
        state.chrome.showControls()
        state.showQuickList(PlaybackOptionsPage.AUDIO)
        state.closeQuickList()
        assertEquals("player-pause", state.restoreControl)
        state.onFocusRestored()

        state.onActionFocused("player-seekbar")
        state.showQuickList(PlaybackOptionsPage.SUBTITLES)
        state.closeQuickList()
        assertEquals("player-seekbar", state.restoreControl)
        state.onFocusRestored()

        // Over hidden controls the list leaves them hidden and restores nothing.
        state.chrome.hideControls()
        state.showQuickList(PlaybackOptionsPage.AUDIO)
        assertFalse(state.chrome.controlsVisible)
        state.closeQuickList()
        assertNull(state.restoreControl)
        state.dispose()
    }

    @Test
    fun aQuickListOverTheMenuReturnsToItsConfiguredControl() = runTest {
        for (overMenu in listOf(null, "player-settings")) {
            val state = PlayerLayerState(this, 5_000L, quickListOverMenuReturn = overMenu)
            state.chrome.showControls()
            state.onActionFocused("player-info")
            state.openOptions()
            state.showQuickList(PlaybackOptionsPage.AUDIO)
            state.closeQuickList()
            assertEquals(overMenu ?: "player-info", state.restoreControl)
            state.dispose()
        }
    }

    @Test
    fun closingInfoChoosesWhatTheChromeShows() = runTest {
        val state = PlayerLayerState(this, 5_000L)
        state.openInfo()
        assertTrue(state.infoOpen)
        assertFalse(state.chrome.hidden)
        state.closeInfo(controls = true)
        assertFalse(state.infoOpen)
        assertTrue(state.chrome.controlsVisible)
        assertTrue(state.restoreInfoFocus)
        state.onFocusRestored()

        state.openInfo()
        state.closeInfo(controls = false)
        assertFalse(state.chrome.controlsVisible)
        assertFalse(state.restoreInfoFocus)

        // A screen layer takes the chrome over: closing leaves it alone.
        state.chrome.showControls()
        state.openInfo()
        state.closeInfo(controls = null)
        assertFalse(state.chrome.controlsVisible)
        assertFalse(state.restoreInfoFocus)
        state.dispose()
    }

    @Test
    fun sharedBackStepsWalkTheOptionsAndLeaveTheRestToTheScreen() = runTest {
        val state = PlayerLayerState(this, 5_000L, initialFocusedControl = "player-pause")
        state.chrome.showControls()
        state.openOptions()
        state.showOptionsPage(PlaybackOptionsPage.AUDIO)
        assertTrue(state.applyBackAction(PlayerBackAction.RETURN_TO_OPTIONS_ROOT))
        assertEquals(PlaybackOptionsPage.ROOT, state.optionsPage)
        assertTrue(state.applyBackAction(PlayerBackAction.CLOSE_OPTIONS))
        assertNull(state.optionsPage)
        assertTrue(state.restoreOptionsFocus)
        state.onFocusRestored()

        state.showQuickList(PlaybackOptionsPage.SUBTITLES)
        assertTrue(state.applyBackAction(PlayerBackAction.RESTORE_AND_CLOSE_QUICK_LIST))
        assertNull(state.optionsPage)
        assertEquals("player-pause", state.restoreControl)

        state.updateStatsVisibility(true)
        assertTrue(state.applyBackAction(PlayerBackAction.HIDE_STATS))
        assertFalse(state.statsVisible)
        assertTrue(state.applyBackAction(PlayerBackAction.HIDE_CONTROLS))
        assertFalse(state.chrome.controlsVisible)

        for (action in listOf(PlayerBackAction.CLOSE_INFO, PlayerBackAction.CLOSE_PLAYER,
                PlayerBackAction.CANCEL_PENDING_SEEK, PlayerBackAction.CLOSE_CHANNEL_DRAWER)) {
            assertFalse(action.name, state.applyBackAction(action))
        }
        state.dispose()
    }

    @Test
    fun screenLayersCoverTheChromeAndCloseForASharedLayer() = runTest {
        var screenLayer = true
        var closes = 0
        val state = PlayerLayerState(
            scope = this,
            autoHideTimeoutMillis = 5_000L,
            screenLayerOpen = { screenLayer },
            closeScreenLayers = { closes++; screenLayer = false },
        )
        assertFalse(state.chrome.hidden)
        state.openInfo()
        assertEquals(1, closes)
        assertFalse(state.chrome.hidden)

        screenLayer = true
        state.yieldToScreenLayer()
        assertFalse(state.infoOpen)
        assertEquals(1, closes)
        assertFalse(state.chrome.hidden)
        state.showOptionsPage(PlaybackOptionsPage.ROOT)
        state.showQuickList(PlaybackOptionsPage.AUDIO)
        assertEquals(3, closes)
        state.closeQuickList()
        state.chrome.hideControls()
        assertTrue(state.chrome.hidden)
        state.dispose()
    }
}
