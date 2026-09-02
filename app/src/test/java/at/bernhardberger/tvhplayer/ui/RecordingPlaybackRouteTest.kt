package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingPlaybackRouteTest {
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
