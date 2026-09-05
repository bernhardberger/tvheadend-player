package at.bernhardberger.tvhplayer.ui

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
        assertContains("ui/screens/OnboardingScreen.kt", "MaterialTheme.colorScheme.error")
        assertContains("ui/screens/settings/SettingsConnection.kt", "MaterialTheme.colorScheme.error")
        assertContains(
            "ui/components/ProgrammeContentDetails.kt",
            "color = MaterialTheme.colorScheme.onSurfaceVariant",
        )
        assertContains(
            "ui/screens/guide/EpgGridModals.kt",
            "DvrEntryState.RECORDING -> TvRecordingColor",
        )
        assertContains("ui/player/LiveProgrammeInfoOverlay.kt", "color = TvRecordingColor")

        val scopedSources = listOf(
            "ui/screens/OnboardingScreen.kt",
            "ui/screens/settings/SettingsConnection.kt",
            "ui/screens/RecordingsScreen.kt",
            "ui/screens/recordings/RecordingsContent.kt",
            "ui/screens/recordings/RecordingsModals.kt",
            "ui/screens/EpgGridScreen.kt",
            "ui/screens/guide/EpgGridContent.kt",
            "ui/screens/guide/EpgGridModals.kt",
            "ui/components/ProgrammeContentDetails.kt",
            "ui/screens/ChannelsScreen.kt",
            "ui/player/LiveProgrammeInfoOverlay.kt",
            "ui/player/OverlayControlsTv.kt",
        ).joinToString("\n") { source(it) }

        listOf(
            "Text(text = it, color = MaterialTheme.colorScheme.primary)",
            "Text(it, color = MaterialTheme.colorScheme.primary)",
        ).forEach { forbidden ->
            assertFalse("Forbidden semantic primary usage: $forbidden", scopedSources.contains(forbidden))
        }
        assertTrue(scopedSources.contains("MaterialTheme.colorScheme.error"))
    }
    private fun assertContains(relativePath: String, expected: String) {
        assertTrue("$relativePath must contain $expected", source(relativePath).contains(expected))
    }

    private fun source(relativePath: String): String = File(
        repositoryRoot,
        "app/src/main/java/at/bernhardberger/tvhplayer/$relativePath",
    ).readText()
}
