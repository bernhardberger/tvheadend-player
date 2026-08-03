package at.bernhardberger.tvhplayer.viewmodels

import at.bernhardberger.tvheadend.client.TvheadendConnection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConnectionReplacementPolicyTest {
    @Test
    fun equalValuesDoNotReconnectWhileAnyFieldChangeDoes() {
        val current = connection()
        val sameValues = connection()

        assertNotSame(current, sameValues)
        assertFalse(connectionRequiresReplacement(current, sameValues))

        listOf(
            connection(host = "other.example.invalid"),
            connection(port = 9983),
            connection(username = "other-user"),
            connection(password = "other-password"),
        ).forEach { changed ->
            assertTrue(connectionRequiresReplacement(current, changed))
        }
        assertTrue(connectionRequiresReplacement(previous = null, candidate = sameValues))
    }

    private fun connection(
        host: String = "tvheadend.example.invalid",
        port: Int = 9982,
        username: String = "test-user",
        password: String = "test-password",
    ): TvheadendConnection = TvheadendConnection(
        host = host,
        port = port,
        username = username,
        password = password,
    )
}
