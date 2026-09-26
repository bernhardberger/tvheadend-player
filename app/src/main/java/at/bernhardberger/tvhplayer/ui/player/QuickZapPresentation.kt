package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** One motion value coordinates departing chrome and the persistent, peeking channel row. */
@Composable
internal fun QuickZapPresentation(
    expanded: Boolean,
    channelsAvailable: Boolean,
    channelContent: @Composable () -> Unit,
    peekAlpha: () -> Float = { 1f },
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
                        translationY = (size.height - 36.dp.toPx()) * (1f - expansion.value)
                    }
                    .background(bottomGradient)
                    .padding(bottom = 32.dp)
                    .testTag("player-zap-tray"),
            ) { channelContent() }
        }
    }
}
