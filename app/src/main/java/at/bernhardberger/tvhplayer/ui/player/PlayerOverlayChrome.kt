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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineActionGap
import at.bernhardberger.tvhplayer.ui.notifications.LocalAppNoticeBottomObstruction

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
        start = PlayerChromeTokens.gridMargin, end = PlayerChromeTokens.gridMargin,
        top = TvOverlayFooterGradientRunout, bottom = TvOverlayBottomPadding,
    ),
    /**
     * Fraction of [PlayerBannerDrop] the footer still rests below its place: controls taken over
     * from the Banner start where its info and timeline rest and rise with their scrim; the header
     * stays put.
     */
    bannerDrop: () -> Float = { 0f },
    /** Trial: how far, in px, the footer's scrim reaches above it, for content standing on top of it. */
    scrimRise: () -> Float = { 0f },
    footerContent: @Composable ColumnScope.() -> Unit,
) {
    val noticeObstruction = LocalAppNoticeBottomObstruction.current
    val density = LocalDensity.current
    DisposableEffect(noticeObstruction) {
        onDispose { noticeObstruction?.value = 0.dp }
    }
    // Inside the controls layer, the header comes down and the footer up as they fade in;
    // controls revealed by a zap fade in where they rest.
    val motion = LocalPlayerControlsMotion.current
    val railExpansion = LocalInPlaceRailExpansion.current
    val viewport = LocalPlayerPageScrim.current.takeIf { motion != null }
    var footerHeight by remember { mutableIntStateOf(0) }
    var footerObstruction by remember { mutableStateOf(0.dp) }
    // Notices rest at the screen's bottom while the page below the controls is in view.
    val pageActive = LocalPlayerPageActive.current
    SideEffect { noticeObstruction?.value = if (pageActive) footerObstruction else 0.dp }
    SideEffect {
        viewport?.bottom = { opacity ->
            val height = footerHeight + scrimRise()
            val alpha = opacity * (1f - railExpansion())
            if (alpha > 0f) translate(top = size.height - height) {
                drawRect(PlayerChromeTokens.bottomScrim(height, this), size = Size(size.width, height), alpha = alpha)
            }
        }
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
                    // Paint the departing controls, not a footer-sized offscreen texture.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .drawWithContent { if (railExpansion() < 1f) drawContent() }
                .testTag("player-footer")
                .onSizeChanged { size ->
                    footerHeight = size.height
                    // Ignore the empty gradient runout; keep content and bottom safe area clear.
                    footerObstruction = with(density) {
                        (size.height.toDp() - footerPadding.calculateTopPadding()).coerceAtLeast(0.dp)
                    }
                }
                .then(if (viewport != null) Modifier else Modifier.drawWithCache {
                    val scrim = PlayerChromeTokens.bottomScrim(size.height, this)
                    onDrawBehind {
                        val rise = scrimRise()
                        if (rise <= 0f) drawRect(scrim) else {
                            val tall = PlayerChromeTokens.bottomScrim(size.height + rise, this)
                            translate(top = -rise) { drawRect(tall, size = size.copy(height = size.height + rise)) }
                        }
                    }
                })
                .padding(footerPadding),
            content = footerContent,
        )
    }
}
