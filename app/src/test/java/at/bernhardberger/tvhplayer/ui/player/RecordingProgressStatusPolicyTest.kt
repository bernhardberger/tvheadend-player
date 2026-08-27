package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.playback.AppRecordingProgressState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingProgressStatusPolicyTest {
    @Test
    fun degradedEpisodeSurvivesRetrySavingAndClearsOnlyAtStableOutcome() {
        var active = recordingDegradedEpisodeActive(
            currentlyActive = false,
            syncState = AppRecordingProgressState.DEGRADED,
        )
        assertTrue(active)

        active = recordingDegradedEpisodeActive(active, AppRecordingProgressState.SAVING)
        assertTrue(active)
        active = recordingDegradedEpisodeActive(active, AppRecordingProgressState.DEGRADED)
        assertTrue(active)

        active = recordingDegradedEpisodeActive(active, AppRecordingProgressState.AVAILABLE)
        assertFalse(active)
        active = recordingDegradedEpisodeActive(active, AppRecordingProgressState.DEGRADED)
        assertTrue(active)
    }

    @Test
    fun readOnlyUnsupportedAndInactiveEndDegradationEpisode() {
        assertFalse(
            recordingDegradedEpisodeActive(true, AppRecordingProgressState.READ_ONLY)
        )
        assertFalse(
            recordingDegradedEpisodeActive(true, AppRecordingProgressState.UNSUPPORTED)
        )
        assertFalse(
            recordingDegradedEpisodeActive(true, AppRecordingProgressState.INACTIVE)
        )
    }
}
