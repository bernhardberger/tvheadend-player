@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackRecovery
import java.lang.reflect.Proxy
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** The app's immediate pause must cancel the SDK recovery deadline, including near expiry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AudioInterruptionRecoveryContractTest {
    @Test
    fun realPauseCancelsRecoveryForBothFocusLossAndNoisyReasons() {
        for (reason in listOf(
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        )) {
            val fixture = RecoveryPlayer()
            val recoveries = mutableListOf<PlaybackRecoveryReason>()
            createTvheadendPlaybackRecovery(fixture.player, onRecoveryRequired = recoveries::add).use { recovery ->
                recovery.beginPlaybackTarget()
                fixture.buffer()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(19_999))
                fixture.pause(reason)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))

                assertTrue("Pause reason $reason must cancel recovery", recoveries.isEmpty())
            }
        }
    }

    private class RecoveryPlayer {
        private val listeners = mutableListOf<Player.Listener>()
        private var playWhenReady = true
        private var parameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getApplicationLooper" -> Looper.getMainLooper()
                "getPlayWhenReady" -> playWhenReady
                "getCurrentTracks" -> Tracks.EMPTY
                "getTrackSelectionParameters" -> parameters
                "setTrackSelectionParameters" -> { parameters = args!![0] as TrackSelectionParameters; null }
                "addListener" -> { listeners.add(args!![0] as Player.Listener); null }
                "removeListener" -> { listeners.remove(args!![0] as Player.Listener); null }
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player

        fun buffer() = listeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_BUFFERING) }

        fun pause(reason: Int) {
            playWhenReady = false
            listeners.toList().forEach { it.onPlayWhenReadyChanged(false, reason) }
        }
    }
}
