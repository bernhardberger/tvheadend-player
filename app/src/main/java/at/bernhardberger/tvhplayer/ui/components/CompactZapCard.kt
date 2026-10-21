package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.images.LogoHaloTransformation
import at.bernhardberger.tvhplayer.images.ScrimTransformation
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.leaving
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations

/** Width of a compact zap card in a row; grids let the card fill its column instead. */
internal val CompactZapCardWidth = 196.dp

/**
 * The compact channel card shared by the player rail and the Channels card layouts.
 * Callers size it: the rail uses [CompactZapCardWidth], a grid fills its column.
 * [programmePicture] leads with the current programme's picture under the kit scrim;
 * without one the card shows the channel logo as the rail does. A grid asks for its rows'
 * [preferredHeight]; the card never gets shorter than its text needs. A [channelLed] card names
 * the channel first and starts its programme line with the start time; otherwise the programme
 * leads and time left follows the channel. While the guide is [programmeLoading], a missing
 * programme line is a [SkeletonLine] rather than "No program information".
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
    channelLed: Boolean = false,
    programmeLoading: Boolean = false,
) {
    val contextMenu = rememberBrowseContextMenuTrigger(onLongClick, menuKey = menuKey)
    val currentEvent = event?.takeIf { it.start.epochSeconds <= nowSec && nowSec < it.stop.epochSeconds }
    // The programme leads unless the card is channel-led; without one, the channel does.
    val programme = currentEvent?.title?.takeIf { it.isNotBlank() }
    val identity = channelTitleText(number, channel.name.orEmpty())
    val tag = "$tagPrefix-${channel.id.value}"
    val textBlockHeight = compactZapTextBlockHeight()
    val imageHeight = maxOf(compactZapCardMinHeight(), preferredHeight ?: 0.dp)
    val currentSession = LocalArtworkContext.current.currentSession
    val artwork = currentEvent?.image?.takeIf { programmePicture && currentSession != null }
    var artworkFailed by remember(artwork, currentSession) { mutableStateOf(false) }
    val showArtwork = artwork != null && !artworkFailed
    val context = LocalPlatformContext.current
    val textTop = ((imageHeight - textBlockHeight) / imageHeight).coerceIn(0f, 1f)
    // A channel-led card's scrim is drawn into its picture once rather than over it every frame.
    val artworkModel = remember(artwork, currentSession, channelLed, textTop) {
        val source = if (artwork != null && currentSession != null) AppArtworkSource(currentSession, artwork) else null
        if (source == null || !channelLed) source
        else ImageRequest.Builder(context).data(source).transformations(compactZapTextScrim(textTop)).build()
    }
    // A channel-led card keeps its logo over the picture, with a halo of its own so it reads
    // without the whole picture darkening.
    val logoOverArt = channelLed && showArtwork
    val halo = if (logoOverArt) CompactZapLogoHalo else 0.dp
    val haloPx = with(LocalDensity.current) { halo.roundToPx() }
    val haloTransformation = remember(haloPx) { if (haloPx > 0) LogoHaloTransformation(haloPx, CompactZapLogoHaloAlpha) else null }
    CompactCard(
        onClick = onClick,
        onLongClick = contextMenu.onLongClick,
        border = embeddedProgressCardBorder(),
        // A scrim keeps text legible over artwork; the logo card's text sits on a flat surface.
        // The player rail keeps the kit's.
        scrimBrush = if (showArtwork && !channelLed) CardDefaults.ScrimBrush else SolidColor(Color.Transparent),
        modifier = modifier.then(contextMenu.modifier),
        image = {
            Box(Modifier.fillMaxWidth().height(imageHeight).background(TvSurfaceColors.containerLowest)) {
                if (artworkModel != null && !artworkFailed) AsyncImage(
                    model = artworkModel, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().testTag("$tag-art"), onError = { artworkFailed = true },
                ) else if (channelLed) {
                    // The channel's own colour from its logo, as the player's hero falls back to.
                    val accent = rememberChannelAccent(channel.icon, channel.id)
                    val top = accent.copy(alpha = CompactZapAccentAlpha).toArgb()
                    Box(Modifier.fillMaxSize().unditheredGradient { size ->
                        LinearGradient(0f, 0f, 0f, size.height, top, 0, Shader.TileMode.CLAMP)
                    })
                }
                if (!showArtwork || logoOverArt) Box(
                    // The room reaches into the title's leading, so the logo centres between the card's
                    // top and the title's letters rather than above its line box. The inset keeps a
                    // tall logo clear of both; a wide one is bound by its width instead.
                    Modifier.fillMaxSize().padding(
                        top = CompactZapLogoTop + CompactZapLogoInset,
                        bottom = textBlockHeight - CompactZapLogoTop + CompactZapLogoInset,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    // The logo fills the room above the text, so a taller card shows a larger logo; its
                    // halo spreads past the room into the picture.
                    PiconBox(
                        piconPath = channel.icon,
                        modifier = Modifier.fillMaxWidth(CompactZapLogoWidthFraction).fillMaxHeight().outset(halo)
                            .testTag("$tag-picon"),
                        transformation = haloTransformation,
                    )
                }
            }
        },
        title = {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (channelLed) identity else programme ?: identity,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("$tag-${if (programme != null && !channelLed) "now" else "identity"}"),
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
            // Only the text shortens; time left stays whole.
            val subtitleModifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = CompactZapStripRoom - 2.dp)
            if (channelLed && programme != null && currentEvent != null) {
                ChannelProgrammeSubtitle(programme, currentEvent.start.epochSeconds, subtitleModifier.testTag("$tag-now"))
            } else if (programme == null && programmeLoading) {
                SkeletonLine(subtitleModifier.testTag("$tag-loading"))
            } else Row(subtitleModifier) {
                Text(
                    text = if (programme != null && !channelLed) identity else stringResource(R.string.no_epg),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                        .testTag("$tag-${if (programme != null && !channelLed) "identity" else "now"}"),
                )
                if (!channelLed && programme != null && currentEvent != null) {
                    Text(
                        stringResource(R.string.quick_zap_minutes_left, minutesLeft(currentEvent, nowSec)),
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

private fun minutesLeft(event: EpgEvent, nowSec: Long): Int = ((event.stop.epochSeconds - nowSec + 59) / 60).toInt()

/**
 * The card's height without a caller's preference: the kit's 196x110 at font scale 1.0, with the
 * two text lines growing with font scale. A grid plans its rows with it.
 */
@Composable
internal fun compactZapCardMinHeight(): Dp = CompactZapPictureRoom + compactZapTextBlockHeight()

@Composable
private fun compactZapTextBlockHeight(): Dp = with(LocalDensity.current) {
    MaterialTheme.typography.titleMedium.lineHeight.toDp() + MaterialTheme.typography.bodySmall.lineHeight.toDp() + CompactZapStripRoom
}

/**
 * The kit's scrim colour, but darker where the text starts: the kit's straight ramp leaves
 * lettering in the picture readable behind the subtitle. [textTop] is the text's top as a fraction
 * of the picture's height.
 */
private fun compactZapTextScrim(textTop: Float) = ScrimTransformation(
    positions = floatArrayOf(0f, textTop, 1f),
    colors = intArrayOf(
        CompactZapScrimColor.copy(alpha = 0f).toArgb(),
        CompactZapScrimColor.copy(alpha = CompactZapScrimTextAlpha).toArgb(),
        CompactZapScrimColor.copy(alpha = CompactZapScrimBottomAlpha).toArgb(),
    ),
)

/** The halo's strength right next to a logo over a picture. */
private const val CompactZapLogoHaloAlpha = 0.6f

/** How far a logo's halo reaches past the logo over a picture. */
private val CompactZapLogoHalo = 16.dp

/** Grows a node by [extra] on every side beyond its constraints, centred over its own slot. */
private fun Modifier.outset(extra: Dp): Modifier = if (extra == 0.dp) this else layout { measurable, constraints ->
    val px = extra.roundToPx()
    val placeable = measurable.measure(constraints.copy(
        minWidth = constraints.minWidth + 2 * px, maxWidth = constraints.maxWidth + 2 * px,
        minHeight = constraints.minHeight + 2 * px, maxHeight = constraints.maxHeight + 2 * px,
    ))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-px, -px) }
}

/** The kit's card scrim colour. */
private val CompactZapScrimColor = Color(28, 27, 31)

/** The kit reaches about 0.5 at the text's top and 0.8 at the bottom. */
private const val CompactZapScrimTextAlpha = 0.8f
private const val CompactZapScrimBottomAlpha = 0.95f

/** The picture's room above the text block, so the card is 196x110 at font scale 1.0. */
private val CompactZapPictureRoom = 62.dp

/** The logo's clearance from the card's top, and roughly the title's leading above its letters. */
private val CompactZapLogoTop = 6.dp

/** The channel colour at the logo card's top, fading out toward its text; the player's hero uses the same. */
private const val CompactZapAccentAlpha = 0.48f

/** A tall logo's clearance above and below, beyond [CompactZapLogoTop]. */
private val CompactZapLogoInset = 8.dp

/** The subtitle's gap to the strip plus the strip itself. */
private val CompactZapStripRoom = 8.dp

private const val CompactZapLogoWidthFraction = 0.6f

