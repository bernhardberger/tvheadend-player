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
import at.bernhardberger.tvhplayer.ui.components.LocalArtworkContext
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.rememberChannelAccent
import coil3.compose.SubcomposeAsyncImage

/** Pass the existing TvheadendArtworkLoader and the current session capability, never a raw URL. */
@Composable
fun ProgrammeHero(
    image: ArtworkId?,
    channelId: ChannelId,
    channelNumber: String,
    picon: ArtworkId?,
    modifier: Modifier = Modifier,
    recordingBadge: Boolean = false,
) {
    val currentSession = LocalArtworkContext.current.currentSession
    val ground = channelFallbackGround(picon, channelId)
    val artwork = image
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
            Box(Modifier.fillMaxSize().background(ground).padding(bottom = bottomRoom, end = endRoom),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(markWidth, markHeight).testTag("programme-fallback-mark"), contentAlignment = Alignment.Center) {
                    if (picon != null && currentSession != null) {
                        // On its own colour's gradient, so no ground to ink it for.
                        PiconBox(picon, Modifier.fillMaxSize(), ground = null)
                    } else Text(channelNumber.ifBlank { stringResource(R.string.player_channel) },
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.headlineSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (artwork != null && currentSession != null) {
            SubcomposeAsyncImage(AppArtworkSource(currentSession, artwork), null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                loading = { fallback() }, error = { fallback() })
        } else fallback()
    }
}

/**
 * What a missing programme picture falls back to: the channel's own colour from its [picon] fading
 * down to the darkest surface, or without a picon (or a known channel) a neutral fade. The hero and
 * the player's artwork tile share it, so the details page and the info bar agree.
 */
@Composable
internal fun channelFallbackGround(picon: ArtworkId?, channelId: ChannelId?): Brush {
    val top = if (picon != null && channelId != null) {
        rememberChannelAccent(picon, channelId).copy(alpha = 0.48f)
    } else TvSurfaceColors.containerLow
    return Brush.verticalGradient(listOf(top, TvSurfaceColors.containerLowest))
}
