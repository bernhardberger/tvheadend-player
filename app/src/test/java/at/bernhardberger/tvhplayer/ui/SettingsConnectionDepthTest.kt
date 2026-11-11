package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.testutil.holdBack

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
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
import at.bernhardberger.tvhplayer.testutil.leaveTouchModeBeforeComposing
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
    private var setupOpen by mutableStateOf(false)
    private var degraded by mutableStateOf(false)

    /** Settings with the Connection list and its server's level, whose Set up row opens the setup over it. */
    @Composable
    private fun SettingsWithSetup(
        navigation: DepthNavigationState,
        setup: @Composable (onSaved: () -> Unit) -> Unit,
        contentPadding: androidx.compose.foundation.layout.PaddingValues = TvFullScreenPadding,
        initialFocusEnabled: Boolean = true,
        isCurrent: Boolean = true,
        onCancelSwitch: (() -> Unit)? = null,
    ) = SettingsScreenNavigation(navigation, listOf(settingsRootLevel()) +
        fixtureConnectionLevels(fixtureStatus("Connected", ConnectionOverviewState.CONNECTED), showRetry = degraded,
            actions = ServerProfileActions(onSetUp = { setupOpen = true })),
        initialFocusEnabled = initialFocusEnabled, contentPadding = contentPadding, isCurrent = isCurrent,
        setup = if (setupOpen) { { setup { setupOpen = false } } } else null,
        onCloseSetup = { setupOpen = false }, onCancelSwitch = onCancelSwitch)

    @Test fun setupBackTakesPrecedenceOverPendingSwitchCancellation() {
        var cancellations = 0
        val editor = Editor()
        setupOpen = true
        compose.leaveTouchModeBeforeComposing()
        compose.setContent {
            TVHeadendPlayerTheme {
                SettingsSetupFrame {
                    SettingsWithSetup(rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name),
                        { saved -> editor.Screen(ConnectionFormState(), saved) }, onCancelSwitch = { cancellations++ })
                }
            }
        }
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        press(Key.Back)
        assertFalse(setupOpen)
        assertEquals(0, cancellations)
        press(Key.Back)
        assertEquals(1, cancellations)
    }

    @Test fun dialogBackTakesPrecedenceOverPendingSwitchCancellation() {
        var cancellations = 0
        var question by mutableStateOf(true)
        compose.leaveTouchModeBeforeComposing()
        compose.setContent {
            TVHeadendPlayerTheme {
                SettingsSetupFrame {
                    SettingsScreenNavigation(
                        rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name),
                        listOf(settingsRootLevel()),
                        dialog = if (question) ({ RemoveServerDialog("Living room", false, {}, { question = false }) }) else null,
                        onCancelSwitch = { cancellations++ })
                }
            }
        }
        compose.onNodeWithTag(REMOVE_KEEP).assertIsFocused()
        press(Key.Back)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse(question)
        assertEquals(0, cancellations)
        press(Key.Back)
        assertEquals(1, cancellations)
    }

    @Composable
    private fun Editor.Screen(form: ConnectionFormState, onSaved: () -> Unit = {}) =
        SettingsConnection(this, session, { restores++ }, form, onSaved)

    @Test fun overviewDefersTheEditorAndBackUnwindsStepsBeforeReturningToEditAction() {
        val editor = Editor()
        val form = ConnectionFormState()
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                SettingsWithSetup(navigation, { onSaved -> editor.Screen(form, onSaved) })
            }
        }
        compose.onNodeWithText("Connection").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Edit connection").assertIsFocused()
        assertEquals(0, editor.loads)
        press(Key.DirectionRight) // Dedicated leaf actions do not enter on Right.
        assertEquals(0, editor.loads)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(1, editor.loads)
        // Editing keeps the saved address on Continue and searches only when asked.
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        compose.onNodeWithText("Find servers on my network").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        // Settings stays where it was under the setup.
        assertEquals(3, navigation.stack.frames.size)
        assertEquals(serverProfileRowId(fixtureActiveServer.id), navigation.stack.active.levelId)
        repeat(2) { press(Key.DirectionUp) }
        compose.onNodeWithTag("setup-host").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.runOnIdle { form.updateHost("draft.invalid") }
        press(Key.DirectionLeft)
        assertTrue(setupOpen)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        fun backKey(down: Boolean, repeat: Int = 0, flags: Int = 0) {
            compose.onRoot().performKeyPress(androidx.compose.ui.input.key.KeyEvent(android.view.KeyEvent(
                0, 0, if (down) android.view.KeyEvent.ACTION_DOWN else android.view.KeyEvent.ACTION_UP,
                android.view.KeyEvent.KEYCODE_BACK, repeat, 0, 0, 0, flags)))
        }
        backKey(false)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        backKey(true)
        backKey(false, flags = android.view.KeyEvent.FLAG_CANCELED)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        holdBack(::backKey) { compose.onAllNodes(hasSetTextAction()).assertCountEquals(1) }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        assertEquals("draft.invalid", form.host)
        assertTrue(setupOpen)
        press(Key.Back)
        compose.onNodeWithText("Edit connection").assertIsFocused()
        assertEquals("", form.password)
        assertEquals(0, editor.saves)
        assertEquals(emptyList<FakeSessionCall>(), session.calls)
    }

    @Test fun leavingSettingsClosesTheSetupLikeBackAndKeepsTheSavedConnection() {
        val editor = Editor()
        lateinit var form: ConnectionFormState
        var current by mutableStateOf(true)
        compose.setContent {
            TVHeadendPlayerTheme {
                val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                // The form lives inside the setup, as on the screen, so closing the setup drops the draft.
                SettingsWithSetup(navigation, { onSaved ->
                    form = rememberConnectionFormState()
                    editor.Screen(form, onSaved)
                }, isCurrent = current)
            }
        }
        val saved = editor.serverSettings.value
        // Each opening shows the saved connection, never a draft: its sign-in, with the password kept out of the form.
        fun openAndTestDraft() {
            press(Key.DirectionCenter)
            compose.onNodeWithTag("setup-continue").assertIsFocused()
            assertEquals("edit.invalid", form.host)
            assertEquals("fixture", form.username)
            assertEquals("", form.password)
            assertTrue(form.hasSavedPassword)
            compose.runOnIdle {
                form.updateHost("draft.invalid")
                form.updateUsername("draft-user")
            }
            val before = session.calls.size
            press(Key.DirectionCenter)
            press(Key.DirectionCenter)
            compose.onNodeWithTag("setup-connecting").assertIsFocused()
            assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls.drop(before))
        }
        // Another destination becoming current closes the setup at once; returning shows the entry.
        fun leaveAndReturn() {
            compose.runOnIdle { current = false }
            compose.waitForIdle()
            assertFalse(setupOpen)
            compose.runOnIdle { current = true }
            compose.onNodeWithText("Edit connection").assertIsFocused()
            assertFalse(setupOpen)
            assertEquals(0, editor.saves)
            assertEquals(saved, editor.serverSettings.value)
        }
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Edit connection").assertIsFocused()

        // Leaving while the test runs cancels it, which disconnects the draft and restores the saved connection.
        openAndTestDraft()
        leaveAndReturn()
        assertEquals(1, restores)
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())

        // A passed but unsaved draft is disconnected too, and the saved connection restored.
        openAndTestDraft()
        compose.runOnIdle { session.publish(ready()) }
        compose.onNodeWithTag("setup-start-watching").assertIsFocused()
        val passed = session.calls.size
        leaveAndReturn()
        assertEquals(2, restores)
        assertEquals(listOf(FakeSessionCall.DISCONNECT), session.calls.drop(passed))

        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        assertEquals("edit.invalid", form.host)
        assertEquals("fixture", form.username)
        assertEquals("", form.password)
        assertTrue(form.hasSavedPassword)
        assertEquals(3, editor.loads)
    }

    @Test fun leftStaysInTheWizardAndBackFromServerReturnsWithoutSaving() {
        val editor = Editor()
        val form = ConnectionFormState()
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                SideRail(currentRoute = AppDestination.SETTINGS,
                    onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                    SettingsWithSetup(navigation, { onSaved -> editor.Screen(form, onSaved) },
                        initialFocusEnabled = !drawerActive, contentPadding = padding)
                }
            }
        }
        repeat(2) { press(Key.DirectionCenter) }
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        compose.onNodeWithTag("setup-port").assertIsFocused()
        press(Key.DirectionLeft) // A step keeps Left, so it cannot drop what was entered.
        compose.onNodeWithTag("setup-port").assertIsFocused()
        assertTrue(setupOpen)
        press(Key.Back)
        assertFalse(setupOpen)
        compose.onNodeWithText("Edit connection").assertIsFocused()
        compose.onNodeWithTag("nav-settings").assertIsNotFocused()
        assertEquals(3, navigation.stack.frames.size)
        assertEquals("", form.password)
        assertEquals(0, editor.saves)
    }

    @Test fun aFailedTestOffersSaveAnywayAndBackUnwindsTheWizard() {
        val editor = Editor()
        val form = ConnectionFormState()
        compose.setContent {
            TVHeadendPlayerTheme {
                val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
                SettingsWithSetup(navigation, { onSaved -> editor.Screen(form, onSaved) })
            }
        }
        repeat(2) { press(Key.DirectionCenter) }
        press(Key.DirectionCenter)
        connect()
        compose.runOnIdle { session.publish(unavailable()) }
        compose.onNodeWithTag("setup-action-try_again").assertIsFocused()
        compose.onNodeWithText("Save anyway").assertIsDisplayed()
        compose.onNodeWithText("Your current connection stays in use.", substring = true).assertIsDisplayed()
        assertEquals(1, restores)
        press(Key.Back)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        assertEquals(0, editor.saves)
        press(Key.Back)
        press(Key.Back)
        compose.onNodeWithText("Edit connection").assertIsFocused()
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

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
        override suspend fun savePasswordServer(host: String, htspPort: Int, username: String, password: String) {
            saves++
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
                SettingsWithSetup(navigation, { onSaved -> editor.Screen(form, onSaved) })
            }
        }
        assertEquals(0, editor.loads)
        compose.onNodeWithText("Connection").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Edit connection").assertIsFocused()
        val parent = navigation.stack.active
        press(Key.DirectionCenter)
        connect()
        compose.runOnIdle { session.publish(ready()) }
        compose.waitForIdle()
        compose.onNodeWithTag("setup-start-watching").assertIsFocused()
        // The test's reconnect changes the Connection page under the setup; it must not take focus.
        compose.runOnIdle { degraded = true }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("setup-start-watching").assertIsFocused()
        compose.runOnIdle { degraded = false }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("setup-start-watching").assertIsFocused()
        assertEquals(0, editor.saves)
        press(Key.DirectionCenter)
        assertTrue(setupOpen)
        assertEquals(ConnectionFormFeedback.SAVE_FAILED, form.feedback)
        compose.onNodeWithTag("setup-start-watching").assertIsFocused()
        editor.failSave = false
        press(Key.DirectionCenter)
        compose.onNodeWithText("Edit connection").assertIsFocused()
        assertFalse(setupOpen)
        assertEquals(2, editor.saves)
        assertEquals(0, restores)
        assertEquals(parent, navigation.stack.active)
        assertEquals("", form.password)
    }

    @Test fun aTypedAddressSurvivesRecreationAndOnlyTheSignInLoadsAgain() {
        val editor = Editor()
        val restoration = StateRestorationTester(compose)
        lateinit var form: ConnectionFormState
        restoration.setContent {
            TVHeadendPlayerTheme {
                form = rememberConnectionFormState()
                SettingsConnection(editor, session, { restores++ }, form)
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
        assertEquals("", form.password)
        assertTrue(form.hasSavedPassword)
    }

    private fun connect() {
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-connecting").assertIsFocused()
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
    }

    private fun ready() = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
        ),
    )

    private fun unavailable() =
        SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE))
}
