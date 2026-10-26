package at.bernhardberger.tvhplayer.ui.screens.settings

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.StreamProfile
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.testing.testSessionObservation
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settingsRootLevel
import at.bernhardberger.tvhplayer.ui.screens.SETTINGS_ROOT
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Offline production Player content, including its real category focus owner. */
@RunWith(Parameterized::class)
@OptIn(ExperimentalTestApi::class)
class SettingsPlayerTest(private val language: String) {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overflowingProfiles_allReachable_selectLastOnce_andReturnUpAndLeft() {
        val configuration = Configuration(composeRule.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            fontScale = 1.5f
        }
        val context = composeRule.activity.createConfigurationContext(configuration)
        val player = context.getString(R.string.settings_player)
        val autoplay = context.getString(R.string.auto_start_playback)
        val profileRow = context.getString(R.string.profile)
        val pageRows = listOf(R.string.auto_start_playback, R.string.timeshift_setting, R.string.keep_channel_setting,
            R.string.profile, R.string.startup_buffer_setting, R.string.refresh_rate_matching_setting,
            R.string.tunneling_setting).map(context::getString)
        val serverDefault = context.getString(R.string.profile_server_default)
        val profiles = List(24) { index ->
            StreamProfile(
                StreamProfileId((index + 1).toString(16).padStart(32, '0')),
                if (language == "de") {
                    "Profil ${index + 1} – Hochauflösendes Fernsehen mit unveränderter Bildqualität und mehrsprachigen Tonspuren"
                } else {
                    "Profile ${index + 1} – High definition television with original picture quality and multiple audio languages"
                },
                "",
            )
        }
        var ui by mutableStateOf(
            SettingsPlayerUiState(
                connected = true,
                profiles = StreamProfilesResult.Available.create(
                    profiles = profiles,
                    originatingSession = requireNotNull(testSessionObservation().currentSession),
                ),
                selectedProfileId = profiles.first().id,
            ),
        )
        val selections = mutableListOf<StreamProfileId?>()
        var switchCalls = 0
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, 1.5f),
            ) {
                TVHeadendPlayerTheme {
                    Box(Modifier.size(960.dp, 540.dp)) {
                        val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.PLAYER.name)
                        SettingsScreenNavigation(
                            navigation = navigation,
                            levels = listOf(settingsRootLevel(), settingsPlayerLevel(
                                ui = ui,
                                onAutoStartPlaybackChanged = { switchCalls++ },
                                onTimeshiftEnabledChanged = { switchCalls++ },
                                onTunnelingEnabledChanged = { switchCalls++ },
                            ), settingsStreamProfileLevel(ui) {
                                selections += it
                                ui = ui.copy(selectedProfileId = it)
                                navigation.pop()
                            }),
                        )
                    }
                }
            }
        }

        val categoryNode = composeRule.onNode(hasText(player) and hasClickAction())
        val heading = composeRule.onNode(hasText(player) and !hasClickAction())
        categoryNode.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        val headingBounds = heading.fetchSemanticsNode().boundsInRoot
        focused(autoplay).assertIsDisplayed()
        pageRows.drop(1).forEach { label ->
            press(Key.DirectionDown)
            focused(label).assertIsDisplayed()
        }
        pageRows.dropLast(1).asReversed().forEach { label ->
            press(Key.DirectionUp)
            focused(label).assertIsDisplayed()
        }

        // The profile row opens its own page on the saved profile.
        repeat(pageRows.indexOf(profileRow)) { press(Key.DirectionDown) }
        focused(profileRow).assertExists()
        press(Key.DirectionCenter)
        focused(profiles.first().name).assertIsSelected()
        press(Key.DirectionUp)
        focused(serverDefault).assertIsNotSelected()

        // Assert every choice, not just a jump/scroll directly to the last profile.
        profiles.forEachIndexed { index, profile ->
            press(Key.DirectionDown)
            val row = focused(profile.name).assertIsDisplayed()
            if (index == 0) row.assertIsSelected() else row.assertIsNotSelected()
        }
        composeRule.runOnIdle {
            assertEquals(emptyList<StreamProfileId?>(), selections)
            assertEquals(0, switchCalls)
        }
        press(Key.DirectionCenter)
        focused(profileRow).assert(hasText(profiles.last().name)).assertIsDisplayed()
        heading.assertIsDisplayed()
        assertEquals(headingBounds, heading.fetchSemanticsNode().boundsInRoot)

        press(Key.DirectionCenter)
        focused(profiles.last().name).assertIsDisplayed().assertIsSelected()
        // Left leaves the scrolled bottom the same way Back does, then the page through the category owner.
        press(Key.DirectionLeft)
        focused(profileRow).assertExists()
        press(Key.DirectionLeft)
        categoryNode.assertIsFocused()
        press(Key.DirectionRight)
        focused(autoplay).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(profiles.last().id), selections)
            assertEquals(0, switchCalls)
        }
    }

    private fun focused(label: String) = composeRule.onNode(isFocused() and hasText(label))

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-font1.5")
        fun languages(): List<Array<String>> = listOf(arrayOf("en"), arrayOf("de"))
    }
}
