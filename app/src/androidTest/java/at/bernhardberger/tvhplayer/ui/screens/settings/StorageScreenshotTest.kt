package at.bernhardberger.tvhplayer.ui.screens.settings

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.viewmodels.CacheClearState
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Offline production General pane and Settings navigation, without a server or app DI. */
@RunWith(Parameterized::class)
@OptIn(ExperimentalTestApi::class)
class StorageScreenshotTest(private val language: String, private val scale: Float, private val state: CacheClearState) {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun storageFocusBackAndCapture() {
        val configuration = Configuration(composeRule.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            fontScale = scale
        }
        val localizedContext = composeRule.activity.createConfigurationContext(configuration)
        val general = localizedContext.getString(R.string.settings_general)
        val languageRow = localizedContext.getString(R.string.language_follow_system)
        val clear = localizedContext.getString(R.string.clear_cache)
        var clearCalls = 0
        var shellBackCalls = 0
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, scale),
            ) {
                TVHeadendPlayerTheme {
                    BackHandler { shellBackCalls++ }
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SettingsScreenNavigation(currentSection = SettingsSection.GENERAL, onNavigate = {}) { _, requester ->
                            SettingsGeneralContent(
                                initialFocusRequester = requester,
                                settings = UiSettings(),
                                selectedLanguage = AppLanguage.SYSTEM,
                                onSelectLanguage = {},
                                onToggleEpg = {},
                                statistics = if (state == CacheClearState.CLEARED) CacheStatistics.EMPTY else CacheStatistics(500_000, 12_000_000, 340),
                                clearState = state,
                                onClearCache = { clearCalls++ },
                            )
                        }
                    }
                }
            }
        }
        composeRule.onNode(hasText(general) and hasClickAction()).assertIsFocused().performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithText(languageRow).assertIsFocused()
        composeRule.onRoot().performKeyInput { repeat(4) { pressKey(Key.DirectionDown) } }
        composeRule.onNodeWithText(clear).assertIsFocused().assertIsDisplayed()
        composeRule.onNodeWithText(clear).performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(if (state == CacheClearState.CLEARING) 0 else 1, clearCalls) }
        composeRule.onNodeWithText(clear).assertIsFocused()
        composeRule.mainClock.advanceTimeBy(500)
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(1920, bitmap.width)
        assertEquals(1080, bitmap.height)
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "storage-captures")
        assertTrue(directory.isDirectory || directory.mkdirs())
        File(directory, "$language-$scale-${state.name.lowercase()}.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        composeRule.onRoot().performKeyInput { repeat(4) { pressKey(Key.DirectionUp) } }
        composeRule.onNodeWithText(languageRow).assertIsFocused().assertIsDisplayed()
        composeRule.onRoot().performKeyInput { repeat(4) { pressKey(Key.DirectionDown) } }
        composeRule.onNodeWithText(clear).assertIsFocused().assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNode(hasText(general) and hasClickAction()).assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, shellBackCalls) }
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.runOnIdle { assertEquals(1, shellBackCalls) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}-{1}-{2}")
        fun scenarios(): List<Array<Any>> = listOf("en", "de").flatMap { language ->
            listOf(1f, 1.3f).flatMap { scale ->
                CacheClearState.entries.map { state -> arrayOf(language, scale, state) }
            }
        }
    }
}
