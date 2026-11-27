package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout

internal object PlayerChromeTokens {
    val chipShape = RoundedCornerShape(4.dp)
    /** Behind a chip's text over a picture or video: the kit's 60% scrim step. */
    val chipScrim = Color.Black.copy(alpha = 0.60f)
    val playNextBottomInset = 35.dp
    val playNextBannerGap = 18.dp
    private val playNextScrimRunout = 60.dp

    /** The resting scrim reaches the viewport edges; a lifted scrim fades out above the Banner. */
    fun DrawScope.drawPlayNextScrim(opacity: Float, bannerTop: Float?, rise: Int) {
        val runout = playNextScrimRunout.toPx()
        val dark = chipScrim.copy(alpha = chipScrim.alpha * opacity)
        val clear = Color.Transparent
        val width = size.width + runout
        val restingBottom = size.height + runout
        val bottom = bannerTop?.coerceAtMost(restingBottom) ?: restingBottom
        if (bottom <= 0f) return
        val bottomFade = if (bottom < restingBottom) {
            (bottom - (size.height - rise)).coerceIn(8.dp.toPx(), runout).coerceAtMost(bottom)
        } else 0f
        val height = bottom - bottomFade
        scale(scaleX = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f, scaleY = 1f) {
            drawRect(dark, size = Size(width, height))
            drawRect(Brush.horizontalGradient(listOf(clear, dark), startX = -runout, endX = 0f),
                topLeft = Offset(-runout, 0f), size = Size(runout, height))
            drawRect(Brush.verticalGradient(listOf(clear, dark), startY = -runout, endY = 0f),
                topLeft = Offset(0f, -runout), size = Size(width, runout))
            drawRect(Brush.radialGradient(listOf(dark, clear), center = Offset.Zero, radius = runout),
                topLeft = Offset(-runout, -runout), size = Size(runout, runout))
            if (bottomFade > 0f) {
                drawRect(Brush.verticalGradient(listOf(dark, clear), startY = height, endY = bottom),
                    topLeft = Offset(0f, height), size = Size(width, bottomFade))
                // Continue both fades through the bottom-start corner without a visible seam.
                val corner = Offset(0f, height)
                scale(scaleX = 1f, scaleY = bottomFade / runout, pivot = corner) {
                    drawRect(Brush.radialGradient(listOf(dark, clear), center = corner, radius = runout),
                        topLeft = Offset(-runout, height), size = Size(runout, runout))
                }
            }
        }
    }
    val heroShape = RoundedCornerShape(12.dp)
    val chipHeight = 24.dp
    val identityWidth = 128.dp
    val identityHeight = 72.dp
    val heroWidth = 360.dp
    val heroHeight = 202.dp
    /** From the preview to the title of the rail's focused row. */
    val previewCardGap = 20.dp

    /**
     * The timeline row's distance to the action row, for live and recordings alike; with the row's
     * 8dp below its labels and the 40dp buttons' 8dp around the glyph it leaves 24dp to the icons.
     */
    val timelineActionGap = 4.dp
    /** Trial: the channel card and the rail's cards, three grid columns wide. */
    val channelCardWidth = 196.dp
    val channelCardHeight = 110.dp
    /** Trial: a row title's height above a card row. */
    val rowTitleGap = 8.dp

    /** Full-width top fade behind the clock: 0.72 at the edge, 0.48 at 56dp, clear at 112dp. */
    val topScrimHeight = 112.dp
    val topScrimStops = arrayOf(
        0f to Color.Black.copy(alpha = 0.72f),
        0.5f to Color.Black.copy(alpha = 0.48f),
        1f to Color.Transparent,
    )
    val topScrim = Brush.verticalGradient(*topScrimStops)

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
}
