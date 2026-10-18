package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.leaving
import coil3.compose.AsyncImage

/** Width of a compact zap card in a row; grids let the card fill its column instead. */
internal val CompactZapCardWidth = 196.dp

/**
 * The compact channel card shared by the player rail and the Channels card layouts.
 * Callers size it: the rail uses [CompactZapCardWidth], a grid fills its column.
 * [programmePicture] leads with the current programme's picture under the kit scrim;
 * without one the card shows the channel logo as the rail does.
 */
@Composable
internal fun CompactZapCard(
    channel: Channel,
    number: Long?,
    event: EpgEvent?,
    nowSec: Long,
    playbackIndicator: ChannelPlaybackIndicator,
    recording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    tagPrefix: String = "player-channel",
    menuKey: Boolean = false,
    programmePicture: Boolean = false,
) {
    val contextMenu = rememberBrowseContextMenuTrigger(onLongClick, menuKey = menuKey)
    val currentEvent = event?.takeIf { it.start.epochSeconds <= nowSec && nowSec < it.stop.epochSeconds }
    // The programme leads; without one, the channel does.
    val programme = currentEvent?.title?.takeIf { it.isNotBlank() }
    val identity = channelTitleText(number, channel.name.orEmpty())
    val tag = "$tagPrefix-${channel.id.value}"
    // Preserve the kit's 54dp picon region; let the two text baselines grow with font scale.
    val cardHeight = with(LocalDensity.current) {
        70.dp + MaterialTheme.typography.titleMedium.lineHeight.toDp() + MaterialTheme.typography.bodySmall.lineHeight.toDp()
    }
    val currentSession = LocalArtworkContext.current.currentSession
    val artwork = currentEvent?.image?.takeIf { programmePicture && currentSession != null }
    var artworkFailed by remember(artwork, currentSession) { mutableStateOf(false) }
    val showArtwork = artwork != null && !artworkFailed
    CompactCard(
        onClick = onClick,
        onLongClick = contextMenu.onLongClick,
        border = embeddedProgressCardBorder(),
        // The kit scrim keeps text legible over artwork; the logo card's text sits on a flat surface.
        scrimBrush = if (showArtwork) CardDefaults.ScrimBrush else SolidColor(Color.Transparent),
        modifier = modifier.then(contextMenu.modifier),
        image = {
            Box(Modifier.fillMaxWidth().height(cardHeight).background(TvSurfaceColors.containerLowest)) {
                if (artwork != null && currentSession != null && !artworkFailed) AsyncImage(
                    model = AppArtworkSource(currentSession, artwork), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().testTag("$tag-art"), onError = { artworkFailed = true },
                ) else PiconBox(
                    piconPath = channel.icon,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(100.dp, 45.dp)
                        .testTag("$tag-picon"),
                )
            }
        },
        title = {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = programme ?: identity,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("$tag-${if (programme != null) "now" else "identity"}"),
                )
                // The new marker fades in; the old one leaves at once so two glyphs never
                // overlap in the slot and its tag and announcement go with it.
                AnimatedContent(
                    targetState = playbackIndicator,
                    transitionSpec = {
                        (fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate)) togetherWith
                            ExitTransition.None).using(null)
                    },
                    label = "zap-card-marker",
                ) { indicator ->
                    if (!leaving) ChannelNowIndicators(playingNow = indicator == ChannelPlaybackIndicator.PLAYING,
                        recordingNow = recording, playbackIndicator = indicator)
                }
            }
        },
        subtitle = {
            // The picon already names the channel, so only its text may shorten; time left stays whole.
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                Text(
                    text = if (programme != null) identity else stringResource(R.string.no_epg),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                        .testTag("$tag-${if (programme != null) "identity" else "now"}"),
                )
                if (programme != null && currentEvent != null) {
                    Text(
                        stringResource(R.string.quick_zap_minutes_left, ((currentEvent.stop.epochSeconds - nowSec + 59) / 60).toInt()),
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.testTag("$tag-left"),
                    )
                }
            }
        },
        description = {
            if (currentEvent != null) {
                ProgressStrip(
                    progress = ((nowSec - currentEvent.start.epochSeconds).toDouble() /
                        (currentEvent.stop.epochSeconds - currentEvent.start.epochSeconds)).toFloat(),
                    height = 2.dp,
                    modifier = Modifier.fillMaxWidth().testTag("$tag-progress"),
                )
            } else Box(Modifier.fillMaxWidth().height(2.dp))
        },
    )
}
