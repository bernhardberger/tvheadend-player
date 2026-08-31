package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
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
    fun layerOpeningsAreExclusiveAndCloseInForegroundOrder() = runTest {
        val state = LivePlayerLayerState(
            scope = this,
            autoHideTimeoutMillis = 5_000L,
        )

        state.openChannelDrawer()
        assertFalse(state.controlsVisible)
        assertTrue(state.channelDrawerOpen)

        state.showOptionsPage(PlaybackOptionsPage.AUDIO)
        assertTrue(state.controlsVisible)
        assertFalse(state.channelDrawerOpen)
        assertEquals(PlaybackOptionsPage.AUDIO, state.optionsPage)

        state.openInfo()
        assertFalse(state.controlsVisible)
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

        state.hideControls()
        assertEquals(
            PlayerForegroundLayer.NONE,
            playerForegroundLayer(state.foregroundContext()),
        )
    }

    @Test
    fun autoHideRestartsFromInteractionAndDirectDisposeCancelsPendingJob() = runTest {
        val state = LivePlayerLayerState(
            scope = this,
            autoHideTimeoutMillis = 5_000L,
        )

        state.updateAutoHideEligibility(eligible = true)
        advanceTimeBy(4_000L)
        state.onUserInteraction()
        advanceTimeBy(4_999L)
        runCurrent()
        assertTrue(state.controlsVisible)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(state.controlsVisible)

        state.showControls()
        state.updateAutoHideEligibility(eligible = true)
        advanceTimeBy(2_500L)
        state.updateAutoHideEligibility(eligible = false)
        advanceTimeBy(5_000L)
        runCurrent()
        assertTrue(state.controlsVisible)

        state.updateAutoHideEligibility(eligible = true)
        state.dispose()
        advanceTimeBy(5_000L)
        runCurrent()
        assertTrue(state.controlsVisible)
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
}
