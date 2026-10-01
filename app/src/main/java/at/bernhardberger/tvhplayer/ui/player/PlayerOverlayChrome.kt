package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineActionGap

/** How much lower the Banner's info and timeline rest than the controls', which sit above the action row. */
internal val PlayerBannerDrop = TvOverlayActionButtonSize + TvOverlayTimelineActionGap

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
    /**
     * Fraction of [PlayerBannerDrop] the footer still rests below its place: controls taken over
     * from the Banner start where its info and timeline rest and rise with their scrim; the header
     * stays put.
     */
    bannerDrop: () -> Float = { 0f },
    footerContent: @Composable ColumnScope.() -> Unit,
) {
    // Inside the controls layer, the header comes down and the footer up as they fade in;
    // controls revealed by a zap fade in where they rest.
    val motion = LocalPlayerControlsMotion.current
    val travels = LocalPlayerControlsEntry.current == PlayerControlsEntry.TRAVEL
    val entering = motion?.animateEnterAfterFirstFrames(tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate))
    fun GraphicsLayerScope.enterFrom(offset: Dp) {
        if (travels && entering != null && motion.transition.targetState == EnterExitState.Visible) {
            translationY = offset.toPx() * (1f - entering.value)
        }
    }
    fun GraphicsLayerScope.riseFromBanner() {
        val drop = bannerDrop()
        if (drop != 0f) translationY = PlayerBannerDrop.toPx() * drop
    }
    Box(modifier = modifier.fillMaxSize()) {
        // The header draws its own top fade; the footer its scrim, in its own bounds.
        headerContent(Modifier.matchParentSize().graphicsLayer { enterFrom(-PlayerMotion.ChromeOffset) })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer { enterFrom(PlayerMotion.ChromeOffset); riseFromBanner() }
                .testTag("player-footer")
                .drawWithCache {
                    val scrim = PlayerChromeTokens.bottomScrim(size.height, this)
                    onDrawBehind { drawRect(scrim) }
                }
                .padding(footerPadding),
            content = footerContent,
        )
    }
}
