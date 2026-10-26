package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.components.PiconBox

/** What the typed digits lead to: the matching channel, none yet, or none at all. */
@Immutable
internal sealed interface ChannelNumberTarget {
    /** The match, with its own [number] for its chip when it has no [picon], as its identity shows it. */
    data class Channel(val number: String, val name: String, val picon: ArtworkId?) : ChannelNumberTarget
    /** Not known yet: more digits may still name a channel, or the list has not loaded. */
    data object Pending : ChannelNumberTarget
    /** The complete number names no channel. */
    data object None : ChannelNumberTarget
}

/**
 * The plate the identity grows into while digits are typed. It changes only colours and whether the
 * chip's tile stays behind a picon; layout and motion are the same for every style.
 */
internal enum class ChannelNumberPlate {
    /** A dense dark panel: reads over bright video without a scrim. */
    Dark,
    /** The identity's own light focus container, with dark content and a soft shadow. */
    Light,
    /** No plate: the entry's content alone, as the identity stands. */
    None,
}

/** The plate number entry ships with; [ChannelNumberPlate.Light] or [ChannelNumberPlate.None] swap it. */
internal val ChannelNumberPlateStyle = ChannelNumberPlate.Dark

@Immutable
private data class ChannelNumberEntry(val number: String, val target: ChannelNumberTarget)

/**
 * Channel number entry, in the top identity's place and bounds ([PlayerTopIdentitySlot]): the
 * destination's chip, the typed digits right-aligned in a field of [maxDigits] (at least three)
 * and the destination's name, on a [plate] that grows out of the content into the identity's
 * bounds. Each digit pushes the earlier ones start-ward; the plate never shrinks while the entry is
 * open, and a digit typed while it grows or leaves carries on from where it stands.
 *
 * With [settles] (the entry was committed to its channel) the number and name slide into the
 * identity's rest alignment as the plate recedes; otherwise the content fades out where it is.
 */
@Composable
internal fun ChannelNumberOverlay(
    number: String,
    target: ChannelNumberTarget,
    modifier: Modifier = Modifier,
    maxDigits: Int = 3,
    settles: Boolean = false,
    plate: ChannelNumberPlate = ChannelNumberPlateStyle,
) {
    val shown = number.isNotEmpty()
    val entry = ChannelNumberEntry(number, target)
    // The outgoing entry keeps its digits and destination until it has left.
    var held by remember { mutableStateOf(entry) }
    SideEffect { if (shown) held = entry }
    val transition = updateTransition(shown, label = "channel number entry")
    if (transition.currentState || transition.targetState) {
        ChannelNumberEntryRow(if (shown) entry else held, shown, transition, settles, maxDigits.coerceAtLeast(3),
            plate, modifier)
    }
}

@Composable
private fun ChannelNumberEntryRow(
    entry: ChannelNumberEntry,
    shown: Boolean,
    transition: Transition<Boolean>,
    settles: Boolean,
    maxDigits: Int,
    plate: ChannelNumberPlate,
    modifier: Modifier,
) {
    // A digit during the exit turns both back from where they stand; the plate never restarts at 0.
    val plateProgress = transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(PlayerMotion.DestinationMs, easing = PlayerMotion.EmphasizedDecelerate)
            else tween(PlayerMotion.DestinationExitMs, easing = PlayerMotion.EmphasizedAccelerate)
        },
        label = "plate",
    ) { if (it) 1f else 0f }
    val contentAlpha = transition.animateFloat(
        transitionSpec = {
            when {
                targetState -> tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate)
                // Settling, the content stays with the plate until it becomes the identity.
                settles -> tween(PlayerMotion.DestinationExitMs, easing = PlayerMotion.EmphasizedAccelerate)
                else -> tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate)
            }
        },
        label = "content",
    ) { if (it) 1f else 0f }
    // An in-place move that comes to rest, so not an accelerating curve; a new entry starts at its field.
    val settle = transition.animateFloat(
        transitionSpec = { if (targetState) snap() else tween(PlayerMotion.DestinationExitMs, easing = PlayerMotion.Standard) },
        label = "settle",
    ) { if (!it && settles) 1f else 0f }

    val style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Measured once per lineup and text size: a number of maxDigits digits never shifts.
    val fieldWidth = remember(measurer, style, maxDigits, density) {
        measurer.measure("0".repeat(maxDigits), style, softWrap = false).size.width
    }
    // One space, as between the rest identity's number and name.
    val spaceWidth = remember(measurer, style, density) { measurer.measure("0 0", style).size.width -
        measurer.measure("00", style).size.width }
    val advance = fieldWidth / maxDigits.toFloat()
    val digits = entry.number.length

    val noChannel = stringResource(R.string.player_channel_number_none)
    val spoken = when (val target = entry.target) {
        is ChannelNumberTarget.Channel -> "${entry.number}, ${target.name}"
        ChannelNumberTarget.None -> "${entry.number}, $noChannel"
        ChannelNumberTarget.Pending -> entry.number
    }
    // The widest the entry has been: the plate never shrinks while it is open.
    var widest by remember { mutableIntStateOf(0) }
    val plateEnd = remember { Animatable(0f) }
    LaunchedEffect(widest) {
        if (plateEnd.value == 0f) plateEnd.snapTo(widest.toFloat())
        else plateEnd.animateTo(widest.toFloat(), tween(PlayerMotion.DestinationMs, easing = PlayerMotion.EmphasizedDecelerate))
    }
    CompositionLocalProvider(LocalContentColor provides plate.contentColor()) {
        Row(
            modifier = modifier
                .onSizeChanged { if (it.width > widest) widest = it.width }
                .channelNumberPlate(plate, plate.containerColor(), plateProgress, plateEnd::value)
                // Read out politely once per digit or destination, not per frame; a leaving entry is silent.
                .semantics {
                    if (shown) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = spoken
                    }
                }
                .graphicsLayer { alpha = contentAlpha.value }
                .padding(horizontal = IdentityInset, vertical = IdentityVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelNumberChip(entry.target, plate)
            Row(
                Modifier
                    .padding(start = IdentityTextGap)
                    .widthIn(max = IdentityTextMaxWidth)
                    // Into the rest identity's alignment: the number's start on the field's start.
                    .graphicsLayer { translationX = -settle.value * (maxDigits - digits).coerceAtLeast(0) * advance },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelNumberDigits(entry.number, with(density) { fieldWidth.toDp() }, style)
                ChannelNumberName(entry.target, Modifier.weight(1f, fill = false).padding(start = with(density) { spaceWidth.toDp() }))
            }
        }
    }
}

/**
 * The typed digits at the end of their field. A new digit fades in at the end while the earlier
 * ones slide start-ward by one advance; a digit arriving mid-slide continues it from where it is.
 */
@Composable
private fun ChannelNumberDigits(number: String, width: Dp, style: TextStyle) {
    val count = number.length
    val shift = animateFloatAsState(count.toFloat(), tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard),
        label = "digit push")
    // The first digit comes in with the entry itself.
    val arrival = remember(number) { Animatable(if (count > 1) 0f else 1f) }
    LaunchedEffect(arrival) { arrival.animateTo(1f, tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate)) }
    Box(Modifier.width(width), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = number,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .wrapContentWidth(Alignment.End, unbounded = true)
                .digitPush(count, shift, arrival::value)
                .testTag("channel-number-digits")
                .semantics { hideFromAccessibility() },
        )
    }
}

private fun Modifier.digitPush(count: Int, shift: State<Float>, arrival: () -> Float) = drawWithCache {
    val fade = Paint()
    onDrawWithContent {
        val advance = size.width / count.coerceAtLeast(1)
        val last = size.width - advance
        // The earlier digits still stand where the last push left them; the shift brings them home.
        val pushed = (count - shift.value).coerceAtLeast(0f) * advance
        if (count > 1) translate(left = pushed) { clipRect(right = last) { this@onDrawWithContent.drawContent() } }
        clipRect(left = last) {
            fade.alpha = arrival()
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(last, 0f, size.width, size.height), fade)
                this@onDrawWithContent.drawContent()
                canvas.restore()
            }
        }
    }
}

/** The destination's picture slot: an empty tile, the match's picon, or a logo-less match's number. */
@Composable
private fun ChannelNumberChip(target: ChannelNumberTarget, plate: ChannelNumberPlate) {
    val channel = target as? ChannelNumberTarget.Channel
    val tile by animateFloatAsState(
        if (channel?.picon == null || plate == ChannelNumberPlate.Light) 1f else 0f,
        tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate), label = "chip tile",
    )
    Box(
        Modifier
            .size(ChipWidth, ChipHeight)
            .drawBehind {
                drawRoundRect(TvSurfaceColors.containerHighest, cornerRadius = CornerRadius(ChipTileCorner.toPx()), alpha = tile)
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = channel,
            contentKey = { it?.picon ?: it?.number },
            transitionSpec = {
                (fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate)) togetherWith
                    fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate))).using(null)
            },
            contentAlignment = Alignment.Center,
            label = "chip",
        ) { shownChannel ->
            when {
                shownChannel == null -> Unit
                shownChannel.picon != null -> PiconBox(
                    shownChannel.picon,
                    Modifier.padding(4.dp).testTag("channel-number-picon"),
                    // On the light plate the logo keeps the dark tile under it.
                    ground = if (plate == ChannelNumberPlate.Light) TvSurfaceColors.containerHighest
                        else MaterialTheme.colorScheme.background,
                )
                // As a logo-less channel's identity shows it.
                else -> Text(shownChannel.number, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    modifier = Modifier.testTag("channel-number-chip-number").semantics { hideFromAccessibility() })
            }
        }
    }
}

@Composable
private fun ChannelNumberName(target: ChannelNumberTarget, modifier: Modifier) {
    AnimatedContent(
        targetState = target.takeIf { it != ChannelNumberTarget.Pending },
        transitionSpec = {
            (fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate)) togetherWith
                fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate))).using(null)
        },
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
        label = "destination",
    ) { destination ->
        if (destination != null) {
            Text(
                text = (destination as? ChannelNumberTarget.Channel)?.name ?: stringResource(R.string.player_channel_number_none),
                style = MaterialTheme.typography.titleLarge,
                color = if (destination == ChannelNumberTarget.None) LocalContentColor.current.copy(alpha = TvTextTertiaryAlpha)
                    else LocalContentColor.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("channel-number-target").semantics { hideFromAccessibility() },
            )
        }
    }
}

/**
 * The plate behind the entry, drawn and never measured: from the content's box ([IdentityInset] and
 * [IdentityVerticalPadding] in, small corners) at [progress] 0 to the identity's bounds at 1, as wide
 * as [end] (the widest the entry has been).
 */
private fun Modifier.channelNumberPlate(
    plate: ChannelNumberPlate,
    color: Color,
    progress: State<Float>,
    end: () -> Float,
): Modifier = if (plate == ChannelNumberPlate.None) this else drawWithCache {
    val shadow = if (plate == ChannelNumberPlate.Light) Paint().asFrameworkPaint() else null
    onDrawBehind {
        val p = progress.value
        if (p <= 0f) return@onDrawBehind
        val insetX = IdentityInset.toPx() * (1f - p)
        val insetY = IdentityVerticalPadding.toPx() * (1f - p)
        val right = end().takeIf { it > 0f } ?: size.width
        val corner = lerp(ChipTileCorner.toPx(), IdentityShape.topStart.toPx(size, this), p)
        if (shadow != null) {
            // The light plate lifts off the picture with a soft shadow; the container itself draws with it.
            shadow.color = color.copy(alpha = color.alpha * p).toArgb()
            shadow.setShadowLayer(PlateShadowBlur.toPx(), 0f, PlateShadowOffset.toPx(),
                Color.Black.copy(alpha = PlateShadowAlpha * p).toArgb())
            drawIntoCanvas {
                it.nativeCanvas.drawRoundRect(insetX, insetY, right - insetX, size.height - insetY, corner, corner, shadow)
            }
        } else {
            drawRoundRect(color, topLeft = Offset(insetX, insetY), size = Size(right - 2 * insetX, size.height - 2 * insetY),
                cornerRadius = CornerRadius(corner), alpha = p)
        }
    }
}

@Composable
private fun ChannelNumberPlate.containerColor(): Color = when (this) {
    ChannelNumberPlate.Dark -> TvSurfaceColors.container.copy(alpha = TvPanelDenseAlpha)
    ChannelNumberPlate.Light -> MaterialTheme.colorScheme.onSurface
    ChannelNumberPlate.None -> Color.Transparent
}

@Composable
private fun ChannelNumberPlate.contentColor(): Color =
    if (this == ChannelNumberPlate.Light) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface

/** The chip's empty tile, and the plate's corners while it is still the content's box. */
private val ChipTileCorner = 8.dp
private val PlateShadowBlur = 8.dp
private val PlateShadowOffset = 2.dp
private const val PlateShadowAlpha = 0.3f
