package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryFailure
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.onboarding.ConnectionSetupFlow
import at.bernhardberger.tvhplayer.ui.screens.onboarding.ConnectionSetupState
import at.bernhardberger.tvhplayer.ui.screens.onboarding.DiscoveryWindow
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupMode
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupStep
import at.bernhardberger.tvhplayer.ui.screens.onboarding.discovering
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The guided setup's keylines, initial focus, D-pad paths and Back, on the production steps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingLayoutEvidenceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var view: View
    private lateinit var state: ConnectionSetupState
    private val session = FakeTvheadendSession()
    private val discovery = MutableStateFlow(discovering())

    @Test
    fun welcomeKeepsItsKeylineAndLeavesBackToTheActivity() {
        show()
        assertEquals(94f, textBounds(R.string.onboarding_title).left, .1f)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-enter-address").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-enter-address").assertIsFocused()
        assertFalse("Back on the welcome leaves for the TV home", backHandled())
    }

    @Test
    fun addressWalksFieldsWithTheDpadAndBackReturnsToTheWelcomeChoice() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(SetupStep.ADDRESS, state.step)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        // The label sits above the outline, so the empty field shows no text where a value goes.
        compose.onAllNodesWithText(label(R.string.onboarding_server_address), useUnmergedTree = true)
            .assertCountEquals(0)
        compose.onNodeWithContentDescription(label(R.string.onboarding_server_address)).assertIsFocused()
        val host = compose.onNodeWithTag("setup-host").fetchSemanticsNode().boundsInRoot
        assertEquals(TOP_FIELD_LABEL + LABEL_HEIGHT, host.top, 6f)

        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-port").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        compose.onNodeWithText(label(R.string.onboarding_server_address_missing)).assertExists()

        back()
        assertEquals(SetupStep.WELCOME, state.step)
        compose.onNodeWithTag("setup-enter-address").assertIsFocused()
    }

    @Test
    fun foundServersTakeFocusAndAChosenOneSkipsTheAddress() {
        show()
        press(Key.DirectionCenter)
        assertEquals(SetupStep.FIND, state.step)
        compose.onNodeWithText(label(R.string.onboarding_find_searching)).assertExists()
        compose.onNodeWithTag("setup-enter-address").assertIsFocused()

        discovery.value = discovering(LIVING_ROOM, OFFICE)
        compose.waitForIdle()
        compose.onNodeWithTag("setup-server-0").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-server-1").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-enter-address").assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(OFFICE.host, state.form.host)
        compose.onNodeWithTag("setup-username").assertIsFocused()

        back()
        assertEquals(SetupStep.FIND, state.step)
        compose.onNodeWithTag("setup-server-1").assertIsFocused()
        back()
        assertEquals(SetupStep.WELCOME, state.step)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
    }

    @Test
    fun anUnavailableSearchOffersTheAddressFirstAndSearchAgain() {
        discovery.value = TvheadendDiscoveryState.Unavailable(TvheadendDiscoveryFailure.START_FAILED)
        show()
        press(Key.DirectionCenter)
        compose.onNodeWithText(label(R.string.onboarding_find_none_title)).assertExists()
        compose.onNodeWithTag("setup-enter-address").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-search-again").assertIsFocused()

        discovery.value = discovering()
        press(Key.DirectionCenter)
        compose.onNodeWithText(label(R.string.onboarding_find_searching)).assertExists()
        compose.onNodeWithTag("setup-enter-address").assertIsFocused()
    }

    @Test
    fun aSearchWithoutAnswersEndsAfterItsWindow() {
        show()
        press(Key.DirectionCenter)
        compose.onNodeWithText(label(R.string.onboarding_find_searching)).assertExists()
        compose.mainClock.advanceTimeBy(DiscoveryWindow.inWholeMilliseconds + 100)
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.onboarding_find_none_title)).assertExists()
        compose.onNodeWithTag("setup-enter-address").assertIsFocused()
    }

    @Test
    fun signInStartsOnTheFirstEmptyFieldAndBackCancelsTheTest() {
        show(SetupStep.SIGN_IN, ConnectionFormState(host = HOST, username = USERNAME))
        compose.onNodeWithTag("setup-password").assertIsFocused()

        state.form.updatePassword(PASSWORD)
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-connect").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(SetupStep.CONNECTING, state.step)
        compose.onNodeWithTag("setup-connect").assertIsFocused().assertTextEquals(label(R.string.onboarding_connecting))
            .assert(hasNoClickAction())
        compose.onNodeWithTag("setup-username").assertIsNotEnabled()

        back()
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())
        compose.onNodeWithTag("setup-connect").assertIsFocused().assertTextEquals(label(R.string.onboarding_connect))
        compose.onNodeWithTag("setup-username").assertIsEnabled()
    }

    @Test
    fun aRejectedSignInFocusesChangeSignInAndBackReturnsToTheFields() {
        startTest()
        session.publish(unavailable(SessionFailure.AUTHENTICATION_REJECTED))
        compose.waitForIdle()
        assertEquals(SetupStep.FAILED, state.step)
        compose.onNodeWithTag("setup-action-change_sign_in").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-action-try_again").assertIsFocused()

        back()
        assertEquals(SetupStep.SIGN_IN, state.step)
        compose.onNodeWithTag("setup-connect").assertIsFocused()
    }

    @Test
    fun anUnreachableServerOffersContinueAnywayLast() {
        startTest()
        session.publish(unavailable(SessionFailure.SERVER_UNREACHABLE))
        compose.waitForIdle()
        compose.onNodeWithTag("setup-action-try_again").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-action-continue_anyway").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-action-continue_anyway").assertIsFocused()
    }

    @Test
    fun aPassedTestCentresStartWatching() {
        startTest()
        session.publish(ready())
        compose.waitForIdle()
        val start = compose.onNodeWithTag("setup-start-watching").assertIsFocused().fetchSemanticsNode().boundsInRoot
        assertEquals(480f, start.center.x, 1f)
    }

    @Test
    @Category(VisualCapture::class)
    fun captureWelcome() {
        show()
        wakeFocus()
        capture("en-font1.0-welcome", textBounds(R.string.onboarding_title), "Find my server")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureFindSearching() {
        show()
        press(Key.DirectionCenter)
        assertSearchIndicatorAtColumnEdge()
        capture("en-font1.0-find-searching", textBounds(R.string.onboarding_find_title), "Enter address manually")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureFindResults() {
        show()
        press(Key.DirectionCenter)
        discovery.value = discovering(LIVING_ROOM, OFFICE)
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.onboarding_find_on_network)).assertExists()
        compose.onNodeWithText(label(R.string.onboarding_find_still_searching)).assertExists()
        assertSearchIndicatorAtColumnEdge()
        assertEquals(ACTIONS_LEFT, compose.onNodeWithTag("setup-server-1").fetchSemanticsNode().boundsInRoot.left, .5f)
        compose.mainClock.advanceTimeBy(1_000) // the focused card at its full scale
        val focused = compose.onNodeWithTag("setup-server-0").assertIsFocused().fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("setup-server-1").fetchSemanticsNode().boundsInRoot
        assertTrue("Background between the focused card and the next: ${next.top - focused.bottom}", next.top - focused.bottom >= 8f)
        capture("en-font1.0-find-results", textBounds(R.string.onboarding_find_title), "Living room")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureFindNone() {
        discovery.value = TvheadendDiscoveryState.Unavailable(TvheadendDiscoveryFailure.START_FAILED)
        show()
        press(Key.DirectionCenter)
        capture("en-font1.0-find-none", textBounds(R.string.onboarding_find_none_title), "Enter address manually")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureAddress() {
        show(SetupStep.ADDRESS)
        wakeFocus()
        capture("en-font1.0-address", textBounds(R.string.onboarding_address_title), "blank server address")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureSignIn() {
        show(SetupStep.SIGN_IN, ConnectionFormState(host = HOST, username = USERNAME))
        press(Key.DirectionUp)
        press(Key.DirectionDown)
        capture("en-font1.0-sign-in", textBounds(R.string.onboarding_sign_in_body), "blank password")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureUnreachable() {
        startTest()
        session.publish(unavailable(SessionFailure.SERVER_UNREACHABLE))
        compose.waitForIdle()
        capture("en-font1.0-unreachable", textBounds(R.string.onboarding_continue_anyway), "Try again")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureWrongSignIn() {
        startTest()
        session.publish(unavailable(SessionFailure.AUTHENTICATION_REJECTED))
        compose.waitForIdle()
        capture("en-font1.0-wrong-sign-in", textBounds(R.string.onboarding_change_sign_in), "Change sign-in")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureEditSummary() {
        showEdit()
        wakeFocus()
        capture("en-font1.0-edit-summary", textBounds(R.string.edit_connection), "Server address")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureEditUnreachable() {
        showEdit()
        compose.onNodeWithTag("setup-test-and-save").requestFocus()
        press(Key.DirectionCenter)
        session.publish(unavailable(SessionFailure.SERVER_UNREACHABLE))
        compose.waitForIdle()
        capture("en-font1.0-edit-unreachable", textBounds(R.string.onboarding_save_anyway), "Try again")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureConnecting() {
        startTest()
        compose.onNodeWithTag("setup-connect").assertIsFocused().assertTextEquals(label(R.string.onboarding_connecting))
            .assert(hasNoClickAction())
        capture("en-font1.0-connecting", textBounds(R.string.onboarding_connecting_title, HOST), "Connecting (Back cancels)")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureSuccess() {
        startTest()
        session.publish(ready())
        compose.waitForIdle()
        compose.onNodeWithTag("setup-start-watching").assertIsFocused()
        capture("en-font1.0-success", textBounds(R.string.onboarding_done_title, HOST), "Start watching")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureAddressError() {
        show(SetupStep.ADDRESS)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        compose.onNodeWithText(label(R.string.onboarding_server_address_missing)).assertExists()
        capture("en-font1.0-address-error", textBounds(R.string.onboarding_address_title), "blank server address, with its error")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureSignInError() {
        show(SetupStep.SIGN_IN, ConnectionFormState(host = HOST, username = USERNAME))
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-connect").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertTrue("An incomplete sign-in tests nothing", session.calls.isEmpty())
        compose.onNodeWithTag("setup-password").assertIsFocused()
        capture("en-font1.0-sign-in-error", textBounds(R.string.onboarding_sign_in_body), "blank password, with its error")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureNotTvheadend() {
        startTest()
        session.publish(unavailable(SessionFailure.INCOMPATIBLE_SERVER))
        compose.waitForIdle()
        compose.onNodeWithTag("setup-action-change_address").assertIsFocused()
        capture("en-font1.0-not-tvheadend", textBounds(R.string.error_111_title), "Change address")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureVersionNotSupported() {
        startTest()
        session.publish(unavailable(SessionFailure.SERVER_UPGRADE_REQUIRED))
        compose.waitForIdle()
        compose.onNodeWithTag("setup-action-try_again").assertIsFocused()
        capture("en-font1.0-version-not-supported", textBounds(R.string.error_105_title), "Try again")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureNoNetwork() {
        startTest()
        session.publish(unavailable(SessionFailure.NETWORK_UNAVAILABLE))
        compose.waitForIdle()
        compose.onNodeWithTag("setup-action-try_again").assertIsFocused()
        compose.onNodeWithTag("setup-action-change_address").assertDoesNotExist()
        compose.onNodeWithTag("setup-action-continue_anyway").assertDoesNotExist()
        capture("en-font1.0-no-network", textBounds(R.string.error_112_title), "Try again")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureEditSummaryTestAndSaveUnfocused() {
        showEdit()
        wakeFocus()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-summary-find").assertIsFocused()
        compose.onNodeWithTag("setup-test-and-save").assertIsEnabled().assertIsNotFocused()
        capture("en-font1.0-edit-summary-test-unfocused", textBounds(R.string.edit_connection), "Find server; Test and save unfocused")
    }

    private fun showEdit() = show(
        SetupStep.SUMMARY,
        ConnectionFormState(host = HOST, username = USERNAME, password = PASSWORD),
        SetupMode.EDIT,
    )

    private fun startTest() {
        show(SetupStep.SIGN_IN, ConnectionFormState(host = HOST, username = USERNAME, password = PASSWORD))
        compose.onNodeWithTag("setup-connect").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
    }

    private fun show(
        step: SetupStep = SetupStep.WELCOME,
        form: ConnectionFormState = ConnectionFormState(),
        mode: SetupMode = SetupMode.FIRST_RUN,
    ) {
        compose.setContent {
            view = LocalView.current
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    // The production screen's surface, so text takes its content colour.
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        colors = SurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    ) {
                        state = remember {
                            ConnectionSetupState(form, session, NoProfiles, { discovery }, scope, mode, step = step)
                        }
                        ConnectionSetupFlow(state)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** A key press leaves touch mode, so a capture shows the focused element as a remote user sees it. */
    private fun wakeFocus() {
        press(Key.DirectionDown)
        press(Key.DirectionUp)
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun backHandled(): Boolean = compose.activity.onBackPressedDispatcher.hasEnabledCallbacks()

    private fun back() {
        assertTrue(backHandled())
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun unavailable(failure: SessionFailure) =
        SessionObservation.create(sessionState = SessionState.Unavailable(failure))

    private fun ready() = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
        ),
    )

    /** The search's spinner keeps one size and the actions column's edge, searching or beside results. */
    private fun assertSearchIndicatorAtColumnEdge() {
        val spinner = compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .fetchSemanticsNode().boundsInRoot
        assertEquals(22f, spinner.width, .5f)
        assertEquals(ACTIONS_LEFT, spinner.left, .5f)
    }

    private fun label(id: Int) = compose.activity.getString(id)
    private fun textBounds(id: Int, vararg args: Any) =
        compose.onNodeWithText(compose.activity.getString(id, *args), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun capture(name: String, title: Rect, focus: String) {
        compose.mainClock.advanceTimeBy(1_000) // settle the focus scale
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/onboarding-captures").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            File(directory, "$name.txt").writeText(
                "canvas=960x540dp; density=1; locale=en; fontScale=1.0; focus=$focus\n" +
                    "step=${state.step}; mode=${state.mode}\n" +
                    "title=$title\nproduction onboarding; fictional server; offline static composition only\n",
            )
            bitmap.recycle()
        }
    }
}

private object NoProfiles : ConnectionProfileEditor {
    override val serverSettings = emptyFlow<ServerSettings>()
    override suspend fun loadServerForEditing(applyAvailable: (String, Int, String, String) -> Unit) =
        error("No profile access in layout evidence")
    override suspend fun saveServer(host: String, htspPort: Int) = error("No profile access in layout evidence")
    override suspend fun savePasswordServer(
        host: String, htspPort: Int, username: String, password: String,
        credentialLease: CredentialEditLease,
    ) = error("No profile access in layout evidence")
    override suspend fun clearProfile() = error("No profile access in layout evidence")
}

/** The first field's label sits just under the 80dp top keyline; the outline follows it. */
/** The actions column's left edge, where unfocused rows start. */
private const val ACTIONS_LEFT = 526f
private const val TOP_FIELD_LABEL = 86f
private const val LABEL_HEIGHT = 24f
private val LIVING_ROOM = DiscoveredTvheadendServer("Living room", "192.0.2.10", 9982)
private val OFFICE = DiscoveredTvheadendServer("tvheadend", "192.0.2.20", 9982)
private const val HOST = "tvheadend.local"
private const val USERNAME = "alex"
private const val PASSWORD = "fake password"
