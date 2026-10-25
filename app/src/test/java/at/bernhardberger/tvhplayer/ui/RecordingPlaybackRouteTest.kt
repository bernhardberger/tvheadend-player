package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingPlaybackRouteTest {
    @Test fun warmRecordingRequiresMatchingTargetSelectionAndCurrentSession() {
        val current = requireNotNull(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(CapabilityAccess.ALLOWED, CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create())).currentSession)
        val other = requireNotNull(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(CapabilityAccess.ALLOWED, CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create())).currentSession)
        val id = DvrEntryId(42)
        val target = AppPlaybackTarget.Recording(id)
        val selection = RecordingPlaybackSelection(current, id)
        assertEquals(id, warmRecordingId(target, selection, current))
        assertNull(warmRecordingId(AppPlaybackTarget.Recording(DvrEntryId(43)), selection, current))
        assertNull(warmRecordingId(target, selection, other))
        assertNull(warmRecordingId(null, selection, current))
        assertNull(warmRecordingId(target, null, current))
        assertNull(warmRecordingId(target, selection, null))
    }

    @Test
    fun keyCarriesOnlyTheStartPolicyWithoutACallerResumePosition() {
        assertEquals(
            RecordingStartMode.RESUME,
            RecordingPlayerKey(recordingId = 42).start,
        )
        assertEquals(
            RecordingStartMode.START_OVER,
            RecordingPlayerKey(recordingId = 42, start = RecordingStartMode.START_OVER).start,
        )
    }

    @Test
    fun playerKeyPreservesTheRequestedPlaybackStart() {
        assertEquals(
            RecordingPlayerKey(recordingId = 42, start = RecordingStartMode.RESUME),
            recordingPlayerKey(DvrEntryId(42), RecordingPlaybackStart.RESUME),
        )
        assertEquals(
            RecordingPlayerKey(recordingId = 42, start = RecordingStartMode.START_OVER),
            recordingPlayerKey(DvrEntryId(42), RecordingPlaybackStart.START_OVER),
        )
    }
}
