package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
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

    private val session = FakeTvheadendSession()
    private var restores = 0

    @Composable
    private fun Editor.Screen(form: ConnectionFormState, requester: FocusRequester, left: (KeyEvent) -> Boolean,
        onSaved: () -> Unit = {}) =
        SettingsConnection(requester, this, session, { restores++ }, form, left, onSaved)

    @Test fun overviewDefersSecureEditorAndBackUnwindsStepsBeforeReturningToEditAction() {
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
                    activeContent = { requester, left -> editor.Screen(form, requester, left) },
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
        compose.onNodeWithTag(SUMMARY_ADDRESS).assertIsFocused() // entry release must not open the address
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        press(Key.DirectionCenter)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.runOnIdle { form.updateHost("draft.invalid") }
        press(Key.DirectionLeft)
        assertTrue(navigation.stack.canPop)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        press(Key.Back)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        press(Key.Back)
        compose.onNodeWithTag(SUMMARY_ADDRESS).assertIsFocused()
        assertEquals("draft.invalid", form.host)
        assertTrue(navigation.stack.canPop)
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
        assertEquals(emptyList<FakeSessionCall>(), session.calls)
        press(Key.Back)
        compose.onNodeWithText("Connection").assertIsFocused()
    }

    @Test fun leftLeavesOnlyFromTheSummaryAndTheBoundaryPopOwnsRepeatsAcrossDisposal() {
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
                        activeContent = { requester, left -> editor.Screen(form, requester, left) },
                    )), initialFocusEnabled = !drawerActive, contentPadding = padding)
                }
            }
        }
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-port").assertIsFocused()
        press(Key.DirectionLeft) // A step keeps Left, so it cannot drop what was entered.
        compose.onNodeWithTag("setup-port").assertIsFocused()
        assertTrue(navigation.stack.canPop)
        press(Key.Back)
        compose.onNodeWithTag("setup-summary-port").assertIsFocused()
        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithText("Clear saved password").assertIsFocused()
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

    @Test fun aFailedTestOffersSaveAnywayAndBackReturnsToTestAndSave() {
        val editor = Editor()
        val form = ConnectionFormState()
        compose.setContent {
            TVHeadendPlayerTheme {
                val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                SettingsScreenNavigation(navigation, listOf(settingsRootLevel(),
                    settingsConnectionOverview("Connected", "tvheadend.local", 9982),
                    settingsLevel(CONNECTION_EDITOR, "Connection", listOf(settingsRow("safe", "Edit connection")),
                        activeContent = { requester, left -> editor.Screen(form, requester, left) })))
            }
        }
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        testAndSave()
        compose.runOnIdle { session.publish(unavailable()) }
        compose.onNodeWithTag("setup-action-try_again").assertIsFocused()
        compose.onNodeWithText("Save anyway").assertIsDisplayed()
        compose.onNodeWithText("Your current connection stays in use.", substring = true).assertIsDisplayed()
        assertEquals(1, restores)
        press(Key.Back)
        compose.onNodeWithTag(TEST_AND_SAVE).assertIsFocused()
        assertEquals(0, editor.saves)
        press(Key.Back)
        compose.onNodeWithText("Edit connection").assertIsFocused()
        assertFalse(secure())
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

    @Test fun aPassedTestSavesAndReturnsToEditActionButAFailedSaveKeepsTheEditor() {
        val editor = Editor().apply { failSave = true }
        val form = ConnectionFormState()
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                SettingsScreenNavigation(navigation, listOf(settingsRootLevel(),
                    settingsConnectionOverview("Connected", "tvheadend.local", 9982),
                    settingsLevel(CONNECTION_EDITOR, "Connection", listOf(settingsRow("safe", "Edit connection")),
                        activeContent = { requester, left -> editor.Screen(form, requester, left, navigation::pop) })))
            }
        }
        press(Key.DirectionCenter)
        assertEquals(0, editor.loads)
        compose.onNodeWithText("Edit connection").assertIsFocused()
        val parent = navigation.stack.active
        press(Key.DirectionCenter)
        testAndSave()
        compose.runOnIdle { session.publish(ready()) }
        compose.waitForIdle()
        assertEquals(CONNECTION_EDITOR, navigation.stack.active.levelId)
        assertEquals(ConnectionFormFeedback.SAVE_FAILED, form.feedback)
        compose.onNodeWithTag(TEST_AND_SAVE).assertIsFocused()
        assertEquals("An unsaved test gives the current connection back", 1, restores)
        editor.failSave = false
        press(Key.DirectionCenter)
        // A fresh attempt shows it started before its own Ready counts.
        compose.runOnIdle { session.publish(SessionObservation.create(sessionState = SessionState.Connecting)) }
        compose.runOnIdle { session.publish(ready()) }
        compose.onNodeWithText("Edit connection").assertIsFocused()
        assertEquals(2, editor.saves)
        assertEquals(1, restores)
        assertEquals(parent, navigation.stack.active)
        assertFalse(secure())
        assertEquals("", form.password)
    }

    @Test fun aTypedAddressSurvivesRecreationAndOnlyTheSignInLoadsAgain() {
        val editor = Editor()
        val restoration = StateRestorationTester(compose)
        lateinit var form: ConnectionFormState
        restoration.setContent {
            TVHeadendPlayerTheme {
                form = rememberConnectionFormState()
                SettingsConnection(remember { FocusRequester() }, editor, session, { restores++ }, form)
            }
        }
        compose.waitForIdle()
        assertEquals(1, editor.loads)
        compose.runOnIdle {
            form.updateHost("typed.invalid")
            form.updatePort("9983")
        }

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("The sign-in the saved state leaves out loads again", 2, editor.loads)
        assertEquals("typed.invalid", form.host)
        assertEquals("9983", form.port)
        assertEquals("fixture", form.username)
        assertEquals("test-only password", form.password)
    }

    /** Down from the address row to "Test and save" (past "Clear saved password") and starts the test. */
    private fun testAndSave() {
        compose.onNodeWithTag(SUMMARY_ADDRESS).assertIsFocused()
        repeat(5) { press(Key.DirectionDown) }
        compose.onNodeWithTag(TEST_AND_SAVE).assertIsFocused()
        press(Key.DirectionCenter)
    }

    private fun ready() = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
        ),
    )

    private fun unavailable() =
        SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE))
}

private const val SUMMARY_ADDRESS = "setup-summary-address"
private const val TEST_AND_SAVE = "setup-test-and-save"
