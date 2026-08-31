package at.bernhardberger.tvhplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.tv.material3.darkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorContractTest {
    @Test
    fun pinnedRolesAndImportantContrastPairsMatchTheProductContract() {
        val actual = tvRoleColors().mapValues { (_, color) -> color.argb() }

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
        val mobileRoles = mobileRoleColors().mapValues { (_, color) -> color.argb() }
        val expected = tvRoleColors().mapKeys { (role, _) ->
            when (role) {
                "border" -> "outline"
                "borderVariant" -> "outlineVariant"
                else -> role
            }
        }.mapValues { (_, color) -> color.argb() }

        assertEquals(expected, mobileRoles)
    }

    private fun assertContrast(foreground: String, background: String) {
        val foregroundColor = Color(foreground.toULong(16).toLong())
        val backgroundColor = Color(background.toULong(16).toLong())
        val lighter = maxOf(foregroundColor.luminance(), backgroundColor.luminance())
        val darker = minOf(foregroundColor.luminance(), backgroundColor.luminance())
        assertTrue("$foreground on $background", (lighter + 0.05f) / (darker + 0.05f) >= 4.5f)
    }

    private fun Color.argb(): String = toArgb().toUInt().toString(16).padStart(8, '0').uppercase()

    private fun tvRoleColors() = mapOf(
        "primary" to TvDarkColors.primary,
        "onPrimary" to TvDarkColors.onPrimary,
        "primaryContainer" to TvDarkColors.primaryContainer,
        "onPrimaryContainer" to TvDarkColors.onPrimaryContainer,
        "inversePrimary" to TvDarkColors.inversePrimary,
        "secondary" to TvDarkColors.secondary,
        "onSecondary" to TvDarkColors.onSecondary,
        "secondaryContainer" to TvDarkColors.secondaryContainer,
        "onSecondaryContainer" to TvDarkColors.onSecondaryContainer,
        "tertiary" to TvDarkColors.tertiary,
        "onTertiary" to TvDarkColors.onTertiary,
        "tertiaryContainer" to TvDarkColors.tertiaryContainer,
        "onTertiaryContainer" to TvDarkColors.onTertiaryContainer,
        "background" to TvDarkColors.background,
        "onBackground" to TvDarkColors.onBackground,
        "surface" to TvDarkColors.surface,
        "onSurface" to TvDarkColors.onSurface,
        "surfaceVariant" to TvDarkColors.surfaceVariant,
        "onSurfaceVariant" to TvDarkColors.onSurfaceVariant,
        "surfaceTint" to TvDarkColors.surfaceTint,
        "inverseSurface" to TvDarkColors.inverseSurface,
        "inverseOnSurface" to TvDarkColors.inverseOnSurface,
        "error" to TvDarkColors.error,
        "onError" to TvDarkColors.onError,
        "errorContainer" to TvDarkColors.errorContainer,
        "onErrorContainer" to TvDarkColors.onErrorContainer,
        "border" to TvDarkColors.border,
        "borderVariant" to TvDarkColors.borderVariant,
        "scrim" to TvDarkColors.scrim,
    )

    private fun mobileRoleColors() = mapOf(
        "primary" to MobileDarkColors.primary,
        "onPrimary" to MobileDarkColors.onPrimary,
        "primaryContainer" to MobileDarkColors.primaryContainer,
        "onPrimaryContainer" to MobileDarkColors.onPrimaryContainer,
        "inversePrimary" to MobileDarkColors.inversePrimary,
        "secondary" to MobileDarkColors.secondary,
        "onSecondary" to MobileDarkColors.onSecondary,
        "secondaryContainer" to MobileDarkColors.secondaryContainer,
        "onSecondaryContainer" to MobileDarkColors.onSecondaryContainer,
        "tertiary" to MobileDarkColors.tertiary,
        "onTertiary" to MobileDarkColors.onTertiary,
        "tertiaryContainer" to MobileDarkColors.tertiaryContainer,
        "onTertiaryContainer" to MobileDarkColors.onTertiaryContainer,
        "background" to MobileDarkColors.background,
        "onBackground" to MobileDarkColors.onBackground,
        "surface" to MobileDarkColors.surface,
        "onSurface" to MobileDarkColors.onSurface,
        "surfaceVariant" to MobileDarkColors.surfaceVariant,
        "onSurfaceVariant" to MobileDarkColors.onSurfaceVariant,
        "surfaceTint" to MobileDarkColors.surfaceTint,
        "inverseSurface" to MobileDarkColors.inverseSurface,
        "inverseOnSurface" to MobileDarkColors.inverseOnSurface,
        "error" to MobileDarkColors.error,
        "onError" to MobileDarkColors.onError,
        "errorContainer" to MobileDarkColors.errorContainer,
        "onErrorContainer" to MobileDarkColors.onErrorContainer,
        "outline" to MobileDarkColors.outline,
        "outlineVariant" to MobileDarkColors.outlineVariant,
        "scrim" to MobileDarkColors.scrim,
    )

    private companion object {
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
