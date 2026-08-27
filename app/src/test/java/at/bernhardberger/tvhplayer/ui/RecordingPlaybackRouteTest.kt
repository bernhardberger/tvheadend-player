package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingPlaybackRouteTest {
    @Test
    fun routeCarriesOnlyTheSdkStartPolicyWithoutACallerResumePosition() {
        assertEquals(
            "recording-player/42/resume",
            Routes.recordingPlayer(42, RecordingPlaybackStart.RESUME),
        )
        assertEquals(
            "recording-player/42/beginning",
            Routes.recordingPlayer(42, RecordingPlaybackStart.START_OVER),
        )
    }
}
