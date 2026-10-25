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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
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
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupStep
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

    @Test
    fun welcomeKeepsItsKeylineAndLeavesBackToTheActivity() {
        show()
        assertEquals(94f, textBounds(R.string.onboarding_title).left, .1f)
        compose.onNodeWithText(label(R.string.onboarding_enter_address)).assertIsFocused()
        assertFalse("Back on the welcome leaves for the TV home", backHandled())
    }

    @Test
    fun addressWalksFieldsWithTheDpadAndBackReturnsToTheWelcomeChoice() {
        show()
        press(Key.DirectionCenter)
        assertEquals(SetupStep.ADDRESS, state.step)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        assertEquals(TOP_FIELD_LABEL, textBounds(R.string.onboarding_server_address).top, 12f)

        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-port").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-host").assertIsFocused()
        compose.onNodeWithText(label(R.string.onboarding_server_address_missing)).assertExists()

        back()
        assertEquals(SetupStep.WELCOME, state.step)
        compose.onNodeWithText(label(R.string.onboarding_enter_address)).assertIsFocused()
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
        capture("en-font1.0-welcome", textBounds(R.string.onboarding_title), "Enter address manually")
    }

    @Test
    @Category(VisualCapture::class)
    fun captureAddress() {
        show(SetupStep.ADDRESS)
        capture("en-font1.0-address", textBounds(R.string.onboarding_address_title), "blank server address")
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

    private fun startTest() {
        show(SetupStep.SIGN_IN, ConnectionFormState(host = HOST, username = USERNAME, password = PASSWORD))
        compose.onNodeWithTag("setup-connect").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
    }

    private fun show(step: SetupStep = SetupStep.WELCOME, form: ConnectionFormState = ConnectionFormState()) {
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
                            ConnectionSetupState(form, session, NoProfiles, scope, step)
                        }
                        ConnectionSetupFlow(state)
                    }
                }
            }
        }
        compose.waitForIdle()
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

    private fun label(id: Int) = compose.activity.getString(id)
    private fun textBounds(id: Int) = compose.onNodeWithText(label(id), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

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

/** The first field's label sits just under the 80dp top keyline. */
private const val TOP_FIELD_LABEL = 86f
private const val HOST = "tvheadend.local"
private const val USERNAME = "alex"
private const val PASSWORD = "fake password"
