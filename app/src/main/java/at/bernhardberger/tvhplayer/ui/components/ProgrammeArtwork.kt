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
import at.bernhardberger.tvhplayer.ui.TvCardProgressStripHeight
import at.bernhardberger.tvhplayer.ui.TvLogoPlateColor
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.isRunning
import coil3.compose.AsyncImage

/** Non-focusable artwork shared by airing cards and browse menus. */
@Composable
internal fun ProgrammeArtwork(
    image: ArtworkId?, channelIcon: ChannelIcon?, progress: Float?,
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
            progressModifier.align(Alignment.BottomStart).fillMaxWidth(), height = TvCardProgressStripHeight, color = progressColor)
    }
}

/**
 * A channel's logo embedded in a programme picture's bottom-start corner, on a dark plate. Sized
 * for an airing card's picture; a smaller picture passes its [scale], so the plate keeps its share.
 */
@Composable
internal fun ArtworkLogoPlate(channelIcon: ChannelIcon?, modifier: Modifier = Modifier, scale: Float = 1f) {
    PiconBox(channelIcon, modifier.size(56.dp * scale, 32.dp * scale)
        .background(TvLogoPlateColor, MaterialTheme.shapes.extraSmall)
        .padding(horizontal = 4.dp * scale, vertical = 3.dp * scale))
}

@Composable
internal fun programmeAiringTime(event: EpgEvent, nowSec: Long): String =
    if (event.isRunning(nowSec)) stringResource(R.string.details_on_now_until, formatHm(event.stop.epochSeconds))
    else detailsFactValue(DetailsFact(DetailsFactKind.STARTS, event.start.epochSeconds), nowSec)
