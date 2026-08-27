package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.data.RecordingProgressCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingProgressStatusPolicyTest {
    @Test
    fun disconnectedCapabilityIsDegradedOnlyForTheActiveRecording() {
        assertTrue(
            recordingDegradedEpisodeActive(
                capability = RecordingProgressCapability.Disconnected,
                recordingActive = true,
            )
        )
        assertFalse(
            recordingDegradedEpisodeActive(
                capability = RecordingProgressCapability.Disconnected,
                recordingActive = false,
            )
        )
    }

    @Test
    fun stableCapabilitiesAreNotReportedAsDegraded() {
        listOf(
            RecordingProgressCapability.Full,
            RecordingProgressCapability.ReadOnly,
            RecordingProgressCapability.Unsupported,
        ).forEach { capability ->
            assertFalse(
                recordingDegradedEpisodeActive(
                    capability = capability,
                    recordingActive = true,
                )
            )
        }
    }
}
