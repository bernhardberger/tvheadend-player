package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlayTopPadding

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

/** Chrome supplies geometry, not paint: one stationary viewport owns all player shading. */
internal class PlayerPageScrim {
    var alpha by mutableStateOf<() -> Float>({ 0f })
    var footerHeight by mutableStateOf<() -> Float>({ 0f })
    var previewTop by mutableStateOf<() -> Float>({ 0f })
    var expansion by mutableStateOf<() -> Float>({ 0f })
}

private data class PlayerScrimStop(val height: Float, val alpha: Float)

internal fun Modifier.playerScrim(
    scrim: PlayerPageScrim,
    progress: () -> Float = { 0f },
    railHeader: Boolean = false,
): Modifier = drawWithCache {
    val height = size.height.coerceAtLeast(1f)
    val header = PlayerChromeTokens.topScrimHeight.toPx()
    val runout = TvOverlayFooterGradientRunout.toPx()
    val footer = (height - scrim.footerHeight()).coerceIn(header.coerceAtMost(height), height)
    val preview = scrim.previewTop().coerceIn(0f, height)
    val railTop = (preview - runout).coerceAtLeast(0f)
    val railHeaderEnd = header.coerceAtMost(railTop)
    fun topAlpha(y: Float): Float = if (y <= header / 2f) {
        0.72f + (0.48f - 0.72f) * y / (header / 2f)
    } else (0.48f * (1f - (y - header / 2f) / (header / 2f))).coerceAtLeast(0f)
    // Four samples per half-header approximate the old
    // two-gradient SRC_OVER curve; below the header all segments are exactly linear.
    val headerStops = List(9) { header * it / 8f }
    val controls = headerStops.map { PlayerScrimStop(it, topAlpha(it)) } + listOf(
        PlayerScrimStop(footer, 0f),
        PlayerScrimStop((footer + runout).coerceAtMost(height), 0.60f),
        PlayerScrimStop((footer + runout + 52.dp.toPx()).coerceAtMost(height), 0.80f),
        PlayerScrimStop(height, 0.92f),
    )
    val rail = (headerStops + listOf(railHeaderEnd, railTop, preview, height)).sorted().map { y ->
        val veil = when {
            y < railHeaderEnd -> 0.36f * y / railHeaderEnd
            y <= railTop -> 0.36f
            y < preview -> 0.36f + 0.24f * (y - railTop) / (preview - railTop)
            else -> 0.60f + 0.32f * ((y - preview) / (height - preview).coerceAtLeast(1f))
        }
        PlayerScrimStop(y, 1f - (1f - topAlpha(y)) * (1f - veil))
    }
    val details = (headerStops + height).map { y ->
        val backdrop = 0.84f + 0.08f * y / height
        PlayerScrimStop(y, 1f - (1f - topAlpha(y)) * (1f - backdrop))
    }
    fun List<PlayerScrimStop>.alphaAt(y: Float): Float {
        val index = indexOfFirst { it.height >= y }
        if (index <= 0) return if (index == 0) first().alpha else last().alpha
        val from = this[index - 1]
        val to = this[index]
        return from.alpha + (to.alpha - from.alpha) * (y - from.height) / (to.height - from.height)
    }
    // Keep every state's edges fixed. Only their ink fades; moving matched stop indices
    // sweeps the footer/preview boundaries through the picture and briefly brightens it.
    val heights = (controls + rail + details).map { it.height }.distinct().sorted()
    val controlsAlpha = heights.map { controls.alphaAt(it) }
    val railAlpha = heights.map { rail.alphaAt(it) }
    val detailsAlpha = heights.map { details.alphaAt(it) }
    onDrawBehind {
        val chrome = scrim.alpha()
        val page = (progress() * BrowseMotionPolicy.pageDownMs / 300f).coerceIn(0f, 1f)
        val detail = if (railHeader) page else BrowseMotionPolicy.pageStandardDecelerate.transform(page)
        if (chrome > 0f || detail > 0f) {
            val expansion = scrim.expansion().coerceIn(0f, 1f)
            val stops = heights.indices.map { index ->
                val base = (controlsAlpha[index] + (railAlpha[index] - controlsAlpha[index]) * expansion) * chrome
                val alpha = base + (detailsAlpha[index] - base) * detail
                (heights[index] / height) to Color.Black.copy(alpha = alpha)
            }.toTypedArray()
            drawRect(Brush.verticalGradient(*stops, endY = height))
        }
    }
}

/** One reversible clock, stationary sections and scrims, independently choreographed elements. */
@Composable
internal fun <T : Any> PlayerPage(
    details: T?,
    modifier: Modifier = Modifier,
    railHeader: (@Composable () -> Unit)? = null,
    railExpanded: Boolean = false,
    nowPlaying: (@Composable () -> Unit)? = null,
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
            Box(Modifier.matchParentSize().testTag("player-page-scrim")
                .playerScrim(scrim, readProgress, railHeader = header != null))
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
                                Box(Modifier.fillMaxSize().padding(top = if (header != null) 156.dp else 0.dp)) { programme(it) }
                            }
                        }
                    }
                }
            }
            // Shared viewport layer: neither strip nor up affordance travels with either page.
            Box(Modifier.fillMaxWidth().padding(top = TvOverlayTopPadding)) {
                // Measure the clock's actual line box, including font scale and font metrics.
                Text("", style = MaterialTheme.typography.titleLarge, maxLines = 1,
                    modifier = Modifier.width(0.dp).clearAndSetSemantics {})
                Box(Modifier.matchParentSize()) {
                    if (nowPlaying != null) AnimatedVisibility(
                        visible = railExpanded || composed,
                        enter = fadeIn(tween(if (header != null) PlayerMotion.MediumMs else 0,
                            easing = PlayerMotion.StandardDecelerate)),
                        exit = fadeOut(tween(if (header != null) PlayerMotion.ShortMs else 0,
                            easing = PlayerMotion.StandardAccelerate)),
                        modifier = Modifier.align(Alignment.CenterStart)
                            .padding(start = PlayerChromeTokens.gridMargin).wrapContentHeight(unbounded = true)
                            .then(if (header == null) Modifier.pageMotion(200..400) else Modifier)
                            .testTag("player-now-playing-motion"),
                    ) { nowPlaying() }
                    if (composed || header != null) PlayerDownHint(
                        stringResource(if (header != null) R.string.nav_channels else R.string.details_back_to_tv),
                        Modifier.align(Alignment.Center).pageMotion(300..500,
                            BrowseMotionPolicy.pageStandardDecelerate).testTag("details-player-hint"), up = true,
                    )
                }
            }
        }
    }
}

/** This is the schedule's actual header, including when only its top section peeks below the rail. */
@Composable
internal fun ProgrammeScheduleHeader(identity: String) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val peekTravel = maxHeight - 156.dp
        Row(Modifier.fillMaxWidth().height(156.dp)
            .padding(horizontal = PlayerChromeTokens.gridMargin)
            .padding(top = 92.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$identity · ${stringResource(R.string.details_tab_schedule)}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).pageMotion(60..360, BrowseMotionPolicy.pageEmphasized,
                    dy = peekTravel, fromAlpha = 0.55f)
                    .padding(end = 20.dp).testTag("details-heading").semantics { heading() })
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
