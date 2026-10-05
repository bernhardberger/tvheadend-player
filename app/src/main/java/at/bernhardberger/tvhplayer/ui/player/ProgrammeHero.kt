package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.rememberChannelAccent
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage

/** Pass the existing TvheadendArtworkLoader and the current session capability, never a raw URL. */
@Composable
fun ProgrammeHero(
    image: String?,
    channelId: ChannelId,
    channelNumber: String,
    picon: ArtworkId?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier = Modifier,
    recordingBadge: Boolean = false,
) {
    val accent = rememberChannelAccent(imageLoader, currentSession, picon, channelId)
    val artwork = ArtworkId.parse(image)
    BoxWithConstraints(modifier.size(PlayerChromeTokens.heroWidth, PlayerChromeTokens.heroHeight)
        .clearAndSetSemantics {}) {
        val compact = maxWidth <= PlayerChromeTokens.identityWidth
        val endRoom = if (compact && recordingBadge) 60.dp else 0.dp
        val bottomRoom = if (compact) 16.dp + with(LocalDensity.current) {
            MaterialTheme.typography.titleMedium.lineHeight.toDp()
        } else 12.dp
        val markWidth = minOf(if (compact) 80.dp else PlayerChromeTokens.identityWidth,
            maxWidth - if (endRoom > 0.dp) endRoom + 12.dp else 44.dp)
        val markHeight = minOf(if (compact) 28.dp else PlayerChromeTokens.identityHeight, maxHeight - bottomRoom)
        val fallback: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                if (picon != null) accent.copy(alpha = 0.48f) else TvSurfaceColors.containerLow,
                TvSurfaceColors.containerLowest,
            ))).padding(bottom = bottomRoom, end = endRoom), contentAlignment = Alignment.Center) {
                Box(Modifier.size(markWidth, markHeight).testTag("programme-fallback-mark"), contentAlignment = Alignment.Center) {
                    if (picon != null && currentSession != null) {
                        PiconBox(imageLoader, picon, Modifier.fillMaxSize(), currentSession)
                    } else Text(channelNumber.ifBlank { stringResource(R.string.player_channel) },
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.headlineSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (artwork != null && currentSession != null) {
            SubcomposeAsyncImage(AppArtworkSource(currentSession, artwork), null, imageLoader,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                loading = { fallback() }, error = { fallback() })
        } else fallback()
    }
}
