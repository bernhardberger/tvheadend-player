package at.bernhardberger.tvhplayer.ui.screens.settings

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.settings.ServerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsConnectionSecureSurfaceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun clearSecureFlag() {
        composeRule.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Test
    fun surfaceAddsAndClearsItsSecureFlagForItsLifetime() {
        var showConnection by mutableStateOf(true)
        composeRule.setContent {
            if (showConnection) {
                SettingsConnection(
                    initialFocusRequester = remember { FocusRequester() },
                    settingsStore = FakeConnectionProfileEditor(),
                )
            }
        }

        composeRule.runOnIdle {
            assertTrue(composeRule.activity.hasSecureFlag())
            showConnection = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertFalse(composeRule.activity.hasSecureFlag()) }
    }

    @Test
    fun disposalPreservesSecureFlagOwnedByAnotherSurface() {
        composeRule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        var showConnection by mutableStateOf(true)
        composeRule.setContent {
            if (showConnection) {
                SettingsConnection(
                    initialFocusRequester = remember { FocusRequester() },
                    settingsStore = FakeConnectionProfileEditor(),
                )
            }
        }

        composeRule.runOnIdle {
            assertTrue(composeRule.activity.hasSecureFlag())
            showConnection = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(composeRule.activity.hasSecureFlag()) }
    }

    @Test
    fun secureFlagRemainsUntilTheFinalOverlappingLeaseIsReleased() {
        val first = ConnectionSecureWindow.acquire(composeRule.activity.window)
        val second = ConnectionSecureWindow.acquire(composeRule.activity.window)

        assertTrue(composeRule.activity.hasSecureFlag())
        first.release()
        first.release()
        assertTrue(composeRule.activity.hasSecureFlag())

        second.release()
        assertFalse(composeRule.activity.hasSecureFlag())
    }

    @Test
    fun saveSubmitsTheSharedFormThroughTheProfileEditor() {
        val editor = FakeConnectionProfileEditor()
        val form = ConnectionFormState()
        composeRule.setContent {
            SettingsConnection(
                initialFocusRequester = remember { FocusRequester() },
                settingsStore = editor,
                form = form,
            )
        }
        composeRule.waitUntil { form.host == FAKE_HOST && form.password == FAKE_PASSWORD }

        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntil { editor.passwordSaveCount == 1 }
        composeRule.runOnIdle {
            assertEquals(FAKE_HOST, editor.savedHost)
            assertEquals(FAKE_PORT, editor.savedPort)
            assertEquals(FAKE_USERNAME, editor.savedUsername)
            assertEquals(ConnectionFormFeedback.SAVED, form.feedback)
            assertEquals(FAKE_PASSWORD, form.password)
        }
    }

    private fun ComponentActivity.hasSecureFlag(): Boolean =
        window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
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

private const val FAKE_HOST = "edit.invalid"
private const val FAKE_PORT = 4_242
private const val FAKE_USERNAME = "edit-user"
private const val FAKE_PASSWORD = "fake password"
