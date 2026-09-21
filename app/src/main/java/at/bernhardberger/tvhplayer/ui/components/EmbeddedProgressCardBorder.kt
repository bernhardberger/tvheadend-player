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
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.border),
        // TV Material expands the outline by this distance; the 3dp stroke is centered on it.
        inset = 2.dp,
        shape = RoundedCornerShape(8.dp),
    ),
)
