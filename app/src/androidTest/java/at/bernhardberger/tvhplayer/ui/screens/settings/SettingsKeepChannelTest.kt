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
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SETTINGS_ROOT
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settingsRootLevel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@OptIn(ExperimentalTestApi::class)
class SettingsKeepChannelTest(private val language: String) {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun picker_focusesSelection_reachesEveryChoice_andRestoresParent() {
        val configuration = Configuration(composeRule.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            fontScale = 1.5f
        }
        val context = composeRule.activity.createConfigurationContext(configuration)
        val heading = context.getString(R.string.keep_channel_setting)
        fun label(minutes: Int) = if (minutes == 0) context.getString(R.string.keep_channel_off)
            else context.getString(R.string.keep_channel_minutes, minutes)
        var minutes by mutableStateOf(20)
        val selections = mutableListOf<Int>()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, 1.5f),
            ) {
                TVHeadendPlayerTheme {
                    Box(Modifier.size(960.dp, 540.dp)) {
                        val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.PLAYER.name)
                        SettingsScreenNavigation(navigation, listOf(
                            settingsRootLevel(),
                            settingsPlayerLevel(SettingsPlayerUiState(keepChannelMinutes = minutes), {}, {}, {}, {}),
                            settingsKeepChannelLevel(minutes) {
                                selections += it
                                minutes = it
                                navigation.pop()
                            },
                        ))
                    }
                }
            }
        }
        press(Key.DirectionCenter)
        repeat(3) { press(Key.DirectionDown) }
        val parent = composeRule.onNode(hasText(heading) and hasClickAction())
        parent.assertIsFocused()
        press(Key.DirectionCenter)
        composeRule.onNodeWithText(label(20)).assertIsFocused().assertIsSelected().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(emptyList<Int>(), selections) }
        press(Key.DirectionUp)
        composeRule.onNodeWithText(label(10)).assertIsFocused().assertIsDisplayed()
        press(Key.DirectionUp)
        composeRule.onNodeWithText(label(0)).assertIsFocused().assertIsDisplayed()
        repeat(3) { press(Key.DirectionDown) }
        composeRule.onNodeWithText(label(30)).assertIsFocused().assertIsDisplayed()
        press(Key.DirectionCenter)
        parent.assertIsFocused()
        composeRule.runOnIdle { assertEquals(listOf(30), selections) }
        press(Key.DirectionCenter)
        composeRule.onNodeWithText(label(30)).assertIsFocused().assertIsSelected()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        parent.assertIsFocused()
        press(Key.DirectionCenter)
        composeRule.onNodeWithText(label(30)).assertIsFocused()
        press(Key.DirectionLeft)
        parent.assertIsFocused()
        composeRule.runOnIdle { assertEquals(listOf(30), selections) }
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
