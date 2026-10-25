package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.*
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.onboarding.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Production steps, with offline state and remote keys; never a live server or credential. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingLayoutEvidenceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var view: View
    private lateinit var state: ConnectionSetupState
    private val session = FakeTvheadendSession()
    private val discovery = MutableStateFlow(discovering())
    private var captureScale = 1f

    @Test fun serverAccountAndConnectAreThreeSeparateScreens() {
        show(form = ConnectionFormState(host = "192.0.2.10"))
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        assertTrue(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-port").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(SetupStep.SIGN_IN, state.step)
        compose.onNodeWithTag("setup-username").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(SetupStep.CONNECTING, state.step)
        compose.onNodeWithTag("setup-connecting").assertIsFocused()
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
        back()
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        back()
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        assertEquals(SetupStep.ADDRESS, state.step)
    }

    @Test fun searchStaysInsideServerAndResultsDoNotStealFocus() {
        show()
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(SetupStep.ADDRESS, state.step)
        assertEquals(1, state.searchRequest)
        assertTrue(state.searching)
        discovery.value = discovering(SERVER)
        compose.waitForIdle()
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-server-0").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-username").assertIsFocused()
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(SERVER.host, state.form.host)
        assertEquals(SERVER.port.toString(), state.form.port)
    }

    @Test fun firstRunSearchIsAutomaticAndNeverValidatesAnUntouchedAddress() {
        show()
        assertEquals(1, state.searchRequest)
        assertTrue(state.searching)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        compose.onNodeWithText("Enter the server address").assertDoesNotExist()
        discovery.value = discovering(SERVER)
        compose.waitForIdle()
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        compose.onNodeWithText("Enter the server address").assertDoesNotExist()
        discovery.value = discovering()
        compose.mainClock.advanceTimeBy(13_000)
        compose.onNodeWithText("Enter the server address").assertDoesNotExist()
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        compose.onNodeWithText("Enter the server address").assertExists()
    }

    @Test fun searchingCannotRestartUntilItFinishes() {
        show()
        compose.onNodeWithTag("setup-find-server")
            .assertIsFocused().assertIsNotEnabled().assertTextEquals("Searching…")
        press(Key.DirectionCenter)
        assertEquals(1, state.searchRequest)
        compose.mainClock.advanceTimeBy(13_000)
        compose.onNodeWithTag("setup-find-server")
            .assertIsFocused().assertIsEnabled().assertTextEquals("Search again")
        press(Key.DirectionCenter)
        assertEquals(2, state.searchRequest)
        compose.onNodeWithTag("setup-find-server").assertIsNotEnabled().assertTextEquals("Searching…")
    }

    @Test fun backToServerRestartsDiscoveryAndKeepsContinueFocused() {
        show(form = ConnectionFormState(host = "192.0.2.10"))
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        back()
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        compose.onNodeWithTag("setup-find-server").assertTextEquals("Searching…")
        assertTrue(state.searching)
        discovery.value = discovering(SERVER)
        compose.onNodeWithTag("setup-server-0").assertExists()
        compose.onNodeWithTag("setup-continue").assertIsFocused()
    }

    @Test fun changeAddressAfterFailureRestartsDiscovery() {
        show(form = ConnectionFormState(host = "192.0.2.10"))
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        compose.runOnIdle { session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE))) }
        compose.onNodeWithTag("setup-action-change_address").requestFocus()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-find-server").assertIsFocused().assertTextEquals("Searching…")
        assertTrue(state.searching)
        discovery.value = discovering(SERVER)
        compose.onNodeWithTag("setup-server-0").assertExists()
    }

    @Test fun handledFindFocusDoesNotReplayWhenReturningFromAccount() {
        show(form = ConnectionFormState(host = "192.0.2.10"))
        discovery.value = discovering(SERVER)
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-server-0").assertIsFocused()
        discovery.value = discovering()
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        assertTrue(state.findFocusRequest > 0)
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        back()
        compose.onNodeWithTag("setup-continue").assertIsFocused()
    }

    @Test fun threeResultsScrollOnlyTheActionsAndKeepContinueInsideSafeArea() {
        show()
        val guidance = compose.onNodeWithText("Where’s your server?").fetchSemanticsNode().boundsInRoot
        discovery.value = discovering(SERVER, SECOND_SERVER, THIRD_SERVER)
        repeat(6) { press(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(1_000)
        val action = compose.onNodeWithTag("setup-continue").assertIsFocused().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        // Semantics reports the unscaled bounds; leave room for TV Surface's 1.1x focus scale.
        assertEquals("The action must not be clipped", 48f, action.height, 0.6f)
        assertTrue("Focus scale must clear the bottom fade", action.bottom + action.height * 0.05f <= 540f - 27f - 24f)
        assertTrue("Focused shape must stay above the bottom safe edge", action.bottom + action.height * 0.05f <= 513f)
        assertEquals(guidance, compose.onNodeWithText("Where’s your server?").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun editSearchesAutomaticallyWithPrefilledFieldsAndTheSameActionOrder() {
        showEdit()
        assertEquals(1, state.searchRequest)
        assertTrue(state.searching)
        assertEquals("192.0.2.10", state.form.host)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        compose.onNodeWithTag("setup-welcome-continue").assertDoesNotExist()
        discovery.value = discovering(SERVER)
        compose.waitForIdle()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-server-0").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-port").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
    }

    @Test fun resultsAboveTheFieldsDoNotStealManualEntryFocus() {
        show()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        discovery.value = discovering(SERVER)
        compose.waitForIdle()
        compose.onNodeWithTag("setup-host").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("setup-server-0").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
    }

    @Test fun firstRunWelcomePrecedesTheStepperAndBackRestoresItsAction() {
        show(SetupStep.WELCOME)
        compose.onNodeWithTag("setup-welcome-continue").assertIsFocused()
        assertFalse(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        compose.onNodeWithText("Account").assertDoesNotExist()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        back()
        compose.onNodeWithTag("setup-welcome-continue").assertIsFocused()
    }

    @Test @Category(VisualCapture::class)
    fun captureWelcome() {
        show(SetupStep.WELCOME)
        press(Key.DirectionCenter)
        back()
        compose.onNodeWithTag("setup-welcome-continue").assertIsFocused()
        capture("welcome")
    }

    @Test @Category(VisualCapture::class)
    fun captureWelcomeLargeFont() {
        show(SetupStep.WELCOME, scale = 1.3f)
        press(Key.DirectionCenter)
        back()
        compose.onNodeWithTag("setup-welcome-continue").assertIsFocused().assertIsDisplayed()
        capture("welcome-font1.3")
    }

    @Test @Category(VisualCapture::class)
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun captureGermanWelcomeLargeFont() {
        show(SetupStep.WELCOME, scale = 1.3f)
        press(Key.DirectionCenter)
        back()
        compose.onNodeWithTag("setup-welcome-continue").assertIsFocused().assertIsDisplayed()
        capture("welcome-de-font1.3")
    }

    @Test fun eyeTogglesItsLabelWithoutOpeningTextEditing() {
        show(SetupStep.SIGN_IN, ConnectionFormState(host = "192.0.2.10"))
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-password").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Show password").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Hide password").assertIsFocused()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Show password").assertIsFocused()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        press(Key.DirectionLeft)
        compose.onNodeWithTag("setup-password").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test fun successInitiallyFocusesALabelledStartWatchingButton() {
        show(SetupStep.DONE, ConnectionFormState(host = "192.0.2.10"))
        assertSuccess()
    }

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanLargeFontSuccessKeepsItsButtonVisibleAndFocused() {
        show(SetupStep.DONE, ConnectionFormState(host = "192.0.2.10"), scale = 1.3f)
        assertSuccess()
    }

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanLargeFontServerAndAccountActionsHaveVisibleLabels() {
        show(form = ConnectionFormState(host = "192.0.2.10"), scale = 1.3f)
        repeat(3) { press(Key.DirectionDown) }
        assertLabelled("setup-continue")
        press(Key.DirectionCenter)
        repeat(2) { press(Key.DirectionDown) }
        assertLabelled("setup-continue")
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-connecting").assertIsFocused().assertIsDisplayed()
    }

    @Test fun savedPasswordIsNotExposedAndClearIsOnlyOnAccount() {
        showEdit()
        compose.onNodeWithTag("setup-clear-password").assertDoesNotExist()
        compose.runOnIdle { state.continueFromAddress() }
        compose.onNodeWithText("Password saved").assertExists()
        compose.onNodeWithTag("setup-clear-password").assertExists()
        assertEquals("", state.form.password)
        compose.runOnIdle { state.clearSavedPassword() }
        compose.onNodeWithTag("setup-clear-password").assertDoesNotExist()
        compose.onNodeWithTag("setup-username").assertIsFocused()
    }

    @Test fun savedPasswordEyeAppearsOnlyAfterTypingAReplacement() {
        showEdit()
        compose.runOnIdle { state.continueFromAddress() }
        compose.onNodeWithContentDescription("Show password").assertDoesNotExist()
        press(Key.DirectionUp)
        compose.onNodeWithTag("setup-password").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("setup-password").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Show password").assertDoesNotExist()
        back()
        compose.onNodeWithText("Password saved").assertExists()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-password").performTextInput("replacement")
        compose.onNodeWithContentDescription("Show password").assertExists()
        compose.onNodeWithTag("setup-password").performTextClearance()
        compose.onNodeWithContentDescription("Show password").assertDoesNotExist()
        back()
        compose.onNodeWithText("Password saved").assertExists()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-password").performTextInput("replacement")
        back()
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Show password").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Hide password").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun largeFontSavedAccountCanReachClearAndSaveWithoutSquashedLabels() {
        show(mode = SetupMode.EDIT, scale = 1.3f)
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        assertLabelled("setup-continue")
        press(Key.DirectionDown)
        assertLabelled("setup-clear-password")
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-connecting").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { session.publish(ready()) }
        assertLabelled("setup-start-watching")
    }

    @Test @Category(VisualCapture::class)
    fun captureServerStates() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionUp)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        capture("server-searching")
        discovery.value = discovering(SERVER)
        compose.waitForIdle()
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        capture("server-results")
        discovery.value = discovering()
        compose.mainClock.advanceTimeBy(13_000)
        capture("server-no-results")
        compose.runOnIdle { state.continueFromAddress() }
        compose.onNodeWithTag("setup-host").assertIsFocused()
        capture("server-validation")
    }

    @Test @Category(VisualCapture::class)
    fun captureThreeServersWithContinueFocused() {
        show()
        discovery.value = discovering(SERVER, SECOND_SERVER, THIRD_SERVER)
        repeat(6) { press(Key.DirectionDown) }
        compose.onNodeWithTag("setup-continue").assertIsFocused().assertIsDisplayed()
        capture("server-three-results-continue")
    }

    @Test @Category(VisualCapture::class)
    fun capturePrefilledServerAndSavedAccount() {
        showEdit()
        press(Key.DirectionDown)
        press(Key.DirectionUp)
        capture("edit-prefilled-server")
        discovery.value = discovering(SERVER)
        compose.waitForIdle()
        capture("edit-server-results")
        compose.runOnIdle { state.continueFromAddress() }
        capture("edit-account-saved-password")
        compose.runOnIdle { state.continueFromSignIn() }
        capture("edit-connect")
    }

    @Test @Category(VisualCapture::class)
    fun captureAccountConnectAndOutcomes() {
        show(SetupStep.SIGN_IN, ConnectionFormState(host = "192.0.2.10"))
        capture("account")
        compose.runOnIdle { state.continueFromSignIn() }
        capture("connect")
        capture("connecting")
        compose.runOnIdle { session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE))) }
        capture("connect-failed")
        press(Key.DirectionCenter)
        compose.runOnIdle { session.publish(SessionObservation.create(sessionState = SessionState.Connecting)) }
        compose.runOnIdle { session.publish(ready()) }
        assertSuccess()
        capture("success-focused")
    }

    private fun assertSuccess() {
        compose.onNodeWithTag("setup-start-watching").assertIsFocused().assertIsDisplayed()
        assertLabelled("setup-start-watching")
    }

    private fun assertLabelled(tag: String) {
        val node = compose.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
        assertTrue(node.fetchSemanticsNode().boundsInRoot.height >= 32f)
        compose.onNode(hasAnyAncestor(hasTestTag(tag)) and hasText("", substring = true), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    private fun showEdit() = show(mode = SetupMode.EDIT)

    private fun show(step: SetupStep = SetupStep.ADDRESS, form: ConnectionFormState = ConnectionFormState(),
        mode: SetupMode = SetupMode.FIRST_RUN, scale: Float = 1f) {
        captureScale = scale
        compose.setContent {
            view = LocalView.current
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                TVHeadendPlayerTheme {
                    Surface(modifier = Modifier.fillMaxSize(), colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground)) {
                        state = remember { ConnectionSetupState(form, session, FixtureProfiles, { discovery }, scope, mode, step = step) }
                        LaunchedEffect(state) { if (mode == SetupMode.EDIT) state.loadSaved() }
                        ConnectionSetupFlow(state)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; compose.waitForIdle() }
    private fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }
    private fun ready() = SessionObservation.create(sessionState = SessionState.Ready(
        ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)))

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        val focused = compose.onAllNodes(isFocused()).fetchSemanticsNodes().single()
        val focus = focused.config[SemanticsProperties.TestTag]
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/onboarding-captures").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            File(directory, "$name.txt").writeText("canvas=960x540dp; locale=${view.resources.configuration.locales[0].language}; fontScale=$captureScale; focus=$focus; step=${state.step}; mode=${state.mode}; production composables, fictional offline data")
            bitmap.recycle()
        }
    }
}

private val SERVER = DiscoveredTvheadendServer("Living room", "192.0.2.10", 9982)
private val SECOND_SERVER = DiscoveredTvheadendServer("Office", "192.0.2.11", 9982)
private val THIRD_SERVER = DiscoveredTvheadendServer("Guest room", "192.0.2.12", 9982)
private object FixtureProfiles : ConnectionProfileEditor {
    override val serverSettings = emptyFlow<ServerSettings>()
    override suspend fun loadServerForEditing(applyAvailable: (String, Int, String, String) -> Unit) {
        applyAvailable("192.0.2.10", 9982, "fixture", "test-only password")
    }
    override suspend fun saveServer(host: String, htspPort: Int) = Unit
    override suspend fun savePasswordServer(host: String, htspPort: Int, username: String, password: String,
        credentialLease: CredentialEditLease) = credentialLease.release()
    override suspend fun clearProfile() = Unit
}
