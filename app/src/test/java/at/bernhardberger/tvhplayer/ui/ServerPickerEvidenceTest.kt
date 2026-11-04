package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.settings.serverProfileDisplayName
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.testutil.leaveTouchModeBeforeComposing
import at.bernhardberger.tvhplayer.ui.components.*
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Offline production picker and shell, with the same fictional programme behind warm playback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w960dp-h540dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServerPickerEvidenceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var view: View
    private var profiles by mutableStateOf(listOf(
        fixtureServer(1, "tvheadend.home.arpa", "anna", "Living room"),
        fixtureServer(2, "tvheadend.home.arpa", "kids"),
        fixtureServer(3, "192.168.1.20"),
        fixtureServer(4, "cabin.home.arpa", "anna", "Cabin", credentialsAvailable = false)))
    private var playing by mutableStateOf(false)
    private var confirmation by mutableStateOf<ServerProfileSummary?>(null)
    private fun tag(index: Int) = "server-picker-${profiles[index].id.value}"
    private fun press(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; compose.waitForIdle() }
    private fun scene(startup: Boolean = false) {
        compose.leaveTouchModeBeforeComposing()
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (playing) {
                        val image = remember { FixtureArt.art("still-ridge-light").asImageBitmap() }
                        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        WarmPlaybackScrim()
                    }
                    val page: @Composable (Boolean, PaddingValues, Boolean) -> Unit = { focus, padding, drawer ->
                        ServerPickerPage(profiles, profiles.first().id, { if (playing) confirmation = it }, {}, focus && confirmation == null,
                            modifier = Modifier.padding(padding).offset(x = if (!startup && !drawer) 17.dp else 0.dp), contentFocusEnabled = confirmation == null, drawerOpen = drawer)
                        confirmation?.let { profile -> SwitchServerDialog(serverProfileDisplayName(profile), profile.username,
                            PlaybackInterruption.LIVE, { confirmation = null }, { confirmation = null }) }
                    }
                    if (startup) page(true, PaddingValues(top = 28.dp, bottom = 32.dp), false) else SideRail(AppDestination.SWITCH_SERVER, {}, {},
                        availableDestinations = setOf(AppDestination.CHANNELS, AppDestination.GUIDE, AppDestination.RECORDINGS,
                            AppDestination.SWITCH_SERVER, AppDestination.SETTINGS),
                        nowPlaying = if (playing) RailNowPlaying(AppPlaybackTarget.Live(ChannelId(1)), ChannelId(1), 1,
                            "Ridge Earth", null, null, false) else null) { padding, drawer ->
                        page(!drawer, padding, drawer)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test @Category(VisualCapture::class) fun switcherFrames() {
        scene()
        // Focus the existing drawer destination: its page previews, with no focused avatar.
        compose.onNodeWithTag("nav-switch-server").performSemanticsAction(SemanticsActions.RequestFocus)
        capture("01-drawer-switch-server-focused", "drawer Switch server")
        press(Key.DirectionRight)
        compose.onNodeWithTag(tag(0)).assertIsFocused()
        press(Key.DirectionRight)
        capture("02-row-kids-focused", "kids")
        press(Key.DirectionRight); press(Key.DirectionRight)
        capture("03-row-password-needed-focused", "Cabin")
        press(Key.DirectionDown)
        compose.onNodeWithTag("server-picker-add").assertIsFocused()
        capture("05-add-server-focused", "Add server")
        compose.runOnIdle { profiles = profiles + listOf(
            fixtureServer(5, "tvheadend.home.arpa", "grandparents", "grandparents-livingroom"),
            fixtureServer(6, "studio.home.arpa", "guest", "Studio"),
            fixtureServer(7, "garden.home.arpa", "viewer", "Garden")) }
        compose.onNodeWithTag(tag(4)).performSemanticsAction(SemanticsActions.RequestFocus)
        capture("04-seven-servers-long-name-focused", "grandparents-livingroom")
        compose.runOnIdle { profiles = profiles.take(4); playing = true }
        compose.onNodeWithTag("nav-switch-server").performSemanticsAction(SemanticsActions.RequestFocus)
        capture("06-open-while-playing", "drawer Switch server; Ridge Earth still")
        press(Key.DirectionRight)
        compose.onNodeWithTag(tag(1)).performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_KEEP).assertIsFocused()
        capture("07-switch-confirm-while-playing", "Keep watching")
        press(Key.Back)
        compose.onNodeWithTag(tag(1)).assertIsFocused()
    }

    @Test @Category(VisualCapture::class) fun startupPicker() {
        profiles = profiles.take(2)
        scene(startup = true)
        compose.onNodeWithTag(tag(0)).assertIsFocused()
        capture("startup-picker", "Living room; full screen")
    }

    @Test @Category(VisualCapture::class) fun anonymousAddress() {
        profiles = listOf(fixtureServer(11, "cabin.home.arpa", label = "Cabin"),
            fixtureServer(12, "cabin.home.arpa", port = 9983))
        scene(startup = true)
        capture("anonymous-address", "Cabin; labeled anonymous and non-default port")
        compose.onNodeWithContentDescription("cabin.home.arpa · port 9983").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithContentDescription("cabin.home.arpa · port 9983").assertIsFocused()
        capture("anonymous-non-default-port", "anonymous address with port 9983")
    }

    private fun capture(name: String, focus: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val dir = File("build/outputs/server-switcher-captures").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            File(dir, "$name.txt").writeText("canvas=960x540dp; locale=en; fontScale=1.0; focus=$focus; fictional fixtures")
            bitmap.recycle()
        }
    }
}
