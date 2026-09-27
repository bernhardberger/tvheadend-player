package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object PlayerTrialTokens {
    val chipShape = RoundedCornerShape(4.dp)
    val heroShape = RoundedCornerShape(12.dp)
    val chipHeight = 24.dp
    val badgeHeight = 20.dp
    val badgeGap = 4.dp
    val identityWidth = 128.dp
    val identityHeight = 72.dp
    val badgeColumnWidth = 224.dp
    val heroWidth = 360.dp
    val heroHeight = 202.dp
    val previewWidth = 160.dp
    val previewHeight = 90.dp
    val previewSlotHeight = 96.dp
    val previewCardGap = 16.dp

    /** Host-sized bottom scrim on the 540dp canvas: clear to y184, then 0.60 / 0.80 / 0.92. */
    val bottomScrim = Brush.verticalGradient(
        0f to Color.Transparent,
        184f / 540f to Color.Transparent,
        248f / 540f to Color.Black.copy(alpha = 0.60f),
        300f / 540f to Color.Black.copy(alpha = 0.80f),
        1f to Color.Black.copy(alpha = 0.92f),
    )

    /** The same scrim anchored to a shorter surface's bottom edge (quick-zap tray): its 356dp span. */
    val bottomScrimSpan = 356.dp
    val bottomScrimStops = arrayOf(
        0f to Color.Transparent,
        64f / 356f to Color.Black.copy(alpha = 0.60f),
        116f / 356f to Color.Black.copy(alpha = 0.80f),
        1f to Color.Black.copy(alpha = 0.92f),
    )
}
