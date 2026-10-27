package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme

private val FocusOutlineWidth = 2.5.dp
private val FocusOutlineInset = 4.25.dp
internal val EmbeddedProgressCardBorderOutset = FocusOutlineInset + FocusOutlineWidth / 2
internal const val EmbeddedProgressCardFocusedScale = 1.05f
internal val EmbeddedProgressCardScale = CardDefaults.scale(focusedScale = EmbeddedProgressCardFocusedScale)

/**
 * Native card outline, displaced outward so embedded edge content remains visible. [held] keeps
 * the focused outline without focus, for a card that stands in for a focused one.
 */
@Composable
internal fun embeddedProgressCardBorder(held: Boolean = false, cardRadius: Dp = 8.dp): CardBorder {
    val focused = Border(
        // Match TV Material ButtonDefaults' focused container.
        border = BorderStroke(FocusOutlineWidth, MaterialTheme.colorScheme.onSurface),
        // TV Material expands the outline by this distance; the stroke is centered on it.
        inset = FocusOutlineInset,
        // TV Material 1.1.0 Card.kt defaults to 8dp; custom cards supply their radius.
        // SurfaceBorder expands bounds, not radii: add the outward inset
        // to keep concentric corners and a 3dp gap inside the centered stroke.
        shape = RoundedCornerShape(cardRadius + FocusOutlineInset),
    )
    return CardDefaults.border(border = if (held) focused else Border.None, focusedBorder = focused)
}
