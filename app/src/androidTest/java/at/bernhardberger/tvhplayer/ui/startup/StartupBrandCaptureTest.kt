package at.bernhardberger.tvhplayer.ui.startup

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Offline production-composable evidence; no backend, startup route or timing is exercised. */
class StartupBrandCaptureTest {
    @get:Rule val rule = createComposeRule()

    @Test fun captureLoadingAndRecoveryInEnglishAndGerman() {
        val locale = mutableStateOf(Locale.US)
        val scale = mutableStateOf(1f)
        val presentation = mutableStateOf<MainStartupPresentation>(
            MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING),
        )
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val localized = remember(configuration, locale.value) {
                Configuration(configuration).apply { setLocale(locale.value) }
            }
            val localContext = remember(context, localized) { context.createConfigurationContext(localized) }
            val density = LocalDensity.current
            LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)
            CompositionLocalProvider(
                LocalContext provides localContext,
                LocalResources provides localContext.resources,
                LocalConfiguration provides localized,
                LocalDensity provides Density(density.density, scale.value),
            ) {
                TVHeadendPlayerTheme {
                    MainStartupScreen(presentation.value, TvFullScreenPadding)
                }
            }
        }
        listOf(Locale.US to 1f, Locale.GERMANY to 1.3f).forEach { (language, fontScale) ->
            rule.runOnUiThread {
                locale.value = language
                scale.value = fontScale
                presentation.value = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
            }
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithText("Tvheadend Player").assertIsDisplayed()
            capture("${language.language}-loading")
            rule.runOnUiThread {
                presentation.value = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.RETRYABLE_FAILURE,
                    listOf(MainStartupActionId.RETRY, MainStartupActionId.CONNECTION_SETTINGS),
                )
            }
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithTag("main-startup-action-RETRY").assertIsFocused()
            rule.onNodeWithText("Action needed").assertDoesNotExist()
            rule.onNodeWithText("Aktion erforderlich").assertDoesNotExist()
            capture("${language.language}-recovery")
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "brand-startup-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
