package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayTopPadding
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineActionGap

/** Same bottom anchor as the composed action band, without hidden focusable controls. */
@Composable
@ReadOnlyComposable
@Suppress("UNUSED_PARAMETER") // Channel availability no longer adds a cue below the actions.
internal fun playerSeekPreviewBottomPadding(channelsAvailable: Boolean) =
    TvOverlayBottomPadding + TvOverlayActionButtonSize + TvOverlayTimelineActionGap

@Composable
internal fun PlayerOverlayChrome(
    headerContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    footerPadding: PaddingValues = PaddingValues(
        start = TvOverlaySidePadding, end = TvOverlaySidePadding,
        top = TvOverlayFooterGradientRunout, bottom = TvOverlayBottomPadding,
    ),
    footerContent: @Composable ColumnScope.() -> Unit,
) {
    // Inside the controls layer, the header comes down and the footer up as they fade in.
    val motion = LocalPlayerControlsMotion.current
    val entering = motion?.animateShown(
        enter = tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate),
        exit = snap(),
        label = "player-chrome-entry",
    )
    fun GraphicsLayerScope.enterFrom(offset: Dp) {
        if (entering != null && motion.transition?.targetState == EnterExitState.Visible) {
            translationY = offset.toPx() * (1f - entering.value)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        headerContent(
            Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { enterFrom(-PlayerMotion.ChromeOffset) }
                .background(topGradient)
                .padding(
                    start = TvOverlaySidePadding,
                    end = TvOverlaySidePadding,
                    top = TvOverlayTopPadding,
                    bottom = TvOverlayHeaderGradientRunout,
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer { enterFrom(PlayerMotion.ChromeOffset) }
                .background(bottomGradient)
                .testTag("player-footer")
                .padding(footerPadding),
            content = footerContent,
        )
    }
}
