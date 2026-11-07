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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsKeepChannelLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsStartupBufferLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsStreamProfileLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsChannelTagsLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.AD_BREAKS_LEVEL
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.SkipLanding
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsAdBreaksLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsPlayerLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsRow
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsSkipLandingLevel
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

/** Settings > Playback's Recording marks group: each row shows the stored choice, and a pick stores it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class RecordingMarksSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun skipLandsDescriptionBounds() = descriptionBounds { settingsSkipLandingLevel(SkipLanding.END) {} }
    @Test fun keepTunedDescriptionBounds() = descriptionBounds { settingsKeepChannelLevel(5) {} }
    @Test fun startupBufferDescriptionBounds() = descriptionBounds { settingsStartupBufferLevel(0, 0) {} }
    @Test fun streamProfileDescriptionBounds() = descriptionBounds { settingsStreamProfileLevel(SettingsPlayerUiState()) {} }
    @Test fun channelGroupsDescriptionBounds() = descriptionBounds {
        settingsChannelTagsLevel(emptyList(), ChannelScopeVisibility(), true, false, {}, {})
    }

    private fun descriptionBounds(levelFactory: @Composable () -> DepthLevel) {
        var id = ""
        var row = ""
        var density = 1f
        compose.setContent {
            TVHeadendPlayerTheme {
                val level = levelFactory()
                id = level.id
                row = level.rows.first().item.id
                density = LocalDensity.current.density
                val navigation = rememberDepthNavigationState(level.id)
                Box(Modifier.fillMaxSize()) { SettingsScreenNavigation(navigation, listOf(level)) }
            }
        }
        compose.waitForIdle()
        val title = compose.onNodeWithTag("settings-level-$id").fetchSemanticsNode().boundsInRoot
        val description = compose.onNodeWithTag("settings-description-$id").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("settings-row-$row").fetchSemanticsNode().boundsInRoot
        assertEquals(title.left, description.left, 0.5f)
        assertEquals(4f * density, description.top - title.bottom, 0.5f)
        assertEquals(24f * density, first.top - description.bottom, 0.5f)
        assertEquals(first.width, description.width, 0.5f)
    }

    @Before
    fun televisionFeature() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
    }

    @Test
    fun theGroupsRowsOpenOnTheStoredChoiceAndStoreAPick() {
        var ui by mutableStateOf(SettingsPlayerUiState())
        val modes = mutableListOf<AdBreakMode>()
        val cuts = mutableListOf<Boolean>()
        val landings = mutableListOf<SkipLanding>()
        compose.setContent {
            TVHeadendPlayerTheme {
                val navigation = rememberDepthNavigationState("root")
                val levels = listOf(
                    settingsLevel("root", "Settings", listOf(settingsRow("player", "Open player", child = SettingsSection.PLAYER.name))),
                    settingsPlayerLevel(ui, {}, {}, {}, onSkipCutsChanged = {
                        cuts += it
                        ui = ui.copy(skipCuts = it)
                    }),
                    settingsAdBreaksLevel(ui.adBreakMode) {
                        modes += it
                        ui = ui.copy(adBreakMode = it)
                        navigation.pop()
                    },
                    settingsSkipLandingLevel(ui.skipLanding) {
                        landings += it
                        ui = ui.copy(skipLanding = it)
                        navigation.pop()
                    },
                )
                Box(Modifier.fillMaxSize()) { SettingsScreenNavigation(navigation, levels) }
            }
        }
        key(Key.DirectionCenter)
        downTo("Ad breaks")
        compose.onNode(isFocused() and hasText("Ad breaks") and hasText("Ask")).assertExists()
        compose.onNode(hasText("Recording marks", ignoreCase = true)).assertExists()

        key(Key.DirectionCenter)
        compose.onNode(isFocused() and hasText("Ask")).assertIsSelected()
        compose.onNodeWithTag("settings-description-$AD_BREAKS_LEVEL").assertDoesNotExist()
        compose.onNode(hasText("Skip automatically")).assertIsNotSelected()
        compose.onNode(hasText("Off") and hasText("Ad breaks play like the rest. They still show on the timeline."))
            .assertExists()
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(listOf(AdBreakMode.AUTOMATIC), modes)
        compose.onNode(isFocused() and hasText("Ad breaks") and hasText("Skip automatically")).assertExists()

        downTo("Skip cuts")
        key(Key.DirectionCenter)
        assertEquals(listOf(false), cuts)

        downTo("Skip lands")
        compose.onNode(isFocused() and hasText("Skip lands") and hasText("At the end")).assertExists()
        key(Key.DirectionCenter)
        compose.onNode(isFocused() and hasText("At the end")).assertIsSelected()
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(listOf(SkipLanding.TWO_SECONDS_BEFORE), landings)
        compose.onNode(isFocused() and hasText("Skip lands") and hasText("2 s before the end")).assertExists()
    }

    private fun downTo(title: String) {
        var downs = 0
        while (compose.onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isEmpty()) {
            check(++downs < 20) { "$title row not reachable" }
            key(Key.DirectionDown)
        }
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
