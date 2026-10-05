package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.core.PlayerBackAction
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.playerBackAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Banner and controls core shared by the players. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerChromeStateTest {
    /** The rail stays expanded down to its schedule and back, so the return never replays its reveal. */
    @Test
    fun railStaysPresentedThroughItsScheduleAndBack() = runTest {
        for (rail in listOf(false, true)) {
            val state = LivePlayerLayerState(this, 5_000L)
            state.showControls()
            state.onActionFocused(PlayerIdentityCardTag)
            if (rail) state.openChannelDrawer()
            state.openInfo()
            assertEquals(rail, state.channelRailPresented)
            state.closeInfo(returnToRail = rail)
            assertEquals(rail, state.channelDrawerOpen)
            assertEquals(rail, state.channelRailPresented)
            if (rail) {
                state.dismissChannelDrawer()
                assertEquals(PlayerIdentityCardTag, state.restoreChannelAction)
            }
            state.chrome.dispose()
        }
    }

    @Test
    fun enteringShowsTheBannerInsteadOfControls() = runTest {
        val state = chrome()
        assertTrue(state.bannerVisible)
        assertFalse(state.controlsVisible)
        state.dispose()
    }

    @Test
    fun bannerStaysWhileTuningAndHidesFiveSecondsAfterTheFirstFrame() = runTest {
        val state = chrome()
        advanceTimeBy(30_000L)
        runCurrent()
        assertTrue("no frame yet: the Banner stays while tuning", state.bannerVisible)
        state.onBannerFramePresented(true)
        advanceTimeBy(4_999L)
        runCurrent()
        assertTrue(state.bannerVisible)
        advanceTimeBy(2L)
        runCurrent()
        assertFalse(state.bannerVisible)
        assertFalse(state.controlsVisible)
        state.dispose()
    }

    @Test
    fun aKeyActingWithoutTheControlsShowsTheBannerForAFullTimeoutEachTime() = runTest {
        val state = chrome()
        state.onBannerFramePresented(true)
        advanceTimeBy(5_001L)
        runCurrent()
        assertFalse("the entry Banner timed out", state.bannerVisible)

        state.peekBanner()
        assertTrue("a pause or quick step shows the Banner", state.bannerVisible)
        assertFalse(state.controlsVisible)
        advanceTimeBy(4_000L)
        state.peekBanner()
        advanceTimeBy(4_000L)
        runCurrent()
        assertTrue("another step restarts the timeout", state.bannerVisible)
        advanceTimeBy(1_001L)
        runCurrent()
        assertFalse(state.bannerVisible)

        state.showControls()
        state.peekBanner()
        assertFalse("with the controls up there is no Banner", state.bannerVisible)
        state.dispose()
    }

    @Test
    fun pausedPlaybackHoldsTheBannerUntilBackOrResume() = runTest {
        val state = chrome()
        state.onBannerFramePresented(true)
        state.holdBanner(true)
        state.peekBanner()
        advanceTimeBy(60_000L)
        runCurrent()
        assertTrue("paused: the Banner stays", state.bannerVisible)

        state.holdBanner(false)
        advanceTimeBy(4_999L)
        runCurrent()
        assertTrue("resumed: a full timeout first", state.bannerVisible)
        advanceTimeBy(2L)
        runCurrent()
        assertFalse(state.bannerVisible)

        state.holdBanner(true)
        state.peekBanner()
        state.hideBanner()
        assertFalse("Back hides the held Banner", state.bannerVisible)
        state.dispose()
    }

    @Test
    fun autoHideRestartsFromInteractionAndDirectDisposeCancelsPendingJob() = runTest {
        val state = chrome()

        state.showControls()
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
    fun aRevealTakesTheBannerOverOrTravelsInUnlessTheCallerNamesTheEntry() = runTest {
        val state = chrome()
        state.showControls()
        assertEquals(PlayerControlsEntry.FROM_BANNER, state.controlsEntry)
        assertFalse(state.bannerVisible)
        state.hideControls()
        state.showControls()
        assertEquals(PlayerControlsEntry.TRAVEL, state.controlsEntry)
        state.hideControls()
        state.peekBanner()
        state.showControls(PlayerControlsEntry.FADE)
        assertEquals(PlayerControlsEntry.FADE, state.controlsEntry)
        assertFalse(state.bannerVisible)
        assertTrue(state.controlsVisible)
        state.dispose()
    }

    @Test
    fun aCoveringLayerHidesTheBannerStopsAutoHideAndBlocksAPeek() = runTest {
        var covered = false
        val state = chrome { covered }
        state.showControls()
        state.updateAutoHideEligibility(eligible = true)
        state.yieldToLayer(controls = true)
        covered = true
        assertEquals(PlayerControlsEntry.TRAVEL, state.controlsEntry)
        advanceTimeBy(10_000L)
        runCurrent()
        assertTrue("a covering menu keeps the controls up", state.controlsVisible)

        state.yieldToLayer(controls = false)
        assertFalse(state.controlsVisible)
        assertFalse(state.hidden)
        state.peekBanner()
        assertFalse("nothing peeks over a covering layer", state.bannerVisible)

        covered = false
        assertTrue(state.hidden)
        state.peekBanner()
        assertTrue(state.bannerVisible)
        state.yieldToLayer()
        assertFalse(state.bannerVisible)
        state.dispose()
    }

    @Test
    fun backHidesTheBannerWhetherPlayingOrPausedAndThenClosesThePlayer() = runTest {
        fun back(state: PlayerChromeState) =
            playerBackAction(PlayerSurface.LIVE, PlayerForegroundLayer.NONE, bannerVisible = state.bannerVisible)
        for (paused in listOf(false, true)) {
            val state = chrome()
            state.onBannerFramePresented(true)
            state.holdBanner(paused)
            assertEquals(PlayerBackAction.HIDE_BANNER, back(state))
            state.hideBanner()
            assertEquals(PlayerBackAction.CLOSE_PLAYER, back(state))
            state.dispose()
        }
    }

    @Test
    fun aDisposedChromeStartsNoBannerTimer() = runTest {
        val state = chrome()
        state.onBannerFramePresented(true)
        state.hideBanner()
        state.dispose()

        state.peekBanner()
        state.holdBanner(true)
        state.holdBanner(false)
        assertTrue(state.bannerVisible)
        advanceTimeBy(60_000L)
        runCurrent()
        assertTrue("no timer runs after dispose", state.bannerVisible)
    }

    private fun TestScope.chrome(isCovered: () -> Boolean = { false }) =
        PlayerChromeState(this, autoHideTimeoutMillis = 5_000L, isCovered = isCovered)
}
