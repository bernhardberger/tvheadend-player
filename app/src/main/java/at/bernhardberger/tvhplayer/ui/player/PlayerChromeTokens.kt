package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout

internal object PlayerChromeTokens {
    val chipShape = RoundedCornerShape(4.dp)
    val heroShape = RoundedCornerShape(12.dp)
    val badgeShape = RoundedCornerShape(2.dp)
    val chipHeight = 24.dp
    val badgeHeight = 18.dp
    val badgeGap = 6.dp
    /** Eyebrow text to the badges on the same line. */
    val eyebrowBadgeGap = 12.dp
    val identityWidth = 128.dp
    val identityHeight = 72.dp
    val heroWidth = 360.dp
    val heroHeight = 202.dp
    val previewWidth = 160.dp
    val previewHeight = 90.dp
    val previewSlotHeight = 96.dp
    val previewCardGap = 16.dp

    /** Trial: Google TV's 960×540dp layout grid: 58dp side margins, 20dp gutters. */
    val gridMargin = 58.dp
    val gridGutter = 20.dp
    /** Trial: the channel card and the rail's cards, three grid columns wide. */
    val channelCardWidth = 196.dp
    val channelCardHeight = 110.dp
    /** Trial: a wide standard card's text, 16dp beside the card. */
    val identityTextGap = 16.dp
    /** Trial: the channel cards' focus growth; 1.05 stays clear of the margin and the arrow lanes. */
    const val cardFocusedScale = 1.05f
    /** Trial: a row title's height above a card row. */
    val rowTitleGap = 8.dp

    /** Full-width top fade behind the clock: 0.64 at the edge, 0.40 at 56dp, clear at 112dp. */
    val topScrimHeight = 112.dp
    val topScrim = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.64f),
        0.5f to Color.Black.copy(alpha = 0.40f),
        1f to Color.Transparent,
    )

    /**
     * The bottom scrim of a footer [height] px tall, drawn in the footer's own bounds: clear at its
     * top edge, 0.60 where its content starts after the [TvOverlayFooterGradientRunout], 0.80 52dp
     * further down, 0.92 at the bottom.
     */
    fun bottomScrim(height: Float, density: Density): Brush = with(density) {
        val runout = TvOverlayFooterGradientRunout.toPx()
        Brush.verticalGradient(
            0f to Color.Transparent,
            (runout / height).coerceAtMost(1f) to Color.Black.copy(alpha = 0.60f),
            ((runout + 52.dp.toPx()) / height).coerceAtMost(1f) to Color.Black.copy(alpha = 0.80f),
            1f to Color.Black.copy(alpha = 0.92f),
            startY = 0f, endY = height,
        )
    }

    /** The quick-zap tray's own scrim, anchored to its bottom edge: a 356dp span. */
    val bottomScrimSpan = 356.dp
    val bottomScrimStops = arrayOf(
        0f to Color.Transparent,
        64f / 356f to Color.Black.copy(alpha = 0.60f),
        116f / 356f to Color.Black.copy(alpha = 0.80f),
        1f to Color.Black.copy(alpha = 0.92f),
    )
}
