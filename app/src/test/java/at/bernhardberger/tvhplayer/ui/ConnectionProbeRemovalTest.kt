package at.bernhardberger.tvhplayer.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionProbeRemovalTest {
    private val repositoryRoot = generateSequence(
        File(requireNotNull(System.getProperty("user.dir"))),
    ) { it.parentFile }.first { File(it, ".git").exists() }

    @Test
    fun connectionEntrySurfacesDoNotOfferASeparateProbe() {
        listOf(
            "ui/screens/OnboardingScreen.kt",
            "ui/screens/settings/SettingsConnection.kt",
        ).forEach { relativePath ->
            val source = File(
                repositoryRoot,
                "app/src/main/java/at/bernhardberger/tvhplayer/$relativePath",
            ).readText()
            assertFalse(relativePath, source.contains("testConnection("))
            assertFalse(relativePath, source.contains("R.string.test_connection"))
            assertFalse(relativePath, source.contains("ConnectionProbeUiState"))
        }
    }
}
