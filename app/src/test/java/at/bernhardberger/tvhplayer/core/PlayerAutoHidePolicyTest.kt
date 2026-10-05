package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAutoHidePolicyTest {
    @Test
    fun mediaPlaybackMustBeReadyAndActuallyPlayingToProgress() {
        assertTrue(playerPlaybackProgressing(isPlaying = true, playerReady = true))
        assertFalse(playerPlaybackProgressing(isPlaying = false, playerReady = true))
        assertFalse(playerPlaybackProgressing(isPlaying = true, playerReady = false))
    }

    @Test
    fun stableProgressingPlaybackCanHideVisibleControls() {
        assertTrue(playerControlsAutoHideEligible(eligibleContext()))
    }

    @Test
    fun hiddenControlsAreNeverRevealedByAutoHidePolicy() {
        assertFalse(
            playerControlsAutoHideEligible(
                eligibleContext().copy(controlsVisible = false),
            )
        )
    }

    @Test
    fun everyUnstableOrForegroundStatePreventsAutoHide() {
        val blocked = listOf(
            eligibleContext().copy(playbackProgressing = false),
            eligibleContext().copy(playbackStable = false),
            eligibleContext().copy(seekPending = true),
            eligibleContext().copy(modalVisible = true),
            eligibleContext().copy(recoveryVisible = true),
            eligibleContext().copy(actionableErrorVisible = true),
        )

        blocked.forEach { context ->
            assertFalse(context.toString(), playerControlsAutoHideEligible(context))
        }
    }

    @Test
    fun theChannelCardAndItsRecentRowWaitLongerThanTheRestOfTheControls() {
        assertEquals(5_000L, playerControlsAutoHideTimeoutMillis(5_000L, channelCardFocused = false))
        assertEquals(30_000L, playerControlsAutoHideTimeoutMillis(5_000L, channelCardFocused = true))
        assertEquals("a longer base is never shortened", 60_000L, playerControlsAutoHideTimeoutMillis(60_000L, channelCardFocused = true))
    }

    private fun eligibleContext() = PlayerAutoHideContext(
        controlsVisible = true,
        playbackProgressing = true,
        playbackStable = true,
        seekPending = false,
        modalVisible = false,
        recoveryVisible = false,
        actionableErrorVisible = false,
    )
}
