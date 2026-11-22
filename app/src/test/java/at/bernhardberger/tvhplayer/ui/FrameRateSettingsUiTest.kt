package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.testutil.pressKey
import android.app.Application
import android.hardware.display.DisplayManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
import at.bernhardberger.tvhplayer.settings.resolveFrameRateSwitching
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settings.FRAME_RATE_LEVEL
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsDisplayRateLevel
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowDisplayManager

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
    @Config(sdk = [30, 31], shadows = [SeamlessDisplayPreference::class])
    fun levelOpensOnTheEffectiveChoiceAndAPickStoresItAndReturns() {
        val title = "Match content frame rate"
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val choices = listOf("Always", "Only if seamless", "Off").let { if (modern) listOf("Follow system") + it else it }
        var ui by mutableStateOf(SettingsPlayerUiState(frameRateSwitching = resolveFrameRateSwitching(null)))
        val picks = mutableListOf<FrameRateSwitching>()
        compose.setContent {
            TVHeadendPlayerTheme {
                val navigation = rememberDepthNavigationState(SettingsSection.PLAYER.name, FRAME_RATE_LEVEL)
                val levels = listOf(
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
        val parentRow = isFocused() and hasText(title)
        compose.onNode(parentRow).assertIsEnabled()

        compose.pressKey(Key.DirectionCenter)
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("depth-active")))
            .assertCountEquals(choices.size)
        val firstChoice = isFocused() and hasText(choices.first())
        compose.onNode(firstChoice).assertIsSelected()
        if (modern) {
            compose.onNode(firstChoice and hasText("Only if seamless", substring = true)).assertExists()
        }
        choices.drop(1).forEach { label ->
            compose.pressKey(Key.DirectionDown)
            compose.onNode(isFocused() and hasText(label)).assertIsNotSelected()
        }

        // Back leaves without choosing.
        compose.pressKey(Key.Back)
        compose.onNode(
            parentRow and if (modern) {
                hasText("Follow system", substring = true) and hasText("Only if seamless", substring = true)
            } else {
                hasText("Always")
            },
        ).assertExists()
        assertEquals(emptyList<FrameRateSwitching>(), picks)

        compose.pressKey(Key.DirectionCenter)
        repeat(choices.lastIndex) { compose.pressKey(Key.DirectionDown) }
        compose.pressKey(Key.DirectionCenter)
        assertEquals(listOf(FrameRateSwitching.OFF), picks)
        compose.onNode(parentRow and hasText("Off")).assertExists()

        compose.pressKey(Key.DirectionCenter)
        compose.onNode(isFocused() and hasText("Off")).assertIsSelected()
    }

    @Implements(DisplayManager::class, minSdk = 31)
    class SeamlessDisplayPreference : ShadowDisplayManager() {
        @Implementation
        fun getMatchContentFrameRateUserPreference(): Int = DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY
    }

    @Test
    fun displayRateLevelListsRatesFocusesRoundedStoredChoiceAndEmitsPicks() {
        val title = "Display refresh rate"
        var ui by mutableStateOf(SettingsPlayerUiState(displayRefreshRate = 59.9401f))
        val picks = mutableListOf<Float?>()
        compose.setContent {
            TVHeadendPlayerTheme {
                val navigation = rememberDepthNavigationState("root")
                val levels = listOf(
                    settingsLevel("root", "Settings", listOf(settingsRow("player", "Open player", child = SettingsSection.PLAYER.name))),
                    settingsPlayerLevel(ui, {}, {}, {}),
                    settingsDisplayRateLevel(ui.displayRefreshRate, listOf(50f, 59.94f, 60f)) {
                        picks += it
                        ui = ui.copy(displayRefreshRate = it)
                        navigation.pop()
                    },
                )
                Box(Modifier.fillMaxSize()) { SettingsScreenNavigation(navigation, levels) }
            }
        }
        compose.pressKey(Key.DirectionCenter)
        var downs = 0
        while (compose.onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isEmpty()) {
            check(++downs < 20) { "Display rate row not reachable" }
            compose.pressKey(Key.DirectionDown)
        }
        compose.onNode(isFocused() and hasText(title) and hasText("59.94 Hz")).assertExists()

        compose.pressKey(Key.DirectionCenter)
        compose.onNode(isFocused() and hasText("59.94 Hz")).assertIsSelected()
        compose.onNode(hasText("60 Hz")).assertIsNotSelected()
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        assertEquals(listOf<Float?>(50f), picks)

        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionUp)
        compose.onNode(isFocused() and hasText("System default")).assertIsNotSelected()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(listOf(50f, null), picks)
    }
}
