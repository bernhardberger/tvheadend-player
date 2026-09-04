package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSearchRequest
import at.bernhardberger.tvheadend.sdk.core.EpgSearchResult
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EpgSearchActionsTest {
    @Test
    fun dispatchesReleasedTypedFullTextRequestForActiveTag() = runTest {
        val capability = currentSession()
        val tagId = ChannelTagId(12)
        val expected = EpgSearchResult.Available.create(
            events = emptyList(),
            originatingSession = capability,
        )
        var dispatchedCapability: CurrentSessionObservation? = null
        var dispatchedRequest: EpgSearchRequest? = null
        val actions = EpgSearchActions { currentSession, request ->
            dispatchedCapability = currentSession
            dispatchedRequest = request
            expected
        }

        val actual = actions.execute(
            currentSession = capability,
            query = "  Evening News  ",
            tagId = tagId,
        )

        assertSame(expected, actual)
        assertSame(capability, dispatchedCapability)
        assertEquals("Evening News", dispatchedRequest?.query)
        assertEquals(true, dispatchedRequest?.fullText)
        assertEquals(tagId, dispatchedRequest?.tagId)
    }

    private fun currentSession(): CurrentSessionObservation = FakeSessionObservation(
        SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED,
                    dvrWrite = CapabilityAccess.ALLOWED,
                ),
            ),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        ),
    ).captureCurrentSession()
}
