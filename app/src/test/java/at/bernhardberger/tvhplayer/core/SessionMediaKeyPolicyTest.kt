package at.bernhardberger.tvhplayer.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMediaKeyPolicyTest {
    @Test fun mediaActionAndOpeningKeySuppressionPoliciesRecognizeMediaKeys() {
        for (key in listOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            assertTrue(mediaPlaybackAction(key, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) != MediaPlaybackAction.NONE)
            // Pure policy coverage only; this does not dispatch events through Compose
            // or prove that a screen consumes the complete down/repeat/up sequence.
            assertTrue(playbackSuppressesRevealingKey(key, key))
            assertEquals(MediaPlaybackAction.NONE, mediaPlaybackAction(key, KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, repeatCount = 1))
            assertFalse(playbackSuppressesRevealingKey(null, key))
        }
    }

    @Test fun applianceAccessibilityDoesNotClaimMediaKeys() {
        for (key in listOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
            assertFalse(ApplianceEntryPolicy.isApplianceEntryKey(key, KeyEvent.KEYCODE_GUIDE))
        }
    }
}
