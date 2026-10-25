package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChannelPlaybackMarkerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun soundBarsAreOnlyPresentForPlayingAndKeepDescription() {
        val state = mutableStateOf(ChannelPlaybackIndicator.PLAYING)
        compose.setContent { MaterialTheme { ChannelPlaybackMarker(state.value) } }
        compose.onNodeWithTag("channel-playing-indicator").assertContentDescriptionEquals(
            RuntimeEnvironment.getApplication().getString(R.string.player_on_now))
        compose.runOnIdle { state.value = ChannelPlaybackIndicator.PAUSED }
        compose.onNodeWithTag("channel-playing-indicator").assertDoesNotExist()
        compose.onNodeWithTag("channel-paused-indicator").assertExists()
        compose.runOnIdle { state.value = ChannelPlaybackIndicator.NONE }
        compose.onNodeWithTag("channel-playing-indicator").assertDoesNotExist()
        compose.onNodeWithTag("channel-paused-indicator").assertDoesNotExist()
    }

    @Test fun barsStayInsideTheSlotAndMoveOutOfPhase() {
        for (frame in 0..100) for (bar in 0..2) {
            assertTrue(watchingBarHeight(bar, frame / 100f) in 0.3f..0.75f)
        }
        assertTrue(watchingBarHeight(0, 0f) != watchingBarHeight(1, 0f))
        assertTrue(watchingBarHeight(0, 0f) != watchingBarHeight(0, 0.25f))
    }
}
