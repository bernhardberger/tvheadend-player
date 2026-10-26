package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsFrameRateLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsPlayerLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsRow
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The frame-rate choice level under Settings > Player: opens on the stored choice, a pick stores it and returns. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class FrameRateSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun televisionFeature() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
    }

    @Test
    fun levelOpensOnTheStoredChoiceAndAPickStoresItAndReturns() {
        val title = "Match content frame rate"
        var ui by mutableStateOf(SettingsPlayerUiState(frameRateSwitching = FrameRateSwitching.SEAMLESS_ONLY))
        val picks = mutableListOf<FrameRateSwitching>()
        compose.setContent {
            TVHeadendPlayerTheme {
                val navigation = rememberDepthNavigationState("root")
                val levels = listOf(
                    settingsLevel("root", "Settings", listOf(settingsRow("player", "Open player", child = SettingsSection.PLAYER.name))),
                    settingsPlayerLevel(ui, {}, {}, {}),
                    settingsFrameRateLevel(ui.frameRateSwitching) {
                        picks += it
                        ui = ui.copy(frameRateSwitching = it)
                        navigation.pop()
                    },
                )
                Box(Modifier.fillMaxSize()) { SettingsScreenNavigation(navigation, levels) }
            }
        }
        key(Key.DirectionCenter)
        var downs = 0
        while (compose.onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isEmpty()) {
            check(++downs < 20) { "Frame rate row not reachable" }
            key(Key.DirectionDown)
        }
        compose.onNode(isFocused() and hasText(title) and hasText("Only if seamless")).assertExists()

        key(Key.DirectionCenter)
        compose.onNode(isFocused() and hasText("Only if seamless")).assertIsSelected()
        compose.onNode(hasText("Always")).assertIsNotSelected()
        // Each option carries its own help instead of one description for the page.
        compose.onNode(isFocused() and hasText("Switches only when the TV can do it without going dark.")).assertExists()
        compose.onNode(hasText("Always") and hasText("Switches the TV to the video’s frame rate, even if the screen goes dark for a moment."))
            .assertExists()

        // Back leaves without choosing.
        key(Key.Back)
        compose.onNode(isFocused() and hasText(title)).assertExists()
        assertEquals(emptyList<FrameRateSwitching>(), picks)

        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(listOf(FrameRateSwitching.OFF), picks)
        compose.onNode(isFocused() and hasText(title) and hasText("Off")).assertExists()

        key(Key.DirectionCenter)
        compose.onNode(isFocused() and hasText("Off")).assertIsSelected()
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
