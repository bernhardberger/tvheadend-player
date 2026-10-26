package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.ServerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsConnectionCredentialTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aPassedTestSavesTheSharedFormThroughTheProfileEditor() {
        val editor = FakeConnectionProfileEditor()
        val session = FakeTvheadendSession()
        val form = ConnectionFormState()
        var saved = 0
        composeRule.setContent {
            SettingsConnection(
                initialFocusRequester = remember { FocusRequester() },
                settingsStore = editor,
                session = session,
                restoreSaved = {},
                form = form,
                onSaved = { saved += 1 },
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(FAKE_HOST, form.host)
            assertEquals("", form.password)
            assertTrue(form.hasSavedPassword)
        }

        composeRule.onNode(hasTestTag("setup-continue"))
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNode(hasTestTag("setup-continue"))
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle {
            session.publish(
                SessionObservation.create(
                    sessionState = SessionState.Ready(
                        ServerCapabilities.create(
                            streaming = CapabilityAccess.ALLOWED,
                            dvrWrite = CapabilityAccess.ALLOWED,
                        ),
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasTestTag("setup-start-watching"))
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle {
            assertEquals(1, editor.passwordSaveCount)
            assertEquals(FAKE_HOST, editor.savedHost)
            assertEquals(FAKE_PORT, editor.savedPort)
            assertEquals(FAKE_USERNAME, editor.savedUsername)
            assertEquals(ConnectionFormFeedback.SAVED, form.feedback)
            assertEquals(1, saved)
        }
    }
}

private class FakeConnectionProfileEditor : ConnectionProfileEditor {
    override val serverSettings: Flow<ServerSettings> = MutableStateFlow(
        ServerSettings(FAKE_HOST, FAKE_PORT, FAKE_USERNAME, passwordConfigured = true),
    )
    var passwordSaveCount = 0
    var savedHost: String? = null
    var savedPort: Int? = null
    var savedUsername: String? = null

    override suspend fun loadServerForEditing(
        applyAvailable: (host: String, port: Int, username: String, password: String) -> Unit,
    ) {
        applyAvailable(FAKE_HOST, FAKE_PORT, FAKE_USERNAME, FAKE_PASSWORD)
    }

    override suspend fun saveServer(host: String, htspPort: Int) = Unit

    override suspend fun savePasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
    ) {
        savedHost = host
        savedPort = htspPort
        savedUsername = username
        if (password == FAKE_PASSWORD) passwordSaveCount += 1
    }

    override suspend fun clearProfile() = Unit
}

private const val FAKE_HOST = "edit.invalid"
private const val FAKE_PORT = 4_242
private const val FAKE_USERNAME = "edit-user"
private const val FAKE_PASSWORD = "fake password"
