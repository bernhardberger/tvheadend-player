package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.compose.ui.graphics.Shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusProperties
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
    /** Trial: the recent channels, newest first, a row above the card that Up opens. */
    recents: List<RecentChannelPeek> = emptyList(),
    recentOpen: Boolean = false,
    onRecentOpenChange: (Boolean) -> Unit = {},
    /** Trial: OK on a recent chip, by its index in [recents]. */
    onRecentClick: (Int) -> Unit = {},
    /** Trial: a key moving through the recent row, which keeps the controls up. */
    onRecentInteraction: () -> Unit = {},
    /** Trial: keeps the focused look while the rail stands in for the card. */
    held: Boolean = false,
) {
    val opens = stringResource(if (channelCard) R.string.player_channel_card_hint else R.string.player_info)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val mainFocus = remember { FocusRequester() }
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
    if (channelCard && onClick != null && recents.isNotEmpty()) {
        RecentRow(recents, recentOpen, onRecentOpenChange, onRecentClick, onRecentInteraction, mainFocus, imageLoader,
            currentSession, hint = focused && !held)
    }
    }
}

/**
 * Trial: the recent channels, a row of logo chips above the channel card. Up on the card opens it onto
 * the newest; Left/Right move along it, OK switches, Down or Back return to the card, and it closes
 * once focus leaves it.
 */
@Composable
private fun BoxScope.RecentRow(
    recents: List<RecentChannelPeek>,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onPick: (Int) -> Unit,
    onInteraction: () -> Unit,
    cardFocus: FocusRequester,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    /** Whether the card has focus: a quiet "⌃ Recent" above it says what Up opens. */
    hint: Boolean,
) {
    val first = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
    // Focus passing from one card to the next leaves the row for a moment: it closes only if focus
    // is still elsewhere a frame later.
    LaunchedEffect(hasFocus) {
        if (!hasFocus && hadFocus) {
            withFrameNanos { }
            hadFocus = false
            onOpenChange(false)
        }
    }
    // Above the card, without taking room in the info bar. The row's own bounds reach past the chips by
    // their zoom and outline, which a fading layer would otherwise cut off.
    Box(Modifier.align(Alignment.TopStart).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = 1200.dp.roundToPx(),
            maxHeight = Constraints.Infinity))
        layout(0, 0) { placeable.place(-RecentOverflow.roundToPx(), -placeable.height) }
    }, contentAlignment = Alignment.BottomStart) {
        AnimatedVisibility(
            visible = hint && !open,
            enter = fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate)),
            exit = fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate)),
        ) {
            // Full strength and shadowed: it stands higher than the controls, where the scrim is thin.
            val tint = MaterialTheme.colorScheme.onSurface
            Row(Modifier.padding(start = RecentOverflow, bottom = RecentHintGap).testTag("player-identity-recent-hint")
                .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_right), contentDescription = null, tint = tint,
                    modifier = Modifier.size(24.dp).rotate(-90f))
                Text(stringResource(R.string.player_row_recent), color = tint, maxLines = 1,
                    style = MaterialTheme.typography.titleSmall.copy(shadow = HintShadow))
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.StandardDecelerate)) +
                slideInVertically(tween(PlayerMotion.MediumMs, easing = PlayerMotion.StandardDecelerate)) { it / 4 },
            exit = fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate)),
        ) {
            Column(
                Modifier.padding(start = RecentOverflow, end = RecentOverflow, top = RecentOverflow,
                    bottom = PlayerChromeTokens.gridGutter).testTag("player-identity-recent-row")
                    .onFocusChanged { state ->
                        hasFocus = state.hasFocus
                        if (state.hasFocus) hadFocus = true
                    },
            ) {
                Text(stringResource(R.string.player_row_recent), color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                    style = MaterialTheme.typography.titleSmall.copy(shadow = HintShadow),
                    modifier = Modifier.padding(bottom = PlayerChromeTokens.rowTitleGap).clearAndSetSemantics {})
                Row(horizontalArrangement = Arrangement.spacedBy(RecentChipGap)) {
                    recents.forEachIndexed { index, recent ->
                        RecentChip(
                            recent, imageLoader, currentSession,
                            modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) onInteraction()
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (event.type == KeyEventType.KeyDown) cardFocus.requestFocus()
                                            true
                                        }
                                        Key.DirectionUp -> true
                                        Key.DirectionLeft -> index == 0
                                        Key.DirectionRight -> index == recents.lastIndex
                                        else -> false
                                    }
                                }
                                .testTag("player-identity-recent-$index"),
                            onClick = { onPick(index); cardFocus.requestFocus() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Trial: a recent channel as a mini wide card: a small near-black tile with its picon (or name), and
 * beside it its number and name over what is on now. The tile takes focus, as a wide card's image does.
 */
@Composable
private fun RecentChip(
    recent: RecentChannelPeek,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val label = listOf(recent.number, recent.name).filter(String::isNotBlank).joinToString(" ")
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(Modifier.width(RecentItemWidth), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RecentTextGap)) {
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TvSurfaceColors.containerLowest, contentColor = onSurface,
                focusedContainerColor = TvSurfaceColors.containerLowest, focusedContentColor = onSurface,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, onSurface), inset = 2.dp, shape = RoundedCornerShape(10.dp)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = PlayerChromeTokens.cardFocusedScale),
            modifier = modifier.size(RecentTileWidth, RecentTileHeight).semantics { contentDescription = label },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (recent.picon != null) {
                    PiconBox(imageLoader, recent.picon, Modifier.size(RecentMarkWidth, RecentMarkHeight)
                        .testTag("player-identity-recent-logo"), currentSession)
                } else {
                    Text(recent.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp).testTag("player-identity-recent-name"))
                }
            }
        }
        Column(Modifier.weight(1f).graphicsLayer { alpha = if (focused) 1f else 0.72f }
            .clearAndSetSemantics {}) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(shadow = HintShadow), color = onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            recent.now?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.copy(shadow = HintShadow), color = onSurface.copy(alpha = 0.80f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Trial: the hint's distance above the card, clear of its focus outline and zoom. */
private val RecentHintGap = 12.dp

/**
 * Trial: the recent mini wide cards: a 16:9 tile and its text, each a third of the row between the
 * margins (844dp at 960dp wide), so long names end inside it.
 */
private val RecentItemWidth = 260.dp
private val RecentTileWidth = 96.dp
private val RecentTileHeight = 54.dp
private val RecentTextGap = 12.dp
private val RecentChipGap = 32.dp
private val RecentMarkWidth = 72.dp
private val RecentMarkHeight = 32.dp

/** Trial: how far a focused chip's zoom and outline reach past it. */
private val RecentOverflow = 8.dp

/** Trial: lifts the hint and the row's title off a bright picture. */
private val HintShadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f)

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

/** Trial: a recent channel, shown in the row above the channel card. */
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
 * Trial: a channel card's face, shared by the player's channel card and the rail's: its picon, or its
 * name without one, on a plain surface.
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
    markWidth: Dp = PiconWidth,
    markHeight: Dp = PiconHeight,
    lift: Dp = PiconLift,
) {
    // Fills the card, which sizes the slot the mark is centred in. Trial: a plain surface for every
    // channel; the picon carries the colour.
    Box(Modifier.fillMaxSize().then(if (!backdrop) Modifier else Modifier.background(TvSurfaceColors.containerLowest)))
    val mark = Modifier.align(Alignment.Center).padding(bottom = lift).size(markWidth, markHeight)
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
