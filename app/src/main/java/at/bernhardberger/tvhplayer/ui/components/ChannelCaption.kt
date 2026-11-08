package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Shared compact under-card channel caption, retaining the theme's medium weight. */
@Composable
internal fun channelCaptionStyle(): TextStyle = MaterialTheme.typography.labelMedium

/**
 * The name under a tile of Home's recent-channels row, shared by the channel tiles and the row's
 * "All channels" end card so both read as one row: centred, two lines, the gap below the picture.
 * An unspecified [color] takes the surrounding content colour.
 */
@Composable
internal fun HomeRowCaption(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text,
        style = channelCaptionStyle().copy(fontWeight = FontWeight.Normal),
        textAlign = TextAlign.Center,
        color = color,
        maxLines = 2,
        minLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(top = StandardCardTextGap).then(modifier),
    )
}
