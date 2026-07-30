package at.bernhardberger.tvhplayer.core

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
