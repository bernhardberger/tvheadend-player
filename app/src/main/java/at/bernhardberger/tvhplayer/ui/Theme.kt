package at.bernhardberger.tvhplayer.ui

import androidx.compose.material3.MaterialTheme as MobileMaterialTheme
import androidx.compose.material3.darkColorScheme as mobileDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

// Bright enough for small recording labels on the product's dark badge surface.
val TvRecordingColor = Color(0xFFFF5449)

// Static neutral tones from the cyan Material palette. TV Material does not yet
// expose the surface-container roles; use these for panels instead of tinting
// surface by elevation. Opacity over video is applied separately by the caller.
object TvSurfaceColors {
    val containerLowest = Color(0xFF0C0F10)
    val containerLow = Color(0xFF191C1E)
    val container = Color(0xFF1D2022)
    val containerHigh = Color(0xFF282A2C)
    val containerHighest = Color(0xFF323537)
    val bright = Color(0xFF37393B)
}

// Material Color Utilities 0.3.0: cyan #00BCFA, orange #FA7F00.
// Dark roles use generated tones; tertiary deliberately uses T70, not T80.
internal val TvDarkColors = darkColorScheme(
    primary = Color(0xFF79D1FF),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004C68),
    onPrimaryContainer = Color(0xFFC3E8FF),
    inversePrimary = Color(0xFF006689),
    secondary = Color(0xFFB5C9D7),
    onSecondary = Color(0xFF20333D),
    secondaryContainer = Color(0xFF364955),
    onSecondaryContainer = Color(0xFFD1E5F4),
    tertiary = Color(0xFFFF8E32),
    onTertiary = Color(0xFF502400),
    tertiaryContainer = Color(0xFF723600),
    onTertiaryContainer = Color(0xFFFFDCC6),
    background = Color(0xFF111416),
    onBackground = Color(0xFFE1E2E5),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC0C7CD),
    surfaceTint = Color(0xFF79D1FF),
    inverseSurface = Color(0xFFE1E2E5),
    inverseOnSurface = Color(0xFF2E3133),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    border = Color(0xFF8A9297),
    borderVariant = Color(0xFF41484D),
    scrim = Color(0xFF000000),
)

// TV Material 1.1.0 does not provide text fields, progress indicators, dividers,
// or dialogs. Keep the official mobile Material implementations color-aligned
// rather than recreating their input, semantics, and accessibility behavior.
internal val MobileDarkColors = mobileDarkColorScheme(
    primary = TvDarkColors.primary,
    onPrimary = TvDarkColors.onPrimary,
    primaryContainer = TvDarkColors.primaryContainer,
    onPrimaryContainer = TvDarkColors.onPrimaryContainer,
    inversePrimary = TvDarkColors.inversePrimary,
    secondary = TvDarkColors.secondary,
    onSecondary = TvDarkColors.onSecondary,
    secondaryContainer = TvDarkColors.secondaryContainer,
    onSecondaryContainer = TvDarkColors.onSecondaryContainer,
    tertiary = TvDarkColors.tertiary,
    onTertiary = TvDarkColors.onTertiary,
    tertiaryContainer = TvDarkColors.tertiaryContainer,
    onTertiaryContainer = TvDarkColors.onTertiaryContainer,
    background = TvDarkColors.background,
    onBackground = TvDarkColors.onBackground,
    surface = TvDarkColors.surface,
    onSurface = TvDarkColors.onSurface,
    surfaceVariant = TvDarkColors.surfaceVariant,
    surfaceDim = TvDarkColors.surface,
    surfaceBright = TvSurfaceColors.bright,
    surfaceContainerLowest = TvSurfaceColors.containerLowest,
    surfaceContainerLow = TvSurfaceColors.containerLow,
    surfaceContainer = TvSurfaceColors.container,
    surfaceContainerHigh = TvSurfaceColors.containerHigh,
    surfaceContainerHighest = TvSurfaceColors.containerHighest,
    onSurfaceVariant = TvDarkColors.onSurfaceVariant,
    surfaceTint = TvDarkColors.surfaceTint,
    inverseSurface = TvDarkColors.inverseSurface,
    inverseOnSurface = TvDarkColors.inverseOnSurface,
    error = TvDarkColors.error,
    onError = TvDarkColors.onError,
    errorContainer = TvDarkColors.errorContainer,
    onErrorContainer = TvDarkColors.onErrorContainer,
    outline = TvDarkColors.border,
    outlineVariant = TvDarkColors.borderVariant,
    scrim = TvDarkColors.scrim,
)

@Composable
fun TVHeadendPlayerTheme(
    content: @Composable () -> Unit
) {
    MobileMaterialTheme(colorScheme = MobileDarkColors) {
        MaterialTheme(
            colorScheme = TvDarkColors,
            typography = Typography(),
            shapes = Shapes(),
            content = content,
        )
    }
}
