package at.bernhardberger.tvhplayer.ui

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
}
