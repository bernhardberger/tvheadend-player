package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.onboarding.ConnectionSetupFlow
import at.bernhardberger.tvhplayer.ui.screens.onboarding.ConnectionSetupState
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val session = FakeTvheadendSession()

    @Test
    fun welcomeChoiceOpensTheAddressOnTheServerField() {
        show(SetupStep.WELCOME, ConnectionFormState())

        composeRule.onNodeWithTag("setup-find-server").assertIsFocused()
        press(Key.DirectionDown)
        composeRule.onNodeWithTag("setup-enter-address").assertIsFocused()
        press(Key.DirectionCenter)
        composeRule.onNodeWithTag("setup-host").assertIsFocused()
    }

    @Test
    fun aSearchWithoutServersOffersTheAddressFirst() {
        show(SetupStep.WELCOME, ConnectionFormState())

        press(Key.DirectionCenter)
        composeRule.onNodeWithText("No servers found").assertExists()
        composeRule.onNodeWithTag("setup-enter-address").assertIsFocused()
        press(Key.DirectionDown)
        composeRule.onNodeWithTag("setup-search-again").assertIsFocused()
    }

    @Test
    fun startWatchingSavesTheTestedProfileAndZerosThePassword() {
        val editor = FakeOnboardingConnectionProfileEditor()
        val form = ConnectionFormState(
            host = " $FAKE_HOST ",
            port = FAKE_PORT.toString(),
            username = FAKE_USERNAME,
            password = FAKE_PASSWORD,
        )
        show(SetupStep.SIGN_IN, form, editor)

        composeRule.onNodeWithTag("setup-connect").assertIsFocused()
        press(Key.DirectionCenter)
        composeRule.runOnIdle {
            assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
            assertEquals("Nothing is saved before the test passes", 0, editor.passwordSaveCount)
        }
        session.publish(
            SessionObservation.create(
                sessionState = SessionState.Ready(
                    ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
                ),
            ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setup-start-watching").assertIsFocused()
        press(Key.DirectionCenter)

        composeRule.runOnIdle {
            assertEquals(1, editor.passwordSaveCount)
            assertEquals(FAKE_HOST, editor.savedHost)
            assertEquals(FAKE_PORT, editor.savedPort)
            assertEquals(FAKE_USERNAME, editor.savedUsername)
            assertEquals(ConnectionFormFeedback.SAVED, form.feedback)
            assertEquals("", form.password)
        }
    }

    private fun show(
        step: SetupStep,
        form: ConnectionFormState,
        editor: ConnectionProfileEditor = FakeOnboardingConnectionProfileEditor(),
    ) {
        composeRule.setContent {
            val scope = rememberCoroutineScope()
            TVHeadendPlayerTheme {
                // A search that ends at once with nothing, as on a network where no server announces itself.
                ConnectionSetupFlow(remember { ConnectionSetupState(form, session, editor, { emptyFlow() }, scope, step = step) })
            }
        }
        composeRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }
}

private class FakeOnboardingConnectionProfileEditor : ConnectionProfileEditor {
    override val serverSettings: Flow<ServerSettings> = MutableStateFlow(ServerSettings())
    var passwordSaveCount = 0
    var savedHost: String? = null
    var savedPort: Int? = null
    var savedUsername: String? = null

    override suspend fun loadServerForEditing(
        applyAvailable: (host: String, port: Int, username: String, password: String) -> Unit,
    ) = Unit

    override suspend fun saveServer(host: String, htspPort: Int) = Unit

    override suspend fun savePasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
        credentialLease: CredentialEditLease,
    ) {
        savedHost = host
        savedPort = htspPort
        savedUsername = username
        if (password == FAKE_PASSWORD) passwordSaveCount += 1
        credentialLease.release()
    }

    override suspend fun clearProfile() = Unit
}

private const val FAKE_HOST = "onboarding.invalid"
private const val FAKE_PORT = 4_242
private const val FAKE_USERNAME = "onboarding-user"
private const val FAKE_PASSWORD = "fake onboarding password"
