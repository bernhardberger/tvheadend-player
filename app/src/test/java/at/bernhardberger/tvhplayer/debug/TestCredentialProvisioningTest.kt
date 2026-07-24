package at.bernhardberger.tvhplayer.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestCredentialProvisioningTest {
    @Test
    fun validFields_areAccepted() {
        assertEquals(
            TestCredentialPayload(
                host = "tvh.test",
                htspPort = 9982,
                username = "operator",
                password = "secret",
                autoConnect = true,
            ),
            validateTestCredentialPayload(
                host = "tvh.test",
                htspPort = 9982,
                username = "operator",
                password = "secret",
                autoConnect = true,
            ),
        )
    }

    @Test
    fun malformedFields_areRejected() {
        assertNull(validateTestCredentialPayload("", 9982, "operator", "secret", true))
        assertNull(validateTestCredentialPayload("tvh.test", 0, "operator", "secret", true))
        assertNull(validateTestCredentialPayload("tvh.test", 9982, "", "secret", true))
        assertNull(validateTestCredentialPayload("tvh.test", 9982, "operator", "", true))
        assertNull(validateTestCredentialPayload("tvh.test", 9982, "operator", "secret", null))
    }
}
