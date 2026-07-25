package at.bernhardberger.tvhplayer.core

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
    fun hiddenControlsAreRevealedByOkAndDpadDown() {
        assertTrue(shouldRevealPlaybackControls(false, KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(shouldRevealPlaybackControls(false, KeyEvent.KEYCODE_ENTER))
        assertTrue(shouldRevealPlaybackControls(false, KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertTrue(shouldRevealPlaybackControls(false, KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(shouldRevealPlaybackControls(false, KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(shouldRevealPlaybackControls(true, KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun pickingCurrentChannelClosesDrawerWithoutRetuning() {
        assertEquals(ChannelPickAction.CLOSE_DRAWER, channelPickAction(33, 33))
        assertEquals(ChannelPickAction.TUNE, channelPickAction(33, 34))
    }

    @Test
    fun channelKeysPageAnOpenBrowserAndTuneFromFullscreenPlayback() {
        assertEquals(ChannelKeyAction.PAGE_LIST, playbackChannelKeyAction(browserVisible = true))
        assertEquals(ChannelKeyAction.TUNE, playbackChannelKeyAction(browserVisible = false))
    }

    @Test
    fun overlayFocusStartsOnThePrimaryNonDestructiveAction() {
        assertEquals(
            PlaybackOverlayFocusTarget.TIMESHIFT_TOGGLE,
            initialPlaybackOverlayFocus(timeshiftAvailable = true),
        )
        assertEquals(
            PlaybackOverlayFocusTarget.CHANNELS,
            initialPlaybackOverlayFocus(timeshiftAvailable = false),
        )
    }
}
