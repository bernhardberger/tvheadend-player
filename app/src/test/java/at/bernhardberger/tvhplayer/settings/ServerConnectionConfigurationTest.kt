package at.bernhardberger.tvhplayer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionConfigurationTest {
    @Test
    fun equalityUsesEveryConnectionValueWhileDiagnosticsRedactSensitiveValues() {
        val configuration = configuration()

        assertEquals(configuration, configuration())
        assertEquals(configuration.hashCode(), configuration().hashCode())
        assertNotEquals(configuration, configuration(host = "other.example.invalid"))
        assertNotEquals(configuration, configuration(htspPort = 9983))
        assertNotEquals(configuration, configuration(username = "other-user"))
        assertNotEquals(configuration, configuration(password = "other-password"))

        val diagnostic = configuration.toString()
        assertFalse(diagnostic.contains("tvheadend.example.invalid"))
        assertFalse(diagnostic.contains("test-user"))
        assertFalse(diagnostic.contains("test-password"))
        assertTrue(diagnostic.contains("htspPort=9982"))
        assertTrue(diagnostic.contains("authenticationMode=PASSWORD"))
    }

    @Test
    fun presentationKeepsEndpointReadableWithoutClaimingAStoredUsername() {
        assertEquals(
            ServerConnectionPresentation(
                endpoint = "tvheadend.example.invalid:9982",
                authenticationMode = ServerAuthenticationMode.ANONYMOUS,
            ),
            serverConnectionPresentation(
                host = " tvheadend.example.invalid ",
                htspPort = 9982,
                passwordConfigured = false,
            ),
        )
        assertEquals(
            ServerConnectionPresentation(
                endpoint = "[2001:db8::10]:9982",
                authenticationMode = ServerAuthenticationMode.PASSWORD,
            ),
            serverConnectionPresentation(
                host = "2001:db8::10",
                htspPort = 9982,
                passwordConfigured = true,
            ),
        )
    }

    @Test
    fun passwordProfileRequiresRealCredentialReentryInsteadOfSubmittingARedactionMarker() {
        val editable = serverSettingsForEditing(
            host = "tvheadend.example.invalid",
            htspPort = 9982,
            passwordConfigured = true,
        )

        assertEquals("", editable.username)
        assertTrue(editable.passwordConfigured)
        assertFalse(editable.username.contains("•"))
        assertFalse(
            replacementCredentialsComplete(
                passwordConfigured = true,
                username = "",
                password = "",
                passwordChanged = false,
            ),
        )
        assertTrue(
            replacementCredentialsComplete(
                passwordConfigured = true,
                username = "viewer",
                password = "replacement",
                passwordChanged = true,
            ),
        )
    }

    @Test
    fun configuredProfilePresentsRedactedMarkersWithoutMakingThemEditableValues() {
        val marker = "Configured"

        assertEquals(marker, configuredCredentialPresentation("", true, marker))
        assertNull(configuredCredentialPresentation("viewer", true, marker))
        assertNull(configuredCredentialPresentation("", false, marker))
        assertFalse(
            replacementCredentialsComplete(
                passwordConfigured = true,
                username = "",
                password = "",
                passwordChanged = false,
            ),
        )
    }

    private fun configuration(
        host: String = "tvheadend.example.invalid",
        htspPort: Int = 9982,
        username: String = "test-user",
        password: String = "test-password",
    ) = ServerConnectionConfiguration(host, htspPort, username, password)
}
