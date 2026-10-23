package at.bernhardberger.tvhplayer.ui.startup

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.MainStartupLoadingTiming
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.mainStartupLoadingFeedback
import at.bernhardberger.tvhplayer.core.toAppError
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.MainStartupComposition
import at.bernhardberger.tvhplayer.ui.MainStartupCompositionState
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Offline production-rendering evidence: 960×540dp/pixels, EN1.0/DE1.3, no backend. */
class StartupBrandCaptureTest {
    @get:Rule val rule = createComposeRule()

    @Test fun captureLoadingAndRecoveryInEnglishAndGerman() {
        val locale = mutableStateOf(Locale.US)
        val scale = mutableStateOf(1f)
        val elapsedMillis = mutableLongStateOf(2_000L)
        val motionEnabled = mutableStateOf(true)
        val brandingVisible = mutableStateOf(true)
        val captureReveal = mutableStateOf(false)
        val revealRequestId = mutableStateOf<Long?>(null)
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
            LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)
            CompositionLocalProvider(
                LocalContext provides localContext,
                LocalResources provides localContext.resources,
                LocalConfiguration provides localized,
                LocalDensity provides Density(1f, scale.value),
            ) {
                TVHeadendPlayerTheme {
                    Box(Modifier.size(960.dp, 540.dp).testTag("startup-capture-viewport")) {
                        if (captureReveal.value) {
                            MainStartupComposition(
                                state = MainStartupCompositionState(
                                    presentation = if (revealRequestId.value == null) {
                                        MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION)
                                    } else MainStartupPresentation.Inactive,
                                    navigationStartDestination = null,
                                    navigationAllowed = false,
                                    contentAllowed = false,
                                    revealRequestId = revealRequestId.value,
                                ),
                                onBack = {}, onAction = {}, registerActivityKeyContract = { {} },
                                // Each reveal entry starts its own loading timing.
                                loadingTiming = remember { MainStartupLoadingTiming().apply {
                                    observe(true, null, SystemClock.uptimeMillis() - elapsedMillis.longValue)
                                } },
                                motionEnabled = true,
                                persistentSurface = {
                                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(
                                        listOf(Color(0xFF35506D), Color(0xFF63816F)),
                                    )))
                                },
                            )
                        } else {
                            MainStartupScreen(
                                presentation.value, TvFullScreenPadding,
                                loadingFeedback = mainStartupLoadingFeedback(presentation.value, elapsedMillis.longValue),
                                motionEnabled = motionEnabled.value,
                                brandingVisible = brandingVisible.value,
                            )
                        }
                    }
                }
            }
        }
        listOf(Locale.US to 1f, Locale.GERMANY to 1.3f).forEach { (language, fontScale) ->
            rule.mainClock.autoAdvance = false
            rule.runOnUiThread {
                locale.value = language
                scale.value = fontScale
                elapsedMillis.longValue = 2_000L
                motionEnabled.value = true
                presentation.value = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
            }
            rule.waitUntil(5_000) {
                rule.mainClock.advanceTimeByFrame()
                rule.onAllNodesWithTag("startup-background-prepared").fetchSemanticsNodes().isNotEmpty()
            }
            // The decoded plate fades in to its resting glow, then breathes.
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithText("Tvheadend Player").assertIsDisplayed()
            capture("${language.language}-loading")
            capture("${language.language}-cached")
            assertBackground()
            val loadingMarkBounds = rule.onNodeWithTag("main-startup-mark").fetchSemanticsNode().boundsInRoot
            rule.runOnUiThread {
                presentation.value = MainStartupPresentation.Passive(
                    MainStartupMessageKind.SYNCING_METADATA,
                )
            }
            rule.mainClock.advanceTimeBy(512)
            capture("${language.language}-sync")
            listOf("hidden399" to 399L, "waiting400" to 400L, "waiting2000" to 2_000L).forEach { (name, elapsed) ->
                rule.runOnUiThread {
                    elapsedMillis.longValue = elapsed
                }
                rule.mainClock.advanceTimeBy(512)
                org.junit.Assert.assertEquals(
                    loadingMarkBounds,
                    rule.onNodeWithTag("main-startup-mark").fetchSemanticsNode().boundsInRoot,
                )
                capture("${language.language}-$name")
            }
            rule.runOnUiThread {
                presentation.value = MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION)
            }
            rule.mainClock.advanceTimeBy(512)
            capture("${language.language}-tuning")
            rule.runOnUiThread {
                brandingVisible.value = false
                presentation.value = MainStartupPresentation.Passive(MainStartupMessageKind.RESUMING_PLAYBACK)
            }
            rule.mainClock.advanceTimeBy(512)
            val returnRing = rule.onNodeWithTag("main-startup-loader").fetchSemanticsNode().boundsInRoot
            assertEquals(480f, returnRing.center.x, 1f)
            assertEquals(270f, returnRing.center.y, 1f)
            assertEquals(44f, returnRing.width, 1f)
            rule.onNodeWithTag("main-startup-mark").assertDoesNotExist()
            assertBackground()
            capture("${language.language}-return")
            rule.runOnUiThread {
                brandingVisible.value = true
                presentation.value = MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION)
            }
            listOf("waiting400" to 400L, "waiting2000" to 2_000L).forEach { (name, elapsed) ->
                rule.runOnUiThread {
                    elapsedMillis.longValue = elapsed
                    motionEnabled.value = false
                }
                rule.mainClock.advanceTimeBy(512)
                capture("${language.language}-reduced-motion-$name")
                assertBackground(enabled = false)
            }
            rule.runOnUiThread {
                captureReveal.value = true
                revealRequestId.value = null
            }
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeByFrame()
            rule.runOnUiThread { revealRequestId.value = 1L }
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeBy(100L, ignoreFrameDuration = true)
            capture("${language.language}-reveal-midfade")
            rule.runOnUiThread {
                captureReveal.value = false
                revealRequestId.value = null
                brandingVisible.value = true
                elapsedMillis.longValue = 399L
                motionEnabled.value = true
            }
            rule.mainClock.advanceTimeByFrame()
            capture("${language.language}-cold-pending399")
            // The logo is already settled on the first frame, before any status text.
            assertSettledMark()
            rule.onNodeWithTag("main-startup-status").assertDoesNotExist()
            rule.runOnUiThread {
                elapsedMillis.longValue = 0L
                captureReveal.value = true
            }
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag("main-startup-status").assertDoesNotExist()
            assertSettledMark()
            rule.runOnUiThread { revealRequestId.value = 2L }
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeBy(100L, ignoreFrameDuration = true)
            capture("${language.language}-reveal-hidden-midfade")
            rule.onNodeWithTag("main-startup-status").assertDoesNotExist()
            rule.onNodeWithTag("main-startup-mark").assertExists()
            rule.runOnUiThread {
                captureReveal.value = false
                revealRequestId.value = null
                brandingVisible.value = true
                elapsedMillis.longValue = 2_000L
                motionEnabled.value = true
                presentation.value = MainStartupPresentation.Actionable(
                    AppErrorCondition.UNREACHABLE.toAppError(),
                )
            }
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithTag("tv-recovery-primary").assertIsFocused()
            // Focus can arrive during Android layout after the paused Compose clock advances.
            // Settle the native TV focus treatment only after focus ownership is confirmed.
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithText("Action needed").assertDoesNotExist()
            rule.onNodeWithText("Aktion erforderlich").assertDoesNotExist()
            capture("${language.language}-recovery")
            assertBackground(enabled = false)
            rule.runOnUiThread {
                presentation.value = MainStartupPresentation.Actionable(
                    AppErrorCondition.AUTHENTICATION_REJECTED.toAppError(),
                )
            }
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithTag("tv-recovery-primary").assertIsFocused()
            rule.mainClock.advanceTimeBy(512)
            capture("${language.language}-recovery-auth")

            // Also capture fresh Settings-only entry, independently of action-row replacement.
            rule.runOnUiThread {
                presentation.value = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
            }
            rule.mainClock.advanceTimeByFrame()
            rule.runOnUiThread {
                presentation.value = MainStartupPresentation.Actionable(
                    AppErrorCondition.AUTHENTICATION_REJECTED.toAppError(),
                )
            }
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithTag("tv-recovery-primary").assertIsFocused()
            rule.mainClock.advanceTimeBy(512)
            capture("${language.language}-recovery-auth-direct")
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "brand-startup-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(rule.onNodeWithTag("startup-capture-viewport").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun assertBackground(enabled: Boolean = true) {
        val captured = rule.onNodeWithTag("startup-capture-viewport").captureToImage().asAndroidBitmap()
        // Sample the original cyan sweep, clear of the centered logo and all status content.
        val actual = captured.getPixel(200, 100)
        if (!enabled) {
            assertEquals(0xFF0F1014.toInt(), actual)
        } else {
            val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
            val plateImage = BitmapFactory.decodeResource(resources, R.drawable.startup_background_plate,
                BitmapFactory.Options().apply { inScaled = false },
            )!!
            val left = kotlin.math.round((960f - 1075f) / 2f)
            val top = kotlin.math.round((540f - 605f) / 2f)
            val plate = plateImage.getPixel(
                ((200f - left) * 960f / 1075f).toInt(),
                ((100f - top) * 540f / 605f).toInt(),
            )
            plateImage.recycle()
            // The centered plate breathes between its 20% resting glow and 30%.
            listOf(16 to 15, 8 to 16, 0 to 20).forEach { (shift, field) ->
                val range = ((plate shr shift) and 255) - field
                val observed = (actual shr shift) and 255
                assertTrue("Original plate channel: $observed vs $field + $range × 20–30%",
                    observed >= field + range * StartupBrandBackgroundRestingAlpha - 4f &&
                        observed <= field + range * StartupBrandBackgroundRestingAlpha * 1.5f + 4f)
            }
        }
    }

    private fun assertSettledMark() {
        val mark = rule.onNodeWithTag("main-startup-mark").captureToImage().toPixelMap()
        listOf(Color(0xFF00BCFA), Color(0xFFFA7F00)).forEach { color ->
            var matches = 0
            for (y in 0 until mark.height) for (x in 0 until mark.width) {
                val pixel = mark[x, y]
                if (kotlin.math.abs(pixel.red - color.red) <= 2f / 255f &&
                    kotlin.math.abs(pixel.green - color.green) <= 2f / 255f &&
                    kotlin.math.abs(pixel.blue - color.blue) <= 2f / 255f
                ) matches++
            }
            assertTrue("The settled logo is opaque: $color", matches > mark.width * mark.height / 40)
        }
    }
}
