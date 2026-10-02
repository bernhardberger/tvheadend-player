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
import androidx.compose.runtime.mutableFloatStateOf
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
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupLoadingTiming
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.mainStartupLoadingFeedback
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
        val brandMillis = mutableFloatStateOf(StartupBrandDurationMillis)
        val elapsedMillis = mutableLongStateOf(2_000L)
        val motionEnabled = mutableStateOf(true)
        val brandingVisible = mutableStateOf(true)
        val revealIntro = mutableStateOf<StartupBrandIntro?>(null)
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
                            CompositionLocalProvider(LocalStartupBrandIntro provides revealIntro.value) {
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
                                 loadingTiming = remember(revealIntro.value) { MainStartupLoadingTiming().apply {
                                     observe(true, null, SystemClock.uptimeMillis() - elapsedMillis.longValue)
                                } },
                                motionEnabled = true,
                                persistentSurface = {
                                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(
                                        listOf(Color(0xFF35506D), Color(0xFF63816F)),
                                    )))
                                },
                            )
                            }
                        } else {
                            MainStartupScreen(
                                presentation.value, TvFullScreenPadding,
                                brandMillis = { brandMillis.floatValue },
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
                // Static entry must show the full held glow as soon as decode completes.
                brandMillis.floatValue = StartupBrandDurationMillis
                elapsedMillis.longValue = 2_000L
                motionEnabled.value = true
                presentation.value = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
            }
            rule.waitUntil(5_000) {
                rule.mainClock.advanceTimeByFrame()
                rule.onAllNodesWithTag("startup-background-prepared").fetchSemanticsNodes().isNotEmpty()
            }
            assertBackgroundAt(StartupBrandDurationMillis)
            rule.mainClock.autoAdvance = false
            rule.runOnUiThread { brandMillis.floatValue = StartupBrandDurationMillis }
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithText("Tvheadend Player").assertIsDisplayed()
            capture("${language.language}-loading")
            capture("${language.language}-cached")
            assertBackgroundAt(StartupBrandDurationMillis)
            val loadingMarkBounds = rule.onNodeWithTag("main-startup-mark").fetchSemanticsNode().boundsInRoot
            listOf(100f, 900f, 1450f, StartupBrandDurationMillis).forEach { millis ->
                rule.runOnUiThread { brandMillis.floatValue = millis }
                rule.mainClock.advanceTimeByFrame()
                capture("${language.language}-motion-${millis.toInt()}ms")
                assertBackgroundAt(millis)
            }
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
                brandMillis.floatValue = 0f
                presentation.value = MainStartupPresentation.Passive(MainStartupMessageKind.RESUMING_PLAYBACK)
            }
            rule.mainClock.advanceTimeBy(512)
            val returnRing = rule.onNodeWithTag("main-startup-loader").fetchSemanticsNode().boundsInRoot
            assertEquals(480f, returnRing.center.x, 1f)
            assertEquals(270f, returnRing.center.y, 1f)
            assertEquals(44f, returnRing.width, 1f)
            rule.onNodeWithTag("main-startup-mark").assertDoesNotExist()
            assertBackgroundAt(StartupBrandDurationMillis)
            capture("${language.language}-return")
            // A return holds the settled glow independently of the assembly clock.
            for (millis in listOf(900f, 60_000f)) {
                rule.runOnUiThread { brandMillis.floatValue = millis }
                rule.mainClock.advanceTimeByFrame()
                assertBackgroundAt(StartupBrandDurationMillis)
            }
            rule.runOnUiThread {
                brandingVisible.value = true
                presentation.value = MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION)
            }
            listOf("waiting400" to 400L, "waiting2000" to 2_000L).forEach { (name, elapsed) ->
                rule.runOnUiThread {
                    elapsedMillis.longValue = elapsed
                    motionEnabled.value = false
                    brandMillis.floatValue = 100f // Reduced motion must still render settled artwork.
                }
                rule.mainClock.advanceTimeBy(512)
                capture("${language.language}-reduced-motion-$name")
                assertBackgroundAt(StartupBrandDurationMillis, enabled = false)
            }
            rule.runOnUiThread {
                revealIntro.value = StartupBrandIntro(true).apply {
                    observe(at.bernhardberger.tvhplayer.core.ConnectionUiState.SyncingChannels)
                    resumed(true); focused(true); entranceReady(); passive(true, gracePassed = true)
                    frame(900f)
                    org.junit.Assert.assertTrue("Partial-assembly capture requires animator_duration_scale > 0", running)
                    assertEquals(900f, millis)
                }
                captureReveal.value = true
                revealRequestId.value = null
            }
            rule.mainClock.advanceTimeByFrame()
            rule.runOnUiThread {
                revealIntro.value!!.observe(at.bernhardberger.tvhplayer.core.ConnectionUiState.Ready)
            }
            rule.mainClock.advanceTimeByFrame()
            rule.runOnUiThread {
                org.junit.Assert.assertTrue(revealIntro.value!!.running)
                assertEquals(900f, revealIntro.value!!.millis)
            }
            rule.runOnUiThread { revealRequestId.value = 1L }
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeBy(100L, ignoreFrameDuration = true)
            rule.runOnUiThread { assertEquals(900f, revealIntro.value!!.outgoingMillis) }
            capture("${language.language}-reveal-midfade")
            rule.runOnUiThread {
                captureReveal.value = false
                revealRequestId.value = null
                brandingVisible.value = true
                brandMillis.floatValue = 0f
                elapsedMillis.longValue = 399L
                motionEnabled.value = true
            }
            rule.mainClock.advanceTimeByFrame()
            capture("${language.language}-cold-pending399")
            assertBackgroundAt(0f)
            rule.onNodeWithTag("main-startup-mark").assertExists()
            rule.onNodeWithTag("main-startup-status").assertDoesNotExist()
            rule.runOnUiThread {
                revealIntro.value = StartupBrandIntro(true).apply {
                    resumed(true); focused(true); entranceReady()
                }
                elapsedMillis.longValue = 0L
                captureReveal.value = true
            }
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag("main-startup-status").assertDoesNotExist()
            rule.onNodeWithTag("main-startup-mark").assertExists()
            val opening = rule.onNodeWithTag("main-startup-mark").captureToImage().toPixelMap()
            rule.runOnUiThread { revealRequestId.value = 2L }
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeBy(100L, ignoreFrameDuration = true)
            capture("${language.language}-reveal-hidden-midfade")
            rule.onNodeWithTag("main-startup-status").assertDoesNotExist()
            rule.onNodeWithTag("main-startup-mark").assertExists()
            val outgoing = rule.onNodeWithTag("main-startup-mark").captureToImage().toPixelMap()
            var newlyFilled = 0
            for (y in 0 until opening.height) for (x in 0 until opening.width) {
                if (opening[x, y].red < 0.15f && outgoing[x, y].red > 0.3f) newlyFilled++
            }
            assertEquals("pending opening art must not snap to its endpoint", 0, newlyFilled)
            rule.runOnUiThread {
                captureReveal.value = false
                revealRequestId.value = null
                brandingVisible.value = true
                elapsedMillis.longValue = 2_000L
                motionEnabled.value = true
                brandMillis.floatValue = StartupBrandDurationMillis
                presentation.value = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.RETRYABLE_FAILURE,
                    listOf(MainStartupActionId.RETRY, MainStartupActionId.CONNECTION_SETTINGS),
                )
            }
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithTag("main-startup-action-RETRY").assertIsFocused()
            // Focus can arrive during Android layout after the paused Compose clock advances.
            // Settle the native TV focus treatment only after focus ownership is confirmed.
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithText("Action needed").assertDoesNotExist()
            rule.onNodeWithText("Aktion erforderlich").assertDoesNotExist()
            capture("${language.language}-recovery")
            assertBackgroundAt(StartupBrandDurationMillis, enabled = false)
            rule.runOnUiThread {
                presentation.value = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.AUTHENTICATION_FAILURE,
                    listOf(MainStartupActionId.CONNECTION_SETTINGS),
                )
            }
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithTag("main-startup-action-CONNECTION_SETTINGS").assertIsFocused()
            rule.mainClock.advanceTimeBy(512)
            capture("${language.language}-recovery-auth")

            // Also capture fresh Settings-only entry, independently of action-row replacement.
            rule.runOnUiThread {
                presentation.value = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
            }
            rule.mainClock.advanceTimeByFrame()
            rule.runOnUiThread {
                presentation.value = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.AUTHENTICATION_FAILURE,
                    listOf(MainStartupActionId.CONNECTION_SETTINGS),
                )
            }
            rule.mainClock.advanceTimeBy(512)
            rule.onNodeWithTag("main-startup-action-CONNECTION_SETTINGS").assertIsFocused()
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

    private fun assertBackgroundAt(millis: Float, enabled: Boolean = true) {
        val captured = rule.onNodeWithTag("startup-capture-viewport").captureToImage().asAndroidBitmap()
        // Sample the original cyan sweep, clear of the centered logo and all status content.
        val actual = captured.getPixel(200, 100)
        val frame = startupBrandBackgroundFrame(millis, enabled)
        if (frame == null || frame.alpha == 0f) {
            assertEquals(0xFF0F1014.toInt(), actual)
        } else {
            val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
            val plateImage = BitmapFactory.decodeResource(resources, R.drawable.startup_background_plate,
                BitmapFactory.Options().apply { inScaled = false },
            )!!
            val drift = (frame.drift - 0.5f) * 0.04f * 960f
            val left = kotlin.math.round((960f - 1075f) / 2f + drift)
            val top = kotlin.math.round((540f - 605f) / 2f + drift)
            val plate = plateImage.getPixel(
                ((200f - left) * 960f / 1075f).toInt(),
                ((100f - top) * 540f / 605f).toInt(),
            )
            plateImage.recycle()
            listOf(16 to 15, 8 to 16, 0 to 20).forEach { (shift, field) ->
                val expected = field + (((plate shr shift) and 255) - field) * frame.alpha
                val observed = (actual shr shift) and 255
                assertTrue("Original plate channel at ${millis}ms: $observed vs $expected",
                    kotlin.math.abs(observed - expected) <= 4f)
            }
        }
    }
}
