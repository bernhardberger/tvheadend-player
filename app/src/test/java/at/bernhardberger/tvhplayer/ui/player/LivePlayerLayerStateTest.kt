package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.playerForegroundLayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LivePlayerLayerStateTest {
    @Test
    fun quickZapKeepsTheTrayUntilExplicitDismissalAndDoesNotAutoHide() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.showControls()
        state.onActionFocused("player-settings")
        state.openChannelDrawer()
        state.core.chrome.updateAutoHideEligibility(false)
        repeat(3) { state.onChannelTuneRequested() }
        advanceTimeBy(20_000L)
        runCurrent()
        assertTrue(state.channelDrawerOpen)
        assertFalse(state.core.chrome.controlsVisible)
        state.dismissChannelDrawer()
        assertFalse(state.channelDrawerOpen)
        assertTrue(state.core.chrome.controlsVisible)
        assertEquals("player-settings", state.core.restoreControl)
        // A zap on the hidden player shows the Banner, not the controls.
        state.core.chrome.hideControls()
        state.onChannelTuneRequested()
        assertTrue(state.core.chrome.bannerVisible)
        assertFalse(state.core.chrome.controlsVisible)
    }

    @Test
    fun controlsRecordWhetherAZapOrTheViewerRevealedThem() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.showControls()
        assertEquals(PlayerControlsEntry.FROM_BANNER, state.core.chrome.controlsEntry)
        state.onChannelTuneRequested()
        assertTrue(state.core.chrome.controlsVisible)
        assertEquals(PlayerControlsEntry.FADE, state.core.chrome.controlsEntry)
        state.core.chrome.hideControls()
        state.showControls()
        assertEquals(PlayerControlsEntry.TRAVEL, state.core.chrome.controlsEntry)

        // Controls returning from the tray, options or info still move in as before.
        state.onChannelTuneRequested()
        state.openChannelDrawer()
        state.onChannelTuneRequested()
        assertEquals(PlayerControlsEntry.TRAVEL, state.core.chrome.controlsEntry)
        state.dismissChannelDrawer()
        assertEquals(PlayerControlsEntry.TRAVEL, state.core.chrome.controlsEntry)
        for (open in listOf(
            { state.core.showOptionsPage(PlaybackOptionsPage.ROOT) },
            { state.openInfo() },
        )) {
            state.onChannelTuneRequested()
            open()
            assertEquals(PlayerControlsEntry.TRAVEL, state.core.chrome.controlsEntry)
        }
        advanceTimeBy(20_000L)
        runCurrent()
    }

    @Test
    fun shelfDismissalRestoresInvokerWithoutChangingTuneCloseBehavior() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        for (action in listOf("player-pause", "player-settings", "player-stop")) {
            state.showControls()
            state.onActionFocused(action)
            state.openChannelDrawer()
            state.core.beginOpeningKeyCycle(19)
            state.dismissChannelDrawer()
            assertEquals(PlayerForegroundLayer.CONTROLS, playerForegroundLayer(state.foregroundContext()))
            assertEquals(action, state.core.restoreControl)
            assertEquals(19, state.core.revealingKeyCode)
            state.core.onFocusRestored()
            assertNull(state.core.restoreControl)
            state.core.endOpeningKeyCycle(19)
        }
        state.onActionFocused("player-settings")
        state.core.chrome.hideControls()
        state.openChannelDrawer()
        state.dismissChannelDrawer()
        assertEquals("player-pause", state.core.restoreControl)
        state.core.onFocusRestored()
        state.openChannelDrawer()
        state.closeChannelDrawer()
        assertFalse(state.core.chrome.controlsVisible)
        assertNull(state.core.restoreControl)
    }

    @Test
    fun aQuickListReturnsFocusToStop() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.showControls()
        state.onActionFocused("player-stop")
        state.core.showQuickList(PlaybackOptionsPage.AUDIO)
        state.core.closeQuickList()
        assertEquals("player-stop", state.core.restoreControl)
        state.core.onFocusRestored()
        state.dispose()
    }

    @Test
    fun layerOpeningsAreExclusiveAndCloseInForegroundOrder() = runTest {
        val state = LivePlayerLayerState(
            scope = this,
            autoHideTimeoutMillis = 5_000L,
        )

        state.openChannelDrawer()
        assertFalse(state.core.chrome.controlsVisible)
        assertTrue(state.channelDrawerOpen)

        state.core.showOptionsPage(PlaybackOptionsPage.AUDIO)
        assertTrue(state.core.chrome.controlsVisible)
        assertFalse(state.channelDrawerOpen)
        assertEquals(PlaybackOptionsPage.AUDIO, state.core.optionsPage)

        state.openInfo()
        assertFalse(state.core.chrome.controlsVisible)
        assertNull(state.core.optionsPage)
        assertFalse(state.channelDrawerOpen)
        assertTrue(state.core.infoOpen)

        state.core.openOptions()
        assertFalse(state.core.infoOpen)
        assertEquals(PlaybackOptionsPage.ROOT, state.core.optionsPage)

        state.openInfo()

        state.showRecordingConfirmation()
        assertEquals(
            PlayerForegroundLayer.CONFIRMATION,
            playerForegroundLayer(state.foregroundContext()),
        )

        state.dismissRecordingConfirmation()
        assertEquals(
            PlayerForegroundLayer.INFO,
            playerForegroundLayer(state.foregroundContext()),
        )

        state.closeInfo()
        assertEquals(
            PlayerForegroundLayer.CONTROLS,
            playerForegroundLayer(state.foregroundContext()),
        )

        state.core.chrome.hideControls()
        assertEquals(
            PlayerForegroundLayer.NONE,
            playerForegroundLayer(state.foregroundContext()),
        )
    }

    @Test
    fun openingKeyTokenEndsOnlyForItsMatchingKeyCycle() = runTest {
        val state = LivePlayerLayerState(
            scope = this,
            autoHideTimeoutMillis = 5_000L,
        )

        state.core.beginOpeningKeyCycle(keyCode = 23)
        assertEquals(23, state.core.revealingKeyCode)

        state.core.endOpeningKeyCycle(keyCode = 24)
        assertEquals(23, state.core.revealingKeyCode)

        state.core.endOpeningKeyCycle(keyCode = 23)
        assertNull(state.core.revealingKeyCode)
    }

    @Test
    fun optionKeysReplaceInfoTheDrawerAndStatsButNotAConfirmation() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        fun keyContext() = PlayerKeyContext(
            surface = PlayerSurface.LIVE,
            controlsVisible = state.core.chrome.controlsVisible,
            seekbarFocused = false,
            timeshiftAvailable = true,
            optionsOpen = state.core.optionsPage != null,
            statsOpen = state.core.statsVisible,
            infoOpen = state.core.infoOpen,
            drawerOpen = state.channelDrawerOpen,
            confirmationOpen =
                playerForegroundLayer(state.foregroundContext()) == PlayerForegroundLayer.CONFIRMATION,
        )

        state.openInfo()
        assertTrue(state.core.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MENU))
        assertFalse(state.core.infoOpen)
        assertEquals(PlaybackOptionsPage.ROOT, state.core.optionsPage)
        assertFalse(state.core.optionsQuickList)
        assertEquals(KeyEvent.KEYCODE_MENU, state.core.revealingKeyCode)
        assertEquals(PlayerForegroundLayer.OPTIONS_ROOT, playerForegroundLayer(state.foregroundContext()))
        state.core.endOpeningKeyCycle(KeyEvent.KEYCODE_MENU)

        state.core.closeOptions()
        state.openChannelDrawer()
        assertTrue(state.core.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK))
        assertFalse(state.channelDrawerOpen)
        assertEquals(PlaybackOptionsPage.AUDIO, state.core.optionsPage)
        assertTrue(state.core.optionsQuickList)
        assertEquals(PlayerForegroundLayer.OPTIONS_QUICK_LIST, playerForegroundLayer(state.foregroundContext()))
        state.core.endOpeningKeyCycle(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK)

        state.core.closeQuickList()
        state.core.chrome.hideControls()
        state.core.updateStatsVisibility(true)
        assertEquals(PlayerForegroundLayer.STATS, playerForegroundLayer(state.foregroundContext()))
        // No root fallback: the captions key opens the Subtitles list, and the short
        // list leaves the hidden controls hidden.
        assertTrue(state.core.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_CAPTIONS))
        assertEquals(PlaybackOptionsPage.SUBTITLES, state.core.optionsPage)
        assertTrue(state.core.optionsQuickList)
        assertFalse(state.core.chrome.controlsVisible)
        // Stats stay enabled; they return once the list closes.
        assertTrue(state.core.statsVisible)
        state.core.endOpeningKeyCycle(KeyEvent.KEYCODE_CAPTIONS)

        state.core.closeQuickList()
        state.openInfo()
        state.showRecordingConfirmation()
        assertFalse(state.core.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MENU))
        assertTrue(state.core.infoOpen)
        assertNull(state.core.optionsPage)
        assertNull(state.core.revealingKeyCode)
        assertEquals(PlayerForegroundLayer.CONFIRMATION, playerForegroundLayer(state.foregroundContext()))

        assertFalse(state.core.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_DPAD_CENTER))
        state.dispose()
    }

    @Test
    fun quickListKeysSwitchMoveDownAndReturnFocusWithoutRevealingControls() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        fun keyContext() = PlayerKeyContext(
            surface = PlayerSurface.LIVE,
            controlsVisible = state.core.chrome.controlsVisible,
            seekbarFocused = false,
            timeshiftAvailable = true,
            optionsOpen = state.core.optionsPage != null,
        )
        state.showControls()
        state.onActionFocused("player-settings")

        assertTrue(state.core.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK))
        assertTrue(state.core.chrome.controlsVisible)
        state.core.endOpeningKeyCycle(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK)
        val presses = state.core.quickList.keyPresses
        state.core.onKeyDown()
        assertEquals(presses + 1, state.core.quickList.keyPresses)

        // The same key again moves focus down instead of reopening.
        assertTrue(state.core.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK))
        assertEquals(1, state.core.quickList.moveDownRequests)
        assertEquals(PlaybackOptionsPage.AUDIO, state.core.optionsPage)
        state.core.endOpeningKeyCycle(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK)

        // The other key switches lists, Menu opens the full root.
        assertTrue(state.core.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_CAPTIONS))
        assertEquals(PlaybackOptionsPage.SUBTITLES, state.core.optionsPage)
        assertTrue(state.core.optionsQuickList)
        assertEquals(1, state.core.quickList.moveDownRequests)
        state.core.endOpeningKeyCycle(KeyEvent.KEYCODE_CAPTIONS)

        state.core.closeQuickList()
        assertNull(state.core.optionsPage)
        assertFalse(state.core.optionsQuickList)
        assertTrue(state.core.chrome.controlsVisible)
        assertEquals("player-settings", state.core.restoreControl)
        // Keys no longer count once the list is closed.
        val closedPresses = state.core.quickList.keyPresses
        state.core.onKeyDown()
        assertEquals(closedPresses, state.core.quickList.keyPresses)

        state.core.onFocusRestored()
        assertTrue(state.core.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_CAPTIONS))
        state.core.endOpeningKeyCycle(KeyEvent.KEYCODE_CAPTIONS)
        assertTrue(state.core.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MENU))
        assertEquals(PlaybackOptionsPage.ROOT, state.core.optionsPage)
        assertFalse(state.core.optionsQuickList)
        state.dispose()
    }
}
