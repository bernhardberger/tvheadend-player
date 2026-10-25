package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvPassiveAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.RecordingBadge
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState

/** The live top identity's test tag. */
internal const val PlayerTopIdentityTag = "player-top-identity"

/**
 * The live channel's identity at the top start, in the same anchor as [NowPlayingStrip]: its picon
 * chip (or number) and "number name" on one line, with REC while the channel records. With [focus] (the controls) it
 * takes focus in the caller's place in the focus graph and OK opens the [recents] row dropping down
 * below it; without (the Banner) it takes no focus.
 *
 * While a layer that shows [NowPlayingStrip] opens (the channel rows, or a page such as the
 * programme details), it shrinks towards the strip's size and fades, so the two read as one object
 * and never stand at full strength together.
 */
@Composable
internal fun PlayerTopIdentity(
    content: PlayerChromeContent,
    modifier: Modifier = Modifier,
    /** The identity's place in the controls' focus graph; null in the Banner. */
    focus: Modifier? = null,
    /** Whether Left/Right change channel here; the hint and the arrows say so only then. */
    zaps: Boolean = true,
    /** OK on the identity. */
    onClick: () -> Unit = {},
    /** The recent channels, newest first, a row below the identity that OK opens. */
    recents: List<RecentChannelPeek> = emptyList(),
    recentOpen: Boolean = false,
    onRecentOpenChange: (Boolean) -> Unit = {},
    /** OK on a recent chip, by its index in [recents]. */
    onRecentClick: (Int) -> Unit = {},
    /** A key moving through the recent row, which keeps the controls up. */
    onRecentInteraction: () -> Unit = {},
    /** Down in the recent row: the caller moves focus on to the action row. */
    onRecentDown: () -> Unit = {},
    /** Whether focus is on the identity or inside its recent row. */
    onFocusWithinChanged: (Boolean) -> Unit = {},
) {
    val rail = LocalRailExpansion.current
    val page = LocalPlayerPageProgress.current
    // 0 at rest, 1 once the strip stands in for the identity.
    val morph = {
        maxOf(rail(), ((page?.invoke() ?: 0f) * BrowseMotionPolicy.pageDownMs / IdentityPageMorphMs).coerceIn(0f, 1f))
    }
    val rowOpen = recentOpen && focus != null && recents.isNotEmpty()
    val identityFocus = remember { FocusRequester() }
    Box(modifier) {
        // The top scrim fades out at 112dp; the open row below it gets its own, clear of the clock.
        AnimatedVisibility(
            visible = rowOpen,
            enter = fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.StandardDecelerate)),
            exit = fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate)),
        ) {
            Box(Modifier.padding(top = RecentScrimTop).fillMaxWidth().height(RecentScrimHeight).background(RecentScrim))
        }
        Layout(
            content = {
                // The room's header band (or the clock's line, if taller), which the identity is centred on, as the strip is.
                Box(Modifier.width(0.dp).heightIn(min = TvBrowseHeaderHeight)) {
                    Text("", style = MaterialTheme.typography.titleLarge, maxLines = 1,
                        modifier = Modifier.width(0.dp).clearAndSetSemantics {})
                }
                val identityLayer = Modifier.graphicsLayer {
                    val m = morph()
                    alpha = 1f - m
                    scaleX = 1f - IdentityMorphShrink * m
                    scaleY = scaleX
                    // The chip's start edge stays where the strip's chip stands.
                    transformOrigin = TransformOrigin(IdentityInset.toPx() / size.width.coerceAtLeast(1f), 0.5f)
                }
                if (focus == null) {
                    val spoken = identitySpoken(content)
                    Box(identityLayer.testTag(PlayerTopIdentityTag).semantics(mergeDescendants = true) {
                        contentDescription = spoken
                    }) {
                        IdentityContent(content, focused = false)
                    }
                } else {
                    FocusableIdentity(content, zaps, recents.isNotEmpty(), onClick,
                        identityLayer.then(focus).focusRequester(identityFocus))
                }
                Box {
                    if (focus != null && recents.isNotEmpty()) RecentRow(recents, rowOpen, onRecentOpenChange, onRecentClick,
                        onRecentInteraction, identityFocus, onRecentDown)
                }
            },
            modifier = Modifier.onFocusChanged { onFocusWithinChanged(it.hasFocus) },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val probe = measurables[0].measure(loose)
            val identity = measurables[1].measure(loose)
            val row = measurables[2].measure(loose)
            val top = TvGridVerticalMargin.roundToPx()
            val identityX = (TvGridHorizontalMargin - IdentityInset).roundToPx()
            val identityY = top + (probe.height - identity.height) / 2
            val rowY = identityY + identity.height + RecentRowGap.roundToPx()
            layout(constraints.maxWidth, constraints.maxHeight) {
                identity.place(identityX, identityY)
                row.place((TvGridHorizontalMargin - RecentOverflow).roundToPx(), rowY)
            }
        }
    }
}

@Composable
private fun FocusableIdentity(
    content: PlayerChromeContent,
    zaps: Boolean,
    hasRecents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val spoken = identitySpoken(content)
    val hint = stringResource(when {
        !zaps -> R.string.player_identity_info_hint
        hasRecents -> R.string.player_channel_card_hint
        else -> R.string.player_channel_card_hint_no_recents
    })
    val onSurface = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        interactionSource = interactions,
        shape = ClickableSurfaceDefaults.shape(IdentityShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, contentColor = onSurface,
            // The light focus container of a tv-material surface, with dark content.
            focusedContainerColor = onSurface, focusedContentColor = MaterialTheme.colorScheme.surface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        // The tag stands before the semantics it carries.
        modifier = modifier.testTag(PlayerTopIdentityTag).semantics {
            contentDescription = spoken
            stateDescription = hint
        },
    ) {
        Box(Modifier.clearAndSetSemantics {}) {
            IdentityContent(content, focused)
            if (focused && zaps) {
                // Faint: Left/Right change channel from here.
                val tint = LocalContentColor.current.copy(alpha = ArrowAlpha)
                Icon(painterResource(R.drawable.ic_keyboard_arrow_left), contentDescription = null, tint = tint,
                    modifier = Modifier.align(Alignment.CenterStart).size(IdentityInset).testTag("player-top-identity-previous"))
                Icon(painterResource(R.drawable.ic_keyboard_arrow_right), contentDescription = null, tint = tint,
                    modifier = Modifier.align(Alignment.CenterEnd).size(IdentityInset).testTag("player-top-identity-next"))
            }
        }
    }
}

/** "Channel 1 Ridge Earth". */
@Composable
private fun identitySpoken(content: PlayerChromeContent): String {
    val info = content.info
    val channel = if (info.channelNumber.isBlank()) info.channelName
        else stringResource(R.string.player_channel_spoken, channelTitleText(info.channelNumber.toLongOrNull(), info.channelName))
    // The REC badge beside the name says it too.
    return if (content.recordingNow) "$channel, ${stringResource(R.string.player_recording)}" else channel
}

@Composable
private fun IdentityContent(
    content: PlayerChromeContent,
    focused: Boolean,
) {
    val info = content.info
    val ink = if (focused) LocalContentColor.current else MaterialTheme.colorScheme.onSurface
    Row(Modifier.padding(horizontal = IdentityInset, vertical = IdentityVerticalPadding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IdentityTextGap)) {
        Box(Modifier.size(ChipWidth, ChipHeight),
            contentAlignment = Alignment.Center) {
            if (content.picon != null) {
                PiconBox(content.picon, Modifier.padding(4.dp).testTag("player-top-identity-logo"))
            } else {
                Text(info.channelNumber, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, modifier = Modifier.testTag("player-top-identity-number"))
            }
        }
        Text(channelTitleText(info.channelNumber.toLongOrNull(), info.channelName),
            // The room's headline: the channel is the player's room, one step below the programme's title.
            style = MaterialTheme.typography.titleLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            // The focusable identity's description, or the Banner's info bar, names the channel.
            modifier = Modifier.weight(1f, fill = false).widthIn(max = IdentityTextMaxWidth)
                .testTag("player-top-identity-name").clearAndSetSemantics {})
        if (content.recordingNow) RecordingBadge(DvrEntryState.RECORDING)
    }
}

/**
 * The recent channels, a row of logo chips below the top identity. OK on the identity opens it onto
 * the newest; Left/Right move along it, OK switches, Up returns to the identity, Down moves on to
 * the action row ([onDown]), and it closes once focus leaves it. Back is the screen's.
 */
@Composable
private fun RecentRow(
    recents: List<RecentChannelPeek>,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onPick: (Int) -> Unit,
    onInteraction: () -> Unit,
    identityFocus: FocusRequester,
    onDown: () -> Unit,
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
    // Focus passing from one chip to the next leaves the row for a moment: it closes only if focus
    // is still elsewhere a frame later.
    LaunchedEffect(hasFocus) {
        if (!hasFocus && hadFocus) {
            withFrameNanos { }
            hadFocus = false
            onOpenChange(false)
        }
    }
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.StandardDecelerate)) +
            slideInVertically(tween(PlayerMotion.MediumMs, easing = PlayerMotion.StandardDecelerate)) { -it / 4 },
        exit = fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate)),
    ) {
        // The padding leaves room for a focused chip's zoom and outline.
        Column(
            Modifier.padding(RecentOverflow).testTag("player-identity-recent-row")
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
                        recent,
                        modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .onPreviewKeyEvent { event ->
                                val down = event.type == KeyEventType.KeyDown
                                if (down) onInteraction()
                                val press = down && event.nativeKeyEvent.repeatCount == 0
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (press) onDown()
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        if (press) identityFocus.requestFocus()
                                        true
                                    }
                                    Key.DirectionLeft -> index == 0
                                    Key.DirectionRight -> index == recents.lastIndex
                                    else -> false
                                }
                            }
                            .testTag("player-identity-recent-$index"),
                        onClick = { onPick(index); identityFocus.requestFocus() },
                    )
                }
            }
        }
    }
}

/**
 * A recent channel as a mini wide card: a small near-black tile with its picon (or name), and
 * beside it its number and name over what is on now. The tile takes focus, as a wide card's image does.
 */
@Composable
private fun RecentChip(
    recent: RecentChannelPeek,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val label = channelTitleText(recent.number.toLongOrNull(), recent.name)
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
                    PiconBox(recent.picon, Modifier.size(RecentMarkWidth, RecentMarkHeight)
                        .testTag("player-identity-recent-logo"))
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
                Text(it, style = MaterialTheme.typography.bodySmall.copy(shadow = HintShadow), color = onSurface.copy(alpha = TvTextSecondaryAlpha),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The recent mini wide cards: a 16:9 tile and its text, each a third of the row between the
 * margins (844dp at 960dp wide), so long names end inside it.
 */
private val RecentItemWidth = 260.dp
private val RecentTileWidth = 96.dp
private val RecentTileHeight = 54.dp
private val RecentTextGap = 12.dp
private val RecentChipGap = 32.dp
private val RecentMarkWidth = 72.dp
private val RecentMarkHeight = 32.dp

/** How far a focused chip's zoom and outline reach past it. */
private val RecentOverflow = 8.dp

/** Lifts the row's title and text off a bright picture. */
private val HintShadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f)

/** A recent channel, shown in the row below the top identity. */
internal data class RecentChannelPeek(
    val picon: ArtworkId?,
    val number: String,
    val name: String,
    val channelId: ChannelId? = null,
    /** The programme on now, if known. */
    val now: String? = null,
)

/** The identity's room beside its content: the focus container's padding and the ‹ › lanes. */
private val IdentityInset = 24.dp
private val IdentityVerticalPadding = 6.dp
private val IdentityTextGap = 16.dp
private val IdentityTextMaxWidth = 480.dp
private val IdentityShape = RoundedCornerShape(12.dp)
private val ChipWidth = 64.dp
private val ChipHeight = 36.dp
private const val ArrowAlpha = TvPassiveAlpha

/** The identity's share of the strip's chip: 48dp of 64dp. */
private const val IdentityMorphShrink = 0.25f

/** How much of a page's opening the identity takes to give way to the strip. */
private const val IdentityPageMorphMs = 200f

/** The open recent row's distance below the identity, and the scrim behind it, from below the clock's line. */
private val RecentRowGap = 8.dp
private val RecentScrimTop = 72.dp
private val RecentScrimHeight = 208.dp
private val RecentScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    0.2f to Color.Black.copy(alpha = 0.56f),
    0.6f to Color.Black.copy(alpha = 0.56f),
    1f to Color.Transparent,
)
