package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconResolverTest {

    @Test
    fun serverRelativePath_isWrappedAsHtspPicon() {
        assertEquals(
            "htsp-picon://default/imagecache/123",
            resolvePiconModel("default", "imagecache/123")
        )
        // Leading slash is normalised away.
        assertEquals(
            "htsp-picon://default/picon/foo.png",
            resolvePiconModel("default", "/picon/foo.png")
        )
    }

    @Test
    fun rawHttpUrls_areNotFetched_overPureHtsp() {
        // Pure-HTSP client: raw remote URLs resolve to null (placeholder), never HTTP.
        assertNull(resolvePiconModel("default", "http://host/icon.png"))
        assertNull(resolvePiconModel("default", "https://host/icon.png"))
        assertNull(resolvePiconModel("default", "HTTPS://HOST/icon.png"))
    }

    @Test
    fun blankInputs_resolveToNull() {
        assertNull(resolvePiconModel("default", null))
        assertNull(resolvePiconModel("default", ""))
        assertNull(resolvePiconModel("default", "   "))
        assertNull(resolvePiconModel("", "imagecache/1"))
    }
}
