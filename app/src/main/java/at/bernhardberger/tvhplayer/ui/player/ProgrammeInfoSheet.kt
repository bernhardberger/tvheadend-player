package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy

/** Only the section at rest may request focus. Outside the player page nothing changes. */
internal val LocalPlayerPageActive = compositionLocalOf { true }
internal val LocalPlayerPageRailHeader = compositionLocalOf { false }
internal val LocalPlayerPageScrim = compositionLocalOf<PlayerPageScrim?> { null }
internal val LocalPlayerPageProgress = compositionLocalOf<(() -> Float)?> { null }
internal val LocalPlayerRailPeek = compositionLocalOf<MutableTransitionState<Boolean>?> { null }

/** All frame-rate reads stay in the layer, including measured travel for the rail. */
@Composable
internal fun Modifier.pageMotion(
    window: IntRange,
    easing: Easing = BrowseMotionPolicy.pageDecelerate,
    dy: Dp = 0.dp,
    dx: Dp = 0.dp,
    entering: Boolean = true,
    fromAlpha: Float = if (entering) 0f else 1f,
    toAlpha: Float = if (entering) 1f else 0f,
    alphaWindow: IntRange = window,
    inheritedFade: IntRange? = null,
    dyPx: (() -> Float)? = null,
): Modifier {
    val progress = LocalPlayerPageProgress.current ?: return this
    fun opacity(): Float {
        val ms = progress() * BrowseMotionPolicy.pageDownMs
        fun fraction(range: IntRange) = easing.transform(((ms - range.first) / (range.last - range.first)).coerceIn(0f, 1f))
        val alpha = fromAlpha + (toAlpha - fromAlpha) * fraction(alphaWindow)
        return if (inheritedFade == null || alpha == 0f) alpha else alpha / (1f - fraction(inheritedFade)).coerceAtLeast(0.001f)
    }
    return graphicsLayer {
        val ms = progress() * BrowseMotionPolicy.pageDownMs
        fun fraction(range: IntRange) = easing.transform(((ms - range.first) / (range.last - range.first)).coerceIn(0f, 1f))
        val travel = if (entering) 1f - fraction(window) else fraction(window)
        translationY = (dyPx?.invoke() ?: dy.toPx()) * travel
        translationX = dx.toPx() * travel
    }.graphicsLayer { // Keep the fade's offscreen bounds inside the translated layer.
        alpha = opacity()
    }.drawWithContent {
        if (opacity() > 0f) drawContent()
    }
}

/** Chrome supplies its measured gradients; the page draws them in the stationary viewport. */
internal class PlayerPageScrim {
    var alpha by mutableStateOf<() -> Float>({ 0f })
    var bottom by mutableStateOf<DrawScope.(Float) -> Unit>({})
    var rail by mutableStateOf<DrawScope.(Float) -> ShaderBrush?>({ null })
}

/** One reversible clock, stationary sections and scrims, independently choreographed elements. */
@Composable
internal fun <T : Any> PlayerPage(
    details: T?,
    modifier: Modifier = Modifier,
    railHeader: (@Composable () -> Unit)? = null,
    player: @Composable (keepControls: Boolean) -> Unit,
    programme: @Composable (T) -> Unit,
) {
    val open = details != null
    val transition = updateTransition(open, label = "player-page")
    val progress = transition.animateFloat({ BrowseMotionPolicy.pageProgress(targetState) }, label = "page-progress") { if (it) 1f else 0f }
    val readProgress = remember { { progress.value } }
    val settled = transition.currentState == open && transition.targetState == open && !transition.isRunning
    val composed = open || !settled
    val shown = rememberLastShown(details)
    val railPeek = remember { MutableTransitionState(false) }
    val peekTransition = updateTransition(railPeek, label = "rail-peek")
    val peekAlpha = peekTransition.animateFloat({
        if (targetState) tween(150, easing = PlayerMotion.StandardDecelerate)
        else tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate)
    }, label = "rail-peek-alpha") { if (it) 1f else 0f }
    val peekVisible = railPeek.currentState || !railPeek.isIdle
    val lastHeader = remember { mutableStateOf(railHeader) }
    androidx.compose.runtime.SideEffect { if (open || railHeader != null) lastHeader.value = railHeader }
    val header = if (!open && (!settled || peekVisible)) railHeader ?: lastHeader.value else railHeader
    val scrim = remember { PlayerPageScrim() }
    CompositionLocalProvider(LocalPlayerPageProgress provides readProgress, LocalPlayerPageScrim provides scrim,
        LocalPlayerRailPeek provides railPeek) {
        Box(modifier.fillMaxSize().clipToBounds()) {
            // Gradients do not need four viewport-sized alpha layer stacks. Multiply their paint
            // alpha in one draw node, and skip invisible passes; frame-rate reads stay in draw.
            Box(Modifier.matchParentSize().testTag("player-page-scrim").drawWithCache {
                val top = PlayerChromeTokens.topScrimHeight.toPx()
                val topBrush = Brush.verticalGradient(*PlayerChromeTokens.topScrimStops, endY = top)
                val detailsBrush = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.84f),
                    1f to Color.Black.copy(alpha = 0.92f))
                onDrawBehind {
                    val ms = readProgress() * BrowseMotionPolicy.pageDownMs
                    val chrome = scrim.alpha()
                    val railFade = (ms / 300f).coerceIn(0f, 1f)
                    val bottomAlpha = chrome * (1f - ((ms - 200f) / 150f).coerceIn(0f, 1f))
                    val railAlpha = chrome * (1f - railFade)
                    val rail = if (railAlpha > 0f) scrim.rail(this, railAlpha) else null
                    if (rail != null) {
                        // Both gradients are black: SrcOver preserves their exact overlap without
                        // painting the top band twice. The rail veil has ink across the viewport.
                        val topOverRail = Brush.verticalGradient(*PlayerChromeTokens.topScrimStops.map { (stop, color) ->
                            stop to color.copy(alpha = color.alpha * chrome)
                        }.toTypedArray(), endY = top) as ShaderBrush
                        drawRect(ShaderBrush(android.graphics.ComposeShader(
                            rail.createShader(size), topOverRail.createShader(size), android.graphics.PorterDuff.Mode.SRC_OVER)))
                    } else if (chrome > 0f) {
                        drawRect(topBrush, size = androidx.compose.ui.geometry.Size(size.width, top), alpha = chrome)
                    }
                    if (bottomAlpha > 0f) scrim.bottom(this, bottomAlpha)
                    val detailsAlpha = if (header != null) railFade else BrowseMotionPolicy.pageStandardDecelerate.transform(railFade)
                    if (detailsAlpha > 0f) drawRect(detailsBrush, alpha = detailsAlpha)
                }
            })
            PlayerMotionFrame(leaving = open || !settled, modifier = Modifier.fillMaxSize().testTag("player-page-first")) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalPlayerPageActive provides (!open && settled)) { player(composed) }
                }
            }
            if (composed || header != null && peekVisible) {
                PlayerMotionFrame(leaving = composed && (!open || !settled), modifier = Modifier.testTag("player-page-second")) {
                    CompositionLocalProvider(
                        LocalPlayerPageActive provides (open && settled),
                        LocalPlayerPageRailHeader provides (header != null),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            if (peekVisible) {
                                // Only text fades here; do not allocate a viewport-sized fade buffer.
                                Box(Modifier.graphicsLayer {
                                        alpha = peekAlpha.value
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                    }.drawWithContent { if (peekAlpha.value > 0f) drawContent() }) { header?.invoke() }
                            }
                            if (composed) shown?.let {
                                Box(Modifier.fillMaxSize().padding(top = if (header != null) 96.dp else 0.dp)) { programme(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** This is the schedule's actual header, including when only its top section peeks below the rail. */
@Composable
internal fun ProgrammeScheduleHeader(identity: String) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val peekTravel = maxHeight - 96.dp
        Row(Modifier.fillMaxWidth().height(96.dp)
            .padding(horizontal = PlayerChromeTokens.gridMargin)
            .padding(top = 32.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$identity · ${stringResource(R.string.details_tab_schedule)}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).pageMotion(0..420, BrowseMotionPolicy.pageEmphasized,
                    dy = peekTravel, fromAlpha = 0.55f)
                    .padding(end = 20.dp).testTag("details-heading").semantics { heading() })
            PlayerDownHint(stringResource(R.string.nav_channels), Modifier.pageMotion(300..500,
                BrowseMotionPolicy.pageStandardDecelerate, dx = 16.dp).testTag("details-player-hint"), up = true)
        }
    }
}

/** Padding and text colour only: motion and dimming belong to the enclosing player page. */
@Composable
internal fun ProgrammeInfoSheetFrame(
    modifier: Modifier = Modifier,
    paneTitle: String? = null,
    panelTag: String = "live-info-panel",
    bottomPadding: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().focusGroup()
        .padding(horizontal = PlayerChromeTokens.gridMargin)
        // The pinned 32dp clock inset + 35dp line box leaves a 9dp gap above this row.
        .padding(top = if (LocalPlayerPageRailHeader.current) 0.dp else 76.dp, bottom = bottomPadding)
        .testTag(panelTag).semantics { paneTitle?.let { this.paneTitle = it } }) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}
