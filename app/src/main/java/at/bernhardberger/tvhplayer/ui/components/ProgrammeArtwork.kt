package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.isRunning
import coil3.compose.AsyncImage

/** Non-focusable artwork shared by airing cards and browse menus. */
@Composable
internal fun ProgrammeArtwork(
    image: ArtworkId?, channelIcon: ArtworkId?, progress: Float?,
    modifier: Modifier = Modifier,
    plateModifier: Modifier = Modifier, progressModifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    val session = LocalArtworkContext.current.currentSession
    Box(modifier.clip(MaterialTheme.shapes.small).background(TvSurfaceColors.containerLowest)) {
        if (image != null && session != null) {
            AsyncImage(AppArtworkSource(session, image), null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (channelIcon != null) ArtworkLogoPlate(channelIcon,
                Modifier.align(Alignment.BottomStart).padding(8.dp).then(plateModifier))
        } else PiconBox(channelIcon, Modifier.align(Alignment.Center).size(100.dp, 45.dp))
        // Same embedded strip as the player's schedule tiles; card borders keep clear of it.
        if (progress != null) ProgressStrip(progress,
            progressModifier.align(Alignment.BottomStart).fillMaxWidth(), height = 3.dp, color = progressColor)
    }
}

/** A channel's logo embedded in a programme picture's bottom-start corner, on a dark plate. */
@Composable
internal fun ArtworkLogoPlate(channelIcon: ArtworkId?, modifier: Modifier = Modifier) {
    PiconBox(channelIcon, modifier.size(56.dp, 32.dp)
        .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
        .padding(horizontal = 4.dp, vertical = 3.dp))
}

@Composable
internal fun programmeAiringTime(event: EpgEvent, nowSec: Long): String =
    if (event.isRunning(nowSec)) stringResource(R.string.details_on_now_until, formatHm(event.stop.epochSeconds))
    else detailsFactValue(DetailsFact(DetailsFactKind.STARTS, event.start.epochSeconds), nowSec)
