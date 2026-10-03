package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.roundToInt
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout

/** One motion value coordinates departing chrome and the persistent, peeking channel row. */
@Composable
internal fun QuickZapPresentation(
    expanded: Boolean,
    channelsAvailable: Boolean,
    channelContent: @Composable () -> Unit,
    peekAlpha: () -> Float = { 1f },
    preview: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    /**
     * Trial: the channel card's bounds in root coordinates. With it, the rail opens out of the card:
     * its cards on the card's line, revealed sideways from the card, and nothing travels up.
     */
    inPlaceAnchor: (() -> Rect?)? = null,
) {
    val expansion = animateFloatAsState(
        if (expanded) 1f else 0f,
        animationSpec = if (expanded) {
            tween(PlayerMotion.PanelMs, easing = PlayerMotion.EmphasizedDecelerate)
        } else {
            tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedAccelerate)
        },
        label = "quick-zap-expansion",
    )
    var previewHeight by remember { mutableIntStateOf(0) }
    if (inPlaceAnchor != null) {
        InPlaceRail(expanded, expansion::value, inPlaceAnchor, channelContent, preview, controls)
        return
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier.fillMaxSize()
                .focusProperties { canFocus = !expanded }
                .then(if (expanded) Modifier.clearAndSetSemantics { } else Modifier)
                .graphicsLayer {
                    translationY = -48.dp.toPx() * expansion.value
                    alpha = 1f - expansion.value
                }
                .testTag("player-zap-controls"),
        ) { controls() }
        if (channelsAvailable || expanded) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .graphicsLayer {
                        alpha = if (expanded) 1f else peekAlpha()
                        // 12dp focus reserve + 24dp of actual card remains at the bottom.
                        // The passive preview slot above the cards stays below the peek.
                        val previewSlot = TvOverlayFooterGradientRunout.toPx() + previewHeight.toFloat()
                        translationY = (size.height - 36.dp.toPx() - previewSlot) * (1f - expansion.value)
                    }
                    // The preview sits above the cards, so the scrim keeps the info bar's
                    // bottom-anchored stops instead of stretching over the tray. While peeking,
                    // the tray's top reaches over the transport row, which already sits on the
                    // controls' own scrim; only the open tray adds one.
                    .drawBehind {
                        val span = PlayerChromeTokens.bottomScrimSpan.toPx().coerceAtMost(size.height)
                        drawRect(
                            Brush.verticalGradient(
                                *PlayerChromeTokens.bottomScrimStops,
                                startY = size.height - span,
                                endY = size.height,
                            ),
                            alpha = expansion.value,
                        )
                    }
                    .padding(bottom = 32.dp)
                    .testTag("player-zap-tray"),
            ) {
                Column {
                    // The scrim's clear runout, so the preview text rests on the dark stops.
                    Spacer(Modifier.height(TvOverlayFooterGradientRunout))
                    Box(
                        Modifier.fillMaxWidth()
                            // Measured with the gap, so the peek offset keeps the cards' top edge.
                            .onSizeChanged { previewHeight = it.height }
                            .padding(bottom = PlayerChromeTokens.previewCardGap)
                            .heightIn(min = PlayerChromeTokens.previewSlotHeight)
                            .graphicsLayer { alpha = expansion.value }
                            .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { })
                            .testTag("player-zap-preview-slot"),
                        contentAlignment = Alignment.BottomStart,
                    ) { preview() }
                    channelContent()
                }
            }
        }
    }
}

/** Trial: the cards' top focus reserve inside the rail, above the first card's top edge. */
private val RailTopInset = 12.dp

@Composable
private fun InPlaceRail(
    expanded: Boolean,
    expansion: () -> Float,
    anchor: () -> Rect?,
    channelContent: @Composable () -> Unit,
    preview: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var previewTop by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().clipToBounds().onGloballyPositioned { origin = it.positionInRoot() }) {
        Box(
            Modifier.fillMaxSize()
                .focusProperties { canFocus = !expanded }
                .then(if (expanded) Modifier.clearAndSetSemantics { } else Modifier)
                .testTag("player-zap-controls"),
        ) {
            // The controls' footer gives way to the rail; their header (clock, top scrim) stays.
            CompositionLocalProvider(LocalInPlaceRailExpansion provides expansion) { controls() }
        }
        if (!expanded && expansion() == 0f) return@Box
        Layout(
            content = {
                Box(
                    Modifier.padding(bottom = PlayerChromeTokens.previewCardGap)
                        .graphicsLayer { alpha = expansion() }
                        .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { })
                        .testTag("player-zap-preview-slot"),
                    contentAlignment = Alignment.BottomStart,
                ) { preview() }
                Box(Modifier.testTag("player-zap-tray")) { channelContent() }
            },
            modifier = Modifier.fillMaxSize()
                .drawBehind {
                    // The picture dims a little below the header's own fade, so no clear band shows
                    // between that fade and this one, while the clock stays as it was; dark from the
                    // preview down.
                    val runout = TvOverlayFooterGradientRunout.toPx()
                    val header = PlayerChromeTokens.topScrimHeight.toPx() / size.height.coerceAtLeast(1f)
                    val top = (previewTop - runout).coerceAtLeast(0f) / size.height.coerceAtLeast(1f)
                    val preview = previewTop.toFloat() / size.height.coerceAtLeast(1f)
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            header.coerceIn(0f, top.coerceIn(0f, 1f)) to Color.Black.copy(alpha = RailVeil),
                            top.coerceIn(0f, 1f) to Color.Black.copy(alpha = RailVeil),
                            preview.coerceIn(top.coerceIn(0f, 1f), 1f) to Color.Black.copy(alpha = 0.60f),
                            1f to Color.Black.copy(alpha = 0.92f),
                        ),
                        alpha = expansion(),
                    )
                }
                .graphicsLayer {
                    alpha = (expansion() * 2.5f).coerceAtMost(1f)
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // Revealed sideways from the card's own bounds, its growing edges faded.
                    val e = expansion()
                    val card = anchor() ?: return@drawWithContent
                    val left = (card.left - origin.x) * (1f - e)
                    val right = (card.right - origin.x) + (size.width - (card.right - origin.x)) * e
                    val feather = RevealFeather.toPx()
                    val from = left - feather
                    val span = (right + feather) - from
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            (feather / span) to Color.Black,
                            ((span - feather) / span) to Color.Black,
                            1f to Color.Transparent,
                            startX = from, endX = right + feather,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = constraints.maxWidth, minHeight = 0)
            val previewPlaceable = measurables[0].measure(loose)
            val railPlaceable = measurables[1].measure(loose)
            val card = anchor()
            // Without a card to open from, the rail keeps the card's usual line above the timeline.
            val top = card?.let { (it.top - origin.y).roundToInt() - RailTopInset.roundToPx() }
                ?: (constraints.maxHeight - railPlaceable.height - 96.dp.roundToPx())
            previewTop = top - previewPlaceable.height
            layout(constraints.maxWidth, constraints.maxHeight) {
                previewPlaceable.place(0, top - previewPlaceable.height)
                railPlaceable.place(0, top)
            }
        }
    }
}

/** Trial: how far the in-place rail has opened, for the controls' footer to give way to it. */
internal val LocalInPlaceRailExpansion = compositionLocalOf<() -> Float> { { 0f } }

/** Trial: the soft edge of the rail's sideways reveal. */
private val RevealFeather = 96.dp

/** Trial: how far the open rail dims the picture above its preview. */
private const val RailVeil = 0.36f
