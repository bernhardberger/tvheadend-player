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

    @Test fun delayIsOneThousandMillisecondsAndEveryExitAndTargetResetsIt() {
        var eligible by mutableStateOf(true)
        var target by mutableStateOf(1)
        lateinit var visible: State<Boolean>
        var startedAt = 0L
        compose.mainClock.autoAdvance = false
        compose.setContent {
            visible = rememberBufferingVisible(target, eligible)
            startedAt = compose.mainClock.currentTime
        }
        compose.mainClock.advanceTimeBy(999, ignoreFrameDuration = true)
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
            compose.mainClock.advanceTimeBy(startedAt + 999 - compose.mainClock.currentTime, ignoreFrameDuration = true)
            compose.runOnIdle { assertFalse(visible.value) }
            compose.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
            compose.runOnIdle { assertTrue(visible.value) }
        }
        compose.runOnIdle { target++; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse(visible.value) }
        compose.mainClock.advanceTimeBy(startedAt + 999 - compose.mainClock.currentTime, ignoreFrameDuration = true)
        compose.runOnIdle { assertFalse(visible.value) }
        compose.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
        compose.runOnIdle { assertTrue(visible.value) }
    }

    @Test fun liveBrightVideoCaptureAndNoFocusOrControlsMovement() = capture(false, 1f)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun recordingGermanLargeTextCaptureAndNoFocusOrControlsMovement() = capture(true, 1.3f)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun liveGermanLargeTextCaptureAndNoFocusOrControlsMovement() = capture(false, 1.3f)

    @Test fun recordingBrightVideoCaptureAndNoFocusOrControlsMovement() = capture(true, 1f)

    private fun capture(recording: Boolean, fontScale: Float) {
        var buffering by mutableStateOf(false)
        lateinit var view: View
        val target = if (recording) AppPlaybackTarget.Recording(DvrEntryId(1)) else live
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        compose.setContent {
            view = LocalView.current
            val context = LocalContext.current
            val loader = remember { ImageLoader.Builder(context).diskCache(null).build() }
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale), LocalLifecycleOwner provides owner) {
                TVHeadendPlayerTheme {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        if (recording) RecordingOverlayControls(
                            imageLoader = loader, piconPath = null, title = "Zeit im Bild",
                            subtitle = "Eine Reise durch die Berge", channelName = "Documentary",
                            positionMs = 1_800_000, durationMs = 3_600_000, growing = false, nowSec = 1_800,
                            canSeek = true, controlsVisible = true, optionsOpen = false,
                            onTogglePlayPause = {}, onSeek = {}, onStopPlayback = {}, onUserInteraction = {},
                            onOpenOptions = {}, onOpenInfo = {},
                        ) else OverlayControlsTv(
                            imageLoader = loader, channelNumber = 1, channelName = "Documentary",
                            piconPath = null, nowEvent = null, nextEvent = null, nowSec = 1_800,
                            controlsVisible = true, optionsOpen = false, onOpenChannels = {},
                            onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                            timeshiftState = at.bernhardberger.tvhplayer.playback.AppTimeshiftState(available = true),
                            timeshiftFeedback = null, onToggleTimeshiftPause = {}, onSeekTimeshift = {}, onGoLive = {},
                        )
                        CompactBufferingStatus(
                            state = if (buffering) AppPlaybackState.Buffering else AppPlaybackState.Playing,
                            playWhenReady = true, target = target, expectedTarget = target, generation = 1,
                            screenActive = true, foregroundBlocked = false, modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("player-pause").assertIsFocused()
        val actionsTag = if (recording) "recording-actions" else "player-actions"
        val before = compose.onNodeWithTag(actionsTag).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { buffering = true; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithTag("player-buffering-status").assertExists()
        compose.onNodeWithTag("player-pause").assertIsFocused()
        assertEquals(before, compose.onNodeWithTag(actionsTag).fetchSemanticsNode().boundsInRoot)
        val locale = if (fontScale == 1.3f) "de" else "en"
        val name = "${if (recording) "recording" else "live"}-$locale-font$fontScale"
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/buffering-evidence").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "$name.txt").writeText("canvas=960x540\ndensity=1.0\nfontScale=$fontScale\nlocale=$locale\nfocus=player-pause\nbackdrop=white bright-video stress\nproduction composables with fake state; no device/runtime claim\n")
        }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("player-buffering-status").assertDoesNotExist()
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("player-buffering-status").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithTag("player-buffering-status").assertExists()
        compose.runOnIdle { buffering = false; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("player-buffering-status").assertDoesNotExist()
        compose.onNodeWithTag("player-pause").assertIsFocused()
    }
}
