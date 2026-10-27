package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.runtime.mutableStateOf
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
 * The rail and the controls in one composition, such as a controls layer's decoration.
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
    val rail = rememberQuickZapRail(expanded)
    Box(Modifier.fillMaxSize()) {
        QuickZapControls(rail, peek, controls)
        QuickZapRail(rail, channelContent, preview, trayBleeds, header, peeks = peek != null)
    }
}

/**
 * The rail's motion and placement, shared by the controls it displaces ([QuickZapControls]) and
 * the rail itself ([QuickZapRail]).
 *
 * A [detached] rail is composed apart from the controls, outside their layer, so the closed rail's
 * cards outlive a hidden layer instead of being built again inside every reveal's first frame.
 * While the controls are composed it draws at their layer's alpha; without them it stands hidden,
 * takes no focus and is not read out.
 */
internal class QuickZapRailState(
    expandedState: State<Boolean>,
    internal val expansionAnimation: Animatable<Float, AnimationVector1D>,
    resting: State<Boolean>,
    internal val detached: Boolean,
) {
    val expanded by expandedState
    internal val expansion: State<Float> = expansionAnimation.asState()
    internal val peek = ChannelPeek(resting)
    internal var viewportHeight by mutableIntStateOf(0)
    internal var railHeight by mutableIntStateOf(0)

    /** The controls are composed: entering, up, or leaving. */
    internal var hosted by mutableStateOf(!detached)
    /** The action row's emphasis, set by the controls; read only while drawing. */
    internal var emphasis by mutableStateOf<() -> Float>({ 1f })
    /** The controls layer's alpha, set by the controls; read only while drawing. */
    internal var layerAlpha by mutableStateOf<() -> Float>({ 1f })
}

@Composable
internal fun rememberQuickZapRail(expanded: Boolean, detached: Boolean = false): QuickZapRailState {
    val expandedState = rememberUpdatedState(expanded)
    val expansion = remember { Animatable(if (expanded) 1f else 0f) }
    return remember {
        QuickZapRailState(expandedState, expansion,
            derivedStateOf { !expandedState.value && expansion.value == 0f }, detached)
    }
}

/**
 * [nowSec] while the controls are composed; otherwise the time they last showed, so a hidden
 * detached rail's cards do not recompose every second. Read it in the rail's content, not
 * beside the rail, so the controls' show and hide recompose only that content.
 */
@Composable
internal fun QuickZapRailState.clock(nowSec: Long): Long {
    val shownAt = remember { longArrayOf(nowSec) }
    if (hosted) shownAt[0] = nowSec
    return shownAt[0]
}

/** The controls beneath the rail, in their layer: their footer gives way as the rail rises. */
@Composable
internal fun QuickZapControls(
    rail: QuickZapRailState,
    /** As [QuickZapPresentation]'s peek: the action row's emphasis, or null where the rail does not peek. */
    peek: (() -> Float)?,
    controls: @Composable () -> Unit,
) {
    // The schedule header peeks only once the rail stands, and leaves before it folds away.
    val railPeek = LocalPlayerRailPeek.current
    LaunchedEffect(railPeek) { railPeek?.targetState = false }
    val viewport = LocalPlayerPageScrim.current
    val scrim = viewport ?: remember { PlayerPageScrim().apply { alpha = { 1f } } }
    val layerAlpha = LocalPlayerControlsAlpha.current
    SideEffect {
        // The denser rail scrim starts at the rail's top.
        scrim.previewTop = { (rail.viewportHeight - rail.railHeight).toFloat() }
        scrim.expansion = rail.expansion::value
        rail.emphasis = peek ?: { 1f }
        rail.layerAlpha = layerAlpha
        rail.hosted = true
    }
    DisposableEffect(scrim) { onDispose { scrim.expansion = { 0f } } }
    DisposableEffect(rail) { onDispose { rail.hosted = false } }
    val expanded = rail.expanded
    Box(Modifier.fillMaxSize().clipToBounds()) {
        if (viewport == null) Box(Modifier.matchParentSize().playerScrim(scrim))
        Box(
            Modifier.fillMaxSize()
                // Only withdraws focus: as their ancestor, granting it would override what the controls withdraw.
                .focusProperties { if (expanded) canFocus = false }
                .then(if (expanded) Modifier.clearAndSetSemantics { } else Modifier)
                .testTag("player-zap-controls"),
        ) {
            // The footer gives way to the rail; the header (clock, top scrim) stays.
            CompositionLocalProvider(LocalRailExpansion provides rail.expansion::value,
                LocalPlayerPageScrim provides scrim,
                LocalChannelPeekLift provides if (peek != null) ChannelPeekFooterLift else 0.dp) { controls() }
        }
    }
}

/**
 * The rail above the controls: the closed rail's peeking cards, and the open rail with its preview
 * and header.
 *
 * @param peeks the closed rail's cards peek at the bottom edge at the controls' emphasis, and rise
 * from there when it opens; otherwise the closed rail stays out of the composition.
 * @param visible for a [detached][QuickZapRailState.detached] rail: the controls are up, rather
 * than away or leaving, so it may take focus and be read out.
 */
@Composable
internal fun QuickZapRail(
    rail: QuickZapRailState,
    channelContent: @Composable () -> Unit,
    preview: @Composable () -> Unit,
    /** The tray continues off the screen's bottom edge, in as much room as the page header leaves it. */
    trayBleeds: Boolean = false,
    /** Centred in the page's top band while the rail is open. */
    header: (@Composable () -> Unit)? = null,
    peeks: Boolean = false,
    visible: Boolean = true,
) {
    val expanded = rail.expanded
    val expansion = rail.expansion
    // Taken as this composition sees it: controls composed in the same pass mark the rail hosted
    // only once that pass applies.
    val hosted = rail.hosted
    LaunchedEffect(expanded) {
        val target = if (expanded) 1f else 0f
        // Away from the controls, or arriving with them, the rail stands as a rail composed with
        // them would begin.
        if (!hosted || expansion.value == target) {
            rail.expansionAnimation.snapTo(target)
        } else {
            rail.expansionAnimation.animateTo(target, if (expanded) {
                tween(PlayerMotion.PanelMs, easing = PlayerMotion.EmphasizedDecelerate)
            } else {
                tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedAccelerate)
            })
        }
    }
    val shown by remember(expansion) { derivedStateOf { expansion.value > 0f } }
    val channelPeek = rail.peek.takeIf { peeks }
    val pageProgress = LocalPlayerPageProgress.current
    val leavingPage by remember(pageProgress) { derivedStateOf { (pageProgress?.invoke() ?: 0f) > 0f } }
    // Leaves with the controls, as the content of their layer does.
    val leaving = rail.detached && rail.hosted && !visible
    val active = rail.hosted && !leaving
    // Built with the controls' first show, not with the player behind its startup cover.
    val built = remember { booleanArrayOf(false) }
    if (rail.hosted) built[0] = true
    Box(
        Modifier.fillMaxSize().clipToBounds().onSizeChanged { rail.viewportHeight = it.height }
            .then(if (rail.detached) Modifier
                .graphicsLayer { alpha = if (rail.hosted) rail.layerAlpha() else 0f }
                .drawWithContent { if (rail.hosted) drawContent() } else Modifier),
    ) {
        if (!built[0] || !shown && !expanded && channelPeek == null) return@Box
        PlayerMotionFrame(leaving = leaving, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth()
                    .then(if (trayBleeds) Modifier.padding(top = TvGridVerticalMargin + TvBrowseHeaderHeight) else Modifier)
                    .onSizeChanged { rail.railHeight = it.height }
                    .graphicsLayer {
                        val e = expansion.value
                        val cardsTop = channelPeek?.cardsTop ?: Float.NaN
                        translationY = if (cardsTop.isNaN()) {
                            size.height * (1f - e)
                        } else {
                            // Rises from where the cards peek, their top just above the bottom edge.
                            lerp(size.height - cardsTop - ChannelPeekDepth.toPx(), 0f, e)
                        }
                        if (peeks) alpha = lerp(ChannelPeekAlpha * rail.emphasis(), 1f, e)
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
                        .then(if (expanded && active) Modifier else Modifier.clearAndSetSemantics { })
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
                            dyPx = { rail.viewportHeight.toFloat() * -1f }) else Modifier)
                        .testTag("player-zap-tray")
                        // The closed rail only peeks: it takes no focus and is not read out.
                        .focusProperties { if (!expanded || !active) canFocus = false }
                        .then(if (expanded && active) Modifier else Modifier.clearAndSetSemantics { }),
                ) { CompositionLocalProvider(LocalChannelPeek provides channelPeek) { channelContent() } }
            }
        }
        if (header != null) Box(
            Modifier.align(Alignment.TopCenter)
                .padding(top = TvGridVerticalMargin).heightIn(min = TvBrowseHeaderHeight)
                .graphicsLayer { alpha = expansion.value }
                .then(if (leavingPage) Modifier.pageMotion(0..200, BrowseMotionPolicy.pageAccelerate,
                    entering = false) else Modifier)
                .then(if (expanded && active) Modifier else Modifier.clearAndSetSemantics { })
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
