package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.channelTitleNumber
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.placeHeaderLines
import at.bernhardberger.tvhplayer.ui.components.rule
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import kotlin.math.pow

/** The top identity's test tag. */
internal const val PlayerTopIdentityTag = "player-top-identity"

/**
 * The passive channel identity at the top start, in the same anchor as [NowPlayingStrip]: its
 * picon (if available) and "number name" live; the recording symbol and date in playback.
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
                    else Modifier.semantics(mergeDescendants = true) {
                        if (spoken.isNotBlank()) contentDescription = spoken
                    },
                )) {
                    val recording = content.info.recording
                    if (recording != null) RecordingIdentityContent(content, recording)
                    else LiveIdentityContent(content)
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
                Text("", style = MaterialTheme.typography.headlineSmall, maxLines = 1,
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
    info.recording?.let {
        return listOfNotNull(it.headline, recordingChannel(content)).joinToString(", ")
    }
    val channel = if (info.channelNumber.isBlank()) info.channelName
        else stringResource(R.string.player_channel_spoken, channelTitleText(info.channelNumber.toLongOrNull(), info.channelName))
    return if (content.showChannelMark) "$channel, ${stringResource(R.string.channel_recording_spoken)}" else channel
}

@Composable
private fun LiveIdentityContent(content: PlayerChromeContent) {
    val info = content.info
    val ink = MaterialTheme.colorScheme.onSurface
    // The chip's height even without one, so the identity keeps its place and size on every channel.
    Row(Modifier.padding(horizontal = IdentityInset, vertical = IdentityVerticalPadding).heightIn(min = ChipHeight),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IdentityTextGap)) {
        // Without a picon there is no logo slot: the name starts on the keyline and already carries
        // the number, which a number chip would only repeat.
        if (content.picon != null) {
            IdentityPicon(content.picon, Modifier.testTag("player-top-identity-logo"))
        }
        val number = info.channelNumber.toLongOrNull()
        val title = buildAnnotatedString {
            append(channelTitleText(number, info.channelName))
            channelTitleNumber(number, info.channelName)?.let {
                addStyle(SpanStyle(color = ink.copy(alpha = TvTextSecondaryAlpha),
                    fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"), 0, it.toString().length)
            }
        }
        Text(title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
            color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            // The identity's merged description names the channel.
            modifier = Modifier.weight(1f, fill = false).widthIn(max = IdentityTextMaxWidth)
                .testTag("player-top-identity-name").clearAndSetSemantics {})
        if (content.showChannelMark) {
            RecordingStatusIndicator(
                DvrEntryState.RECORDING, Modifier.testTag("player-identity-recording-mark"), announceState = false,
                textStyle = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun recordingChannel(content: PlayerChromeContent): String? =
    content.info.channelName.takeUnless { content.withoutChannel || it.isBlank() }

@Composable
private fun RecordingIdentityContent(content: PlayerChromeContent, recording: RecordingIdentity) {
    val info = content.info
    val channel = recordingChannel(content)
    val ink = MaterialTheme.colorScheme.onSurface
    Row(Modifier.padding(horizontal = IdentityInset, vertical = IdentityVerticalPadding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(RecordingMarkSlot), contentAlignment = Alignment.Center) {
            val growing = info.nowRecording
            if (growing != null) {
                val rule = growing.rule
                val markSize = if (rule == null) RecordingDotViewport else RecordingStackViewport
                RecordingStatusIndicator(info.nowRecordingStateOverride ?: growing.state,
                    Modifier.testTag("player-identity-recording-mark")
                        .offset(x = if (rule == null) RecordingDotOffset else 0.dp)
                        .requiredSize(markSize),
                    rule = rule, announceState = false, size = markSize)
            } else {
                Icon(painterResource(at.bernhardberger.tvhplayer.R.drawable.ic_video_library), null,
                    Modifier.testTag("player-recording-identity-symbol").requiredSize(RecordingStackViewport),
                    tint = ink)
            }
        }
        val headline = recording.headline ?: channel ?: return@Row
        val headlineStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium)
        val textColumn = Modifier.widthIn(max = IdentityTextMaxWidth).clearAndSetSemantics {}
        if (recording.headline == null || channel == null) {
            Text(headline, modifier = textColumn, style = headlineStyle, color = ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Layout(
                modifier = textColumn,
                content = {
                    Text(headline, style = headlineStyle, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(channel, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = TvTextSecondaryAlpha),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            ) { measurables, constraints ->
                val first = measurables[0].measure(constraints)
                val second = measurables[1].measure(constraints)
                placeHeaderLines(first, second, constraints, maxOf(first.width, second.width))
            }
        }
    }
}

// Compensate for vector padding so the 32dp stack and 28dp dot ink meet the keyline.
private val RecordingMarkSlot = 32.dp
private val RecordingStackViewport = 38.4.dp
private val RecordingDotViewport = 48.dp
private val RecordingDotOffset = (-2).dp

@Composable
internal fun IdentityPicon(
    picon: ChannelIcon,
    modifier: Modifier = Modifier,
    ground: Color = MaterialTheme.colorScheme.background,
) {
    PiconBox(picon, modifier.size(ChipWidth, ChipHeight),
        contentScale = IdentityPiconScale, ground = ground, trim = true)
}

internal val IdentityPiconScale: ContentScale = object : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        if (!srcSize.isSpecified || srcSize.isEmpty()) return ScaleFactor(1f, 1f)
        val aspect = (srcSize.width / srcSize.height).coerceAtLeast(1f)
        val height = dstSize.height * aspect.pow(-0.28f)
        return ContentScale.Fit.computeScaleFactor(srcSize, Size(dstSize.width, height))
    }
}

/** The identity's horizontal padding; keeps its visible content on the grid margin. */
internal val IdentityInset = 24.dp
internal val IdentityVerticalPadding = 6.dp
internal val IdentityTextGap = 20.dp
internal val IdentityTextMaxWidth = 480.dp
internal val IdentityShape = RoundedCornerShape(12.dp)
internal val ChipWidth = 72.dp
internal val ChipHeight = 36.dp

/** The identity's share of the strip's chip: 48dp of 72dp. */
private const val IdentityMorphShrink = 1f / 3f

/** How much of a page's opening the identity takes to give way to the strip. */
private const val IdentityPageMorphMs = 200f
