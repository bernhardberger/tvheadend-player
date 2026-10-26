package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.depth.*
import at.bernhardberger.tvhplayer.ui.screens.*
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfflinePatternTest {
    @get:Rule val compose = createComposeRule()

    @Test fun profileFailuresKeepTheExistingSettingsRecoveryAction() {
        var retries = 0
        var setupOpen by mutableStateOf(false)
        compose.setContent {
            TVHeadendPlayerTheme {
                val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                SettingsScreenNavigation(navigation, listOf(settingsRootLevel(),
                    settingsConnectionOverview("Saved sign-in unavailable", null, null,
                        degraded = true, onRetry = { retries++ }, recoveryRequiresSettings = true,
                        onSetUp = { setupOpen = true })),
                    setup = if (setupOpen) {
                        {
                            val server = remember { FocusRequester() }
                            LaunchedEffect(Unit) { server.requestFocus() }
                            // Like the setup, Back goes to the back dispatcher, where Settings closes it.
                            val dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!
                                .onBackPressedDispatcher
                            androidx.tv.material3.Button(onClick = {}, modifier = Modifier.focusRequester(server)
                                .onKeyEvent { it.key == Key.Back && it.type == KeyEventType.KeyUp && dispatcher.onBackPressed().let { true } }) {
                                androidx.tv.material3.Text("Server")
                            }
                        }
                    } else null,
                    onCloseSetup = { setupOpen = false })
            }
        }
        press(Key.DirectionCenter)
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("Set up connection").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Server").assertIsFocused()
        assertTrue(setupOpen)
        assertEquals(0, retries)
        press(Key.Back)
        compose.onNodeWithText("Set up connection").assertIsFocused()
    }

    @Test fun retryStaysFocusedButDoesNotDispatchWhileConnecting() {
        var connecting by mutableStateOf(false)
        var retries = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                SettingsScreenNavigation(navigation, listOf(settingsRootLevel(),
                    settingsConnectionOverview("Connection unavailable", null, null,
                        degraded = true, connecting = connecting, onRetry = { retries++ })))
            }
        }
        press(Key.DirectionCenter)
        compose.onNodeWithText("Try again").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, retries)
        compose.runOnIdle { connecting = true }
        compose.onNodeWithText("Try again").assertIsFocused()
        compose.onNodeWithText("Connecting…").assertIsDisplayed()
        press(Key.DirectionCenter)
        assertEquals(1, retries)
        compose.runOnIdle { connecting = false }
        compose.onNodeWithText("Try again").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(2, retries)
    }

    @Test fun retryFocusRecoveryAndBadgeAcrossCollapsedAndExpandedRail() {
        var degraded by mutableStateOf(true)
        var retries = 0
        lateinit var view: View
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SideRail(currentRoute = AppDestination.SETTINGS, showEpgMenu = true,
                            connectionDegraded = degraded, onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                            SettingsScreenNavigation(navigation, listOf(settingsRootLevel(),
                                settingsConnectionOverview(
                                    if (degraded) "Can’t reach the server • Showing saved channels" else "Connected",
                                    null, 9982, degraded, onRetry = { retries++ })),
                                contentPadding = padding, initialFocusEnabled = !drawerActive)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Connection").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Try again").assertIsFocused()
        compose.onNodeWithText("Tvheadend Player also retries on its own").assertIsDisplayed()
        compose.onNodeWithTag("connection-problem-badge", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").assertContentDescriptionEquals("Settings, connection problem")
        compose.onNodeWithText("Offline").assertDoesNotExist()
        assertOtherRailLabels()
        compose.onAllNodesWithTag("connection-problem-badge", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("connection-problem-badge", useUnmergedTree = true)
            .assert(hasAnyAncestor(hasTestTag("nav-settings")))
        assertEquals(160f, compose.onNodeWithTag("global-drawer-surface").fetchSemanticsNode().boundsInRoot.width, 0.01f)
        capture(view, "settings-connection-offline")
        press(Key.DirectionCenter)
        assertEquals(1, retries)
        compose.runOnIdle { degraded = false }
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("Set up connection").assertIsFocused()
        compose.onNodeWithTag("connection-problem-badge", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("nav-settings").assertContentDescriptionEquals("Settings")
        press(Key.Back)
        compose.onNodeWithText("Connection").assertIsFocused()
        press(Key.Back)
        compose.onNodeWithTag("nav-settings").assertIsFocused()
        compose.runOnIdle { degraded = true }
        compose.onNodeWithTag("nav-settings").assertIsFocused().assertContentDescriptionEquals("Settings, connection problem")
        compose.onNodeWithTag("connection-problem-badge", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithText("Offline", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Offline", useUnmergedTree = true).assertIsDisplayed()
            .assert(hasAnyAncestor(hasTestTag("nav-settings")))
        assertOtherRailLabels()
        assertTrue(compose.onNodeWithTag("global-drawer-surface").fetchSemanticsNode().boundsInRoot.width > 160f)
        capture(view, "rail-expanded-offline")
        compose.runOnIdle { degraded = false }
        compose.onNodeWithTag("nav-settings").assertIsFocused().assertContentDescriptionEquals("Settings")
        compose.onNodeWithTag("connection-problem-badge", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Offline").assertDoesNotExist()
    }

    private fun assertOtherRailLabels() {
        compose.onNodeWithTag("nav-channels").assertContentDescriptionEquals("Channels")
        compose.onNodeWithTag("nav-epg").assertContentDescriptionEquals("Guide")
        compose.onNodeWithTag("nav-recordings").assertContentDescriptionEquals("Recordings")
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    private fun capture(view: View, name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            assertEquals(1920, bitmap.width)
            assertEquals(1080, bitmap.height)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/u15-offline").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
