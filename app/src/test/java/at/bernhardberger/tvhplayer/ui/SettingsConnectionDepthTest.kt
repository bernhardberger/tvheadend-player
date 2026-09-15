package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import at.bernhardberger.tvhplayer.settings.*
import at.bernhardberger.tvhplayer.ui.components.depth.*
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.screens.*
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w960dp-h540dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsConnectionDepthTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun overviewDefersSecureEditorAndUnwindsEditingBeforeReturningToEditAction() {
        val editor = Editor()
        val form = ConnectionFormState()
        lateinit var navigation: DepthNavigationState
        var current by mutableStateOf(true)
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                SettingsScreenNavigation(navigation, listOf(settingsRootLevel(),
                    settingsConnectionOverview("Connected", "tvheadend.local", 9982), settingsLevel(
                    CONNECTION_EDITOR, "Connection",
                    listOf(settingsRow("open", "Open connection settings")),
                    activeContent = { requester, left -> SettingsConnection(requester, editor, form, left) },
                )), isCurrent = current)
            }
        }
        compose.onNodeWithText("Connection").assertIsFocused()
        assertEquals(0, editor.loads)
        assertFalse(secure())
        press(Key.DirectionCenter)
        compose.onNodeWithText("Edit connection").assertIsFocused()
        assertFalse(secure())
        assertEquals(0, editor.loads)
        press(Key.DirectionRight) // Dedicated leaf actions do not enter on Right.
        assertEquals(0, editor.loads)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.waitForIdle()
        assertFalse(secure()) // press alone never enters
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
        assertTrue(secure())
        assertEquals(1, editor.loads)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0) // entry release must not edit Host
        press(Key.DirectionCenter)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.runOnIdle { form.updateHost("draft.invalid") }
        press(Key.DirectionLeft)
        assertTrue(navigation.stack.canPop)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        press(Key.Back)
        assertTrue(navigation.stack.canPop)
        assertEquals("draft.invalid", form.host)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        // A global destination departure also tears down the live editor immediately.
        compose.runOnIdle { current = false }
        compose.waitForIdle()
        assertFalse(secure())
        assertEquals("", form.password)
        compose.runOnIdle { current = true }
        press(Key.Back)
        compose.onNodeWithText("Edit connection").assertIsFocused()
        assertFalse(secure())
        assertEquals("", form.password)
        assertEquals(0, editor.saves)
        press(Key.Back)
        compose.onNodeWithText("Connection").assertIsFocused()
    }

    @Test fun leftNavigatesEditorButtonsThenBoundaryPopOwnsRepeatsAcrossDisposal() {
        val editor = Editor()
        val form = ConnectionFormState()
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                SideRail(currentRoute = AppDestination.SETTINGS, showEpgMenu = true,
                    onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                    SettingsScreenNavigation(navigation, listOf(settingsRootLevel(),
                        settingsConnectionOverview("Connected", "tvheadend.local", 9982), settingsLevel(
                        CONNECTION_EDITOR, "Connection",
                        listOf(settingsRow("open", "Open connection settings")),
                        activeContent = { requester, left -> SettingsConnection(requester, editor, form, left) },
                    )), initialFocusEnabled = !drawerActive, contentPadding = padding)
                }
            }
        }
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        repeat(4) { press(Key.DirectionDown) }
        compose.onNodeWithText("Clear saved password").assertIsFocused()
        val draft = form.password
        press(Key.DirectionLeft)
        compose.onNodeWithText("Save").assertIsFocused().assertIsDisplayed()
        assertTrue(navigation.stack.canPop)
        assertEquals(draft, form.password)
        repeat(4) { press(Key.DirectionUp) }
        compose.onRoot().performKeyInput { keyDown(Key.DirectionLeft) }
        compose.waitForIdle()
        compose.onNodeWithText("Edit connection").assertIsFocused()
        compose.runOnUiThread {
            val now = android.os.SystemClock.uptimeMillis()
            compose.activity.dispatchKeyEvent(android.view.KeyEvent(now, now,
                android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT, 1))
        }
        compose.onRoot().performKeyInput { keyUp(Key.DirectionLeft) }
        compose.onNodeWithText("Edit connection").assertIsFocused()
        compose.onNodeWithTag("nav-settings").assertIsNotFocused()
        assertEquals(2, navigation.stack.frames.size)
        assertFalse(secure())
        assertEquals("", form.password)
        assertEquals(0, editor.saves)
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }
    private fun secure() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private class Editor : ConnectionProfileEditor {
        var loads = 0
        var saves = 0
        var failSave = false
        override val serverSettings = MutableStateFlow(ServerSettings("edit.invalid", 9982, "fixture", true))
        override suspend fun loadServerForEditing(applyAvailable: (String, Int, String, String) -> Unit) {
            loads++
            applyAvailable("edit.invalid", 9982, "fixture", "test-only password")
        }
        override suspend fun saveServer(host: String, htspPort: Int) { saves++ }
        override suspend fun savePasswordServer(host: String, htspPort: Int, username: String, password: String,
            credentialLease: CredentialEditLease) {
            saves++
            credentialLease.release()
            if (failSave) throw java.io.IOException("fixture failure")
        }
        override suspend fun clearProfile() = Unit
    }

    @Test fun successfulSaveReturnsToEditActionButFailureKeepsTheEditor() {
        val editor = Editor().apply { failSave = true }
        val form = ConnectionFormState()
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                SettingsScreenNavigation(navigation, listOf(settingsRootLevel(),
                    settingsConnectionOverview("Connected", "tvheadend.local", 9982),
                    settingsLevel(CONNECTION_EDITOR, "Connection", listOf(settingsRow("safe", "Edit connection")),
                        activeContent = { requester, left -> SettingsConnection(requester, editor, form, left, navigation::pop) })))
            }
        }
        press(Key.DirectionCenter)
        assertEquals(0, editor.loads)
        compose.onNodeWithText("Edit connection").assertIsFocused()
        val parent = navigation.stack.active
        press(Key.DirectionCenter)
        repeat(4) { press(Key.DirectionDown) }
        press(Key.DirectionLeft)
        compose.onNodeWithText("Save").assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitForIdle()
        assertEquals(CONNECTION_EDITOR, navigation.stack.active.levelId)
        assertEquals(ConnectionFormFeedback.SAVE_FAILED, form.feedback)
        editor.failSave = false
        press(Key.DirectionCenter)
        compose.onNodeWithText("Edit connection").assertIsFocused()
        assertEquals(2, editor.saves)
        assertEquals(parent, navigation.stack.active)
        assertFalse(secure())
        assertEquals("", form.password)
    }
}
