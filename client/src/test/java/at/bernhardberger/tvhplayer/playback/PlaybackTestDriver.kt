@file:androidx.media3.common.util.UnstableApi
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package at.bernhardberger.tvhplayer.playback

import android.os.Looper
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.robolectric.RobolectricUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** Drives due coroutine/looper work without advancing recovery timers during real-thread handoffs. */
internal class PlaybackTestDriver(
    private val scheduler: TestCoroutineScheduler,
    private val waitTimeoutMillis: Long = 5_000,
) {
    fun await(
        label: String = "playback condition",
        timeoutMillis: Long = waitTimeoutMillis,
        diagnostics: () -> String = { "" },
        predicate: () -> Boolean,
    ) {
        try {
            // Future SDK watchdogs are not part of a thread handoff.
            RobolectricUtil.runLooperUntil(Looper.getMainLooper(), {
                scheduler.runCurrent()
                predicate()
            }, timeoutMillis, Clock.DEFAULT, 0)
        } catch (timeout: TimeoutException) {
            throw AssertionError("Timed out after ${timeoutMillis}ms waiting for $label. ${diagnostics()}", timeout)
        }
    }

    fun settle(player: ExoPlayer) {
        scheduler.runCurrent()
        val handled = AtomicBoolean()
        player.createMessage { _, _ -> handled.set(true) }.send()
        await("playback-thread round trip") { handled.get() }
        // Drain coroutine effects of the acknowledgement before main callbacks, then their effects.
        scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        scheduler.runCurrent()
    }

    suspend fun <T> exercise(scope: CoroutineScope, timeoutMillis: Long, block: suspend () -> T): T {
        val exercise = scope.async { block() }
        // Suspending here would let runTest auto-advance timers while SDK threads are still replying.
        // This real-time watchdog owns the whole body, independently of each shorter inner wait.
        await("test exercise completion", timeoutMillis) { exercise.isCompleted }
        return exercise.await()
    }
}
