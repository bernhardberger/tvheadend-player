package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.settings.hasUnseenWhatsNew
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import at.bernhardberger.tvhplayer.ui.screens.SETTINGS_ROOT
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settingsRootLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsAboutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    private var sessionState by mutableStateOf<SessionState>(SessionState.Disconnected)

    private fun show(fontScale: Float = 1f, machineTranslated: Boolean? = false) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        SettingsScreenNavigation(rememberDepthNavigationState(SETTINGS_ROOT, ABOUT_LEVEL),
                            listOf(settingsRootLevel()) + (
                                if (machineTranslated == null) settingsAboutLevels(sessionState)
                                else settingsAboutLevels(sessionState, machineTranslated = machineTranslated)))
                    }
                }
            }
        }
    }

    @Test fun serverMetadataDisappearsWhenTheSessionIsNoLongerReady() {
        sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.DENIED,
            serverName = "Demonstration", serverVersion = "4.3"))
        show()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Demonstration • 4.3", substring = true).assertIsDisplayed()
        compose.runOnIdle { sessionState = SessionState.Connecting }
        compose.onAllNodesWithText("Demonstration • 4.3", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Not connected", substring = true).assertIsDisplayed()
    }

    @Test fun aboutAndLicenseRestoreFocusAndHideEmptyCredits() {
        show()
        compose.onNodeWithText("About").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Version", substring = true).assertIsFocused()
        compose.onAllNodesWithText("TRANSLATION").assertCountEquals(0)
        compose.onAllNodesWithText("Machine-translated").assertCountEquals(0)
        press(Key.DirectionDown)
        compose.onNodeWithText("What's new").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Not connected", substring = true).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("GNU General Public License v3").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        val scroll = compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        val before = scroll.value()
        repeat(5) { press(Key.DirectionDown) }
        compose.runOnIdle { org.junit.Assert.assertTrue(scroll.value() > before) }
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        fun backKey(down: Boolean, repeat: Int = 0, flags: Int = 0) {
            compose.onNodeWithTag("player-info-reading").performKeyPress(androidx.compose.ui.input.key.KeyEvent(
                android.view.KeyEvent(0, 0,
                    if (down) android.view.KeyEvent.ACTION_DOWN else android.view.KeyEvent.ACTION_UP,
                    android.view.KeyEvent.KEYCODE_BACK, repeat, 0, 0, 0, flags)))
        }
        backKey(false)
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        backKey(true)
        backKey(false, flags = android.view.KeyEvent.FLAG_CANCELED)
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        backKey(true)
        backKey(true, repeat = 1, flags = android.view.KeyEvent.FLAG_LONG_PRESS)
        backKey(true, repeat = 2)
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        backKey(false)
        compose.onNodeWithText("GNU General Public License v3").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Notices and third-party libraries", substring = true).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        val noticesScroll = compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        press(Key.DirectionDown)
        compose.runOnIdle { org.junit.Assert.assertTrue(noticesScroll.value() > 0f) }
        press(Key.Back)
        compose.onNodeWithText("Notices and third-party libraries", substring = true).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Privacy policy").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        // The reader shows the shipped policy as plain text, without Markdown markers.
        compose.onNodeWithText("Data stored on the TV", substring = true).assertExists()
        compose.onAllNodesWithText("## ", substring = true).assertCountEquals(0)
        press(Key.Back)
        compose.onNodeWithText("Privacy policy").assertIsFocused()
        press(Key.Back)
        compose.onNodeWithText("About").assertIsFocused()
    }

    @Test fun machineTranslatedLanguageShowsItsNoticeUnderTranslation() {
        show(machineTranslated = true)
        press(Key.DirectionCenter)
        repeat(6) { press(Key.DirectionDown) }
        compose.onNodeWithText("Machine-translated", substring = true).assertIsFocused()
        compose.onNodeWithText("TRANSLATION").assertIsDisplayed()
        compose.onNodeWithText("Nobody has checked this translation yet", substring = true).assertIsDisplayed()
    }

    // The notice follows the language's own resource flag (null passes none): machine-drafted Japanese
    // shows it under Translation, reviewed German does not.
    @Test @Config(qualifiers = "ja-w960dp-h540dp-land-mdpi")
    fun machineDraftedJapaneseShowsTheNoticeFromItsResources() {
        show(machineTranslated = null)
        press(Key.DirectionCenter)
        val title = RuntimeEnvironment.getApplication().resources.getString(at.bernhardberger.tvhplayer.R.string.about_machine_translated)
        org.junit.Assert.assertNotEquals("Machine-translated", title)
        repeat(6) { press(Key.DirectionDown) }
        compose.onNodeWithText(title, substring = true).assertIsFocused()
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun reviewedGermanShowsNoMachineNotice() {
        show(machineTranslated = null)
        press(Key.DirectionCenter)
        val title = RuntimeEnvironment.getApplication().resources.getString(at.bernhardberger.tvhplayer.R.string.about_machine_translated)
        compose.onNodeWithText(title, substring = true).assertDoesNotExist()
    }

    @Test @Category(VisualCapture::class)
    fun aboutEnglishCapture() = capture("en-font1.0", 1f)

    @Test @Category(VisualCapture::class)
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun aboutGermanCapture() = capture("de-font1.3", 1.3f)

    @Test @Category(VisualCapture::class)
    @Config(qualifiers = PseudoLocaleEvidence.QUALIFIERS_EN_XA)
    fun aboutEnXaCapture() = capture("en-XA-font1.0", 1f)

    @Test @Category(VisualCapture::class)
    @Config(qualifiers = PseudoLocaleEvidence.QUALIFIERS_AR_XB)
    fun aboutArXbCapture() = capture("ar-XB-font1.0", 1f)

    private var captureScale = 1f

    @Test fun whatsNewMarkersClearTogetherAndConnectionProblemWins() = whatsNewJourney(false)

    @Test @Category(VisualCapture::class)
    fun whatsNewEnglishCaptures() = whatsNewJourney(true)

    private fun whatsNewJourney(captures: Boolean) {
        val store = UiSettingsStore(InMemoryPreferencesDataStore())
        val version = at.bernhardberger.tvhplayer.BuildConfig.VERSION_CODE
        runBlocking { store.initializeWhatsNew(version, 100, 200) }
        var degraded by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    val settings by store.settings.collectAsState(initial = UiSettings())
                    val unseen = hasUnseenWhatsNew(settings.whatsNewSeenVersion, version)
                    val scope = rememberCoroutineScope()
                    val navigation = rememberDepthNavigationState(SETTINGS_ROOT, ABOUT_LEVEL)
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        SideRail(currentRoute = AppDestination.SETTINGS, connectionDegraded = degraded,
                            whatsNewUnseen = unseen, onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                            SettingsScreenNavigation(navigation,
                                listOf(settingsRootLevel(whatsNewUnseen = unseen)) +
                                    settingsAboutLevels(sessionState, machineTranslated = false,
                                        whatsNewUnseen = unseen,
                                        onWhatsNewOpened = { scope.launch { store.markWhatsNewSeen(version) } }),
                                contentPadding = padding, initialFocusEnabled = !drawerActive)
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("settings-row-$ABOUT_LEVEL").assertIsFocused()
            .assertContentDescriptionEquals("About, New")
        compose.onNodeWithTag("whats-new-badge", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").assertContentDescriptionEquals("Settings, New")
        if (captures) save("whats-new-rail-collapsed-en-font1.0")
        compose.runOnIdle { degraded = true }
        compose.onNodeWithTag("whats-new-badge", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("connection-problem-badge", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").assertContentDescriptionEquals("Settings, connection problem")
        compose.runOnIdle { degraded = false }
        press(Key.Back)
        compose.onNodeWithTag("nav-settings").assertIsFocused()
        compose.onNode(hasText("New") and hasAnyAncestor(hasTestTag("nav-settings")), useUnmergedTree = true)
            .assertIsDisplayed()
        if (captures) save("whats-new-rail-expanded-focused-en-font1.0")
        compose.runOnIdle { degraded = true }
        compose.onNode(hasText("New") and hasAnyAncestor(hasTestTag("nav-settings")), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Offline", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { degraded = false }
        press(Key.DirectionUp)
        compose.onNodeWithTag("nav-recordings").assertIsFocused()
        if (captures) save("whats-new-rail-expanded-unfocused-en-font1.0")
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        compose.onNodeWithTag("settings-row-$ABOUT_LEVEL").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-version").assertIsFocused()
        compose.onNodeWithTag("settings-row-whats-new").assertContentDescriptionEquals("What's new, New")
        press(Key.DirectionDown)
        compose.onNodeWithTag("settings-row-whats-new").assertIsFocused()
        if (captures) save("whats-new-about-en-font1.0")
        press(Key.DirectionCenter)
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        org.junit.Assert.assertTrue(compose.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit) and
                hasAnyAncestor(hasTestTag("player-info-reading")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty())
        if (captures) save("whats-new-reader-en-font1.0")
        org.junit.Assert.assertEquals(version, runBlocking { store.settings.first().whatsNewSeenVersion })
        press(Key.Back)
        compose.onNodeWithTag("settings-row-whats-new").assertIsFocused()
        compose.onNodeWithTag("settings-row-whats-new")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        press(Key.Back)
        compose.onNodeWithTag("settings-row-$ABOUT_LEVEL").assertIsFocused()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onNodeWithTag("whats-new-badge", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("nav-settings").assertContentDescriptionEquals("Settings")
        press(Key.Back)
        compose.onNodeWithTag("nav-settings").assertIsFocused()
        compose.onNode(hasText("New") and hasAnyAncestor(hasTestTag("nav-settings")), useUnmergedTree = true).assertDoesNotExist()
    }

    private fun capture(name: String, scale: Float) {
        captureScale = scale
        show(scale)
        save("$name-root")
        press(Key.DirectionCenter)
        save(name)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        save("$name-license")
        press(Key.Back)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        save("$name-notices")
        press(Key.Back)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        save("$name-privacy")
    }

    private fun save(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            // Dialogs own a separate window; capture the top window, not the Settings view beneath it.
            val global = Class.forName("android.view.WindowManagerGlobal")
            val instance = global.getMethod("getInstance").invoke(null)
            val windows = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<*>
            val captureView = windows.lastOrNull() as? View ?: view
            val bitmap = Bitmap.createBitmap(captureView.width, captureView.height, Bitmap.Config.ARGB_8888)
            captureView.draw(Canvas(bitmap))
            val target = File("../build/review/about/$name.png")
            CaptureOutput.writePng(target, bitmap)
            PseudoLocaleEvidence.mirror("about", name, bitmap, captureView, captureScale, "mdpi 1.0",
                "native D-pad focus", "Settings About, licence, notices and privacy policy; production SettingsScreenNavigation")
            bitmap.recycle()
        }
    }

    private fun press(key: Key) {
        val target = if (compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("player-info-reading")
        } else compose.onNode(isFocused())
        target.performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
    }
}
