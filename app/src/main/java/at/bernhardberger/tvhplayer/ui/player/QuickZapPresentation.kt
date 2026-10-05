package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding

/**
 * One motion value coordinates the departing footer and the rail rising beneath it, as one
 * vertical step. The rail rests where the footer was; the page scrim follows the same value.
 */
@Composable
internal fun QuickZapPresentation(
    expanded: Boolean,
    channelContent: @Composable () -> Unit,
    preview: @Composable () -> Unit,
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
    val revealed by remember(expansion) { derivedStateOf { expansion.value == 1f } }
    val shown by remember(expansion) { derivedStateOf { expansion.value > 0f } }
    // The schedule header peeks only once the rail stands, and leaves before it folds away.
    val railPeek = LocalPlayerRailPeek.current
    LaunchedEffect(expanded, revealed, railPeek) { railPeek?.targetState = expanded && revealed }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var railHeight by remember { mutableIntStateOf(0) }
    val pageProgress = LocalPlayerPageProgress.current
    val leavingPage by remember(pageProgress) { derivedStateOf { (pageProgress?.invoke() ?: 0f) > 0f } }
    val viewport = LocalPlayerPageScrim.current
    val scrim = viewport ?: remember { PlayerPageScrim().apply { alpha = { 1f } } }
    SideEffect {
        scrim.previewTop = { (viewportHeight - railHeight).toFloat() }
        scrim.expansion = expansion::value
    }
    DisposableEffect(scrim) { onDispose { scrim.expansion = { 0f } } }
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewportHeight = it.height }) {
        if (viewport == null) Box(Modifier.matchParentSize().playerScrim(scrim))
        Box(
            Modifier.fillMaxSize()
                .focusProperties { canFocus = !expanded }
                .then(if (expanded) Modifier.clearAndSetSemantics { } else Modifier)
                .testTag("player-zap-controls"),
        ) {
            // The footer gives way to the rail; the header (clock, top scrim) stays.
            CompositionLocalProvider(LocalRailExpansion provides expansion::value,
                LocalPlayerPageScrim provides scrim) { controls() }
        }
        if (!shown && !expanded) return@Box
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { railHeight = it.height }
                .graphicsLayer { translationY = size.height * (1f - expansion.value) }
                .padding(bottom = TvOverlayBottomPadding),
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .padding(bottom = PlayerChromeTokens.previewCardGap)
                    .then(if (leavingPage) Modifier.pageMotion(0..200, BrowseMotionPolicy.pageAccelerate,
                        dy = (-120).dp, entering = false) else Modifier)
                    .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { })
                    .testTag("player-zap-preview-slot"),
                contentAlignment = Alignment.BottomStart,
            ) { preview() }
            // Leaves entirely above the viewport, further and so faster than the headline rising beneath it.
            Box(
                Modifier.fillMaxWidth()
                    .then(if (leavingPage) Modifier.pageMotion(60..360, BrowseMotionPolicy.pageEmphasized,
                        entering = false, alphaWindow = 60..260,
                        dyPx = { viewportHeight.toFloat() * -1f }) else Modifier)
                    .testTag("player-zap-tray"),
            ) { channelContent() }
        }
    }
}

/** How far the rail has opened, for the controls' footer to give way to it. */
internal val LocalRailExpansion = compositionLocalOf<() -> Float> { { 0f } }
