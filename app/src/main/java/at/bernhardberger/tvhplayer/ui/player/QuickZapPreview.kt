package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.ImageLoader
import coil3.compose.AsyncImage

@Composable
fun QuickZapPreview(
    title: String,
    metadata: String,
    summary: String?,
    next: String?,
    image: String?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val artwork = ArtworkId.parse(image)
    var artworkFailed by remember(artwork, currentSession) { mutableStateOf(false) }
    Row(modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (artwork != null && currentSession != null && !artworkFailed) AsyncImage(
            AppArtworkSource(currentSession, artwork), null, imageLoader,
            modifier = Modifier.align(Alignment.Bottom).size(PlayerChromeTokens.previewWidth, PlayerChromeTokens.previewHeight), contentScale = ContentScale.Crop,
            onError = { artworkFailed = true },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(metadata, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface)
            // A sports title says which event; its subtitle says which session.
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f))
            }
            summary?.takeIf { it.isNotBlank() && it != subtitle }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f))
            }
            next?.let { Text(stringResource(R.string.player_next, it), style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)) }
        }
    }
}

/** Invisible, unannounced stand-in as tall as the fullest [QuickZapPreview] at the current font scale. */
@Composable
internal fun QuickZapPreviewReserve(modifier: Modifier = Modifier) {
    Column(
        modifier.heightIn(min = PlayerChromeTokens.previewHeight).alpha(0f).clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("", style = MaterialTheme.typography.labelLarge, minLines = 1)
        Text("", style = MaterialTheme.typography.titleLarge, minLines = 1)
        Text("", style = MaterialTheme.typography.bodyLarge, minLines = 1)
        Text("", style = MaterialTheme.typography.bodyMedium, minLines = 2)
        Text("", style = MaterialTheme.typography.labelLarge, minLines = 1)
    }
}
