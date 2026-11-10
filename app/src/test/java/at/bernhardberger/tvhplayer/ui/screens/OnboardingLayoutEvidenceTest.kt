package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.testutil.pressKey
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.ui.components.GuidedStepGuidance
import at.bernhardberger.tvhplayer.ui.components.GuidedStepper
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
import at.bernhardberger.tvhplayer.ui.PseudoLocaleEvidence
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import at.bernhardberger.tvhplayer.testutil.leaveTouchModeBeforeComposing
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
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

    @Test fun longStepLabelsShareWidthAndDoNotMoveTheHeading() {
        val short = listOf("Server", "Account", "Connect")
        val long = listOf("A much longer server label", "A much longer account label", "A much longer connection label")
        var labels by mutableStateOf(short)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                TVHeadendPlayerTheme {
                    Column(Modifier.width(340.dp)) {
                        GuidedStepGuidance("Heading", "Explanation", stepper = { GuidedStepper(labels, 1) })
                    }
                }
            }
        }
        val headingTop = compose.onNodeWithText("Heading").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { labels = long }
        long.forEach { label ->
            val node = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("every label has at least 60dp", node.boundsInRoot.width >= 60f)
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
            assertEquals(2, results.single().lineCount)
            assertTrue(results.single().isLineEllipsized(1))
        }
        assertEquals(headingTop, compose.onNodeWithText("Heading").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        compose.runOnIdle { labels = listOf("Server", "Konto", "Verbinden") }
        assertEquals(headingTop, compose.onNodeWithText("Heading").fetchSemanticsNode().boundsInRoot.top, 0.5f)
    }

    @Test fun serverAccountAndConnectAreThreeSeparateScreens() {
        show(form = ConnectionFormState(host = "192.0.2.10"))
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        assertTrue(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("setup-port").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(SetupStep.SIGN_IN, state.step)
        compose.onNodeWithTag("setup-username").assertIsFocused()
        repeat(3) { compose.pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(SetupStep.CONNECTING, state.step)
        compose.onNodeWithTag("setup-connecting").assertIsFocused()
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
        back()
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        back()
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        assertEquals(SetupStep.ADDRESS, state.step)
        assertEquals(0, state.searchRequest)
        assertFalse(state.searching)
    }

    @Test fun searchIsItsOwnScreenAndChoosingAServerGoesOnToAccount() {
        show()
        compose.onNodeWithTag("setup-find-server").assertIsFocused().assertTextEquals("Find servers on my network")
        assertEquals(0, state.searchRequest)
        compose.pressKey(Key.DirectionCenter)
        assertEquals(SetupStep.FIND_SERVER, state.step)
        assertEquals(1, state.searchRequest)
        assertTrue(state.searching)
        compose.onNodeWithTag("setup-host").assertDoesNotExist()
        compose.onNodeWithTag("setup-search-again").assertIsFocused().assertTextEquals("Searching…")
        discovery.value = discovering(SERVER)
        compose.waitForIdle()
        compose.onNodeWithTag("setup-search-again").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("setup-server-0").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("setup-username").assertIsFocused()
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(SERVER.host, state.form.host)
        assertEquals(SERVER.port.toString(), state.form.port)
        compose.onNodeWithText(SERVER.host).assertExists()
        back()
        assertEquals(SetupStep.ADDRESS, state.step)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
    }

    @Test fun firstRunSearchesOnlyWhenAskedAndNeverValidatesAnUntouchedAddress() {
        show()
        assertEquals(0, state.searchRequest)
        assertFalse(state.searching)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        discovery.value = discovering(SERVER)
        compose.waitForIdle()
        compose.onNodeWithText("Enter the server address").assertDoesNotExist()
        discovery.value = discovering()
        compose.mainClock.advanceTimeBy(13_000)
        back()
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        compose.onNodeWithText("Enter the server address").assertDoesNotExist()
        repeat(3) { compose.pressKey(Key.DirectionDown) }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithText("Enter the server address").assertExists()
    }

    @Test fun searchingCannotRestartUntilItFinishes() {
        show(SetupStep.FIND_SERVER)
        compose.onNodeWithTag("setup-search-again")
            .assertIsFocused().assertIsNotEnabled().assertTextEquals("Searching…")
        compose.pressKey(Key.DirectionCenter)
        assertEquals(1, state.searchRequest)
        compose.mainClock.advanceTimeBy(13_000)
        compose.onNodeWithTag("setup-search-again")
            .assertIsFocused().assertIsEnabled().assertTextEquals("Search again")
        compose.pressKey(Key.DirectionCenter)
        assertEquals(2, state.searchRequest)
        compose.onNodeWithTag("setup-search-again").assertIsNotEnabled().assertTextEquals("Searching…")
    }

    @Test fun anEmptySearchOffersTheAddressFieldButNotWhileItRuns() {
        show(SetupStep.FIND_SERVER)
        compose.onNodeWithTag("setup-enter-manually").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(13_000)
        compose.onNodeWithTag("setup-search-again").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("setup-enter-manually").assertIsFocused().assertTextEquals("Enter address manually")
        compose.pressKey(Key.DirectionCenter)
        assertEquals(SetupStep.ADDRESS, state.step)
        compose.onNodeWithTag("setup-host").assertIsFocused()
    }

    @Test fun anEmptySearchWithAResultOffersNoManualEntry() {
        show(SetupStep.FIND_SERVER)
        discovery.value = discovering(SERVER)
        compose.mainClock.advanceTimeBy(13_000)
        compose.onNodeWithTag("setup-server-0").assertExists()
        compose.onNodeWithTag("setup-enter-manually").assertDoesNotExist()
    }

    @Test fun backFromTheSearchReturnsToItsActionAndStopsIt() {
        show()
        compose.pressKey(Key.DirectionCenter)
        assertTrue(state.searching)
        back()
        assertEquals(SetupStep.ADDRESS, state.step)
        assertFalse(state.searching)
        compose.onNodeWithTag("setup-find-server").assertIsFocused().assertIsEnabled()
    }

    @Test fun changeAddressAfterFailureReturnsToServerWithoutSearching() {
        show(form = ConnectionFormState(host = "192.0.2.10"))
        repeat(3) { compose.pressKey(Key.DirectionDown) }
        compose.pressKey(Key.DirectionCenter)
        repeat(3) { compose.pressKey(Key.DirectionDown) }
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle { session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE))) }
        compose.onNodeWithTag("setup-action-change_address").requestFocus()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        assertFalse(state.searching)
    }

    @Test fun aVanishedResultMovesFocusOnceAndNotOnTheNextVisit() {
        show(SetupStep.FIND_SERVER)
        discovery.value = discovering(SERVER)
        compose.waitForIdle()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("setup-server-0").assertIsFocused()
        discovery.value = discovering()
        compose.onNodeWithTag("setup-search-again").assertIsFocused()
        assertTrue(state.findFocusRequest > 0)
        back()
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        repeat(3) { compose.pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("setup-continue").assertIsFocused()
    }

    @Test fun aLongListRunsPastTheScreenWithFocusInsideTheSafeArea() {
        show(SetupStep.FIND_SERVER)
        val guidance = compose.onNodeWithText("Find your server").fetchSemanticsNode().boundsInRoot
        discovery.value = discovering(*MANY_SERVERS)
        compose.waitForIdle()
        val reach = MANY_SERVERS.indices.maxOf { compose.onNodeWithTag("setup-server-$it").fetchSemanticsNode().boundsInRoot.bottom }
        assertEquals("Only the screen's edge cuts the list", 540f, reach, 0.5f)
        repeat(MANY_SERVERS.size) { compose.pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(1_000)
        val last = compose.onNodeWithTag("setup-server-${MANY_SERVERS.lastIndex}")
            .assertIsFocused().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Focused row must stay above the bottom safe edge, was ${last.bottom}", last.bottom <= 505f)
        assertEquals(guidance, compose.onNodeWithText("Find your server").fetchSemanticsNode().boundsInRoot)
        val topTag = "setup-server-1"
        repeat(MANY_SERVERS.size - 2) { compose.pressKey(Key.DirectionUp) }
        compose.mainClock.advanceTimeBy(1_000)
        val top = compose.onNodeWithTag(topTag).assertIsFocused().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Focused row must stay below the top safe edge, was ${top.top}", top.top >= 80f)
    }

    @Test fun editStartsOnContinueWithPrefilledFieldsAndSearchesOnlyWhenAsked() {
        showEdit()
        assertEquals(0, state.searchRequest)
        assertFalse(state.searching)
        assertEquals("192.0.2.10", state.form.host)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        compose.onNodeWithTag("setup-welcome-continue").assertDoesNotExist()
        compose.onNodeWithText("Tvheadend Player").assertDoesNotExist()
        compose.onNodeWithTag("setup-signature").assertDoesNotExist()
        repeat(3) { compose.pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(SetupStep.FIND_SERVER, state.step)
        assertTrue(state.searching)
        discovery.value = discovering(SERVER)
        compose.waitForIdle()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("setup-server-0").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(SetupStep.SIGN_IN, state.step)
        back()
        assertEquals(SetupStep.ADDRESS, state.step)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
    }

    @Test fun firstRunWelcomePrecedesTheStepperAndBackRestoresItsAction() {
        show(SetupStep.WELCOME)
        compose.onNodeWithTag("setup-welcome-continue").assertIsFocused()
        assertFalse(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        compose.onNodeWithText("Account").assertDoesNotExist()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        compose.mainClock.advanceTimeBy(1_000)
        back()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("setup-welcome-continue").assertIsFocused()
    }

    @Test fun aKeyDuringTheWelcomeFadeOnlyEndsIt() {
        compose.mainClock.autoAdvance = false
        show(SetupStep.WELCOME)
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("setup-welcome-continue").assertIsFocused()
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(100)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(100)
        assertEquals("The key that ended the fade must not press Continue", SetupStep.WELCOME, state.step)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(100)
        assertEquals(SetupStep.ADDRESS, state.step)
    }

    @Test fun aHeldFadeKeyStaysSwallowedThroughAnotherKeyAndItsRepeats() {
        compose.mainClock.autoAdvance = false
        show(SetupStep.WELCOME)
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("setup-welcome-continue").assertIsFocused()
        compose.onRoot().performKeyInput {
            keyDown(Key.DirectionCenter)
            pressKey(Key.DirectionLeft)
            advanceEventTime(1_000)
            keyUp(Key.DirectionCenter)
        }
        compose.mainClock.advanceTimeBy(100)
        assertEquals("Another key must not free the key that ended the fade", SetupStep.WELCOME, state.step)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(100)
        assertEquals(SetupStep.ADDRESS, state.step)
    }

    @Test fun firstRunsSignatureStandsOnTheSafeLine() {
        show()
        val bottom = compose.onNodeWithTag("setup-signature").fetchSemanticsNode().boundsInRoot.bottom
        assertEquals(540f - 27f, bottom / compose.density.density, 0.5f)
    }

    @Test @Category(VisualCapture::class)
    fun captureWelcome() {
        show(SetupStep.WELCOME)
        compose.pressKey(Key.DirectionCenter)
        back()
        compose.onNodeWithTag("setup-welcome-continue").assertIsFocused()
        capture("welcome")
    }

    @Test fun showPasswordRowSitsBetweenThePasswordAndContinue() {
        show(SetupStep.SIGN_IN, ConnectionFormState(host = "192.0.2.10"))
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("setup-password").assertIsFocused()
        compose.pressKey(Key.DirectionRight)
        compose.onNodeWithTag("setup-password").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithText("Show password").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithText("Show password").assertIsFocused()
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("setup-password").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
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
        repeat(3) { compose.pressKey(Key.DirectionDown) }
        assertLabelled("setup-continue")
        compose.pressKey(Key.DirectionCenter)
        repeat(3) { compose.pressKey(Key.DirectionDown) }
        assertLabelled("setup-continue")
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("setup-connecting").assertIsFocused().assertIsDisplayed()
    }

    @Test fun savedPasswordIsNotExposedAndClearIsOnlyOnAccount() {
        showEdit()
        compose.onNodeWithTag("setup-clear-password").assertDoesNotExist()
        compose.runOnIdle { state.continueFromAddress() }
        compose.onNodeWithText("Password saved").assertExists()
        compose.onNodeWithTag("setup-clear-password").assertExists()
        assertEquals("", state.form.password)
        compose.onNodeWithText("Show password").assertExists().assertIsNotEnabled()
        compose.onNodeWithTag("setup-password").requestFocus()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("setup-password").performTextInput("replacement")
        compose.onNodeWithText("Show password").assertIsEnabled()
        back()
        compose.runOnIdle { state.clearSavedPassword() }
        compose.onNodeWithTag("setup-clear-password").assertDoesNotExist()
        compose.onNodeWithTag("setup-username").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun largeFontSavedAccountCanReachClearAndSaveWithoutSquashedLabels() {
        show(mode = SetupMode.EDIT, scale = 1.3f)
        repeat(3) { compose.pressKey(Key.DirectionDown) }
        compose.pressKey(Key.DirectionCenter)
        assertLabelled("setup-continue")
        compose.pressKey(Key.DirectionDown)
        assertLabelled("setup-clear-password")
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("setup-connecting").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { session.publish(ready()) }
        assertLabelled("setup-start-watching")
    }

    @Test @Category(VisualCapture::class)
    fun captureServerStates() {
        show()
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        capture("server")
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("setup-search-again").assertIsFocused()
        capture("search-searching")
        discovery.value = discovering(SERVER)
        compose.waitForIdle()
        compose.onNodeWithTag("setup-search-again").assertIsFocused()
        capture("search-results")
        discovery.value = discovering()
        compose.mainClock.advanceTimeBy(13_000)
        capture("search-no-results")
        back()
        compose.runOnIdle { state.continueFromAddress() }
        compose.onNodeWithTag("setup-host").assertIsFocused()
        capture("server-validation")
    }

    @Test @Category(VisualCapture::class)
    fun capturePrefilledServerAndSavedAccount() {
        showEdit()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionUp)
        capture("edit-prefilled-server")
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
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle { session.publish(SessionObservation.create(sessionState = SessionState.Connecting)) }
        compose.runOnIdle { session.publish(ready()) }
        assertSuccess()
        capture("success-focused")
    }

    @Test fun theSuccessActionLooksFocusedWhenTheTestPasses() {
        show(SetupStep.SIGN_IN, ConnectionFormState(host = "192.0.2.10"))
        leaveTouchMode()
        compose.runOnIdle { state.continueFromSignIn() }
        compose.waitForIdle()
        compose.onNodeWithTag("setup-connecting").assertIsFocused()
        compose.runOnIdle { session.publish(ready()) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-start-watching")
    }

    @Test fun everyStepsStartingActionLooksFocusedOnATv() {
        show(SetupStep.WELCOME)
        leaveTouchMode()
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-welcome-continue")
        compose.pressKey(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-find-server")
        back()
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-welcome-continue")
        compose.pressKey(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(1_000)
        compose.pressKey(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("setup-search-again").assertTextEquals("Searching…")
        assertLooksFocused("setup-search-again")
        compose.mainClock.advanceTimeBy(13_000)
        compose.onNodeWithTag("setup-search-again").assertTextEquals("Search again")
        assertLooksFocused("setup-search-again")
        back()
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-find-server")
    }

    @Test fun theWholeFirstRunRouteLooksFocusedOnATvFromItsFirstFrame() {
        leaveTouchModeBeforeComposing()
        show(SetupStep.WELCOME, ConnectionFormState(host = "192.0.2.10"))
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-welcome-continue")
        compose.pressKey(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-find-server")
        repeat(3) { compose.pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-continue")
        compose.pressKey(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(1_000)
        assertFieldLooksFocused("setup-username")
        back()
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-continue")
    }

    @Test fun aFailedTestsFirstActionLooksFocusedOnATv() {
        leaveTouchModeBeforeComposing()
        show(SetupStep.SIGN_IN, ConnectionFormState(host = "192.0.2.10"))
        compose.mainClock.advanceTimeBy(1_000)
        assertFieldLooksFocused("setup-username")
        compose.runOnUiThread { state.connect() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE))) }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(SetupStep.FAILED, state.step)
        assertLooksFocused("setup-action-try_again")
        compose.runOnUiThread { state.onAction(SetupAction.CHANGE_ADDRESS) }
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-find-server")
    }

    @Test fun backFromTheSearchLooksFocusedOnATv() {
        leaveTouchModeBeforeComposing()
        show()
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-find-server")
        compose.pressKey(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(SetupStep.FIND_SERVER, state.step)
        back()
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-find-server")
    }

    @Test fun aSettingsEditorLooksFocusedOnATvFromItsFirstFrame() {
        leaveTouchModeBeforeComposing()
        showEdit()
        compose.mainClock.advanceTimeBy(1_000)
        assertLooksFocused("setup-continue")
    }

    private fun leaveTouchModeBeforeComposing() = compose.leaveTouchModeBeforeComposing()

    private fun assertFieldLooksFocused(tag: String) {
        val node = compose.onNodeWithTag(tag).assertIsFocused()
        val image = node.captureToImage().toPixelMap()
        val focusBorder = (0 until 8).map { image[image.width / 2, it] }
        assertTrue("the focused $tag has a light blue border, top pixels $focusBorder",
            focusBorder.any { it.blue > 0.8f && it.red < 0.6f })
    }

    /** A TV is not in touch mode, the test default: only then does the view hand focus straight back. */
    private fun leaveTouchMode() {
        compose.runOnIdle {
            ReflectionHelpers.callInstanceMethod<Boolean>(view.rootView.parent, "ensureTouchModeLocally", ClassParameter.from(Boolean::class.javaPrimitiveType, false))
        }
    }

    private fun assertLooksFocused(tag: String) {
        val node = compose.onNodeWithTag(tag).assertIsFocused()
        val image = node.captureToImage().toPixelMap()
        val row = (0 until image.width).map { image[it, image.height / 2].luminance() }.sorted()
        assertTrue("the focused $tag has a light fill, its middle row is at ${row[row.size / 2]}", row[row.size / 2] > 0.5f)
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

    private fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }
    private fun ready() = SessionObservation.create(sessionState = SessionState.Ready(
        ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)))

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        val focused = compose.onAllNodes(isFocused()).fetchSemanticsNodes().single()
        val focus = if (SemanticsProperties.TestTag in focused.config) focused.config[SemanticsProperties.TestTag] else "show-password-row"
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/onboarding-captures")
            CaptureOutput.writePng(File(directory, "$name.png"), bitmap)
            PseudoLocaleEvidence.mirror("onboarding", name, bitmap, view, captureScale, "mdpi 1.0", focus ?: "none",
                "step=${state.step}; mode=${state.mode}; production ConnectionSetupFlow; fictional offline discovery data")
            CaptureOutput.writeText(File(directory, "$name.txt")) { "canvas=960x540dp; pixels=${view.width}x${view.height}; locale=${view.resources.configuration.locales[0].toLanguageTag()}; fontScale=$captureScale; focus=$focus; step=${state.step}; mode=${state.mode}; production composables, fictional offline data" }
            bitmap.recycle()
        }
    }
}

private val SERVER = DiscoveredTvheadendServer("Living room", "192.0.2.10", 9982)
private val MANY_SERVERS = Array(9) { DiscoveredTvheadendServer("Server ${it + 1}", "192.0.2.${it + 20}", 9982) }
private object FixtureProfiles : ConnectionProfileEditor {
    override val serverSettings = emptyFlow<ServerSettings>()
    override suspend fun loadServerForEditing(applyAvailable: (String, Int, String, String) -> Unit) {
        applyAvailable("192.0.2.10", 9982, "fixture", "test-only password")
    }
    override suspend fun saveServer(host: String, htspPort: Int) = Unit
    override suspend fun savePasswordServer(host: String, htspPort: Int, username: String, password: String) = Unit
    override suspend fun clearProfile() = Unit
}
