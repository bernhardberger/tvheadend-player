package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvhplayer.core.PlaybackCardFit
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors

@Composable
internal fun PictureBand(painter: Painter, height: Dp, surface: Color) {
    Box(Modifier.fillMaxWidth().height(height)) {
        Image(painter, null, Modifier.fillMaxSize(), BandAlignment, ContentScale.Crop)
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.verticalGradient(0.4f to Color.Transparent,
                (1f - 16.dp.toPx() / size.height) to surface, 1f to surface))
        })
    }
}

@Composable
private fun ChannelLine(
    picon: ChannelIcon?, channel: String, airTime: String?, airTimeOwnRow: Boolean, modifier: Modifier,
) {
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = PLAYBACK_CHANNEL_ROW.dp)
    Row(rowModifier, Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        picon?.let { InlineLogo(it, modifier) }
        Text(channelLine(channel, airTime.takeUnless { airTimeOwnRow }), Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelLarge, color = TvTextColors.secondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (airTime != null && airTimeOwnRow) {
        Spacer(Modifier.height(4.dp))
        AirTime(airTime)
    }
}

@Composable
private fun AirTime(airTime: String) {
    Box(Modifier.heightIn(min = PLAYBACK_CHANNEL_ROW.dp), contentAlignment = Alignment.CenterStart) {
        Text(airTime, style = MaterialTheme.typography.labelLarge, color = TvTextColors.secondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaybackTitle(title: String, fit: PlaybackCardFit, episode: String) {
    if (title.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface, maxLines = fit.titleLines, overflow = TextOverflow.Ellipsis)
    }
    if (fit.episodeLines > 0 && episode.isNotBlank()) {
        Spacer(Modifier.height(if (title.isBlank()) 8.dp else 4.dp))
        Text(episode, style = MaterialTheme.typography.bodyLarge, color = TvTextColors.secondary,
            maxLines = fit.episodeLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun PlaybackHeadingBody(
    picon: ChannelIcon?, channel: String, airTime: String?, airTimeOwnRow: Boolean,
    title: String, fit: PlaybackCardFit, episode: String, modifier: Modifier, piconModifier: Modifier,
) {
    Column(modifier) {
        ChannelLine(picon, channel, airTime, airTimeOwnRow, piconModifier)
        PlaybackTitle(title, fit, episode)
    }
}

/** A 2:3 poster is shown whole beside the heading rather than cropped into the band. */
@Composable
internal fun PosterHeading(
    painter: Painter, accent: Color?, picon: ChannelIcon?, channel: String, airTime: String?,
    title: String, fit: PlaybackCardFit, episode: String, modifier: Modifier,
) {
    Row(Modifier.padding(start = CardPadding, end = CardPadding, top = CardPadding),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.drawBehind {
            if (accent != null) {
                val radius = size.maxDimension * .8f
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .7f), Color.Transparent), center, radius),
                    radius, center)
            }
        }.size(PosterWidth, PosterHeight).clip(MaterialTheme.shapes.small)
            .background(TvSurfaceColors.containerLowest)) {
            Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.width(PosterColumnWidth)) {
            ChannelLine(picon, channel, null, false, modifier)
            PlaybackTitle(title, fit, episode)
            if (airTime != null) {
                Spacer(Modifier.height(4.dp))
                AirTime(airTime)
            }
        }
    }
}
