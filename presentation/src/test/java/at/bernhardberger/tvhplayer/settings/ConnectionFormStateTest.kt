package at.bernhardberger.tvhplayer.settings

import androidx.compose.runtime.saveable.SaverScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionFormStateTest {
    @Test
    fun endpointAndCredentialValidationUsesOneFormPolicy() {
        val form = ConnectionFormState()

        assertFalse(form.canSubmit)

        form.updateHost(" tvheadend.invalid ")
        assertTrue(form.canSubmit)

        form.updatePort("0")
        assertFalse(form.canSubmit)
        form.updatePort("65536")
        assertFalse(form.canSubmit)
        form.updatePort("not-a-port")
        assertFalse(form.canSubmit)

        form.updatePort("9982")
        form.updateUsername(FAKE_USERNAME)
        assertFalse(form.canSubmit)
        form.updatePassword(FAKE_PASSWORD)
        assertTrue(form.canSubmit)
        form.updateUsername("")
        assertFalse(form.canSubmit)
        form.updatePassword("")
        assertTrue(form.canSubmit)
    }

    @Test
    fun serverProfileFollowsTheSameValidationAndSignInChoice() {
        val form = ConnectionFormState(port = "9982")
        assertTrue(form.hostMissing)
        assertEquals(null, form.serverProfile())

        // The SDK profile is opaque; the session tests cover what it carries.
        form.updateHost(" $FAKE_HOST ")
        assertTrue(form.serverProfile() != null)

        form.updateUsername(FAKE_USERNAME)
        assertTrue(form.credentialsIncomplete)
        assertEquals(null, form.serverProfile())
        form.updatePassword("   ")
        assertEquals("A blank password is no sign-in", null, form.serverProfile())
        form.updatePassword(FAKE_PASSWORD)
        assertTrue(form.serverProfile() != null)

        form.updatePort("70000")
        assertTrue(form.portInvalid)
        assertEquals(null, form.serverProfile())
    }

    @Test
    fun savedFormStateContainsOnlyTheEndpoint() {
        val form = ConnectionFormState(
            host = FAKE_HOST,
            port = FAKE_PORT.toString(),
            username = FAKE_USERNAME,
            password = FAKE_PASSWORD,
        )

        val saved = with(ConnectionFormStateSaver) {
            SaveAllScope.save(form)
        }
        val restored = ConnectionFormStateSaver.restore(requireNotNull(saved))

        assertEquals(listOf(FAKE_HOST, FAKE_PORT.toString()), saved)
        assertEquals(FAKE_HOST, restored?.host)
        assertEquals(FAKE_PORT.toString(), restored?.port)
        assertEquals("", restored?.username)
        assertEquals("", restored?.password)
        assertEquals(null, restored?.feedback)
    }

    @Test
    fun editableProfileLoadsSubmitsAndClearsWithinTheFormLifetime() = runTest {
        val editor = FakeConnectionProfileEditor()
        val form = ConnectionFormState()

        form.loadFrom(editor)

        assertEquals(FAKE_HOST, form.host)
        assertEquals(FAKE_PORT.toString(), form.port)
        assertEquals(FAKE_USERNAME, form.username)
        assertEquals("", form.password)
        assertTrue(form.hasSavedPassword)

        form.updateHost(" $EDITED_HOST ")
        form.updatePort(EDITED_PORT.toString())
        form.updateUsername(EDITED_USERNAME)
        form.updatePassword(EDITED_PASSWORD)

        assertEquals(
            ConnectionFormFeedback.SAVED,
            form.submit(editor),
        )
        assertEquals(
            SavedPassword(EDITED_HOST, EDITED_PORT, EDITED_USERNAME, EDITED_PASSWORD),
            editor.savedPassword,
        )

        form.clearCredentials()

        assertEquals("", form.username)
        assertEquals("", form.password)
        assertFalse(form.toString().contains(EDITED_PASSWORD))
    }

    @Test
    fun savedPasswordIsRetainedForSubmitButNeverBecomesFieldText() = runTest {
        val editor = FakeConnectionProfileEditor()
        val form = ConnectionFormState()
        form.loadFrom(editor)
        assertEquals("", form.password)
        assertTrue(form.canSubmit)
        assertTrue(form.hasSavedPassword)
        assertEquals(ConnectionFormFeedback.SAVED, form.submit(editor))
        assertEquals(SavedPassword(FAKE_HOST, FAKE_PORT, FAKE_USERNAME, FAKE_PASSWORD), editor.savedPassword)

        form.updatePassword(EDITED_PASSWORD)
        assertFalse(form.hasSavedPassword)
        form.clearPassword()
        assertFalse(form.hasSavedPassword)
        assertTrue(form.credentialsIncomplete)
    }

    @Test
    fun deletingAReplacementRestoresTheSavedPasswordUntilExplicitlyCleared() = runTest {
        val editor = FakeConnectionProfileEditor()
        val form = ConnectionFormState()
        form.loadFrom(editor)
        form.updatePassword(EDITED_PASSWORD)
        form.updatePassword("")
        assertTrue(form.hasSavedPassword)
        assertEquals("", form.password)
        assertEquals(ConnectionFormFeedback.SAVED, form.submit(editor))
        assertEquals(SavedPassword(FAKE_HOST, FAKE_PORT, FAKE_USERNAME, FAKE_PASSWORD), editor.savedPassword)
        form.clearPassword()
        assertFalse(form.hasSavedPassword)
        assertTrue(form.credentialsIncomplete)
    }

    @Test
    fun anonymousProfilePrefillsEndpointAndSavesWithoutSignIn() = runTest {
        val editor = FakeConnectionProfileEditor(username = "", password = "")
        val form = ConnectionFormState()

        form.loadFrom(editor)
        val feedback = form.submit(editor)

        assertEquals(ConnectionFormFeedback.SAVED, feedback)
        assertEquals(FAKE_HOST, form.host)
        assertEquals(FAKE_PORT.toString(), form.port)
        assertEquals("", form.username)
        assertEquals("", form.password)
        assertEquals(FAKE_HOST to FAKE_PORT, editor.savedAnonymous)
    }

    @Test
    fun invalidSubmissionDoesNotReachTheProfileEditor() = runTest {
        val editor = FakeConnectionProfileEditor()
        val form = ConnectionFormState()

        val feedback = form.submit(editor)

        assertEquals(ConnectionFormFeedback.INVALID, feedback)
        assertEquals(null, editor.savedAnonymous)
        assertEquals(null, editor.savedPassword)
    }

    @Test
    fun failedSubmissionReturnsTypedFeedbackWithoutClearingCredentials() = runTest {
        val editor = FakeConnectionProfileEditor(failOnSave = true)
        val form = ConnectionFormState()

        form.loadFrom(editor)

        assertEquals(
            ConnectionFormFeedback.SAVE_FAILED,
            form.submit(editor),
        )
        assertEquals(ConnectionFormFeedback.SAVE_FAILED, form.feedback)
        assertEquals(FAKE_USERNAME, form.username)
        assertEquals("", form.password)
        assertTrue(form.hasSavedPassword)
    }

    @Test
    fun unavailableProfileLeavesTheFormEmpty() = runTest {
        val form = ConnectionFormState()

        form.loadFrom(FakeConnectionProfileEditor(available = false))

        assertEquals("", form.host)
        assertEquals("9982", form.port)
        assertEquals("", form.username)
        assertEquals("", form.password)
    }
}

private class FakeConnectionProfileEditor(
    private val username: String = FAKE_USERNAME,
    private val password: String = FAKE_PASSWORD,
    private val available: Boolean = true,
    private val failOnSave: Boolean = false,
    serverSettings: ServerSettings = ServerSettings(),
) : ConnectionProfileEditor {
    override val serverSettings: Flow<ServerSettings> = MutableStateFlow(serverSettings)
    var savedPassword: SavedPassword? = null
    var savedAnonymous: Pair<String, Int>? = null

    override suspend fun loadServerForEditing(
        applyAvailable: (host: String, port: Int, username: String, password: String) -> Unit,
    ) {
        if (available) applyAvailable(FAKE_HOST, FAKE_PORT, username, password)
    }

    override suspend fun saveServer(host: String, htspPort: Int) {
        if (failOnSave) error("fake save failure")
        savedAnonymous = host to htspPort
    }

    override suspend fun savePasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
    ) {
        if (failOnSave) error("fake save failure")
        savedPassword = SavedPassword(host, htspPort, username, password)
    }

    override suspend fun clearProfile() = Unit
}

private data class SavedPassword(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
)

private object SaveAllScope : SaverScope {
    override fun canBeSaved(value: Any): Boolean = true
}

private const val FAKE_HOST = "edit.invalid"
private const val FAKE_PORT = 4_242
private const val FAKE_USERNAME = "edit-user"
private const val FAKE_PASSWORD = "fake password"
private const val EDITED_HOST = "edited.invalid"
private const val EDITED_PORT = 9_982
private const val EDITED_USERNAME = "edited-user"
private const val EDITED_PASSWORD = "edited fake password"
