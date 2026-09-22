package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme

/** Native card outline, displaced outward so embedded edge content remains visible. */
@Composable
internal fun embeddedProgressCardBorder(): CardBorder = CardDefaults.border(
    focusedBorder = Border(
        // Match TV Material ButtonDefaults' focused container.
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
        // TV Material expands the outline by this distance; the 3dp stroke is centered on it.
        inset = 2.dp,
        // TV Material 1.1.0 Card.kt pins the private native shape to 8dp.
        // SurfaceBorder expands bounds, not radii: add the 2dp outward inset
        // to keep concentric corners and a 0.5dp gap inside the centered stroke.
        shape = RoundedCornerShape(8.dp + 2.dp),
    ),
)
