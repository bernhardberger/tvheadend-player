package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactBufferingStatusTest {
    @get:Rule val compose = createComposeRule()
    private val live = AppPlaybackTarget.Live(ChannelId(1))

    @Test fun onlyActiveCurrentBufferingWithPlaybackIntentIsEligible() {
        fun eligible(state: AppPlaybackState = AppPlaybackState.Buffering, intent: Boolean = true,
                     target: AppPlaybackTarget? = live, active: Boolean = true, blocked: Boolean = false) =
            bufferingStatusEligible(state, intent, target, live, active, blocked)
        assertTrue(eligible())
        for (state in listOf(AppPlaybackState.Idle, AppPlaybackState.Starting, AppPlaybackState.Playing,
            AppPlaybackState.Finished,
            AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 1_000),
            AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER))) assertFalse(eligible(state))
        assertFalse(eligible(intent = false))
        assertFalse(eligible(active = false))
        assertFalse(eligible(blocked = true))
        assertFalse(eligible(target = null))
        assertFalse(eligible(target = AppPlaybackTarget.Live(ChannelId(2))))
        assertFalse(eligible(target = AppPlaybackTarget.Recording(DvrEntryId(1))))
    }

    @Test fun delayIsOneThousandMillisecondsAndEveryExitAndTargetResetsIt() =
        assertContinuousDelay(PlayerBusyStatus.BUFFERING)

    @Test fun tuningDelayIsFiveHundredMillisecondsAndEveryExitAndTargetResetsIt() =
        assertContinuousDelay(PlayerBusyStatus.TUNING)

    private fun assertContinuousDelay(status: PlayerBusyStatus) {
        var eligible by mutableStateOf(true)
        var target by mutableStateOf(1)
        lateinit var visible: State<Boolean>
        var startedAt = 0L
        compose.mainClock.autoAdvance = false
        compose.setContent {
            visible = rememberBusyVisible(target, eligible, status)
            startedAt = compose.mainClock.currentTime
        }
        compose.mainClock.advanceTimeBy(status.delayMs - 1, ignoreFrameDuration = true)
        compose.runOnIdle { assertFalse(visible.value) }
        compose.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
        compose.runOnIdle { assertTrue(visible.value) }
        // Every subsequent stall starts a fresh interval.
        repeat(2) {
            compose.runOnIdle { eligible = false; Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertFalse(visible.value); eligible = true; Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(startedAt + status.delayMs - 1 - compose.mainClock.currentTime, ignoreFrameDuration = true)
            compose.runOnIdle { assertFalse(visible.value) }
            compose.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
            compose.runOnIdle { assertTrue(visible.value) }
        }
        compose.runOnIdle { target++; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse(visible.value) }
        compose.mainClock.advanceTimeBy(startedAt + status.delayMs - 1 - compose.mainClock.currentTime, ignoreFrameDuration = true)
        compose.runOnIdle { assertFalse(visible.value) }
        compose.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
        compose.runOnIdle { assertTrue(visible.value) }
    }
}
