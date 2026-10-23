package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private class BackdropLayer<T>(val id: Int, val target: T) {
    var ready by mutableStateOf(false)
    var revealing = false
    val alpha = Animatable(0f)
}

/**
 * A backdrop that follows the focused item [target] reads, with the browse screens' backdrop
 * timing ([BrowseMotionPolicy.backdropSettleMs]). Each new backdrop is composed unseen until its
 * [content] reports it ready, so the previous one stays instead of a half-loaded one showing,
 * then fades in over it, at most once per [BrowseMotionPolicy.backdropIntervalMs]; [onReveal]
 * tells which one is fading in. Reading the target here keeps focus steps from recomposing the
 * caller.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun <T> FocusBackdrop(
    target: () -> T,
    modifier: Modifier = Modifier,
    onReveal: (T) -> Unit = {},
    content: @Composable (target: T, onReady: () -> Unit) -> Unit,
) {
    val currentTarget by rememberUpdatedState(target)
    val currentOnReveal by rememberUpdatedState(onReveal)
    val layers = remember { mutableStateListOf(BackdropLayer(0, target())) }
    val reveals = remember { Mutex() }
    LaunchedEffect(Unit) {
        var nextId = 1
        val targets = snapshotFlow { currentTarget() }.debounce(BrowseMotionPolicy.backdropSettleMs)
        while (true) {
            val next = targets.first { it != layers.last().target }
            // One still loading has not shown anything; the newer target replaces it.
            layers.removeAll { !it.revealing }
            if (layers.lastOrNull()?.target != next) layers += BackdropLayer(nextId++, next)
            // The next change also waits for focus to settle, which completes the interval.
            delay(BrowseMotionPolicy.backdropIntervalMs - BrowseMotionPolicy.backdropSettleMs)
        }
    }
    Box(modifier) {
        for (layer in layers) key(layer.id) {
            LaunchedEffect(Unit) {
                withTimeoutOrNull(BrowseMotionPolicy.backdropLoadTimeoutMs) {
                    snapshotFlow { layer.ready }.first { it }
                }
                // A slow backdrop and a quick successor still change at most once per interval.
                reveals.withLock {
                    // A newer target replaced this one while it loaded or waited its turn.
                    if (layer !in layers) return@LaunchedEffect
                    layer.revealing = true
                    currentOnReveal(layer.target)
                    launch {
                        layer.alpha.animateTo(1f, BrowseMotionPolicy.backdropFade())
                        // Opaque now, it covers everything beneath.
                        val index = layers.indexOf(layer)
                        if (index > 0) layers.removeRange(0, index)
                    }
                    delay(BrowseMotionPolicy.backdropIntervalMs)
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = layer.alpha.value }) {
                content(layer.target) { layer.ready = true }
            }
        }
    }
}

/**
 * The details page's backdrop over the whole screen: the [art] blurred, or else the [logo]
 * blurred on its colour. Without either it is the plain screen background, so a step to an item
 * without a picture fades the previous backdrop out. It is opaque, so it takes the shell's
 * gradient directly. [onReady] waits for both its colour and its picture, so neither is seen to step.
 */
@Composable
internal fun BlurredScreenBackdrop(art: ArtworkId?, logo: ArtworkId?, onReady: () -> Unit, modifier: Modifier = Modifier) {
    var tintSettled by remember { mutableStateOf(false) }
    var pictureSettled by remember { mutableStateOf(false) }
    if (tintSettled && pictureSettled) LaunchedEffect(Unit) { onReady() }
    val tint = rememberDetailsTint(art, logo, onSettled = { tintSettled = true })
    // Once the opaque art shows, the tint beneath it would only cost another full-screen fill.
    var artShown by remember(art) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().underBrowseShellScrim(opaque = true)
            .drawBehind { if (!artShown) drawRect(tint.value) }.then(modifier),
    ) {
        DetailsHero(art, Modifier, blurred = true, picon = logo, onLoaded = { artShown = true },
            onSettled = { pictureSettled = true })
    }
}
