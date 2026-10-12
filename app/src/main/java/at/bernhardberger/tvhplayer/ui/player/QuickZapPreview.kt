package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.ImageLoader
import coil3.compose.AsyncImage

/**
 * The rail's preview of the focused card, in the immersive list's order: facts, title and
 * subtitle as one group, the description a step below, Next last. The text keeps the cards' start
 * keyline whether or not there is artwork; the artwork trails at the end, on the text's last line.
 */
@Composable
fun QuickZapPreview(
    title: String,
    metadata: String,
    summary: String?,
    next: String?,
    image: ArtworkId?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val artwork = image
    var artworkFailed by remember(artwork, currentSession) { mutableStateOf(false) }
    val secondary = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextSecondaryAlpha)
    val tertiary = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextTertiaryAlpha)
    Row(
        modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(TvGridGutter),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(metadata, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = tertiary)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface)
            // A sports title says which event; its subtitle says which session.
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = secondary)
            }
            summary?.takeIf { it.isNotBlank() && it != subtitle }?.let {
                Spacer(Modifier.height(PreviewDescriptionGap))
                Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, color = secondary)
            }
            next?.let {
                Spacer(Modifier.height(PreviewNextGap))
                Text(stringResource(R.string.player_next, it), style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = tertiary)
            }
        }
        if (artwork != null && currentSession != null && !artworkFailed) AsyncImage(
            AppArtworkSource(currentSession, artwork), null, imageLoader,
            modifier = Modifier.size(PlayerChromeTokens.channelCardWidth, PlayerChromeTokens.channelCardHeight)
                .clip(PreviewArtworkShape),
            contentScale = ContentScale.Crop,
            onError = { artworkFailed = true },
        )
    }
}

private val PreviewArtworkShape = RoundedCornerShape(12.dp)
private val PreviewDescriptionGap = 16.dp
private val PreviewNextGap = 12.dp

/** Invisible, unannounced stand-in as tall as the fullest [QuickZapPreview] at the current font scale. */
@Composable
internal fun QuickZapPreviewReserve(modifier: Modifier = Modifier) {
    Column(modifier.heightIn(min = PlayerChromeTokens.channelCardHeight).alpha(0f).clearAndSetSemantics { }) {
        Text("", style = MaterialTheme.typography.labelLarge, minLines = 1)
        Spacer(Modifier.height(4.dp))
        Text("", style = MaterialTheme.typography.headlineMedium, minLines = 1)
        Spacer(Modifier.height(2.dp))
        Text("", style = MaterialTheme.typography.bodyLarge, minLines = 1)
        Spacer(Modifier.height(PreviewDescriptionGap))
        Text("", style = MaterialTheme.typography.bodyLarge, minLines = 2)
        Spacer(Modifier.height(PreviewNextGap))
        Text("", style = MaterialTheme.typography.labelLarge, minLines = 1)
    }
}
