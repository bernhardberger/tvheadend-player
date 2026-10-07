package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import at.bernhardberger.tvhplayer.ui.TvIconButtonSize
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout

/**
 * How much lower the Banner's info and timeline rest than the controls', which sit above the action
 * row: the action row and its gap, and the timeline's room below its row, which the Banner gives to
 * its bottom margin.
 */
internal val PlayerBannerDrop = TvIconButtonSize + PlayerChromeTokens.timelineActionGap + TimelineRowInset

/** The Banner's bottom padding: its timeline row ends on the bottom margin, not its room below the row. */
internal val PlayerBannerBottomPadding = TvGridVerticalMargin - TimelineRowInset

/** How far the footer travels up while the rail takes its place; the page's own exit travels the same. */
private val RailFooterRise = 96.dp

/** Same bottom anchor as the composed action band, without hidden focusable controls. */
@Composable
@ReadOnlyComposable
@Suppress("UNUSED_PARAMETER") // Channel availability no longer adds a cue below the actions.
internal fun playerSeekPreviewBottomPadding(channelsAvailable: Boolean) =
    TvGridVerticalMargin + TvIconButtonSize + PlayerChromeTokens.timelineActionGap

@Composable
internal fun PlayerOverlayChrome(
    headerContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    footerPadding: PaddingValues = PaddingValues(
        start = TvGridHorizontalMargin, end = TvGridHorizontalMargin,
        top = TvOverlayFooterGradientRunout, bottom = TvGridVerticalMargin,
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
    val railExpansion = LocalRailExpansion.current
    val viewport = LocalPlayerPageScrim.current.takeIf { motion != null }
    var footerHeight by remember { mutableIntStateOf(0) }
    SideEffect {
        viewport?.footerHeight = { footerHeight.toFloat() }
    }
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
                .pageMotion(0..220, BrowseMotionPolicy.pageAccelerate, dy = (-96).dp, entering = false)
                .graphicsLayer {
                    enterFrom(PlayerMotion.ChromeOffset); riseFromBanner(); alpha = 1f - railExpansion()
                    // The footer gives way upward as the rail rises beneath it.
                    translationY -= RailFooterRise.toPx() * railExpansion()
                    // Paint the departing controls, not a footer-sized offscreen texture.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .drawWithContent { if (railExpansion() < 1f) drawContent() }
                .testTag("player-footer")
                .onSizeChanged { size ->
                    footerHeight = size.height
                }
                .then(if (viewport != null) Modifier else Modifier.drawWithCache {
                    val scrim = PlayerChromeTokens.bottomScrim(size.height, this)
                    onDrawBehind { drawRect(scrim) }
                })
                .padding(footerPadding),
            content = footerContent,
        )
    }
}
