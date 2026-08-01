package at.bernhardberger.tvhplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.tv.material3.darkColorScheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorContractTest {
    private val repositoryRoot = generateSequence(
        File(requireNotNull(System.getProperty("user.dir"))),
    ) { it.parentFile }.first { File(it, ".git").exists() }
    private val themeSource = File(
        repositoryRoot,
        "app/src/main/java/at/bernhardberger/tvhplayer/ui/Theme.kt",
    ).readText()
    private val xmlThemeSource = File(
        repositoryRoot,
        "app/src/main/res/values/themes.xml",
    ).readText()

    @Test
    fun productThemeHasNoReachableLightScheme() {
        assertFalse(themeSource.contains("lightColorScheme"))
        assertFalse(themeSource.contains("LightColors"))
        assertFalse(themeSource.contains("darkTheme"))
        assertTrue(xmlThemeSource.contains("Theme.Material3.Dark.NoActionBar"))
        assertFalse(xmlThemeSource.contains("Theme.Material3.DayNight.NoActionBar"))
    }

    @Test
    fun tvSchemeExplicitlyOwnsAllMaterialForTvRoles() {
        val scheme = sourceBlock("private val DarkColors = darkColorScheme(")

        tvRoles.forEach { role ->
            assertTrue("Missing explicit TV role: $role", Regex("\\b$role\\s*=").containsMatchIn(scheme))
        }
        assertEquals(tvRoles.size, Regex("(?m)^\\s{4}\\w+\\s*=").findAll(scheme).count())
    }

    @Test
    fun pinnedRolesAndImportantContrastPairsMatchTheProductContract() {
        val scheme = sourceBlock("private val DarkColors = darkColorScheme(")
        val actual = tvRoles.associateWith { role ->
            val match = Regex("\\b$role\\s*=\\s*Color\\(0x([0-9A-Fa-f]{8})\\)").find(scheme)
            requireNotNull(match) { "Role $role must be pinned to an explicit ARGB value" }
            match.groupValues[1].uppercase()
        }

        assertEquals(expectedTvRoles, actual)
        assertContrast(actual.getValue("primary"), actual.getValue("onPrimary"))
        assertContrast(actual.getValue("onSurface"), actual.getValue("surface"))
        assertContrast(actual.getValue("inverseOnSurface"), actual.getValue("inverseSurface"))
        assertContrast(actual.getValue("error"), actual.getValue("surface"))
        assertContrast("FFFF5449", actual.getValue("surface"))
    }

    @Test
    fun inheritedTvMaterial110RolesArePinnedToTheirResolvedBaseline() {
        val baseline = darkColorScheme()

        mapOf(
            "FF6750A4" to baseline.inversePrimary,
            "FFEFB8C8" to baseline.tertiary,
            "FF492532" to baseline.onTertiary,
            "FF633B48" to baseline.tertiaryContainer,
            "FFFFD8E4" to baseline.onTertiaryContainer,
            "FFE6E1E5" to baseline.inverseSurface,
            "FF313033" to baseline.inverseOnSurface,
            "FFF2B8B5" to baseline.error,
            "FF601410" to baseline.onError,
            "FF8C1D18" to baseline.errorContainer,
            "FFF9DEDC" to baseline.onErrorContainer,
            "FF000000" to baseline.scrim,
        ).forEach { (expected, actual) ->
            assertEquals(expected, actual.toArgb().toUInt().toString(16).padStart(8, '0').uppercase())
        }
    }

    @Test
    fun mobileSchemeMirrorsEveryOverlappingTvRole() {
        val scheme = sourceBlock("private val MobileDarkColors = mobileDarkColorScheme(")
        val directRoles = tvRoles - setOf("border", "borderVariant")

        directRoles.forEach { role ->
            assertTrue(
                "Mobile role $role must mirror TV",
                Regex("\\b$role\\s*=\\s*DarkColors\\.$role\\b").containsMatchIn(scheme),
            )
        }
        assertTrue(Regex("\\boutline\\s*=\\s*DarkColors\\.border\\b").containsMatchIn(scheme))
        assertTrue(Regex("\\boutlineVariant\\s*=\\s*DarkColors\\.borderVariant\\b").containsMatchIn(scheme))
    }

    private fun sourceBlock(prefix: String): String {
        val start = themeSource.indexOf(prefix)
        require(start >= 0) { "Missing source block: $prefix" }
        val end = themeSource.indexOf("\n)", start)
        require(end >= 0) { "Unterminated source block: $prefix" }
        return themeSource.substring(start, end)
    }

    private fun assertContrast(foreground: String, background: String) {
        val foregroundColor = Color(foreground.toULong(16).toLong())
        val backgroundColor = Color(background.toULong(16).toLong())
        val lighter = maxOf(foregroundColor.luminance(), backgroundColor.luminance())
        val darker = minOf(foregroundColor.luminance(), backgroundColor.luminance())
        assertTrue("$foreground on $background", (lighter + 0.05f) / (darker + 0.05f) >= 4.5f)
    }

    private companion object {
        val tvRoles = listOf(
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
            "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
            "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
            "background", "onBackground", "surface", "onSurface", "surfaceVariant",
            "onSurfaceVariant", "surfaceTint", "inverseSurface", "inverseOnSurface",
            "error", "onError", "errorContainer", "onErrorContainer", "border", "borderVariant",
            "scrim",
        )

        val expectedTvRoles = mapOf(
            "primary" to "FF00BCFA", "onPrimary" to "FF00344B",
            "primaryContainer" to "FF003E55", "onPrimaryContainer" to "FFC3E8FF",
            "inversePrimary" to "FF6750A4", "secondary" to "FFC4E8FE",
            "onSecondary" to "FF0D3446", "secondaryContainer" to "FF274B5D",
            "onSecondaryContainer" to "FFC4E8FE", "tertiary" to "FFEFB8C8",
            "onTertiary" to "FF492532", "tertiaryContainer" to "FF633B48",
            "onTertiaryContainer" to "FFFFD8E4", "background" to "FF0F1014",
            "onBackground" to "FFE3E3E8", "surface" to "FF17181D",
            "onSurface" to "FFE3E3E8", "surfaceVariant" to "FF23242A",
            "onSurfaceVariant" to "FFC4C6D0", "surfaceTint" to "FF00BCFA",
            "inverseSurface" to "FFE6E1E5", "inverseOnSurface" to "FF313033",
            "error" to "FFF2B8B5", "onError" to "FF601410",
            "errorContainer" to "FF8C1D18", "onErrorContainer" to "FFF9DEDC",
            "border" to "FF8E9099", "borderVariant" to "FF44464E", "scrim" to "FF000000",
        )
    }
}
