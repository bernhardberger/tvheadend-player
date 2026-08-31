package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun introductionContinueMovesToConnectionStep() {
        var step by mutableStateOf(OnboardingStep.INTRODUCTION)

        composeRule.setContent {
            TVHeadendPlayerTheme {
                if (step == OnboardingStep.INTRODUCTION) {
                    OnboardingIntroduction { step = OnboardingStep.CONNECTION }
                } else {
                    androidx.tv.material3.Text("Connection step")
                }
            }
        }

        composeRule.onNodeWithText("Set up TVHeadend").performClick()
        composeRule.onNodeWithText("Connection step").assertIsDisplayed()
    }

    @Test
    fun connectionSubmissionUsesSharedFormAndZerosThePassword() {
        val editor = FakeOnboardingConnectionProfileEditor()
        val form = ConnectionFormState(
            host = " $FAKE_HOST ",
            port = FAKE_PORT.toString(),
            username = FAKE_USERNAME,
            password = FAKE_PASSWORD,
        )

        composeRule.setContent {
            TVHeadendPlayerTheme {
                OnboardingConnection(
                    settingsStore = editor,
                    onBack = {},
                    form = form,
                )
            }
        }

        composeRule.onNodeWithText("Save and continue").performClick()
        composeRule.waitUntil { editor.passwordSaveCount == 1 }
        composeRule.runOnIdle {
            assertEquals(FAKE_HOST, editor.savedHost)
            assertEquals(FAKE_PORT, editor.savedPort)
            assertEquals(FAKE_USERNAME, editor.savedUsername)
            assertEquals(ConnectionFormFeedback.SAVED, form.feedback)
            assertEquals("", form.password)
        }
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
