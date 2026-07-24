package at.bernhardberger.tvhplayer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StoredPasswordTest {
    @Test
    fun missingValueIsEmpty() {
        assertSame(
            StoredPassword.Empty,
            decodeStoredPassword(encoded = null, decrypt = { error("must not decrypt") }),
        )
    }

    @Test
    fun validValueIsAvailable() {
        assertEquals(
            StoredPassword.Available("secret"),
            decodeStoredPassword(encoded = "encrypted", decrypt = { "secret" }),
        )
    }

    @Test
    fun decryptionFailureIsUnavailable() {
        assertSame(
            StoredPassword.Unavailable,
            decodeStoredPassword(encoded = "damaged", decrypt = { error("invalid key") }),
        )
    }
}
