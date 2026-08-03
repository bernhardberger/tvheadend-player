package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvheadend.core.ConnectionFailureKind
import at.bernhardberger.tvheadend.core.ConnectionProbeResult
import at.bernhardberger.tvhplayer.ui.screens.ConnectionProbeUiState
import at.bernhardberger.tvhplayer.ui.screens.isActionableFailure
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticColorUsageTest {
    private val repositoryRoot = generateSequence(
        File(requireNotNull(System.getProperty("user.dir"))),
    ) { it.parentFile }.first { File(it, ".git").exists() }

    @Test
    fun successMetadataRecordingAndFailureUseTheirOwnedRoles() {
        assertContains("ui/screens/OnboardingScreen.kt", "probeState.isActionableFailure()")
        assertContains("ui/screens/settings/SettingsConnection.kt", "probeState.isActionableFailure()")
        assertContains(
            "ui/screens/settings/SettingsSimpleTv.kt",
            "PinFeedbackKind.SUCCESS -> MaterialTheme.colorScheme.onSurface",
        )
        assertContains(
            "ui/components/ProgrammeContentDetails.kt",
            "color = MaterialTheme.colorScheme.onSurfaceVariant",
        )
        assertContains("ui/screens/EpgGridScreen.kt", "DvrState.SCHEDULED, DvrState.RECORDING -> TvRecordingColor")
        assertContains("ui/player/LiveProgrammeInfoOverlay.kt", "color = TvRecordingColor")

        val scopedSources = listOf(
            "ui/screens/OnboardingScreen.kt",
            "ui/screens/settings/SettingsConnection.kt",
            "ui/screens/settings/SettingsSimpleTv.kt",
            "ui/screens/RecordingsScreen.kt",
            "ui/screens/EpgGridScreen.kt",
            "ui/components/ProgrammeContentDetails.kt",
            "ui/screens/ChannelsScreen.kt",
            "ui/player/LiveProgrammeInfoOverlay.kt",
            "ui/player/OverlayControlsTv.kt",
        ).joinToString("\n") { source(it) }

        listOf(
            "PinFeedbackKind.SUCCESS -> MaterialTheme.colorScheme.primary",
            "Text(text = it, color = MaterialTheme.colorScheme.primary)",
            "Text(it, color = MaterialTheme.colorScheme.primary)",
        ).forEach { forbidden ->
            assertFalse("Forbidden semantic primary usage: $forbidden", scopedSources.contains(forbidden))
        }
        assertTrue(scopedSources.contains("MaterialTheme.colorScheme.error"))
    }

    @Test
    fun onlyCompletedConnectionProbeFailuresAreActionableFailures() {
        assertFalse(ConnectionProbeUiState.Idle.isActionableFailure())
        assertFalse(ConnectionProbeUiState.Testing.isActionableFailure())
        assertFalse(
            ConnectionProbeUiState.Complete(
                ConnectionProbeResult.Success(serverVersion = 42, channelCount = 12),
            ).isActionableFailure()
        )
        assertTrue(
            ConnectionProbeUiState.Complete(
                ConnectionProbeResult.Failure(ConnectionFailureKind.AUTHENTICATION),
            ).isActionableFailure()
        )
    }

    private fun assertContains(relativePath: String, expected: String) {
        assertTrue("$relativePath must contain $expected", source(relativePath).contains(expected))
    }

    private fun source(relativePath: String): String = File(
        repositoryRoot,
        "app/src/main/java/at/bernhardberger/tvhplayer/$relativePath",
    ).readText()
}
