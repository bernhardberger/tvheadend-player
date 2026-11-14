package at.bernhardberger.tvhplayer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvhplayer.core.playbackCardTint
import at.bernhardberger.tvhplayer.core.programmeArtworkModel
import at.bernhardberger.tvhplayer.images.usesCoverBox
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest

/** What leads the card: a wide picture, a whole poster, the channel's logo, or only text. */
internal enum class PlaybackPicture { Band, Poster, Logo, None }

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
    val request = rememberPlaybackArtworkRequest(art)
    val painter = rememberAsyncImagePainter(request)
    val pictureState by painter.state.collectAsState()
    val picture = playbackPicture(request != null, pictureState, picon != null)
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
        val body: @Composable (Modifier, ChannelIcon?, Modifier) -> Unit = {
                bodyModifier, linePicon, linePiconModifier ->
            PlaybackHeadingBody(linePicon, channel, airTime, measured.airTimeOwnRow, title, fit, episode,
                bodyModifier.padding(horizontal = CardPadding), linePiconModifier)
        }
        when (picture) {
            PlaybackPicture.Band -> Box {
                PictureBand(painter, fit.band.dp, surface)
                body(Modifier.padding(top = fit.band.dp - 16.dp), picon, piconModifier)
            }
            PlaybackPicture.Poster ->
                PosterHeading(painter, accent, picon, channel, airTime, title, fit, episode, piconModifier)
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

@Composable
private fun rememberPlaybackArtworkRequest(art: ChannelIcon?): ImageRequest? {
    val session = LocalArtworkContext.current.currentSession
    val context = LocalPlatformContext.current
    return remember(session, art, context) {
        if (art != null && session != null) {
            ImageRequest.Builder(context).data(programmeArtworkModel(art, session)).build()
        } else null
    }
}

private fun playbackPicture(hasRequest: Boolean, state: AsyncImagePainter.State, hasPicon: Boolean) = when {
    hasRequest && state !is AsyncImagePainter.State.Error ->
        (state as? AsyncImagePainter.State.Success)?.result?.image
            ?.takeIf { usesCoverBox(it.width, it.height) }?.let { PlaybackPicture.Poster } ?: PlaybackPicture.Band
    hasPicon -> PlaybackPicture.Logo
    else -> PlaybackPicture.None
}

private fun opaque(rgb: Int) = Color(0xFF000000.toInt() or rgb)

@Composable
private fun PlaybackCardLower(
    statusTop: Dp, recording: PlaybackCardRecording?, waiting: String?, buttons: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(start = CardPadding, end = CardPadding, bottom = CardPadding)) {
        if (recording != null) {
            Spacer(Modifier.height(statusTop))
            Row(Modifier.semantics(mergeDescendants = true) { contentDescription = recording.spokenStatus },
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(recordingMarkColor(focused = false), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(recording.status, style = MaterialTheme.typography.bodyMedium, color = TvTextColors.secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            ProgressStrip(recording.progress, Modifier.fillMaxWidth().clearAndSetSemantics { },
                color = recordingMarkColor(focused = false))
        }
        if (waiting != null) {
            Spacer(Modifier.height(16.dp))
            Text(waiting, style = MaterialTheme.typography.bodyLarge.balanced(), color = TvTextColors.secondary)
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
    enabled: Boolean = true,
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
    if (filled) Button(onClick, buttonModifier, enabled = enabled, scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        interactionSource = interaction, content = content)
    else OutlinedButton(onClick, buttonModifier, enabled = enabled,
        scale = OutlinedButtonDefaults.scale(focusedScale = 1f),
        contentPadding = OutlinedButtonDefaults.ButtonWithIconContentPadding,
        interactionSource = interaction, content = content)
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
internal val PlaybackCardWidth = 400.dp
internal const val PLAYBACK_CARD_MAX_HEIGHT = 476f
internal val CardPadding = 24.dp
internal const val PLAYBACK_CHANNEL_ROW = 20f
internal val InlineLogoSpace = 44.dp
private val LogoBandHeight = 136.dp
internal val LogoHeadingTop = 112.dp
internal val PosterWidth = 96.dp
internal val PosterHeight = 144.dp
internal val PosterColumnWidth = 240.dp
internal val BandAlignment = BiasAlignment(0f, -0.3f)
