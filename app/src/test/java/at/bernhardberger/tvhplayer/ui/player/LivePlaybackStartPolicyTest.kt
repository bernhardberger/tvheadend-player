package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.resolveLivePlaybackSelection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LivePlaybackStartPolicyTest {
    @Test
    fun initialPlaybackIsResolvedOnlyAfterTargetRequestCompletes() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val requestResult = CompletableDeferred<PlaybackTargetResult>()
        var resolvedResult: PlaybackTargetResult? = null

        val start = launch {
            startInitialLivePlayback(
                startPlayback = {
                    requestStarted.complete(Unit)
                    requestResult.await()
                },
                onResolved = { resolvedResult = it },
            )
        }
        requestStarted.await()
        runCurrent()

        assertFalse(start.isCompleted)
        assertEquals(null, resolvedResult)
        requestResult.complete(PlaybackTargetResult.STARTED)
        start.join()
        assertEquals(PlaybackTargetResult.STARTED, resolvedResult)
    }

    @Test
    fun retainedRequestedSelectionIsReauthorizedAfterGenerationReplacement() {
        val channel = Channel.create(id = ChannelId(23), name = "Twenty Three")
        val observations = FakeSessionObservation(currentObservation(channel))
        val requested = LivePlaybackSelection(
            currentSession = observations.captureCurrentSession(),
            channelId = channel.id,
        )
        observations.publish(currentObservation(channel))

        val currentObservation = observations.observation.value
        val resolved = requireNotNull(
            resolveLivePlaybackSelection(
                observation = currentObservation,
                channelId = channel.id,
                requestedSelection = requested,
            ),
        )

        assertNotSame(requested, resolved)
        assertNotSame(requested.currentSession, resolved.currentSession)
        assertSame(currentObservation.currentSession, resolved.currentSession)
    }

    private fun currentObservation(channel: Channel): SessionObservation =
        SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED,
                    dvrWrite = CapabilityAccess.ALLOWED,
                ),
            ),
            channelState = ChannelRepositoryState.Current(
                ChannelCatalog.create(channels = listOf(channel)),
            ),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )
}
