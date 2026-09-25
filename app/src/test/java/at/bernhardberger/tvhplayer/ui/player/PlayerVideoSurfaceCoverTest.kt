package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class PlayerVideoSurfaceCoverTest {
    @get:Rule val compose = createComposeRule()
    private val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Application>()).build()

    @After fun release() = player.release()

    @Test fun opaqueCoverHidesTheSurfaceUntilTheTargetFrameIsVisible() {
        val visible = mutableStateOf(false)
        compose.setContent { PlayerVideoSurface(player, AspectRatioMode.FIT, videoVisible = visible.value) }

        compose.onNodeWithTag("video-cover").assertIsDisplayed()

        visible.value = true
        compose.waitForIdle()
        compose.onNodeWithTag("video-cover").assertDoesNotExist()

        visible.value = false
        compose.waitForIdle()
        compose.onNodeWithTag("video-cover").assertIsDisplayed()
    }
}
