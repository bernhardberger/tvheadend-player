package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Shared compact under-card channel caption, retaining the theme's medium weight. */
@Composable
internal fun channelCaptionStyle(): TextStyle = MaterialTheme.typography.labelMedium

internal val HomeCaptionLineGap = 4.dp

internal val HomeQuietCaptionColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

/**
 * The name under a tile of Home's recent-channels row, shared by the channel tiles and the row's
 * "All channels" end card: one start-aligned line with the standard gap below the picture.
 */
@Composable
internal fun HomeRowCaption(text: String, color: Color = Color.Unspecified) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Start,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(top = StandardCardTextGap),
    )
}
