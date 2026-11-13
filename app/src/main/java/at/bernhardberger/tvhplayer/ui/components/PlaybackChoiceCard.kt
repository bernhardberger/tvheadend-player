package at.bernhardberger.tvhplayer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvhplayer.core.PlaybackCardFit
import at.bernhardberger.tvhplayer.core.fitPlaybackCard
import at.bernhardberger.tvhplayer.core.playbackCardTint
import at.bernhardberger.tvhplayer.core.programmeArtworkModel
import at.bernhardberger.tvhplayer.images.usesCoverBox
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest

/** What leads the card: a wide picture, a whole poster, the channel's logo, or only text. */
private enum class PlaybackPicture { Band, Poster, Logo, None }

/** A recording's running state on the card: the status line and how far the recording has got. */
internal class PlaybackCardRecording(val status: String, val spokenStatus: String, val progress: Float)

/**
 * The recording playback card: a picture above the programme, its recording state, and a stack of
 * full-width choices. The heading gives way, never the choices, so the card stays under its cap.
 */
@Composable
internal fun PlaybackChoiceCard(
    art: ChannelIcon?,
    picon: ChannelIcon?,
    channel: String,
    airTime: String?,
    title: String,
    episode: String,
    recording: PlaybackCardRecording?,
    waiting: String?,
    buttonCount: Int,
    modifier: Modifier = Modifier,
    piconModifier: Modifier = Modifier,
    buttons: @Composable ColumnScope.() -> Unit,
) {
    val session = LocalArtworkContext.current.currentSession
    val context = LocalPlatformContext.current
    val request = remember(session, art, context) {
        if (art != null && session != null) ImageRequest.Builder(context).data(programmeArtworkModel(art, session)).build()
        else null
    }
    val painter = rememberAsyncImagePainter(request)
    val pictureState by painter.state.collectAsState()
    val picture = when {
        request != null && pictureState !is AsyncImagePainter.State.Error ->
            (pictureState as? AsyncImagePainter.State.Success)?.result?.image
                ?.takeIf { usesCoverBox(it.width, it.height) }?.let { PlaybackPicture.Poster } ?: PlaybackPicture.Band
        picon != null -> PlaybackPicture.Logo
        else -> PlaybackPicture.None
    }
    val neutral = MaterialTheme.colorScheme.background
    val detailsTint by rememberDetailsTint(art, picon)
    val tint = if (detailsTint == neutral) null else playbackCardTint(detailsTint.toArgb())
    val surface = tint?.let { opaque(it.surface) } ?: TvSurfaceColors.container
    val halo = tint?.let { opaque(it.halo) }
    val accent = tint?.let { opaque(it.accent) }
    val measured = rememberPlaybackCardFit(picture, picon != null, channel, airTime, title, episode,
        recording?.status, waiting, buttonCount)
    val fit = measured.fit
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.drawBehind {
            if (halo != null) {
                val radius = size.maxDimension * .75f
                drawCircle(Brush.radialGradient(listOf(halo.copy(alpha = .28f), Color.Transparent), center, radius),
                    radius, center)
            }
        }.width(PlaybackCardWidth).clip(shape).background(surface).testTag("centred-dialog-card"),
    ) {
        val body: @Composable (Modifier, ChannelIcon?, Modifier) -> Unit = { bodyModifier, linePicon, linePiconModifier ->
            PlaybackHeadingBody(linePicon, channel, airTime, measured.airTimeOwnRow, title, fit, episode,
                bodyModifier.padding(horizontal = CardPadding), linePiconModifier)
        }
        when (picture) {
            PlaybackPicture.Band -> Box {
                PictureBand(painter, fit.band.dp, surface)
                body(Modifier.padding(top = fit.band.dp - 16.dp), picon, piconModifier)
            }
            PlaybackPicture.Poster -> PosterHeading(painter, accent, picon, channel, airTime, title, fit, episode, piconModifier)
            PlaybackPicture.Logo -> Box {
                if (accent != null) Box(Modifier.fillMaxWidth().height(LogoBandHeight)
                    .background(Brush.verticalGradient(listOf(accent, surface))))
                PiconBox(picon, Modifier.padding(start = CardPadding, top = CardPadding).size(128.dp, 72.dp)
                    .then(piconModifier), alignment = Alignment.CenterStart, trim = true, ground = accent ?: surface)
                body(Modifier.padding(top = LogoHeadingTop), null, Modifier)
            }
            PlaybackPicture.None -> body(Modifier.padding(top = CardPadding), null, Modifier)
        }
        PlaybackCardLower(if (picture == PlaybackPicture.Poster) 16.dp else 12.dp, recording, waiting, buttons)
    }
}

private fun opaque(rgb: Int) = Color(0xFF000000.toInt() or rgb)

private class PlaybackCardMeasure(val fit: PlaybackCardFit, val airTimeOwnRow: Boolean)

/** Line counts measured at the card's text widths, fitted under the height cap. */
@Composable
private fun rememberPlaybackCardFit(
    picture: PlaybackPicture, hasPicon: Boolean, channel: String, airTime: String?, title: String,
    episode: String, status: String?, waiting: String?, buttonCount: Int,
): PlaybackCardMeasure {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val type = MaterialTheme.typography
    return remember(picture, hasPicon, channel, airTime, title, episode, status, waiting, buttonCount, density, type) {
        with(density) {
            val poster = picture == PlaybackPicture.Poster
            val column = (if (poster) PosterColumnWidth else PlaybackCardWidth - CardPadding * 2).roundToPx()
            fun measure(text: String, style: TextStyle, max: Int) = measurer.measure(text, style,
                overflow = TextOverflow.Ellipsis, maxLines = max, constraints = Constraints(maxWidth = column))
            fun lines(text: String, style: TextStyle, max: Int) = if (text.isBlank()) 0 else measure(text, style, max).lineCount
            // Heights as the text actually lays out, so the band only gives way when the card really overflows.
            fun height(text: String?, style: TextStyle, max: Int) =
                if (text.isNullOrBlank() || max == 0) 0f else measure(text, style, max).size.height.toDp().value
            val logo = if (hasPicon && picture != PlaybackPicture.Logo) InlineLogoSpace.roundToPx() else 0
            val ownRow = !poster && airTime != null && measurer.measure(channelLine(channel, airTime), type.labelMedium,
                maxLines = 1, softWrap = false).size.width > column - logo
            val channelRows = if (ownRow) ChannelRow * 2 + 4f else ChannelRow
            val waitingHeight = waiting?.let { 16f + height(it, type.bodyLarge, Int.MAX_VALUE) } ?: 0f
            val statusHeight = status?.let { (if (poster) 16f else 12f) + maxOf(8f, height(it, type.bodyMedium, 1)) + 12f + 4f } ?: 0f
            val stack = 24f + buttonCount * 40f + (buttonCount - 1).coerceAtLeast(0) * 12f + 24f
            val heading = { t: Int, e: Int ->
                val text = 8f + height(title, type.headlineMedium, t) + (if (e > 0) 4f + height(episode, type.bodyLarge, e) else 0f)
                when (picture) {
                    PlaybackPicture.Band -> -16f + channelRows + text
                    PlaybackPicture.Logo -> LogoHeadingTop.value + channelRows + text
                    PlaybackPicture.None -> CardPadding.value + channelRows + text
                    PlaybackPicture.Poster -> CardPadding.value +
                        maxOf(PosterHeight.value, ChannelRow + text + (if (airTime != null) 4f + ChannelRow else 0f))
                }
            }
            val fit = fitPlaybackCard(PlaybackCardMaxHeight, if (picture == PlaybackPicture.Band) 112f..160f else 0f..0f,
                lines(title, type.headlineMedium, 3), lines(episode, type.bodyLarge, 2)) { t, e ->
                heading(t, e) + statusHeight + waitingHeight + stack
            }
            PlaybackCardMeasure(fit, ownRow)
        }
    }
}

private fun channelLine(channel: String, airTime: String?) = listOfNotNull(channel.takeIf(String::isNotBlank), airTime)
    .joinToString(" · ")

@Composable
private fun PictureBand(painter: Painter, height: Dp, surface: Color) {
    Box(Modifier.fillMaxWidth().height(height)) {
        Image(painter, null, Modifier.fillMaxSize(), BandAlignment, ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to surface)))
    }
}

@Composable
private fun ChannelLine(picon: ChannelIcon?, channel: String, airTime: String?, airTimeOwnRow: Boolean, piconModifier: Modifier) {
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = ChannelRow.dp)
    ProgrammeEyebrow(picon, channelLine(channel, airTime.takeUnless { airTimeOwnRow }), rowModifier, piconModifier)
    if (airTime != null && airTimeOwnRow) {
        Spacer(Modifier.height(4.dp))
        AirTime(airTime)
    }
}

@Composable
private fun AirTime(airTime: String) {
    Box(Modifier.heightIn(min = ChannelRow.dp), contentAlignment = Alignment.CenterStart) {
        Text(airTime, style = MaterialTheme.typography.labelMedium, color = TvTextColors.tertiary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaybackTitle(title: String, fit: PlaybackCardFit, episode: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface, maxLines = fit.titleLines, overflow = TextOverflow.Ellipsis)
    if (fit.episodeLines > 0 && episode.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(episode, style = MaterialTheme.typography.bodyLarge, color = TvTextColors.secondary,
            maxLines = fit.episodeLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaybackHeadingBody(
    picon: ChannelIcon?, channel: String, airTime: String?, airTimeOwnRow: Boolean,
    title: String, fit: PlaybackCardFit, episode: String, modifier: Modifier, piconModifier: Modifier,
) {
    Column(modifier) {
        ChannelLine(picon, channel, airTime, airTimeOwnRow, piconModifier)
        Spacer(Modifier.height(8.dp))
        PlaybackTitle(title, fit, episode)
    }
}

/** A 2:3 poster is shown whole beside the heading rather than cropped into the band. */
@Composable
private fun PosterHeading(
    painter: Painter, accent: Color?, picon: ChannelIcon?, channel: String, airTime: String?,
    title: String, fit: PlaybackCardFit, episode: String, piconModifier: Modifier,
) {
    Row(Modifier.padding(start = CardPadding, end = CardPadding, top = CardPadding),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.drawBehind {
            if (accent != null) {
                val radius = size.maxDimension * .8f
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .7f), Color.Transparent), center, radius),
                    radius, center)
            }
        }.size(PosterWidth, PosterHeight).clip(MaterialTheme.shapes.small).background(TvSurfaceColors.containerLowest)) {
            Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.width(PosterColumnWidth)) {
            ChannelLine(picon, channel, null, false, piconModifier)
            Spacer(Modifier.height(8.dp))
            PlaybackTitle(title, fit, episode)
            if (airTime != null) {
                Spacer(Modifier.height(4.dp))
                AirTime(airTime)
            }
        }
    }
}

@Composable
private fun PlaybackCardLower(
    statusTop: Dp, recording: PlaybackCardRecording?, waiting: String?, buttons: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(start = CardPadding, end = CardPadding, bottom = CardPadding)) {
        if (recording != null) {
            Spacer(Modifier.height(statusTop))
            Row(Modifier.semantics(mergeDescendants = true) { contentDescription = recording.spokenStatus },
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(TvRecordingColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(recording.status, style = MaterialTheme.typography.bodyMedium, color = TvTextColors.secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            ProgressStrip(recording.progress, Modifier.fillMaxWidth().clearAndSetSemantics { }, color = TvRecordingColor)
        }
        if (waiting != null) {
            Spacer(Modifier.height(16.dp))
            Text(waiting, style = MaterialTheme.typography.bodyLarge, color = TvTextColors.secondary)
        }
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = buttons)
    }
}

/**
 * One full-width choice in the stack. Focus shows by fill alone, so nothing grows past the card's
 * keyline. Icon, label and [endsAt] form one centred group; the end time is the button's state, not
 * part of its name, and is left out of sight when it would not fit beside the whole label.
 */
@Composable
internal fun PlaybackChoiceButton(
    label: String,
    @DrawableRes icon: Int,
    endsAt: String?,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val content: @Composable RowScope.() -> Unit = {
        Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            LabelWithEndTime(label, endsAt, onFill = filled || focused)
        }
    }
    val buttonModifier = modifier.fillMaxWidth().height(40.dp)
        .semantics { if (endsAt != null) stateDescription = endsAt }
    if (filled) Button(onClick, buttonModifier, scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding, interactionSource = interaction, content = content)
    else OutlinedButton(onClick, buttonModifier, scale = OutlinedButtonDefaults.scale(focusedScale = 1f),
        contentPadding = OutlinedButtonDefaults.ButtonWithIconContentPadding, interactionSource = interaction, content = content)
}

/** The label never shortens for the end time: the time follows on its baseline only when both fit. */
@Composable
private fun LabelWithEndTime(label: String, endsAt: String?, onFill: Boolean) {
    Layout({
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (endsAt != null) PlaybackEndTime(endsAt, onFill)
    }) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val labelPlaceable = measurables[0].measure(loose)
        val gap = EndTimeGap.roundToPx()
        val endPlaceable = measurables.getOrNull(1)?.measure(Constraints())
            ?.takeIf { labelPlaceable.width + gap + it.width <= constraints.maxWidth }
        val labelBaseline = labelPlaceable[FirstBaseline]
        val endBaseline = endPlaceable?.get(FirstBaseline) ?: labelBaseline
        val baseline = maxOf(labelBaseline, endBaseline)
        val height = maxOf(baseline - labelBaseline + labelPlaceable.height,
            endPlaceable?.let { baseline - endBaseline + it.height } ?: 0)
        val width = labelPlaceable.width + (endPlaceable?.let { gap + it.width } ?: 0)
        layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            labelPlaceable.place(0, baseline - labelBaseline)
            endPlaceable?.place(labelPlaceable.width + gap, baseline - endBaseline)
        }
    }
}

/** Metadata tone: on a pill's fill the button's own content colour, otherwise tertiary text. */
@Composable
private fun PlaybackEndTime(text: String, onFill: Boolean) {
    Text(text, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelMedium,
        color = if (onFill) LocalContentColor.current.copy(alpha = TvTextTertiaryAlpha) else TvTextColors.tertiary,
        maxLines = 1, softWrap = false)
}

private val EndTimeGap = 8.dp
private val PlaybackCardWidth = 400.dp
private const val PlaybackCardMaxHeight = 476f
private val CardPadding = 24.dp
private const val ChannelRow = 20f
private val InlineLogoSpace = 44.dp
private val LogoBandHeight = 136.dp
private val LogoHeadingTop = 112.dp
private val PosterWidth = 96.dp
private val PosterHeight = 144.dp
private val PosterColumnWidth = 240.dp
private val BandAlignment = BiasAlignment(0f, -0.3f)
