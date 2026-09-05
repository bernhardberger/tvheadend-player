package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackKeyPolicyTest {
    @Test
    fun mapsOnlyMediaPlaybackKeys() {
        assertEquals(
            MediaPlaybackAction.TOGGLE,
            mediaPlaybackAction(10, playKeyCode = 11, pauseKeyCode = 12, toggleKeyCode = 10),
        )
        assertEquals(
            MediaPlaybackAction.PLAY,
            mediaPlaybackAction(11, playKeyCode = 11, pauseKeyCode = 12, toggleKeyCode = 10),
        )
        assertEquals(
            MediaPlaybackAction.PAUSE,
            mediaPlaybackAction(12, playKeyCode = 11, pauseKeyCode = 12, toggleKeyCode = 10),
        )
        assertEquals(
            MediaPlaybackAction.NONE,
            mediaPlaybackAction(13, playKeyCode = 11, pauseKeyCode = 12, toggleKeyCode = 10),
        )
    }

    @Test
    fun hiddenControlsAreRevealedByOkAndVerticalDpad() {
        assertTrue(shouldRevealPlaybackControls(false, KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(shouldRevealPlaybackControls(false, KeyEvent.KEYCODE_ENTER))
        assertTrue(shouldRevealPlaybackControls(false, KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertTrue(shouldRevealPlaybackControls(false, KeyEvent.KEYCODE_DPAD_DOWN))
        assertTrue(shouldRevealPlaybackControls(false, KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(shouldRevealPlaybackControls(true, KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun revealingKeyCycleIsSuppressedUntilItsMatchingKeyUp() {
        assertTrue(
            playbackSuppressesRevealingKey(
                revealingKeyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            )
        )
        assertFalse(
            playbackSuppressesRevealingKey(
                revealingKeyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
            )
        )
        assertFalse(
            playbackSuppressesRevealingKey(
                revealingKeyCode = null,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            )
        )
    }

    @Test
    fun everyActionThatCreatesFocusStartsACompleteOpeningKeyCycle() {
        listOf(
            PlayerKeyAction.REVEAL_CONTROLS,
            PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE,
            PlayerKeyAction.OPEN_CHANNELS,
            PlayerKeyAction.OPEN_INFO,
        ).forEach { action ->
            assertTrue(action.name, playerKeyActionStartsOpeningCycle(action))
        }

        listOf(
            PlayerKeyAction.PASS_THROUGH,
            PlayerKeyAction.SEEK_BACK,
            PlayerKeyAction.SEEK_FORWARD,
            PlayerKeyAction.HIDE_CONTROLS,
            PlayerKeyAction.CLOSE_PLAYER,
            PlayerKeyAction.DISMISS_OVERLAY_ONLY,
        ).forEach { action ->
            assertFalse(action.name, playerKeyActionStartsOpeningCycle(action))
        }
    }

    @Test
    fun liveWithoutTimeshiftUsesRevealAndChannelDrawer() {
        val ctx = PlayerKeyContext(
            surface = PlayerSurface.LIVE,
            controlsVisible = false,
            seekbarFocused = false,
            timeshiftAvailable = false,
        )
        assertEquals(
            PlayerKeyAction.REVEAL_CONTROLS,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_CENTER),
        )
        assertEquals(
            PlayerKeyAction.OPEN_CHANNELS,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_LEFT),
        )
        assertEquals(
            PlayerKeyAction.PASS_THROUGH,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_RIGHT),
        )
        assertEquals(
            PlayerKeyAction.REVEAL_CONTROLS,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_UP),
        )
        assertEquals(
            PlayerKeyAction.REVEAL_CONTROLS,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_DOWN),
        )
        assertEquals(
            PlayerKeyAction.CLOSE_PLAYER,
            playerKeyAction(ctx, KeyEvent.KEYCODE_BACK),
        )
    }

    @Test
    fun liveWithTimeshiftTogglesPauseAndSeeks() {
        val ctx = PlayerKeyContext(
            surface = PlayerSurface.LIVE,
            controlsVisible = false,
            seekbarFocused = false,
            timeshiftAvailable = true,
        )
        assertEquals(
            PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_CENTER),
        )
        assertEquals(
            PlayerKeyAction.SEEK_BACK,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_LEFT),
        )
        assertEquals(
            PlayerKeyAction.SEEK_FORWARD,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_RIGHT),
        )
    }

    @Test
    fun recordingUsesVerticalDpadToRevealControls() {
        val ctx = PlayerKeyContext(
            surface = PlayerSurface.RECORDING,
            controlsVisible = false,
            seekbarFocused = false,
            timeshiftAvailable = false,
        )
        assertEquals(
            PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_CENTER),
        )
        assertEquals(
            PlayerKeyAction.REVEAL_CONTROLS,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_UP),
        )
        assertEquals(
            PlayerKeyAction.REVEAL_CONTROLS,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_DOWN),
        )
        assertEquals(
            PlayerKeyAction.SEEK_BACK,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_LEFT),
        )
        assertEquals(
            PlayerKeyAction.SEEK_FORWARD,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_RIGHT),
        )
    }

    @Test
    fun openInfoLetsItsFocusedButtonHandleCenter() {
        val ctx = PlayerKeyContext(
            surface = PlayerSurface.LIVE,
            controlsVisible = false,
            seekbarFocused = false,
            timeshiftAvailable = true,
            infoOpen = true,
        )
        assertEquals(
            PlayerKeyAction.PASS_THROUGH,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_CENTER),
        )
    }

    @Test
    fun dedicatedTvRemoteKeysOpenInfoAndChannels() {
        val live = PlayerKeyContext(
            surface = PlayerSurface.LIVE,
            controlsVisible = false,
            seekbarFocused = false,
            timeshiftAvailable = true,
        )
        assertEquals(
            PlayerKeyAction.OPEN_INFO,
            playerKeyAction(live, KeyEvent.KEYCODE_INFO),
        )
        assertEquals(
            PlayerKeyAction.OPEN_CHANNELS,
            playerKeyAction(live, KeyEvent.KEYCODE_TV_CONTENTS_MENU),
        )
        assertEquals(
            PlayerKeyAction.OPEN_CHANNELS,
            playerKeyAction(live, KeyEvent.KEYCODE_TV_NUMBER_ENTRY),
        )
        assertEquals(
            PlayerKeyAction.OPEN_INFO,
            playerKeyAction(live.copy(controlsVisible = true), KeyEvent.KEYCODE_INFO),
        )
        assertEquals(
            PlayerKeyAction.OPEN_CHANNELS,
            playerKeyAction(live, KeyEvent.KEYCODE_BOOKMARK),
        )
    }

    @Test
    fun ordinaryLiveBackClosesOnlyAfterChromeIsDismissed() {
        val ctx = PlayerKeyContext(
            surface = PlayerSurface.LIVE,
            controlsVisible = false,
            seekbarFocused = false,
            timeshiftAvailable = false,
        )
        assertEquals(
            PlayerKeyAction.CLOSE_PLAYER,
            playerKeyAction(ctx, KeyEvent.KEYCODE_BACK),
        )
        val withControls = ctx.copy(controlsVisible = true)
        assertEquals(
            PlayerKeyAction.HIDE_CONTROLS,
            playerKeyAction(withControls, KeyEvent.KEYCODE_BACK),
        )
    }

    @Test
    fun nonTimeshiftLiveOpensTheShelfOnLeftAndListRemoteKeys() {
        val ctx = PlayerKeyContext(
            surface = PlayerSurface.LIVE,
            controlsVisible = false,
            seekbarFocused = false,
            timeshiftAvailable = false,
        )
        assertEquals(
            PlayerKeyAction.OPEN_CHANNELS,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_LEFT),
        )
        assertEquals(
            PlayerKeyAction.OPEN_CHANNELS,
            playerKeyAction(ctx, KeyEvent.KEYCODE_TV_CONTENTS_MENU),
        )
        assertEquals(
            PlayerKeyAction.OPEN_CHANNELS,
            playerKeyAction(ctx, KeyEvent.KEYCODE_BOOKMARK),
        )
        assertEquals(
            PlayerKeyAction.OPEN_CHANNELS,
            playerKeyAction(ctx, KeyEvent.KEYCODE_TV_NUMBER_ENTRY),
        )
    }

    @Test
    fun controlsVisibleBackHidesControlsAndPassesOtherKeys() {
        val ctx = PlayerKeyContext(
            surface = PlayerSurface.LIVE,
            controlsVisible = true,
            seekbarFocused = false,
            timeshiftAvailable = true,
        )
        assertEquals(
            PlayerKeyAction.HIDE_CONTROLS,
            playerKeyAction(ctx, KeyEvent.KEYCODE_BACK),
        )
        assertEquals(
            PlayerKeyAction.PASS_THROUGH,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_LEFT),
        )
    }

    @Test
    fun seekbarFocusedScrubsAndLeavesOnUpDown() {
        val ctx = PlayerKeyContext(
            surface = PlayerSurface.RECORDING,
            controlsVisible = true,
            seekbarFocused = true,
            timeshiftAvailable = false,
        )
        assertEquals(
            PlayerKeyAction.SEEK_BACK,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_LEFT),
        )
        assertEquals(
            PlayerKeyAction.SEEK_FORWARD,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_RIGHT),
        )
        assertEquals(
            PlayerKeyAction.PASS_THROUGH,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_UP),
        )
        assertEquals(
            PlayerKeyAction.PASS_THROUGH,
            playerKeyAction(ctx, KeyEvent.KEYCODE_DPAD_DOWN),
        )
    }

    @Test
    fun pickingCurrentChannelClosesDrawerWithoutRetuning() {
        assertEquals(ChannelPickAction.CLOSE_DRAWER, channelPickAction(ChannelId(33), ChannelId(33)))
        assertEquals(ChannelPickAction.TUNE, channelPickAction(ChannelId(33), ChannelId(34)))
        assertEquals(ChannelPickAction.TUNE, channelPickAction(null, ChannelId(33)))
    }

    @Test
    fun recoveryParentLetsFocusedRetryReceiveNavigationAndCenter() {
        assertFalse(playerParentConsumesRecoveryKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(playerParentConsumesRecoveryKey(KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(playerParentConsumesRecoveryKey(KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(playerParentConsumesRecoveryKey(KeyEvent.KEYCODE_DPAD_LEFT))
        assertFalse(playerParentConsumesRecoveryKey(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(playerParentConsumesRecoveryKey(KeyEvent.KEYCODE_CHANNEL_UP))
        assertTrue(playerParentConsumesRecoveryKey(KeyEvent.KEYCODE_1))
    }

    @Test
    fun channelKeysTuneInTheShelfAndFullscreenPlayback() {
        assertEquals(ChannelKeyAction.TUNE, playbackChannelKeyAction(browserVisible = true))
        assertEquals(ChannelKeyAction.TUNE, playbackChannelKeyAction(browserVisible = false))
    }

    @Test
    fun overlayFocusStartsOnControlCluster() {
        assertEquals(
            PlaybackOverlayFocusTarget.CONTROLS_CLUSTER,
            initialPlaybackOverlayFocus(timeshiftAvailable = true),
        )
        assertEquals(
            PlaybackOverlayFocusTarget.CONTROLS_CLUSTER,
            initialPlaybackOverlayFocus(timeshiftAvailable = false),
        )
    }
}
