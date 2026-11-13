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
        assertContains("ui/screens/onboarding/ConnectionSetupSteps.kt", "MaterialTheme.colorScheme.error")
        assertContains(
            "ui/components/ProgrammeDetailsLayout.kt",
            "color = MaterialTheme.colorScheme.onSurfaceVariant",
        )
        assertContains(
            "ui/components/ProgrammeDetailsPage.kt",
            "DetailsStatus.RECORDING -> RecordingBadge(DvrEntryState.RECORDING,",
        )
        assertContains("ui/components/StatusBadge.kt", "StatusBadgeTone.RECORDING -> TvRecordingContainerColor")

        val scopedSources = listOf(
            "ui/screens/onboarding/ConnectionSetupSteps.kt",
            "ui/screens/settings/SettingsConnection.kt",
            "ui/screens/RecordingsScreen.kt",
            "ui/screens/recordings/RecordingsContent.kt",
            "ui/screens/recordings/RecordingsModals.kt",
            "ui/screens/EpgGridScreen.kt",
            "ui/screens/guide/EpgGridContent.kt",
            "ui/screens/guide/EpgGridModals.kt",
            "ui/screens/guide/GuideHeader.kt",
            "ui/screens/guide/GuideTimelineBody.kt",
            "ui/screens/guide/GuideOverlayHost.kt",
            "ui/components/ProgrammeDetailsLayout.kt",
            "ui/components/ProgrammeDetailsPage.kt",
            "ui/screens/ChannelsScreen.kt",
            "ui/player/PlayerChromeControls.kt",
        ).joinToString("\n") { source(it) }

        listOf(
            "Text(text = it, color = MaterialTheme.colorScheme.primary)",
            "Text(it, color = MaterialTheme.colorScheme.primary)",
        ).forEach { forbidden ->
            assertFalse("Forbidden semantic primary usage: $forbidden", scopedSources.contains(forbidden))
        }
        assertTrue(scopedSources.contains("MaterialTheme.colorScheme.error"))
    }
    @Test
    fun recordingRedsAreDrawnOnlyByTheSharedBadgeAndDot() {
        // Every recording mark goes through RecordingBadge or RecordingStatusIndicator, and a mark or
        // progress bar drawn elsewhere through recordingMarkColor, so each surface gets the red picked for it.
        val owners = setOf(
            "app/src/main/java/at/bernhardberger/tvhplayer/ui/components/RecordingStatusIndicator.kt",
            "app/src/main/java/at/bernhardberger/tvhplayer/ui/components/StatusBadge.kt",
            "brand/src/main/java/at/bernhardberger/tvhplayer/ui/Theme.kt",
        )
        val reds = Regex("""\bTv(OnRecordingContainer|RecordingContainer|RecordingFocused|Recording)Color\b""")
        val offenders = listOf("app/src/main/java", "brand/src/main/java")
            .asSequence()
            .flatMap { File(repositoryRoot, it).walkTopDown() }
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.relativeTo(repositoryRoot).invariantSeparatorsPath !in owners && reds.containsMatchIn(it.readText()) }
            .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
            .toList()
        assertTrue("Recording reds used outside the shared badge and dot: $offenders", offenders.isEmpty())
    }

    private fun assertContains(relativePath: String, expected: String) {
        assertTrue("$relativePath must contain $expected", source(relativePath).contains(expected))
    }

    private fun source(relativePath: String): String = File(
        repositoryRoot,
        "app/src/main/java/at/bernhardberger/tvhplayer/$relativePath",
    ).readText()
}
