package at.bernhardberger.tvhplayer.ui.screens.settings

import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.settings.ServerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionEditCredentialsTest {
    @Test
    fun fakeCredentialsPrefillEditSaveAndClearWithinTheEditorLifetime() = runTest {
        val editor = FakeConnectionProfileEditor()
        val credentials = ConnectionEditCredentials()
        var leaseReleases = 0

        credentials.loadFrom(editor) { _, _ -> }

        assertEquals(FAKE_USERNAME, credentials.username)
        assertEquals(FAKE_PASSWORD, credentials.password)

        credentials.updateUsername(EDITED_USERNAME)
        credentials.updatePassword(EDITED_PASSWORD)
        credentials.saveTo(editor, EDITED_HOST, EDITED_PORT) {
            CredentialEditLease { leaseReleases += 1 }
        }

        assertEquals(
            SavedPassword(EDITED_HOST, EDITED_PORT, EDITED_USERNAME, EDITED_PASSWORD),
            editor.savedPassword,
        )
        assertEquals(1, leaseReleases)

        credentials.clear()

        assertEquals("", credentials.username)
        assertEquals("", credentials.password)
    }

    @Test
    fun anonymousProfilePrefillsEndpointWithoutAcquiringCredentialLease() = runTest {
        val editor = FakeConnectionProfileEditor(username = "", password = "")
        val credentials = ConnectionEditCredentials()
        var endpoint: Pair<String, Int>? = null
        var leaseAcquisitions = 0

        credentials.loadFrom(editor) { host, port -> endpoint = host to port }
        credentials.saveTo(editor, FAKE_HOST, FAKE_PORT) {
            leaseAcquisitions += 1
            CredentialEditLease {}
        }

        assertEquals(FAKE_HOST to FAKE_PORT, endpoint)
        assertEquals("", credentials.username)
        assertEquals("", credentials.password)
        assertEquals(FAKE_HOST to FAKE_PORT, editor.savedAnonymous)
        assertEquals(0, leaseAcquisitions)
    }

    @Test
    fun unavailableProfileLeavesTheEditorEmpty() = runTest {
        val credentials = ConnectionEditCredentials()
        var endpointApplied = false

        credentials.loadFrom(FakeConnectionProfileEditor(available = false)) { _, _ ->
            endpointApplied = true
        }

        assertEquals(false, endpointApplied)
        assertEquals("", credentials.username)
        assertEquals("", credentials.password)
    }
}

private class FakeConnectionProfileEditor(
    private val username: String = FAKE_USERNAME,
    private val password: String = FAKE_PASSWORD,
    private val available: Boolean = true,
) : ConnectionProfileEditor {
    override val serverSettings: Flow<ServerSettings> = MutableStateFlow(ServerSettings())
    var savedPassword: SavedPassword? = null
    var savedAnonymous: Pair<String, Int>? = null

    override suspend fun loadServerForEditing(
        applyAvailable: (host: String, port: Int, username: String, password: String) -> Unit,
    ) {
        if (available) applyAvailable(FAKE_HOST, FAKE_PORT, username, password)
    }

    override suspend fun saveServer(host: String, htspPort: Int) {
        savedAnonymous = host to htspPort
    }

    override suspend fun savePasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
        credentialLease: CredentialEditLease,
    ) {
        savedPassword = SavedPassword(host, htspPort, username, password)
        credentialLease.release()
    }

    override suspend fun clearProfile() = Unit
}

private data class SavedPassword(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
)

private const val FAKE_HOST = "edit.invalid"
private const val FAKE_PORT = 4_242
private const val FAKE_USERNAME = "edit-user"
private const val FAKE_PASSWORD = "fake password"
private const val EDITED_HOST = "edited.invalid"
private const val EDITED_PORT = 9_982
private const val EDITED_USERNAME = "edited-user"
private const val EDITED_PASSWORD = "edited fake password"
