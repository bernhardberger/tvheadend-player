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
        state.onActionFocused("player-identity-card")
        state.openChannelDrawer()
        state.chrome.updateAutoHideEligibility(false)
        repeat(3) { state.onChannelTuneRequested() }
        advanceTimeBy(20_000L)
        runCurrent()
        assertTrue(state.channelDrawerOpen)
        assertFalse(state.chrome.controlsVisible)
        state.dismissChannelDrawer()
        assertFalse(state.channelDrawerOpen)
        assertTrue(state.chrome.controlsVisible)
        assertEquals("player-identity-card", state.restoreChannelAction)
        // A zap on the hidden player shows the Banner, not the controls.
        state.chrome.hideControls()
        state.onChannelTuneRequested()
        assertTrue(state.chrome.bannerVisible)
        assertFalse(state.chrome.controlsVisible)
    }

    @Test
    fun controlsRecordWhetherAZapOrTheViewerRevealedThem() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.showControls()
        assertEquals(PlayerControlsEntry.FROM_BANNER, state.chrome.controlsEntry)
        state.onChannelTuneRequested()
        assertTrue(state.chrome.controlsVisible)
        assertEquals(PlayerControlsEntry.FADE, state.chrome.controlsEntry)
        state.chrome.hideControls()
        state.showControls()
        assertEquals(PlayerControlsEntry.TRAVEL, state.chrome.controlsEntry)

        // Controls returning from the tray, options or info still move in as before.
        state.onChannelTuneRequested()
        state.openChannelDrawer()
        state.onChannelTuneRequested()
        assertEquals(PlayerControlsEntry.TRAVEL, state.chrome.controlsEntry)
        state.dismissChannelDrawer()
        assertEquals(PlayerControlsEntry.TRAVEL, state.chrome.controlsEntry)
        for (open in listOf(
            { state.showOptionsPage(PlaybackOptionsPage.ROOT) },
            { state.openInfo() },
        )) {
            state.onChannelTuneRequested()
            open()
            assertEquals(PlayerControlsEntry.TRAVEL, state.chrome.controlsEntry)
        }
        advanceTimeBy(20_000L)
        runCurrent()
    }

    @Test
    fun shelfDismissalRestoresInvokerWithoutChangingTuneCloseBehavior() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        for (action in listOf("player-pause", "player-identity-card", "player-record", "player-settings", "player-stop")) {
            state.showControls()
            state.onActionFocused(action)
            state.openChannelDrawer()
            state.beginOpeningKeyCycle(19)
            state.dismissChannelDrawer()
            assertEquals(PlayerForegroundLayer.CONTROLS, playerForegroundLayer(state.foregroundContext()))
            assertEquals(if (action == "player-stop") "player-pause" else action, state.restoreChannelAction)
            assertEquals(19, state.revealingKeyCode)
            state.onChannelActionRestored()
            assertNull(state.restoreChannelAction)
            state.endOpeningKeyCycle(19)
        }
        state.onActionFocused("player-settings")
        state.chrome.hideControls()
        state.openChannelDrawer()
        state.dismissChannelDrawer()
        assertEquals("player-pause", state.restoreChannelAction)
        state.onChannelActionRestored()
        state.openChannelDrawer()
        state.closeChannelDrawer()
        assertFalse(state.chrome.controlsVisible)
        assertNull(state.restoreChannelAction)
    }

    @Test
    fun aQuickListReturnsFocusToStop() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.showControls()
        state.onActionFocused("player-stop")
        state.showQuickList(PlaybackOptionsPage.AUDIO)
        state.closeQuickList()
        assertEquals("player-stop", state.restoreChannelAction)
        state.onChannelActionRestored()

        // Focus on a control that has no restore target leaves nothing to restore.
        state.onActionFocused("player-seekbar")
        state.showQuickList(PlaybackOptionsPage.SUBTITLES)
        state.closeQuickList()
        assertNull(state.restoreChannelAction)
        state.dispose()
    }

    @Test
    fun layerOpeningsAreExclusiveAndCloseInForegroundOrder() = runTest {
        val state = LivePlayerLayerState(
            scope = this,
            autoHideTimeoutMillis = 5_000L,
        )

        state.openChannelDrawer()
        assertFalse(state.chrome.controlsVisible)
        assertTrue(state.channelDrawerOpen)

        state.showOptionsPage(PlaybackOptionsPage.AUDIO)
        assertTrue(state.chrome.controlsVisible)
        assertFalse(state.channelDrawerOpen)
        assertEquals(PlaybackOptionsPage.AUDIO, state.optionsPage)

        state.openInfo()
        assertFalse(state.chrome.controlsVisible)
        assertNull(state.optionsPage)
        assertFalse(state.channelDrawerOpen)
        assertTrue(state.infoOpen)

        state.openOptions()
        assertFalse(state.infoOpen)
        assertEquals(PlaybackOptionsPage.ROOT, state.optionsPage)

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

        state.chrome.hideControls()
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

        state.beginOpeningKeyCycle(keyCode = 23)
        assertEquals(23, state.revealingKeyCode)

        state.endOpeningKeyCycle(keyCode = 24)
        assertEquals(23, state.revealingKeyCode)

        state.endOpeningKeyCycle(keyCode = 23)
        assertNull(state.revealingKeyCode)
    }

    @Test
    fun optionKeysReplaceInfoTheDrawerAndStatsButNotAConfirmation() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        fun keyContext() = PlayerKeyContext(
            surface = PlayerSurface.LIVE,
            controlsVisible = state.chrome.controlsVisible,
            seekbarFocused = false,
            timeshiftAvailable = true,
            optionsOpen = state.optionsPage != null,
            statsOpen = state.statsVisible,
            infoOpen = state.infoOpen,
            drawerOpen = state.channelDrawerOpen,
            confirmationOpen =
                playerForegroundLayer(state.foregroundContext()) == PlayerForegroundLayer.CONFIRMATION,
        )

        state.openInfo()
        assertTrue(state.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MENU))
        assertFalse(state.infoOpen)
        assertEquals(PlaybackOptionsPage.ROOT, state.optionsPage)
        assertFalse(state.optionsQuickList)
        assertEquals(KeyEvent.KEYCODE_MENU, state.revealingKeyCode)
        assertEquals(PlayerForegroundLayer.OPTIONS_ROOT, playerForegroundLayer(state.foregroundContext()))
        state.endOpeningKeyCycle(KeyEvent.KEYCODE_MENU)

        state.closeOptions()
        state.openChannelDrawer()
        assertTrue(state.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK))
        assertFalse(state.channelDrawerOpen)
        assertEquals(PlaybackOptionsPage.AUDIO, state.optionsPage)
        assertTrue(state.optionsQuickList)
        assertEquals(PlayerForegroundLayer.OPTIONS_QUICK_LIST, playerForegroundLayer(state.foregroundContext()))
        state.endOpeningKeyCycle(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK)

        state.closeQuickList()
        state.chrome.hideControls()
        state.updateStatsVisibility(true)
        assertEquals(PlayerForegroundLayer.STATS, playerForegroundLayer(state.foregroundContext()))
        // No root fallback: the captions key opens the Subtitles list, and the short
        // list leaves the hidden controls hidden.
        assertTrue(state.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_CAPTIONS))
        assertEquals(PlaybackOptionsPage.SUBTITLES, state.optionsPage)
        assertTrue(state.optionsQuickList)
        assertFalse(state.chrome.controlsVisible)
        // Stats stay enabled; they return once the list closes.
        assertTrue(state.statsVisible)
        state.endOpeningKeyCycle(KeyEvent.KEYCODE_CAPTIONS)

        state.closeQuickList()
        state.openInfo()
        state.showRecordingConfirmation()
        assertFalse(state.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MENU))
        assertTrue(state.infoOpen)
        assertNull(state.optionsPage)
        assertNull(state.revealingKeyCode)
        assertEquals(PlayerForegroundLayer.CONFIRMATION, playerForegroundLayer(state.foregroundContext()))

        assertFalse(state.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_DPAD_CENTER))
        state.dispose()
    }

    @Test
    fun quickListKeysSwitchMoveDownAndReturnFocusWithoutRevealingControls() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        fun keyContext() = PlayerKeyContext(
            surface = PlayerSurface.LIVE,
            controlsVisible = state.chrome.controlsVisible,
            seekbarFocused = false,
            timeshiftAvailable = true,
            optionsOpen = state.optionsPage != null,
        )
        state.showControls()
        state.onActionFocused("player-record")

        assertTrue(state.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK))
        assertTrue(state.chrome.controlsVisible)
        state.endOpeningKeyCycle(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK)
        val presses = state.quickList.keyPresses
        state.onKeyDown()
        assertEquals(presses + 1, state.quickList.keyPresses)

        // The same key again moves focus down instead of reopening.
        assertTrue(state.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK))
        assertEquals(1, state.quickList.moveDownRequests)
        assertEquals(PlaybackOptionsPage.AUDIO, state.optionsPage)
        state.endOpeningKeyCycle(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK)

        // The other key switches lists, Menu opens the full root.
        assertTrue(state.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_CAPTIONS))
        assertEquals(PlaybackOptionsPage.SUBTITLES, state.optionsPage)
        assertTrue(state.optionsQuickList)
        assertEquals(1, state.quickList.moveDownRequests)
        state.endOpeningKeyCycle(KeyEvent.KEYCODE_CAPTIONS)

        state.closeQuickList()
        assertNull(state.optionsPage)
        assertFalse(state.optionsQuickList)
        assertTrue(state.chrome.controlsVisible)
        assertEquals("player-record", state.restoreChannelAction)
        // Keys no longer count once the list is closed.
        val closedPresses = state.quickList.keyPresses
        state.onKeyDown()
        assertEquals(closedPresses, state.quickList.keyPresses)

        state.onChannelActionRestored()
        assertTrue(state.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_CAPTIONS))
        state.endOpeningKeyCycle(KeyEvent.KEYCODE_CAPTIONS)
        assertTrue(state.openOptionsForKey(keyContext(), KeyEvent.KEYCODE_MENU))
        assertEquals(PlaybackOptionsPage.ROOT, state.optionsPage)
        assertFalse(state.optionsQuickList)
        state.dispose()
    }
}
