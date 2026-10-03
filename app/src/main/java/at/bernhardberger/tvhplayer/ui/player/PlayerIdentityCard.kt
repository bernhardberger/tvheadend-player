package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.tv.material3.CardDefaults
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
private val CardWidth = PlayerChromeTokens.channelCardWidth
private val CardHeight = PlayerChromeTokens.channelCardHeight

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
    /** Trial: Left/Right on the focused card open the channel rail. */
    channelCard: Boolean = false,
    /** Trial: the last other channel, a row of its own above the focused card. */
    recent: RecentChannelPeek? = null,
    /** Trial: focuses the recent card; Up on the channel card requests it. */
    recentFocus: FocusRequester? = null,
    onRecentClick: () -> Unit = {},
    /** Trial: keeps the focused look while the rail stands in for the card. */
    held: Boolean = false,
) {
    val opens = stringResource(if (channelCard) R.string.player_channel_card_hint else R.string.player_info)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val mainFocus = remember { FocusRequester() }
    var recentFocused by remember { mutableStateOf(false) }
    // The tag stands before anything that clears semantics, which would drop it with the rest.
    val tagged = modifier.testTag(PlayerIdentityCardTag).size(CardWidth, CardHeight)
    Box {
    CompactCard(
        onClick = onClick ?: {},
        interactionSource = interactions,
        border = embeddedProgressCardBorder(held),
        scale = channelCardScale(held),
        scrimBrush = SolidColor(Color.Transparent),
        modifier = if (onClick == null) tagged.clearAndSetSemantics {}.focusProperties { canFocus = false } else {
            tagged.focusRequester(mainFocus).semantics { contentDescription = opens }
        },
        image = {
            IdentityCardImage(content, imageLoader, currentSession, cue = onClick != null && !channelCard, focused = focused)
            ChannelCardLabel(content.info.channelNumber, content.info.channelName.takeIf { content.picon != null })
            if (channelCard && onClick != null && (focused || held)) {
                // Inside the card beside the logo, on its line: the card steps through channels. In the
                // card's own slot they zoom with it and stay above the focused card's raised surface.
                val tint = MaterialTheme.colorScheme.onSurface
                Icon(painterResource(R.drawable.ic_keyboard_arrow_left), contentDescription = null, tint = tint,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = ArrowInset, bottom = PiconLift)
                        .size(24.dp).testTag("player-identity-previous"))
                Icon(painterResource(R.drawable.ic_keyboard_arrow_right), contentDescription = null, tint = tint,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = ArrowInset, bottom = PiconLift)
                        .size(24.dp).testTag("player-identity-next"))
            }
        },
        title = {},
    )
    if (channelCard && onClick != null && recent != null && recentFocus != null) {
        // A row of its own above the card, without taking room in the info bar.
        Box(Modifier.align(Alignment.TopStart).layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = 1200.dp.roundToPx(),
                maxHeight = Constraints.Infinity))
            layout(0, 0) { placeable.place(0, -placeable.height) }
        }) {
            AnimatedVisibility(
                visible = focused || recentFocused || held,
                enter = fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate)),
                exit = fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate)),
            ) {
                Column(Modifier.padding(bottom = PlayerChromeTokens.gridGutter).testTag("player-identity-recent-row")) {
                    Text(stringResource(R.string.player_row_last_channel), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                        modifier = Modifier.padding(bottom = PlayerChromeTokens.rowTitleGap).clearAndSetSemantics {})
                    CompactCard(
                        onClick = { onRecentClick(); mainFocus.requestFocus() },
                        border = embeddedProgressCardBorder(),
                        scale = channelCardScale(),
                        scrimBrush = SolidColor(Color.Transparent),
                        modifier = Modifier.size(CardWidth, CardHeight).focusRequester(recentFocus)
                            .onFocusChanged { recentFocused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (event.type == KeyEventType.KeyDown) mainFocus.requestFocus()
                                        true
                                    }
                                    Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> true
                                    else -> false
                                }
                            }
                            .semantics { contentDescription = listOf(recent.number, recent.name).filter(String::isNotBlank).joinToString(" ") }
                            .testTag("player-identity-recent"),
                        image = {
                            ChannelCardFace(recent.picon, recent.channelId, recent.name, imageLoader, currentSession)
                            ChannelCardLabel(recent.number, recent.name.takeIf { recent.picon != null }, tag = "player-identity-recent-label")
                        },
                        title = {},
                    )
                }
            }
        }
    }
    }
}

/** Trial: the channel cards' scale; [held] keeps the focused size without focus, so nothing grows or shrinks. */
internal fun channelCardScale(held: Boolean = false) = CardDefaults.scale(
    scale = if (held) PlayerChromeTokens.cardFocusedScale else 1f,
    focusedScale = PlayerChromeTokens.cardFocusedScale,
)

/** Trial: an arrow's inset from the card's edge, in the room beside the logo. */
private val ArrowInset = 4.dp

/**
 * Trial: "number name" along a channel card's bottom, in the player and in the rail alike. It stands
 * clear of the progress along the card's bottom edge, whether the card shows one or not, so it never
 * moves between them. A card without a picon shows its name on the face, and only the number here.
 */
@Composable
internal fun BoxScope.ChannelCardLabel(
    number: String?,
    name: String?,
    tag: String = "player-identity-label",
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val text = listOfNotNull(number?.takeIf(String::isNotBlank), name?.takeIf(String::isNotBlank)).joinToString(" ")
    Row(
        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = ChannelCardLabelBottom),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).testTag(tag))
        trailing()
    }
}

/** The label's distance from the card's bottom edge: the progress's 3dp and the gap above it. */
private val ChannelCardLabelBottom = 12.dp

/** Trial: the last other channel, shown above the channel card. */
internal data class RecentChannelPeek(
    val picon: ArtworkId?,
    val number: String,
    val name: String,
    val channelId: ChannelId? = null,
    /** The programme on now, if known. */
    val now: String? = null,
)

/** The picon's room: centred, lifted clear of the label along the card's bottom edge. */
private val PiconWidth = 136.dp
private val PiconHeight = 60.dp
private val PiconLift = 12.dp

/**
 * Trial: a channel card's face, shared by the player's channel card and the rail's: the channel's
 * own colour behind its picon, or its name without one.
 */
@Composable
internal fun BoxScope.ChannelCardFace(
    picon: ArtworkId?,
    channelId: ChannelId?,
    name: String,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    logoTag: String = "player-identity-logo",
    nameTag: String = "player-identity-name",
    backdrop: Boolean = true,
) {
    val tint = channelId?.let { rememberChannelAccent(imageLoader, currentSession, picon, it) }
    // Fills the card, which sizes the slot the mark is centred in.
    Box(Modifier.fillMaxSize().then(if (!backdrop) Modifier else Modifier.background(TvSurfaceColors.containerLow).then(
        if (tint != null) Modifier.background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.48f), tint.copy(alpha = 0.16f)))) else Modifier,
    )))
    val mark = Modifier.align(Alignment.Center).padding(bottom = PiconLift).size(PiconWidth, PiconHeight)
    if (picon != null) {
        PiconBox(imageLoader, picon, mark.testTag(logoTag), currentSession)
    } else {
        Box(mark, contentAlignment = Alignment.Center) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag(nameTag))
        }
    }
}

@Composable
private fun BoxScope.IdentityCardImage(
    content: PlayerChromeContent,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    cue: Boolean,
    focused: Boolean,
) {
    val artwork = remember(currentSession, content.artwork) {
        currentSession?.let { session -> ArtworkId.parse(content.artwork)?.let { AppArtworkSource(session, it) } }
    }
    if (artwork == null) {
        ChannelCardFace(content.picon, content.channelId, content.info.channelName, imageLoader, currentSession)
    } else {
        Box(Modifier.fillMaxSize().background(TvSurfaceColors.containerLow)) {
            // The picture is a backdrop: dimmed where it is drawn, so the picon and the number read over it.
            AsyncImage(artwork, null, imageLoader, Modifier.fillMaxSize().testTag("player-identity-artwork"), contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = ArtworkScrim), BlendMode.SrcAtop))
            ChannelCardFace(content.picon, null, content.info.channelName, imageLoader, currentSession, backdrop = false)
        }
    }
    if (cue) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (focused) 1f else 0.8f),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp).testTag("player-identity-info-cue"))
    }
}

private const val ArtworkScrim = 0.68f
