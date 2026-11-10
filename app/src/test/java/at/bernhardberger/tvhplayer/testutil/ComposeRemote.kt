package at.bernhardberger.tvhplayer.testutil

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import android.view.KeyEvent as NativeKey

/** Sends (down, repeat, flags) through the existing dispatch path without advancing its clock. */
internal fun pressBack(send: (Boolean, Int, Int) -> Unit, beforeRelease: () -> Unit = {}) {
    send(true, 0, 0)
    beforeRelease()
    send(false, 0, 0)
}

/** Includes the long-press flag and a second repeat; assertions may run before release. */
internal fun holdBack(send: (Boolean, Int, Int) -> Unit, beforeRelease: () -> Unit = {}) = pressBack(send) {
    send(true, 1, NativeKey.FLAG_LONG_PRESS)
    send(true, 2, 0)
    beforeRelease()
}

/** Sends a complete remote press, then drains ready Compose work. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeContentTestRule.pressKey(key: Key) {
    onRoot().performKeyInput { pressKey(key) }
    waitForIdle()
}

/** Preserves tests that settle clock-driven animations after a remote press. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeContentTestRule.pressKey(key: Key, settleFrames: Int, frameMillis: Long = 100) {
    onRoot().performKeyInput { pressKey(key) }
    settleFrames(settleFrames, frameMillis)
}

/** The caller retains its original number and duration of clock steps. */
internal fun ComposeContentTestRule.settleFrames(frameCount: Int, frameMillis: Long = 100) {
    repeat(frameCount) {
        mainClock.advanceTimeBy(frameMillis)
        waitForIdle()
    }
}

internal fun ComposeContentTestRule.changeState(block: () -> Unit) = runOnIdle {
    block()
    Snapshot.sendApplyNotifications()
}
