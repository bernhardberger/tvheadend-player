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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        val category = context.getString(R.string.settings_player_nav)
        val timeshift = context.getString(R.string.timeshift_setting)
        val refreshRate = context.getString(R.string.refresh_rate_matching_setting)
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
        val navigations = mutableListOf<SettingsSection>()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, 1.5f),
            ) {
                TVHeadendPlayerTheme {
                    Box(Modifier.size(960.dp, 540.dp)) {
                        SettingsScreenNavigation(
                            currentSection = SettingsSection.PLAYER,
                            onNavigate = { navigations += it },
                        ) { _, requester ->
                            SettingsPlayerContent(
                                initialFocusRequester = requester,
                                ui = ui,
                                onTimeshiftEnabledChanged = { switchCalls++ },
                                onRefreshRateMatchingEnabledChanged = { switchCalls++ },
                                onProfileSelected = {
                                    selections += it
                                    ui = ui.copy(selectedProfileId = it)
                                },
                            )
                        }
                    }
                }
            }
        }

        val categoryNode = composeRule.onNode(hasText(category) and hasClickAction())
        val heading = composeRule.onNode(hasText(player) and !hasClickAction())
        val headingBounds = heading.fetchSemanticsNode().boundsInRoot
        categoryNode.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText(timeshift).assertIsFocused().assertIsDisplayed()
        composeRule.onNodeWithText(profiles.last().name).assertIsNotDisplayed()
        press(Key.DirectionDown)
        composeRule.onNodeWithText(refreshRate).assertIsFocused().assertIsDisplayed()
        press(Key.DirectionDown)
        composeRule.onNodeWithText(serverDefault).assertIsFocused().assertIsNotSelected()

        // Assert every choice, not just a jump/scroll directly to the last profile.
        profiles.forEachIndexed { index, profile ->
            press(Key.DirectionDown)
            val row = composeRule.onNodeWithText(profile.name).assertIsFocused().assertIsDisplayed()
            if (index == 0) row.assertIsSelected() else row.assertIsNotSelected()
        }
        composeRule.runOnIdle {
            assertEquals(emptyList<StreamProfileId?>(), selections)
            assertEquals(0, switchCalls)
        }
        press(Key.DirectionCenter)
        composeRule.onNodeWithText(profiles.last().name).assertIsFocused().assertIsSelected()
        heading.assertIsDisplayed()
        assertEquals(headingBounds, heading.fetchSemanticsNode().boundsInRoot)

        profiles.dropLast(1).asReversed().forEach { profile ->
            press(Key.DirectionUp)
            composeRule.onNodeWithText(profile.name)
                .assertIsFocused().assertIsDisplayed().assertIsNotSelected()
        }
        press(Key.DirectionUp)
        composeRule.onNodeWithText(serverDefault).assertIsFocused().assertIsDisplayed()
        press(Key.DirectionUp)
        composeRule.onNodeWithText(refreshRate).assertIsFocused().assertIsDisplayed()
        press(Key.DirectionUp)
        composeRule.onNodeWithText(timeshift).assertIsFocused().assertIsDisplayed()

        // Left must also leave the scrolled bottom through the existing category owner.
        repeat(profiles.size + 2) { press(Key.DirectionDown) }
        composeRule.onNodeWithText(profiles.last().name)
            .assertIsFocused().assertIsDisplayed().assertIsSelected()
        press(Key.DirectionLeft)
        categoryNode.assertIsFocused()
        press(Key.DirectionRight)
        composeRule.onNodeWithText(timeshift).assertIsFocused().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(profiles.last().id), selections)
            assertEquals(0, switchCalls)
            assertEquals(emptyList<SettingsSection>(), navigations)
        }
    }

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
