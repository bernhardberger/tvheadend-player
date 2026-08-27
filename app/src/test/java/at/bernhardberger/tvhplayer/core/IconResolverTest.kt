package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconResolverTest {

    @Test
    fun positiveImagecacheSelector_isWrappedAsAuthenticatedArtwork() {
        assertEquals(
            AppArtworkSource("imagecache/123"),
            resolvePiconModel("default", "imagecache/123")
        )
        assertEquals(
            AppArtworkSource("imagecache/456"),
            resolvePiconModel("default", "/imagecache/456")
        )
    }

    @Test
    fun nonImagecacheAndMalformedSelectors_areRejected() {
        assertNull(resolvePiconModel("default", "/picon/foo.png"))
        assertNull(resolvePiconModel("default", "imagecache/0"))
        assertNull(resolvePiconModel("default", "imagecache/not-an-id"))
        assertNull(resolvePiconModel("default", "imagecache/1/extra"))
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
