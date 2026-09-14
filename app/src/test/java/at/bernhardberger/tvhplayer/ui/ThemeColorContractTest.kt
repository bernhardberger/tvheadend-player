package at.bernhardberger.tvhplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
        assertContrast(actual.getValue("tertiary"), actual.getValue("onTertiary"))
        assertContrast(actual.getValue("onTertiaryContainer"), actual.getValue("tertiaryContainer"))
        assertContrast(actual.getValue("onSurface"), actual.getValue("surface"))
        assertContrast(actual.getValue("inverseOnSurface"), actual.getValue("inverseSurface"))
        assertContrast(actual.getValue("error"), actual.getValue("surface"))
        assertContrast(actual.getValue("onErrorContainer"), actual.getValue("errorContainer"))
        assertContrast("FFFF5449", actual.getValue("surface"))
    }

    @Test
    fun panelForegroundsRemainReadableOverBrightVideo() {
        listOf(
            TvSurfaceColors.container.copy(alpha = TvPanelBrowseAlpha),
            TvSurfaceColors.container.copy(alpha = TvPanelDenseAlpha),
            TvSurfaceColors.containerHigh.copy(alpha = TvPanelDenseAlpha),
        ).forEach { panel ->
            val background = panel.compositeOver(Color.White)
            assertContrast(TvDarkColors.onSurfaceVariant.argb(), background.argb())
            assertContrast(
                TvDarkColors.onSurface.copy(alpha = TvTextTertiaryAlpha)
                    .compositeOver(background).argb(),
                background.argb(),
            )
        }
    }

    @Test
    fun neutralSurfaceHierarchyAndTvFocusKeepReadableForegrounds() {
        val surfaces = listOf(
            TvSurfaceColors.containerLowest, TvDarkColors.surface,
            TvSurfaceColors.containerLow, TvSurfaceColors.container,
            TvSurfaceColors.containerHigh, TvSurfaceColors.containerHighest,
            TvSurfaceColors.bright,
        )
        surfaces.zipWithNext().forEach { (lower, higher) ->
            assertTrue(lower.luminance() < higher.luminance())
        }
        surfaces.forEach { surface ->
            assertContrast(TvDarkColors.onSurface.argb(), surface.argb())
            assertContrast(TvDarkColors.onSurfaceVariant.argb(), surface.argb())
        }
        assertContrast(TvDarkColors.inverseOnSurface.argb(), TvDarkColors.onSurface.argb())
        assertEquals(TvSurfaceColors.containerLowest, MobileDarkColors.surfaceContainerLowest)
        assertEquals(TvSurfaceColors.containerLow, MobileDarkColors.surfaceContainerLow)
        assertEquals(TvSurfaceColors.container, MobileDarkColors.surfaceContainer)
        assertEquals(TvSurfaceColors.containerHigh, MobileDarkColors.surfaceContainerHigh)
        assertEquals(TvSurfaceColors.containerHighest, MobileDarkColors.surfaceContainerHighest)
        assertEquals(TvSurfaceColors.bright, MobileDarkColors.surfaceBright)
        assertEquals(TvDarkColors.surface, MobileDarkColors.surfaceDim)
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
            "primary" to "FF79D1FF", "onPrimary" to "FF003549",
            "primaryContainer" to "FF004C68", "onPrimaryContainer" to "FFC3E8FF",
            "inversePrimary" to "FF006689", "secondary" to "FFB5C9D7",
            "onSecondary" to "FF20333D", "secondaryContainer" to "FF364955",
            "onSecondaryContainer" to "FFD1E5F4", "tertiary" to "FFFF8E32",
            "onTertiary" to "FF502400", "tertiaryContainer" to "FF723600",
            "onTertiaryContainer" to "FFFFDCC6", "background" to "FF111416",
            "onBackground" to "FFE1E2E5", "surface" to "FF111416",
            "onSurface" to "FFE1E2E5", "surfaceVariant" to "FF41484D",
            "onSurfaceVariant" to "FFC0C7CD", "surfaceTint" to "FF79D1FF",
            "inverseSurface" to "FFE1E2E5", "inverseOnSurface" to "FF2E3133",
            "error" to "FFFFB4AB", "onError" to "FF690005",
            "errorContainer" to "FF93000A", "onErrorContainer" to "FFFFDAD6",
            "border" to "FF8A9297", "borderVariant" to "FF41484D", "scrim" to "FF000000",
        )
    }
}
