package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class OperationSelectionGenerationTest {
    @Test
    fun staleASelectionsKeepCapabilityAWhenSessionBPublishesCollidingIds() {
        val observations = FakeSessionObservation(currentObservation())
        val capabilityA = observations.captureCurrentSession()
        val eventId = EventId(7L)
        val channelId = ChannelId(11L)
        val recordingId = DvrEntryId(7L)

        val programme = ProgrammeRecordingTarget(
            eventId = eventId,
            channelId = channelId,
            start = 100L,
            stop = 200L,
            title = "A",
            currentSession = capabilityA,
        )
        val livePlayback = LivePlaybackSelection(capabilityA, channelId)
        val recordingPlayback = RecordingPlaybackSelection(capabilityA, recordingId)
        val artwork = AppArtworkSource(capabilityA, "imagecache/7")

        observations.publish(currentObservation())
        val capabilityB = observations.captureCurrentSession()

        assertNotSame(capabilityA, capabilityB)
        assertSame(capabilityA, programme.currentSession)
        assertSame(capabilityA, livePlayback.currentSession)
        assertSame(capabilityA, recordingPlayback.currentSession)
        assertSame(capabilityA, artwork.currentSession)
    }

    private fun currentObservation(): SessionObservation = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED,
                dvrWrite = CapabilityAccess.ALLOWED,
            ),
        ),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )
}
