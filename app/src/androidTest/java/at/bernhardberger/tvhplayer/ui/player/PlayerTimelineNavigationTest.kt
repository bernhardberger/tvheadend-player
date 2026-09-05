package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@OptIn(ExperimentalTestApi::class)
class PlayerTimelineNavigationTest(private val recording: Boolean) {
    @get:Rule val rule = createComposeRule()

    @Test
    fun verticalRelocationCommitsOnceAndConsumesRepeatAndRelease() {
        var commits = 0
        var leakedEvents = 0
        rule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().onKeyEvent { leakedEvents++; false }) {
                    if (recording) RecordingOverlayControls(
                        imageLoader = imageLoader, piconPath = null, title = "Recording", subtitle = null,
                        channelName = "Channel", positionMs = 30_000, durationMs = 120_000, growing = false,
                        nowSec = 0, canSeek = true, controlsVisible = true, optionsOpen = false,
                        onTogglePlayPause = {}, onSeek = {}, onStopPlayback = {}, onUserInteraction = {},
                        onOpenOptions = {}, onOpenInfo = {}, onCommitSeek = { commits++ },
                    ) else OverlayControlsTv(
                        imageLoader = imageLoader, channelNumber = 1, channelName = "Channel", piconPath = null,
                        nowEvent = null, nextEvent = null, nowSec = 0, controlsVisible = true, optionsOpen = false,
                        onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                        timeshiftState = AppTimeshiftState(available = true, bufferStartMs = 0, positionMs = 60_000, liveEdgeMs = 120_000),
                        timeshiftFeedback = null, onToggleTimeshiftPause = {}, onSeekTimeshift = {}, onGoLive = {},
                        onCommitSeek = { commits++ },
                    )
                }
            }
        }
        val timeline = if (recording) "recording-seekbar" else "player-seekbar"
        rule.onNodeWithTag(timeline).assertIsFocused()
        rule.onRoot().performKeyInput { keyDown(Key.DirectionUp) }
        rule.onNodeWithTag("player-info").assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeySync(
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 2),
        )
        rule.onRoot().performKeyInput { keyUp(Key.DirectionUp) }
        rule.onNodeWithTag("player-info").assertIsFocused()
        rule.runOnIdle { assertEquals(1, commits); assertEquals(0, leakedEvents) }
        rule.onRoot().performKeyInput { keyDown(Key.DirectionDown) }
        rule.onNodeWithTag(timeline).assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeySync(
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 2),
        )
        rule.onRoot().performKeyInput { keyUp(Key.DirectionDown) }
        rule.onNodeWithTag(timeline).assertIsFocused()
        rule.runOnIdle { assertEquals(1, commits); assertEquals(0, leakedEvents) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "recording={0}")
        fun surfaces() = listOf(false, true)
    }
}
