package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.playback.AppVideoPresentation
import at.bernhardberger.tvhplayer.playback.AppLiveTargetPresentation
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainStartupPlaybackTest {
    @Test fun resumeCopyRequiresTheCapturedPriorLiveChannelToMatchResolvedTarget() {
        val target = ApplianceLaunchTarget(ApplianceLaunchRequest(1L), ChannelId(1L), "One")
        assertTrue(mainStartupReturningToPlayback(ChannelId(1L), target))
        assertFalse(mainStartupReturningToPlayback(ChannelId(2L), target))
        assertFalse(mainStartupReturningToPlayback(ChannelId(1L), null))
        // Fresh autoplay and a prior recording both have no pre-request live identity.
        assertFalse(mainStartupReturningToPlayback(null, target))
    }
    private fun outcome(
        expected: Long? = 7L,
        frame: AppVideoPresentation? = AppVideoPresentation(7L, false),
        playing: Boolean = true,
        audioOnly: Boolean = false,
        recovery: Boolean = false,
        warm: Boolean = false,
    ) = mainStartupPlaybackOutcome(expected, frame?.let {
        AppLiveTargetPresentation(it.epoch, it.visible, playing, audioOnly)
    }, recovery, warm)

    @Test fun routeOrPlayingWithoutVideoCannotCompleteStartup() {
        assertEquals(null, outcome(expected = null))
        assertEquals(null, outcome())
        assertEquals(null, outcome(frame = null, audioOnly = true))
    }

    @Test fun onlyThisTunesFrameOrPositivelyPlayingAudioOnlyCompletes() {
        assertEquals(MainStartupPlaybackOutcome.PRESENTED, outcome(frame = AppVideoPresentation(7L, true)))
        assertEquals(MainStartupPlaybackOutcome.PRESENTED, outcome(audioOnly = true))
        assertEquals(null, outcome(audioOnly = true, playing = false))
        assertEquals(null, outcome(frame = AppVideoPresentation(6L, true)))
        assertEquals(null, outcome(frame = AppVideoPresentation(8L, true), audioOnly = true))
    }

    @Test fun recoveryEscapesImmediatelyEvenIfAFrameWasPreviouslyVisible() {
        assertEquals(MainStartupPlaybackOutcome.RECOVERY, outcome(expected = null, recovery = true))
        assertEquals(MainStartupPlaybackOutcome.RECOVERY, outcome(frame = AppVideoPresentation(7L, true), recovery = true))
    }

    @Test fun matchingAlreadyPlayingAdoptionDoesNotWaitForAnotherFrame() {
        assertEquals(MainStartupPlaybackOutcome.ALREADY_PLAYING, outcome(frame = AppVideoPresentation(7L, true), warm = true))
        assertEquals(MainStartupPlaybackOutcome.ALREADY_PLAYING, outcome(audioOnly = true, warm = true))
        assertEquals(null, outcome(warm = true))
        assertEquals(null, outcome(warm = true, frame = AppVideoPresentation(6L, true)))
    }

    @Test fun profileReplacementImmediatelyWithdrawsRevealWithoutWaitingForSessionRecovery() {
        val target = ApplianceLaunchTarget(ApplianceLaunchRequest(1L), ChannelId(1L), "One")
        val reveal = MainStartupReveal(target, profileGeneration = 4L)
        assertEquals(target, reveal.targetFor(4L))
        assertEquals(null, reveal.targetFor(5L))
        assertEquals(null, reveal.targetFor(6L))
    }
}
