package at.bernhardberger.tvhplayer.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingPlaybackPolicyTest {
    @Test
    fun hiddenPlayerUsesControlsAndSeekContract() {
        assertEquals(
            RecordingPlaybackKeyAction.SEEK_BACK,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_DPAD_LEFT),
        )
        assertEquals(
            RecordingPlaybackKeyAction.SEEK_FORWARD,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_DPAD_RIGHT),
        )
        assertEquals(
            RecordingPlaybackKeyAction.REVEAL_CONTROLS,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_DPAD_UP),
        )
        assertEquals(
            RecordingPlaybackKeyAction.REVEAL_CONTROLS,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_DPAD_DOWN),
        )
        assertEquals(
            RecordingPlaybackKeyAction.REVEAL_AND_TOGGLE_PAUSE,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_DPAD_CENTER),
        )
        assertEquals(
            RecordingPlaybackKeyAction.HIDE_CONTROLS,
            recordingPlaybackKeyAction(true, KeyEvent.KEYCODE_BACK),
        )
        assertEquals(
            RecordingPlaybackKeyAction.CLOSE,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_BACK),
        )
    }

    @Test
    fun visiblePlayerLeavesDirectionKeysToControlFocus() {
        listOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
        ).forEach { keyCode ->
            assertEquals(
                RecordingPlaybackKeyAction.PASS_THROUGH,
                recordingPlaybackKeyAction(true, keyCode),
            )
        }
    }

    @Test
    fun revealingKeyCycleIsSuppressedUntilItsMatchingKeyUp() {
        assertTrue(
            recordingPlaybackSuppressesRevealingKey(
                revealingKeyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            )
        )
        assertEquals(
            false,
            recordingPlaybackSuppressesRevealingKey(
                revealingKeyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
            ),
        )
        assertEquals(
            false,
            recordingPlaybackSuppressesRevealingKey(
                revealingKeyCode = null,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            ),
        )
    }

    @Test
    fun recordingStartsCompleteCyclesForEveryFocusCreatingAction() {
        listOf(
            RecordingPlaybackKeyAction.REVEAL_CONTROLS,
            RecordingPlaybackKeyAction.REVEAL_AND_TOGGLE_PAUSE,
            RecordingPlaybackKeyAction.OPEN_INFO,
        ).forEach { action ->
            assertTrue(action.name, recordingKeyActionStartsOpeningCycle(action))
        }

        listOf(
            RecordingPlaybackKeyAction.PASS_THROUGH,
            RecordingPlaybackKeyAction.SEEK_BACK,
            RecordingPlaybackKeyAction.SEEK_FORWARD,
            RecordingPlaybackKeyAction.HIDE_CONTROLS,
            RecordingPlaybackKeyAction.CLOSE,
        ).forEach { action ->
            assertEquals(false, recordingKeyActionStartsOpeningCycle(action))
        }
    }

}
