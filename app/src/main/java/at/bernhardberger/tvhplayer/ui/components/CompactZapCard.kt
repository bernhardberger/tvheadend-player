package at.bernhardberger.tvhplayer.ui.components

import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayBoxAlpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import androidx.tv.material3.ProvideTextStyle
import androidx.compose.foundation.layout.RowScope
import at.bernhardberger.tvhplayer.ui.common.formatClock
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * without one the card shows the channel logo as the rail does. A grid asks for its rows'
 * [preferredHeight]; the card never gets shorter than its text needs.
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
    preferredHeight: Dp? = null,
) {
    val contextMenu = rememberBrowseContextMenuTrigger(onLongClick, menuKey = menuKey)
    val currentEvent = event?.takeIf { it.start.epochSeconds <= nowSec && nowSec < it.stop.epochSeconds }
    // The programme leads; without one, the channel does.
    val programme = currentEvent?.title?.takeIf { it.isNotBlank() }
    val identity = channelTitleText(number, channel.name.orEmpty())
    val tag = "$tagPrefix-${channel.id.value}"
    // The tag band and logo room stay fixed; the two text lines grow with font scale.
    val typography = MaterialTheme.typography
    val textBlockHeight = compactZapTextBlockHeight()
    val cardHeight = CompactZapTagBand + CompactZapLogoRoom + textBlockHeight
    val currentSession = LocalArtworkContext.current.currentSession
    val artwork = currentEvent?.image?.takeIf { programmePicture && currentSession != null }
    var artworkFailed by remember(artwork, currentSession) { mutableStateOf(false) }
    val showArtwork = artwork != null && !artworkFailed
    CompactCard(
        onClick = onClick,
        onLongClick = contextMenu.onLongClick,
        border = embeddedProgressCardBorder(),
        // The kit scrim keeps text legible over artwork; the logo card's text sits on a flat surface.
        scrimBrush = if (showArtwork) compactZapTextScrim(cardHeight, textBlockHeight) else SolidColor(Color.Transparent),
        modifier = modifier.then(contextMenu.modifier),
        image = {
            Box(Modifier.fillMaxWidth().height(maxOf(cardHeight, preferredHeight ?: cardHeight)).background(TvSurfaceColors.containerLowest)) {
                if (artwork != null && currentSession != null && !artworkFailed) AsyncImage(
                    model = AppArtworkSource(currentSession, artwork), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().testTag("$tag-art"), onError = { artworkFailed = true },
                ) else Box(
                    Modifier.fillMaxSize().padding(top = CompactZapTagBand, bottom = textBlockHeight + 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // The logo fills the room between the tags and the text, so a taller card shows a larger logo.
                    PiconBox(
                        piconPath = channel.icon,
                        modifier = Modifier.fillMaxWidth(CompactZapLogoWidthFraction).fillMaxHeight().testTag("$tag-picon"),
                    )
                }
                // The kit's embed: tags in the picture's top corners, clear of the text below.
                // The programme and channel are read first; the start time is visual context.
                if (programme != null && currentEvent != null) CompactZapTag(Modifier.align(Alignment.TopStart).clearAndSetSemantics { }) {
                    Text(formatClock(currentEvent.start.epochSeconds), maxLines = 1, softWrap = false,
                        modifier = Modifier.testTag("$tag-start"))
                }
                val marked = playbackIndicator != ChannelPlaybackIndicator.NONE || recording
                if (marked || currentEvent != null) CompactZapTag(Modifier.align(Alignment.TopEnd)) {
                    if (currentEvent != null) Text(
                        stringResource(R.string.quick_zap_minutes_left, ((currentEvent.stop.epochSeconds - nowSec + 59) / 60).toInt()),
                        maxLines = 1, softWrap = false, modifier = Modifier.testTag("$tag-left"),
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
                            recordingNow = recording, playbackIndicator = indicator, iconSize = CompactZapTagIconSize,
                            modifier = if (currentEvent != null) Modifier.padding(start = 6.dp) else Modifier)
                    }
                }
            }
        },
        title = {
            Text(
                text = programme ?: identity,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    .testTag("$tag-${if (programme != null) "now" else "identity"}"),
            )
        },
        subtitle = {
            Text(
                text = if (programme != null) identity else stringResource(R.string.no_epg),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = CompactZapStripRoom - 2.dp)
                    .testTag("$tag-${if (programme != null) "identity" else "now"}"),
            )
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

/**
 * The card's height without a caller's preference: the tag band, the logo room and the two text
 * lines at the current font scale. A grid plans its rows with it.
 */
@Composable
internal fun compactZapCardMinHeight(): Dp = CompactZapTagBand + CompactZapLogoRoom + compactZapTextBlockHeight()

@Composable
private fun compactZapTextBlockHeight(): Dp = with(LocalDensity.current) {
    MaterialTheme.typography.titleMedium.lineHeight.toDp() + MaterialTheme.typography.bodySmall.lineHeight.toDp() + CompactZapStripRoom
}

/**
 * The kit's bottom scrim, but solid behind both text lines: it starts clear above the text and is
 * dark by the title's top, so lettering in the picture cannot run into the text.
 */
private fun compactZapTextScrim(cardHeight: Dp, textBlockHeight: Dp): Brush {
    val textTop = ((cardHeight - textBlockHeight) / cardHeight).coerceIn(0f, 1f)
    return Brush.verticalGradient(
        0f to Color.Transparent,
        (textTop - CompactZapScrimRamp).coerceAtLeast(0f) to Color.Transparent,
        textTop to Color.Black.copy(alpha = TvOverlayBoxAlpha),
        1f to Color.Black.copy(alpha = TvPanelDenseAlpha),
    )
}

/** The scrim's ramp above the text, as a fraction of the card's height. */
private const val CompactZapScrimRamp = 0.3f

/** The kit's embed tag: a small label pill 8dp in from a corner, on a mostly opaque dark fill. */
@Composable
private fun CompactZapTag(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.padding(8.dp)
            .background(Color.Black.copy(alpha = TvOverlayBoxAlpha), CompactZapTagShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = TvTextSecondaryAlpha))) {
            content()
        }
    }
}

/** The tags' band at the top of the picture: 8dp inset, a 16dp label line and 4dp clearance. */
private val CompactZapTagBand = 28.dp

/** Between the tags and the text: room for the logo; the card is 196x126 at font scale 1.0. */
private val CompactZapLogoRoom = 50.dp

/** The subtitle's gap to the strip plus the strip itself. */
private val CompactZapStripRoom = 8.dp

private const val CompactZapLogoWidthFraction = 0.6f

private val CompactZapTagShape = RoundedCornerShape(12.dp)

/** Markers fit the tag's label line instead of growing the pill. */
private val CompactZapTagIconSize = 12.dp
