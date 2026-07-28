package at.bernhardberger.tvhplayer.ui

import androidx.compose.material3.MaterialTheme as MobileMaterialTheme
import androidx.compose.material3.darkColorScheme as mobileDarkColorScheme
import androidx.compose.material3.lightColorScheme as mobileLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

private val DarkColors = darkColorScheme(
    primary = Color(0xFF00BCFA),
    onPrimary = Color(0xFF00344B),
    primaryContainer = Color(0xFF003E55),
    onPrimaryContainer = Color(0xFFC3E8FF),
    secondary = Color(0xFFC4E8FE),
    onSecondary = Color(0xFF0D3446),
    secondaryContainer = Color(0xFF274B5D),
    onSecondaryContainer = Color(0xFFC4E8FE),
    background = Color(0xFF0F1014),
    onBackground = Color(0xFFE3E3E8),
    surface = Color(0xFF17181D),
    onSurface = Color(0xFFE3E3E8),
    surfaceVariant = Color(0xFF23242A),
    onSurfaceVariant = Color(0xFFC4C6D0),
    border = Color(0xFF8E9099),
    borderVariant = Color(0xFF44464E)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1C6DD0),
    onPrimary = Color.White,

    primaryContainer = Color(0xFFD6E7FF),
    onPrimaryContainer = Color(0xFF001B33),

    secondary = Color(0xFF2A5D9F),
    onSecondary = Color.White,

    secondaryContainer = Color(0xFFD8E2FF),
    onSecondaryContainer = Color(0xFF001B33),

    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF0B0F14),

    surface = Color.White,
    onSurface = Color(0xFF0B0F14),

    surfaceVariant = Color(0xFFE8EEF6),
    onSurfaceVariant = Color(0xFF3A465A),

    border = Color(0xFF7C8AA0),
    borderVariant = Color(0xFFCCD6E4)
)

// TV Material 1.1.0 does not provide text fields, progress indicators, dividers,
// or dialogs. Keep the official mobile Material implementations color-aligned
// rather than recreating their input, semantics, and accessibility behavior.
private val MobileDarkColors = mobileDarkColorScheme(
    primary = DarkColors.primary,
    onPrimary = DarkColors.onPrimary,
    primaryContainer = DarkColors.primaryContainer,
    onPrimaryContainer = DarkColors.onPrimaryContainer,
    secondary = DarkColors.secondary,
    onSecondary = DarkColors.onSecondary,
    secondaryContainer = DarkColors.secondaryContainer,
    onSecondaryContainer = DarkColors.onSecondaryContainer,
    background = DarkColors.background,
    onBackground = DarkColors.onBackground,
    surface = DarkColors.surface,
    onSurface = DarkColors.onSurface,
    surfaceVariant = DarkColors.surfaceVariant,
    onSurfaceVariant = DarkColors.onSurfaceVariant,
    outline = DarkColors.border,
    outlineVariant = DarkColors.borderVariant,
)

private val MobileLightColors = mobileLightColorScheme(
    primary = LightColors.primary,
    onPrimary = LightColors.onPrimary,
    primaryContainer = LightColors.primaryContainer,
    onPrimaryContainer = LightColors.onPrimaryContainer,
    secondary = LightColors.secondary,
    onSecondary = LightColors.onSecondary,
    secondaryContainer = LightColors.secondaryContainer,
    onSecondaryContainer = LightColors.onSecondaryContainer,
    background = LightColors.background,
    onBackground = LightColors.onBackground,
    surface = LightColors.surface,
    onSurface = LightColors.onSurface,
    surfaceVariant = LightColors.surfaceVariant,
    onSurfaceVariant = LightColors.onSurfaceVariant,
    outline = LightColors.border,
    outlineVariant = LightColors.borderVariant,
)

@Composable
fun TVHeadendPlayerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val mobileColors = if (darkTheme) MobileDarkColors else MobileLightColors

    MobileMaterialTheme(colorScheme = mobileColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography(),
            shapes = Shapes(),
            content = content,
        )
    }
}
