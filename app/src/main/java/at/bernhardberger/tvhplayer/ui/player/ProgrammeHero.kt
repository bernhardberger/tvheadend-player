package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
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
) {
    val accent = rememberChannelAccent(imageLoader, currentSession, picon, channelId)
    val artwork = ArtworkId.parse(image)
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            if (picon != null) accent.copy(alpha = 0.48f) else TvSurfaceColors.containerLow,
            TvSurfaceColors.containerLowest,
        ))), contentAlignment = Alignment.Center) {
            if (picon != null && currentSession != null) {
                PiconBox(imageLoader, picon, Modifier.size(PlayerTrialTokens.identityWidth, PlayerTrialTokens.identityHeight), currentSession)
            } else Text(channelNumber.ifBlank { stringResource(R.string.trial_channel) }, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    Box(modifier.size(PlayerTrialTokens.heroWidth, PlayerTrialTokens.heroHeight)
        .clip(PlayerTrialTokens.heroShape).clearAndSetSemantics {}) {
        if (artwork != null && currentSession != null) {
            SubcomposeAsyncImage(AppArtworkSource(currentSession, artwork), null, imageLoader,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                loading = { fallback() }, error = { fallback() })
        } else fallback()
    }
}
