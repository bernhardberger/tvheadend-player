package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState

/** The live top identity's test tag. */
internal const val PlayerTopIdentityTag = "player-top-identity"

/**
 * The passive channel identity at the top start, in the same anchor as [NowPlayingStrip]: its
 * picon (if available) and "number name" on one line, with REC while the channel records.
 *
 * While a layer that shows [NowPlayingStrip] opens (the channel rows, or a page such as the
 * programme details), it shrinks towards the strip's size and fades, so the two read as one object
 * and never stand at full strength together. While [numberEntryShown] it fades out under the
 * channel number entry, which stands in its place ([PlayerTopIdentitySlot]), and back once the entry leaves.
 */
@Composable
internal fun PlayerTopIdentity(
    content: PlayerChromeContent,
    modifier: Modifier = Modifier,
    numberEntryShown: Boolean = false,
) {
    // Out as the entry's plate grows over it, back in at rest as the entry leaves.
    val present = animateFloatAsState(if (numberEntryShown) 0f else 1f,
        if (numberEntryShown) tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate)
        else tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate),
        label = "identity under number entry")
    val rail = LocalRailExpansion.current
    val page = LocalPlayerPageProgress.current
    // 0 at rest, 1 once the strip stands in for the identity.
    val morph = {
        maxOf(rail(), ((page?.invoke() ?: 0f) * BrowseMotionPolicy.pageDownMs / IdentityPageMorphMs).coerceIn(0f, 1f))
    }
    Box(modifier) {
        Layout(
            content = {
                HeaderBandProbe()
                val identityLayer = Modifier.graphicsLayer {
                    val m = morph()
                    alpha = (1f - m) * present.value
                    scaleX = 1f - IdentityMorphShrink * m
                    scaleY = scaleX
                    // The chip's start edge stays where the strip's chip stands.
                    transformOrigin = TransformOrigin(IdentityInset.toPx() / size.width.coerceAtLeast(1f), 0.5f)
                }
                val spoken = identitySpoken(content)
                // Faded out under the entry, it is not read out either; the entry speaks for itself.
                Box(identityLayer.testTag(PlayerTopIdentityTag).then(
                    if (numberEntryShown) Modifier.clearAndSetSemantics {}
                    else Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
                )) {
                    IdentityContent(content)
                }
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val probe = measurables[0].measure(loose)
            val identity = measurables[1].measure(loose)
            val origin = identityOrigin(probe.height, identity.height)
            layout(constraints.maxWidth, constraints.maxHeight) {
                identity.place(origin)
            }
        }
    }
}

/**
 * [content] where [PlayerTopIdentity] stands, whether or not the chrome is up: the channel number
 * entry takes the identity's place this way and fades in as the identity fades out under it. The
 * content starts at the identity's top edge; taller content, as at larger font scales, grows down.
 */
@Composable
internal fun PlayerTopIdentitySlot(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = {
            HeaderBandProbe()
            // The identity's own height: its chip or its name's line, whichever is taller.
            Box(Modifier.width(0.dp).padding(vertical = IdentityVerticalPadding).heightIn(min = ChipHeight)) {
                Text("", style = MaterialTheme.typography.titleLarge, maxLines = 1,
                    modifier = Modifier.width(0.dp).clearAndSetSemantics {})
            }
            Box { content() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val probe = measurables[0].measure(loose)
        val identity = measurables[1].measure(loose)
        val slot = measurables[2].measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            slot.place(identityOrigin(probe.height, identity.height))
        }
    }
}

/** The room's header band (or the clock's line, if taller), which the identity is centred on, as the strip is. */
@Composable
private fun HeaderBandProbe() {
    Box(Modifier.width(0.dp).heightIn(min = TvBrowseHeaderHeight)) {
        Text("", style = MaterialTheme.typography.titleLarge, maxLines = 1,
            modifier = Modifier.width(0.dp).clearAndSetSemantics {})
    }
}

/** The identity's top start: its content on the grid margin, centred on the [probe][HeaderBandProbe]'s band. */
private fun Density.identityOrigin(probeHeight: Int, height: Int) = IntOffset(
    (TvGridHorizontalMargin - IdentityInset).roundToPx(),
    TvGridVerticalMargin.roundToPx() + (probeHeight - height) / 2,
)

/** "Channel 1 Ridge Earth". */
@Composable
private fun identitySpoken(content: PlayerChromeContent): String {
    val info = content.info
    val channel = if (info.channelNumber.isBlank()) info.channelName
        else stringResource(R.string.player_channel_spoken, channelTitleText(info.channelNumber.toLongOrNull(), info.channelName))
    return if (content.showChannelMark) "$channel, ${stringResource(R.string.channel_recording_spoken)}" else channel
}

@Composable
private fun IdentityContent(content: PlayerChromeContent) {
    val info = content.info
    val ink = MaterialTheme.colorScheme.onSurface
    // The chip's height even without one, so the identity keeps its place and size on every channel.
    Row(Modifier.padding(horizontal = IdentityInset, vertical = IdentityVerticalPadding).heightIn(min = ChipHeight),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IdentityTextGap)) {
        // Without a picon there is no logo slot: the name starts on the keyline and already carries
        // the number, which a number chip would only repeat.
        if (content.picon != null) {
            Box(Modifier.size(ChipWidth, ChipHeight), contentAlignment = Alignment.Center) {
                PiconBox(content.picon, Modifier.padding(4.dp).testTag("player-top-identity-logo"))
            }
        }
        Text(channelTitleText(info.channelNumber.toLongOrNull(), info.channelName),
            // The room's headline: the channel is the player's room, one step below the programme's title.
            style = MaterialTheme.typography.titleLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            // The identity's merged description names the channel.
            modifier = Modifier.weight(1f, fill = false).widthIn(max = IdentityTextMaxWidth)
                .testTag("player-top-identity-name").clearAndSetSemantics {})
        if (content.showChannelMark) {
            RecordingStatusIndicator(
                DvrEntryState.RECORDING, Modifier.testTag("player-identity-recording-mark"), announceState = false,
                textStyle = MaterialTheme.typography.titleLarge)
        }
    }
}

/** The identity's horizontal padding; keeps its visible content on the grid margin. */
internal val IdentityInset = 24.dp
internal val IdentityVerticalPadding = 6.dp
internal val IdentityTextGap = 16.dp
internal val IdentityTextMaxWidth = 480.dp
internal val IdentityShape = RoundedCornerShape(12.dp)
internal val ChipWidth = 64.dp
internal val ChipHeight = 36.dp

/** The identity's share of the strip's chip: 48dp of 64dp. */
private const val IdentityMorphShrink = 0.25f

/** How much of a page's opening the identity takes to give way to the strip. */
private const val IdentityPageMorphMs = 200f
