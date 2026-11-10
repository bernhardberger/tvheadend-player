package at.bernhardberger.tvhplayer.testutil

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey

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
