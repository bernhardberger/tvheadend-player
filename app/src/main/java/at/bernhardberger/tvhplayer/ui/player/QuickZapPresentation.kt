package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout

/** One motion value coordinates departing chrome and the persistent, peeking channel row. */
@Composable
internal fun QuickZapPresentation(
    expanded: Boolean,
    channelsAvailable: Boolean,
    channelContent: @Composable () -> Unit,
    peekAlpha: () -> Float = { 1f },
    preview: (@Composable () -> Unit)? = null,
    controls: @Composable () -> Unit,
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
                        // New design: the passive preview slot above the cards stays below the peek.
                        val previewSlot = if (preview != null) TvOverlayFooterGradientRunout.toPx() + previewHeight.toFloat() else 0f
                        translationY = (size.height - 36.dp.toPx() - previewSlot) * (1f - expansion.value)
                    }
                    .then(
                        if (preview == null) {
                            Modifier.background(bottomGradient)
                        } else {
                            // New design: the preview sits above the cards, so the scrim keeps the
                            // info bar's bottom-anchored stops instead of stretching over the tray.
                            Modifier.drawBehind {
                                val span = PlayerTrialTokens.bottomScrimSpan.toPx().coerceAtMost(size.height)
                                drawRect(Brush.verticalGradient(
                                    *PlayerTrialTokens.bottomScrimStops,
                                    startY = size.height - span,
                                    endY = size.height,
                                ))
                            }
                        },
                    )
                    .padding(bottom = 32.dp)
                    .testTag("player-zap-tray"),
            ) {
                if (preview == null) {
                    channelContent()
                } else {
                    Column {
                        // The scrim's clear runout, so the preview text rests on the dark stops.
                        Spacer(Modifier.height(TvOverlayFooterGradientRunout))
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = PlayerTrialTokens.previewSlotHeight)
                                .onSizeChanged { previewHeight = it.height }
                                .graphicsLayer { alpha = expansion.value }
                                .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { })
                                .testTag("trial-zap-preview-slot"),
                        ) { preview() }
                        channelContent()
                    }
                }
            }
        }
    }
}
