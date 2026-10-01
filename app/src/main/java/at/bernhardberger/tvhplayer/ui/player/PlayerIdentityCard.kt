package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CompactCard
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.embeddedProgressCardBorder
import at.bernhardberger.tvhplayer.ui.components.rememberChannelAccent
import coil3.ImageLoader
import coil3.compose.AsyncImage

/** The card's test tag, and the [PlayerChromeActions.restoreFocus] token that returns focus to it. */
internal const val PlayerIdentityCardTag = "player-identity-card"

/** One size for every channel and programme, so the programme block never moves with it. */
private val CardWidth = 160.dp
private val CardHeight = 90.dp
/** The picon's room, centred in the whole card. */
private val PiconWidth = 112.dp
private val PiconHeight = 52.dp

/**
 * The info bar's identity, in the quick-zap card's kit: a [CompactCard] whose image slot holds the
 * channel's picon (or name), centred over the channel's own colour or the programme's dimmed
 * artwork, and whose title slot holds the number. With [onClick] (the controls) it takes focus,
 * says what it opens and always shows a small info cue; the caller's [modifier] places it in the
 * focus graph. Without [onClick] (the Banner) it takes no focus and says nothing.
 */
@Composable
internal fun PlayerIdentityCard(
    content: PlayerChromeContent,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val opens = stringResource(R.string.player_info)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    // The tag stands before anything that clears semantics, which would drop it with the rest.
    val tagged = modifier.testTag(PlayerIdentityCardTag).size(CardWidth, CardHeight)
    CompactCard(
        onClick = onClick ?: {},
        interactionSource = interactions,
        border = embeddedProgressCardBorder(),
        scrimBrush = SolidColor(Color.Transparent),
        modifier = if (onClick == null) tagged.clearAndSetSemantics {}.focusProperties { canFocus = false } else {
            tagged.semantics { contentDescription = opens }
        },
        image = { IdentityCardImage(content, imageLoader, currentSession, cue = onClick != null, focused = focused) },
        title = {
            if (content.info.channelNumber.isNotBlank()) {
                Text(content.info.channelNumber, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), maxLines = 1,
                    modifier = Modifier.padding(start = 10.dp, bottom = 5.dp).testTag("player-identity-number"))
            }
        },
    )
}

@Composable
private fun BoxScope.IdentityCardImage(
    content: PlayerChromeContent,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    cue: Boolean,
    focused: Boolean,
) {
    val picon = content.picon
    val artwork = remember(currentSession, content.artwork) {
        currentSession?.let { session -> ArtworkId.parse(content.artwork)?.let { AppArtworkSource(session, it) } }
    }
    val tint = content.channelId?.let { rememberChannelAccent(imageLoader, currentSession, picon, it) }
    Box(Modifier.fillMaxSize().background(TvSurfaceColors.containerLow).then(
        if (tint != null) Modifier.background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.48f), tint.copy(alpha = 0.16f)))) else Modifier,
    )) {
        // The picture is a backdrop: dimmed where it is drawn, so the picon and the number read over
        // any of it and the channel's colour stays as it is until the picture has loaded.
        if (artwork != null) {
            AsyncImage(artwork, null, imageLoader, Modifier.fillMaxSize().testTag("player-identity-artwork"), contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = ArtworkScrim), BlendMode.SrcAtop))
        }
        val mark = Modifier.align(Alignment.Center).size(PiconWidth, PiconHeight)
        if (picon != null) {
            PiconBox(imageLoader, picon, mark.testTag("player-identity-logo"), currentSession)
        } else {
            Box(mark, contentAlignment = Alignment.Center) {
                Text(content.info.channelName, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("player-identity-name"))
            }
        }
        if (cue) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (focused) 1f else 0.8f),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp).testTag("player-identity-info-cue"))
        }
    }
}

private const val ArtworkScrim = 0.68f
