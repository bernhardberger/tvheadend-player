package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.settings.PlayerChromeDesign
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsPlayerDesignLevel
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

/** The Player design choice level under Settings > Player, like the other player choices. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
class PlayerDesignSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun televisionFeature() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
    }

    @Test fun english() = level(title = "Player design", current = "Current", new = "New")

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun german() = level(title = "Player-Design", current = "Aktuell", new = "Neu")

    private fun level(title: String, current: String, new: String) {
        var design by mutableStateOf(PlayerChromeDesign.CURRENT)
        var selections = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                val navigation = rememberDepthNavigationState("root")
                val levels = listOf(
                    settingsLevel("root", "Settings", listOf(settingsRow("player", "Open player", child = SettingsSection.PLAYER.name))),
                    settingsPlayerLevel(SettingsPlayerUiState(), {}, {}, {}, {}, playerDesign = design),
                    settingsPlayerDesignLevel(design) {
                        selections++
                        design = it
                        navigation.pop()
                    },
                )
                Box(Modifier.fillMaxSize()) { SettingsScreenNavigation(navigation, levels) }
            }
        }
        key(Key.DirectionCenter)
        var downs = 0
        while (compose.onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isEmpty()) {
            check(++downs < 20) { "Player design row not reachable" }
            key(Key.DirectionDown)
        }
        compose.onNode(isFocused() and hasText(title) and hasText(current)).assertExists()

        // Opening lands on the stored choice; Back leaves without choosing.
        key(Key.DirectionCenter)
        compose.onNode(isFocused() and hasText(current)).assertIsSelected()
        compose.onNode(hasText(new) and hasClickAction()).assertIsNotSelected()
        key(Key.Back)
        compose.onNode(isFocused() and hasText(title)).assertExists()
        assertEquals(0, selections)

        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(1, selections)
        assertEquals(PlayerChromeDesign.NEW, design)
        compose.onNode(isFocused() and hasText(title) and hasText(new)).assertExists()

        key(Key.DirectionCenter)
        compose.onNode(isFocused() and hasText(new)).assertIsSelected()
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
