@file:OptIn(at.bernhardberger.tvheadend.playback.ExperimentalRecordingCoordinationApi::class)

package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.playback.RecordingProgressSyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingProgressStatusPolicyTest {
    @Test
    fun degradedEpisodeSurvivesRetrySavingAndClearsOnlyAtStableOutcome() {
        var active = recordingDegradedEpisodeActive(
            currentlyActive = false,
            syncState = RecordingProgressSyncState.Degraded,
        )
        assertTrue(active)

        active = recordingDegradedEpisodeActive(active, RecordingProgressSyncState.Saving)
        assertTrue(active)
        active = recordingDegradedEpisodeActive(active, RecordingProgressSyncState.Degraded)
        assertTrue(active)

        active = recordingDegradedEpisodeActive(active, RecordingProgressSyncState.Available)
        assertFalse(active)
        active = recordingDegradedEpisodeActive(active, RecordingProgressSyncState.Degraded)
        assertTrue(active)
    }

    @Test
    fun readOnlyUnsupportedAndInactiveEndDegradationEpisode() {
        assertFalse(
            recordingDegradedEpisodeActive(true, RecordingProgressSyncState.ReadOnly)
        )
        assertFalse(
            recordingDegradedEpisodeActive(true, RecordingProgressSyncState.Unsupported)
        )
        assertFalse(
            recordingDegradedEpisodeActive(true, RecordingProgressSyncState.Inactive)
        )
    }
}
