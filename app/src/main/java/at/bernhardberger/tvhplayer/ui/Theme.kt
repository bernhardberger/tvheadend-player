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

internal val TvDarkColors = darkColorScheme(
    primary = Color(0xFF00BCFA),
    onPrimary = Color(0xFF00344B),
    primaryContainer = Color(0xFF003E55),
    onPrimaryContainer = Color(0xFFC3E8FF),
    inversePrimary = Color(0xFF6750A4),
    secondary = Color(0xFFC4E8FE),
    onSecondary = Color(0xFF0D3446),
    secondaryContainer = Color(0xFF274B5D),
    onSecondaryContainer = Color(0xFFC4E8FE),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF0F1014),
    onBackground = Color(0xFFE3E3E8),
    surface = Color(0xFF17181D),
    onSurface = Color(0xFFE3E3E8),
    surfaceVariant = Color(0xFF23242A),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFF00BCFA),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    border = Color(0xFF8E9099),
    borderVariant = Color(0xFF44464E),
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
