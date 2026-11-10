package at.bernhardberger.tvhplayer.testutil

import android.os.Looper
import androidx.compose.ui.test.junit4.ComposeTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import org.robolectric.Shadows.shadowOf

/** Drives injected work and its Main handoff, without giving a real worker a wall-clock head start. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun ComposeTestRule.awaitScheduledWork(dispatcher: TestDispatcher, condition: () -> Boolean) {
    mainClock.advanceTimeUntil(timeoutMillis = 1_000) {
        dispatcher.scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}
