@file:androidx.media3.common.util.UnstableApi
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class)

package at.bernhardberger.tvhplayer.playback

import android.os.Looper
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.playback.MuxFrameType
import at.bernhardberger.tvheadend.sdk.playback.StreamIndex
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStream
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import at.bernhardberger.tvheadend.sdk.testing.ScriptedSubscriptionConnection
import at.bernhardberger.tvheadend.sdk.testing.SubscriptionBinaryFixture
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

/** A current, ready playback session with the caller's exact catalog and recordings. */
internal fun currentPlaybackObservation(
    channels: List<Channel> = emptyList(),
    recordings: List<DvrEntry> = emptyList(),
): SessionObservation = SessionObservation.create(
    sessionState = SessionState.Ready(
        ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
    ),
    channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels = channels)),
    epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
    dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = recordings)),
)

internal fun subscriptionStream(index: Long, type: SubscriptionStreamType) = SubscriptionStream(
    index = StreamIndex(index), type = type, language = null, compositionId = null, ancillaryId = null,
    width = null, height = null, frameDuration = null, aspectNumerator = null, aspectDenominator = null,
    audioType = null, audioVersion = null, channelCount = null, rate = null, rdsUecp = null,
)

internal suspend fun emitVideoAudioPair(connection: ScriptedSubscriptionConnection, index: Int) {
    val time = index * 40_000L
    // Synthetic MPEG-2 sequence/picture headers and an MPEG-1 Layer II frame. No decoding needed.
    val video = byteArrayOf(0, 0, 1, 0xb3.toByte(), 0x14, 0, 0xf0.toByte(), 0x13,
        0xff.toByte(), 0xff.toByte(), 0xe0.toByte(), 0x18,
        0, 0, 1, 0xb8.toByte(), 0, 0, 0, 0x40, 0, 0, 1, 0, 0, 8, 0, 0)
    val audio = ByteArray(417).apply { this[0] = 0xff.toByte(); this[1] = 0xfd.toByte(); this[2] = 0x80.toByte(); this[3] = 0xc0.toByte() }
    connection.emit(SubscriptionEvent.Packet(MuxFrameType.I, StreamIndex(1), time, time, 40_000, SubscriptionBinaryFixture(video)))
    connection.emit(SubscriptionEvent.Packet(MuxFrameType.I, StreamIndex(2), time, time, 40_000, SubscriptionBinaryFixture(audio)))
}

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
