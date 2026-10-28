package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvCompactProgressStripHeight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackMarker
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.AppChannelNumbers
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.progressColor
import at.bernhardberger.tvhplayer.ui.components.programmeProgress
import at.bernhardberger.tvhplayer.ui.components.watchingIndicator
import at.bernhardberger.tvhplayer.ui.components.isPlayingItem
import at.bernhardberger.tvhplayer.ui.components.liveViewerEpochSec
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvheadend.sdk.core.ChannelId

/** Passive identity of the playing channel, independent of the programme being browsed. */
@Composable
internal fun NowPlayingStrip(
    channelId: ChannelId,
    channelNumber: String?,
    channelName: String,
    picon: ChannelIcon?,
    programme: EpgEvent?,
    nowSec: Long,
    modifier: Modifier = Modifier,
    playbackIndicator: ChannelPlaybackIndicator = ChannelPlaybackIndicator.NONE,
    programmeAt: (Long) -> EpgEvent? = { null },
) {
    val at = liveViewerEpochSec(channelId, nowSec)
    val displayed = if (at != null && at < (programme?.start?.epochSeconds ?: nowSec))
        programmeAt(at) ?: programme else programme
    val current = displayed?.takeIf { it.isRunning(nowSec) || (at != null && it.isRunning(at)) }
    Row(modifier.testTag("player-now-playing").semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NowPlayingPlate(picon, channelNumber) {
            val progress = programmeProgress(current, nowSec)
            if (progress != null) ProgressStrip(
                progress = progress,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), height = TvCompactProgressStripHeight,
                color = progressColor(watchingIndicator(current, nowSec).isPlayingItem),
            )
        }
        Column(Modifier.widthIn(max = 200.dp).width(IntrinsicSize.Max)) {
            // The playing card's marker: this strip is what plays, not what the rail or details show.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(channelTitleText(channelNumber?.toLongOrNull(), channelName),
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    ChannelPlaybackMarker(playbackIndicator, size = 16.dp)
                }
            }
            Text(current?.title.orEmpty(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal val NowPlayingPlateWidth = 48.dp
internal val NowPlayingPlateHeight = 27.dp

/**
 * The playing channel's logo plate, or its number/placeholder without a logo. The plate carries [strip], the
 * progress strip; without it the strip would float under the logo. [contentAlpha] dims only the mark.
 */
@Composable
internal fun NowPlayingPlate(
    picon: ChannelIcon?,
    channelNumber: String?,
    modifier: Modifier = Modifier,
    contentAlpha: Float = 1f,
    strip: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.size(NowPlayingPlateWidth, NowPlayingPlateHeight).clip(PlayerChromeTokens.chipShape)
        .background(TvSurfaceColors.containerLowest), contentAlignment = Alignment.Center) {
        val mark = if (contentAlpha < 1f) Modifier.graphicsLayer { alpha = contentAlpha } else Modifier
        if (picon != null || !AppChannelNumbers.shown) PiconBox(picon, mark.padding(3.dp))
        else Text(channelNumber.orEmpty(), mark, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        strip()
    }
}
