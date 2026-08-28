package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconResolverTest {
    private val currentSession = FakeSessionObservation(
        SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED,
                    dvrWrite = CapabilityAccess.ALLOWED,
                )
            ),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )
    ).captureCurrentSession()

    @Test
    fun positiveImagecacheSelector_isWrappedAsAuthenticatedArtwork() {
        assertEquals(
            AppArtworkSource(currentSession, "imagecache/123"),
            resolvePiconModel(currentSession, "default", "imagecache/123")
        )
        assertEquals(
            AppArtworkSource(currentSession, "imagecache/456"),
            resolvePiconModel(currentSession, "default", "/imagecache/456")
        )
    }

    @Test
    fun nonImagecacheAndMalformedSelectors_areRejected() {
        assertNull(resolvePiconModel(currentSession, "default", "/picon/foo.png"))
        assertNull(resolvePiconModel(currentSession, "default", "imagecache/0"))
        assertNull(resolvePiconModel(currentSession, "default", "imagecache/not-an-id"))
        assertNull(resolvePiconModel(currentSession, "default", "imagecache/1/extra"))
    }

    @Test
    fun rawHttpUrls_areNotFetched_overPureHtsp() {
        // Pure-HTSP client: raw remote URLs resolve to null (placeholder), never HTTP.
        assertNull(resolvePiconModel(currentSession, "default", "http://host/icon.png"))
        assertNull(resolvePiconModel(currentSession, "default", "https://host/icon.png"))
        assertNull(resolvePiconModel(currentSession, "default", "HTTPS://HOST/icon.png"))
    }

    @Test
    fun blankInputs_resolveToNull() {
        assertNull(resolvePiconModel(currentSession, "default", null))
        assertNull(resolvePiconModel(currentSession, "default", ""))
        assertNull(resolvePiconModel(currentSession, "default", "   "))
        assertNull(resolvePiconModel(currentSession, "", "imagecache/1"))
    }
}
