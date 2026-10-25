package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
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

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        SettingsScreenNavigation(rememberDepthNavigationState(SETTINGS_ROOT, ABOUT_LEVEL),
                            listOf(settingsRootLevel(false)) + settingsAboutLevels(sessionState))
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
        compose.onNodeWithText("Demonstration · 4.3", substring = true).assertIsDisplayed()
        compose.runOnIdle { sessionState = SessionState.Connecting }
        compose.onAllNodesWithText("Demonstration · 4.3", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Not connected", substring = true).assertIsDisplayed()
    }

    @Test fun aboutAndLicenseRestoreFocusAndHideEmptyCredits() {
        show()
        compose.onNodeWithText("About").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Version", substring = true).assertIsFocused()
        compose.onAllNodesWithText("Translator credits").assertCountEquals(0)
        press(Key.DirectionDown)
        compose.onNodeWithText("Not connected", substring = true).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("GNU General Public License v3")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        press(Key.DirectionCenter)
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        val scroll = compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        val before = scroll.value()
        repeat(5) { press(Key.DirectionDown) }
        compose.runOnIdle { org.junit.Assert.assertTrue(scroll.value() > before) }
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        press(Key.Back)
        compose.onNodeWithText("GNU General Public License v3").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Notices and third-party libraries", substring = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        press(Key.DirectionCenter)
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        val noticesScroll = compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        press(Key.DirectionDown)
        compose.runOnIdle { org.junit.Assert.assertTrue(noticesScroll.value() > 0f) }
        press(Key.Back)
        compose.onNodeWithText("Notices and third-party libraries", substring = true).assertIsFocused()
        press(Key.Back)
        compose.onNodeWithText("About").assertIsFocused()
    }

    @Test @Category(VisualCapture::class)
    fun aboutEnglishCapture() = capture("en-font1.0", 1f)

    @Test @Category(VisualCapture::class)
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun aboutGermanCapture() = capture("de-font1.3", 1.3f)

    private fun capture(name: String, scale: Float) {
        show(scale)
        save("$name-root")
        press(Key.DirectionCenter)
        save(name)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        save("$name-license")
        press(Key.Back)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        save("$name-notices")
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
            target.parentFile.mkdirs()
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
