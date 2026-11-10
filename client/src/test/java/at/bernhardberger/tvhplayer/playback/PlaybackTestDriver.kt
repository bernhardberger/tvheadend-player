@file:androidx.media3.common.util.UnstableApi
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package at.bernhardberger.tvhplayer.playback

import android.os.Looper
import androidx.media3.common.FlagSet
import androidx.media3.common.Player
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

internal fun dispatchPlayerError(
    player: Player,
    listeners: List<Player.Listener>,
    error: ExoPlaybackException,
    setPlayerError: (ExoPlaybackException) -> Unit,
) {
    setPlayerError(error)
    listeners.toList().forEach {
        it.onPlayerErrorChanged(error)
        it.onPlayerError(error)
    }
    val events = Player.Events(FlagSet.Builder().add(Player.EVENT_PLAYER_ERROR).build())
    listeners.toList().forEach { it.onEvents(player, events) }
}

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
        // PlayerMessage is acknowledged by Media3's real playback thread.
        await("playback-thread round trip") { handled.get() }
        // Drain coroutine effects of the acknowledgement before main callbacks, then their effects.
        scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        scheduler.runCurrent()
    }

    /** Controlled work has no real-thread handoff to wait for: a missing signal is a failure now. */
    fun awaitCurrent(label: String, predicate: () -> Boolean) {
        scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        scheduler.runCurrent()
        if (!predicate()) throw AssertionError("Missing signal after controlled work: $label")
    }

    /** For live sources whose manager and SDK work use this scheduler, not real IO threads. */
    fun settleLive(player: ExoPlayer) {
        repeat(8) {
            scheduler.runCurrent()
            TestPlayerRunHelper.advance(player).untilPendingCommandsAreFullyHandled()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    fun advanceLive(player: ExoPlayer, clock: FakeClock, millis: Long) {
        require(millis >= 0)
        settleLive(player)
        var remaining = millis
        while (remaining > 0) {
            val step = minOf(remaining, 100)
            scheduler.advanceTimeBy(step)
            clock.advanceTime(step)
            settleLive(player)
            remaining -= step
        }
    }

    suspend fun <T> exercise(
        scope: CoroutineScope,
        timeoutMillis: Long? = null,
        settle: () -> Unit = {},
        block: suspend () -> T,
    ): T {
        val exercise = scope.async { block() }
        try {
            if (timeoutMillis == null) {
                scheduler.runCurrent()
                if (!exercise.isCompleted) settle()
                awaitCurrent("test exercise completion") { exercise.isCompleted }
            } else {
                // Real IO/loader bodies need a watchdog; suspension would auto-advance SDK timers.
                await("test exercise completion", timeoutMillis) { exercise.isCompleted }
            }
            return exercise.await()
        } finally {
            // A missing controlled signal must not leave a suspended child for runTest's watchdog.
            exercise.cancel()
        }
    }
}
