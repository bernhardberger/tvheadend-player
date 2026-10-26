package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.util.lerp
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy

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
    /** The tray continues off the screen's bottom edge, in as much room as the page header leaves it. */
    trayBleeds: Boolean = false,
    /** Centred in the page's top band while the rail is open. */
    header: (@Composable () -> Unit)? = null,
    /**
     * The closed rail's cards peek at the bottom edge at this emphasis (the action row's), and
     * rise from there when it opens. Null keeps the closed rail out of the composition.
     */
    peek: (() -> Float)? = null,
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
    val currentExpanded by rememberUpdatedState(expanded)
    val resting = remember(expansion) { derivedStateOf { !currentExpanded && expansion.value == 0f } }
    val channelPeek = if (peek != null) remember(resting) { ChannelPeek(resting) } else null
    // The schedule header peeks only once the rail stands, and leaves before it folds away.
    val railPeek = LocalPlayerRailPeek.current
    LaunchedEffect(railPeek) { railPeek?.targetState = false }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var railHeight by remember { mutableIntStateOf(0) }
    val pageProgress = LocalPlayerPageProgress.current
    val leavingPage by remember(pageProgress) { derivedStateOf { (pageProgress?.invoke() ?: 0f) > 0f } }
    val viewport = LocalPlayerPageScrim.current
    val scrim = viewport ?: remember { PlayerPageScrim().apply { alpha = { 1f } } }
    SideEffect {
        // The denser rail scrim starts at the rail's top.
        scrim.previewTop = { (viewportHeight - railHeight).toFloat() }
        scrim.expansion = expansion::value
    }
    DisposableEffect(scrim) { onDispose { scrim.expansion = { 0f } } }
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewportHeight = it.height }) {
        if (viewport == null) Box(Modifier.matchParentSize().playerScrim(scrim))
        Box(
            Modifier.fillMaxSize()
                // Only withdraws focus: as their ancestor, granting it would override what the controls withdraw.
                .focusProperties { if (expanded) canFocus = false }
                .then(if (expanded) Modifier.clearAndSetSemantics { } else Modifier)
                .testTag("player-zap-controls"),
        ) {
            // The footer gives way to the rail; the header (clock, top scrim) stays.
            CompositionLocalProvider(LocalRailExpansion provides expansion::value,
                LocalPlayerPageScrim provides scrim,
                LocalChannelPeekLift provides if (channelPeek != null) ChannelPeekFooterLift else 0.dp) { controls() }
        }
        if (!shown && !expanded && channelPeek == null) return@Box
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .then(if (trayBleeds) Modifier.padding(top = TvGridVerticalMargin + TvBrowseHeaderHeight) else Modifier)
                .onSizeChanged { railHeight = it.height }
                .graphicsLayer {
                    val e = expansion.value
                    val cardsTop = channelPeek?.cardsTop ?: Float.NaN
                    translationY = if (cardsTop.isNaN()) {
                        size.height * (1f - e)
                    } else {
                        // Rises from where the cards peek, their top just above the bottom edge.
                        lerp(size.height - cardsTop - ChannelPeekDepth.toPx(), 0f, e)
                    }
                    if (peek != null) alpha = lerp(ChannelPeekAlpha * peek(), 1f, e)
                }
                .then(if (channelPeek == null) Modifier else Modifier
                    .onGloballyPositioned { channelPeek.layer = it; channelPeek.update() }
                    // Only the cards peek: what stands above them is uncovered as the rail rises.
                    .drawWithContent {
                        val cardsTop = channelPeek.cardsTop
                        val e = expansion.value
                        if (e < 1f && !cardsTop.isNaN()) {
                            clipRect(left = -size.width, top = cardsTop * (1f - e),
                                right = size.width * 2f, bottom = size.height * 2f) {
                                this@drawWithContent.drawContent()
                            }
                        } else if (!cardsTop.isNaN() || e > 0f) {
                            drawContent()
                        }
                    })
                .padding(bottom = if (trayBleeds) 0.dp else TvGridVerticalMargin),
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .padding(bottom = PlayerChromeTokens.previewCardGap)
                    .then(if (leavingPage) Modifier.pageMotion(0..200, BrowseMotionPolicy.pageAccelerate,
                        dy = (-120).dp, entering = false) else Modifier)
                    .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { })
                    .then(if (channelPeek != null) Modifier.graphicsLayer { alpha = expansion.value } else Modifier)
                    .testTag("player-zap-preview-slot"),
                contentAlignment = Alignment.BottomStart,
            ) {
                // Not while only peeking: a held preview of the last card would cross-fade in as the rail rises.
                if (expanded || shown) preview()
            }
            // Leaves entirely above the viewport, further and so faster than the headline rising beneath it.
            Box(
                Modifier.fillMaxWidth()
                    .then(if (trayBleeds) Modifier.weight(1f, fill = false) else Modifier)
                    .then(if (leavingPage) Modifier.pageMotion(60..360, BrowseMotionPolicy.pageEmphasized,
                        entering = false, alphaWindow = 60..260,
                        dyPx = { viewportHeight.toFloat() * -1f }) else Modifier)
                    .testTag("player-zap-tray")
                    // The closed rail only peeks: it takes no focus and is not read out.
                    .focusProperties { if (!expanded) canFocus = false }
                    .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { }),
            ) { CompositionLocalProvider(LocalChannelPeek provides channelPeek) { channelContent() } }
        }
        if (header != null) Box(
            Modifier.align(Alignment.TopCenter)
                .padding(top = TvGridVerticalMargin).heightIn(min = TvBrowseHeaderHeight)
                .graphicsLayer { alpha = expansion.value }
                .then(if (leavingPage) Modifier.pageMotion(0..200, BrowseMotionPolicy.pageAccelerate,
                    entering = false) else Modifier)
                .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { })
                .testTag("player-zap-header"),
            contentAlignment = Alignment.Center,
        ) { header() }
    }
}

/** How far the rail has opened, for the controls' footer to give way to it. */
internal val LocalRailExpansion = compositionLocalOf<() -> Float> { { 0f } }

/** How much of the closed rail's cards shows at the bottom edge. */
private val ChannelPeekDepth = 24.dp

/** The peeking cards' emphasis relative to the action row: present, but quieter than the controls. */
private const val ChannelPeekAlpha = 0.6f

/** How far the controls' footer rises to make room for the peeking cards. */
internal val ChannelPeekFooterLift = 14.dp

/** The closed rail's cards, peeking below the controls; null where the rail does not peek. */
internal class ChannelPeek(restingState: State<Boolean>) {
    /** The rail is closed and still: nothing in the peek moves, such as the playing card's sound bars. */
    val resting by restingState
    internal var layer: LayoutCoordinates? = null
    private var cards: LayoutCoordinates? = null
    private var cardsRoom = 0f

    /** The peeking cards' top in the rail; not a number until both are placed. */
    var cardsTop by mutableFloatStateOf(Float.NaN)
        private set

    /** The peeking row places its cards, [room] below the top of its list. */
    fun cards(coordinates: LayoutCoordinates, room: Float) {
        cards = coordinates
        cardsRoom = room
        update()
    }

    fun update() {
        val layer = layer?.takeIf { it.isAttached } ?: return
        val cards = cards?.takeIf { it.isAttached } ?: return
        cardsTop = layer.localPositionOf(cards, Offset.Zero).y + cardsRoom
    }
}

internal val LocalChannelPeek = compositionLocalOf<ChannelPeek?> { null }

/** Whether this list's cards are the rail's peeking ones, rather than another row's. */
internal val LocalChannelPeekAnchor = compositionLocalOf { true }

/** Lifts the controls' footer while the closed rail peeks beneath it. */
internal val LocalChannelPeekLift = compositionLocalOf { 0.dp }
