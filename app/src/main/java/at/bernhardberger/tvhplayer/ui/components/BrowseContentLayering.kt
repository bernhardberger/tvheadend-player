package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The one shell gradient and the outgoing ink that must appear underneath it. */
internal class BrowseContentLayering {
    internal val details = mutableStateMapOf<Any, State<Float>>()
    internal fun detailsAlpha(): Float = details.values.maxOfOrNull { it.value } ?: 0f
    internal val modalScrims = mutableStateMapOf<Any, State<Float>>()
    internal fun modalScrimAlpha(): Float = modalScrims.values.maxOfOrNull { it.value } ?: 0f
    internal var shell: BrowseShellNode? = null
    internal val departures = mutableSetOf<BrowseDepartureNode>()

    internal fun invalidateDepartures() {
        departures.forEach { it.invalidateDraw() }
    }
}

internal val LocalBrowseContentLayering = staticCompositionLocalOf<BrowseContentLayering?> { null }
private val LocalBrowseDeparture = staticCompositionLocalOf { false }

/** An ancestor's departure wins, even when a nested visit is still locally current. */
@Composable
internal fun BrowseContentLayer(
    departing: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    val shell = LocalBrowseContentLayering.current
    val parentDeparting = LocalBrowseDeparture.current
    CompositionLocalProvider(LocalBrowseDeparture provides (parentDeparting || (shell != null && departing))) {
        content(
            if (shell != null && departing && !parentDeparting) BrowseDepartureElement(shell)
            else Modifier,
        )
    }
}

/**
 * A screen's backdrop sits under the shell's gradient like the shell's own background, not over it.
 * Content already drawn [inOffscreenLayer] lets that layer keep the gradient off transparent pixels,
 * and darkens only as the drawer opens.
 */
@Composable
internal fun Modifier.underBrowseShellScrim(inOffscreenLayer: Boolean = false): Modifier =
    LocalBrowseContentLayering.current?.let {
        then(BrowseDepartureElement(it, if (inOffscreenLayer) BrowseScrimMode.ATOP else BrowseScrimMode.LAYERED))
    } ?: this

/** How a node takes the shell's gradient: in its own layer, or atop a caller's layer as the drawer opens. */
internal enum class BrowseScrimMode { LAYERED, ATOP }

internal fun Modifier.browseShellLayer(layering: BrowseContentLayering, fullWidth: Dp): Modifier =
    then(BrowseShellElement(layering, fullWidth))

private data class BrowseShellElement(val layering: BrowseContentLayering, val fullWidth: Dp) :
    ModifierNodeElement<BrowseShellNode>() {
    override fun create() = BrowseShellNode(layering, fullWidth)
    override fun update(node: BrowseShellNode) {
        node.fullWidth = fullWidth
        node.invalidateDraw()
        layering.invalidateDepartures()
    }
    override fun InspectorInfo.inspectableProperties() { name = "browseShellLayer" }
}

internal class BrowseShellNode(
    private val layering: BrowseContentLayering,
    var fullWidth: Dp,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    override fun onAttach() { layering.shell = this }
    override fun onDetach() {
        layering.shell = null
        layering.invalidateDepartures()
    }
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        invalidateDraw()
        layering.invalidateDepartures()
    }

    internal fun frame(): BrowseScrimFrame {
        val viewport = requireLayoutCoordinates().size
        val rail = (with(requireDensity()) { fullWidth.toPx() } - viewport.width).coerceAtLeast(0f)
        val runout = with(requireDensity()) { 128.dp.toPx() }
        val rtl = requireLayoutDirection() == LayoutDirection.Rtl
        val left = if (rtl) 0f else -rail
        val right = if (rtl) viewport.width + rail else viewport.width.toFloat()
        val (closed, open) = with(requireDensity()) { ClosedDrawerWidth.toPx() to OpenDrawerWidth.toPx() }
        val opening = ((rail - closed) / (open - closed)).coerceIn(0f, 1f)
        return BrowseScrimFrame(
            Rect(left, 0f, right, viewport.height.toFloat()),
            Offset(if (rtl) right else left, 0f),
            Offset(if (rtl) viewport.width - runout else runout, 0f),
            lerp(ClosedRailEdgeAlpha, OpenDrawerEdgeAlpha, opening),
            opening,
        )
    }

    /** The screen's top-left corner in [target]'s coordinates, whatever the drawer's width. */
    internal fun screenOrigin(target: LayoutCoordinates): Offset? =
        if (isAttached) target.localPositionOf(requireLayoutCoordinates(), frame().bounds.topLeft) else null

    override fun ContentDrawScope.draw() {
        drawScrim(frame(), BlendMode.SrcOver, 1f - layering.detailsAlpha())
        drawContent()
    }
}

internal data class BrowseScreenBackdropElement(
    val layering: BrowseContentLayering,
    val fullWidth: Dp,
    val visibleWidthPx: Int?,
) : ModifierNodeElement<BrowseScreenBackdropNode>() {
    override fun create() = BrowseScreenBackdropNode(layering, fullWidth)
    override fun update(node: BrowseScreenBackdropNode) {
        node.fullWidth = fullWidth
        node.invalidateMeasurement()
    }
    override fun InspectorInfo.inspectableProperties() { name = "browseScreenBackdrop" }
}

/**
 * The drawer moves the browse content by moving an ancestor, which re-runs no placement down here.
 * The visible width it leaves the content arrives in the same measure pass, so a change re-places
 * the backdrop at the screen edge before that frame draws.
 */
internal class BrowseScreenBackdropNode(private val layering: BrowseContentLayering, var fullWidth: Dp) :
    Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val width = fullWidth.roundToPx()
        val page = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        return layout(constraints.maxWidth, page.height) {
            val origin = coordinates?.let { layering.shell?.screenOrigin(it) }
            if (origin == null) page.placeRelative(constraints.maxWidth - width, 0)
            else page.place(origin.x.roundToInt(), 0)
        }
    }
}

private data class BrowseDepartureElement(val layering: BrowseContentLayering, val mode: BrowseScrimMode = BrowseScrimMode.LAYERED) :
    ModifierNodeElement<BrowseDepartureNode>() {
    override fun create() = BrowseDepartureNode(layering, mode)
    override fun update(node: BrowseDepartureNode) {
        node.mode = mode
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() { name = "browseDepartureInk" }
}

internal class BrowseDepartureNode(private val layering: BrowseContentLayering, internal var mode: BrowseScrimMode = BrowseScrimMode.LAYERED) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private val paint = Paint()

    override fun onAttach() { layering.departures += this }
    override fun onDetach() { layering.departures -= this }
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        // Ancestor graphics-layer motion moves even cached leaves. Re-record the
        // screen-aligned attenuation instead of retaining a stale local origin.
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val shell = layering.shell
        if (shell == null || !shell.isAttached) {
            drawContent()
            return
        }
        val local = requireLayoutCoordinates()
        val source = shell.requireLayoutCoordinates()
        val frame = shell.frame()
        val mapped = BrowseScrimFrame(
            Rect(local.localPositionOf(source, frame.bounds.topLeft),
                local.localPositionOf(source, frame.bounds.bottomRight)),
            local.localPositionOf(source, frame.start),
            local.localPositionOf(source, frame.end),
            frame.edgeAlpha,
            frame.opening,
        )
        if (mode == BrowseScrimMode.ATOP) {
            // Foreground content reaches the closed rail's gradient at its keyline, so it darkens
            // only as the drawer opens over it, and at the edge no more than the kit's modal scrim.
            drawContent()
            drawScrim(mapped, BlendMode.SrcAtop,
                (1f - layering.detailsAlpha()) * mapped.opening * ForegroundUnderDrawerAlpha / mapped.edgeAlpha)
            return
        }
        val canvas = drawContext.canvas
        // Full shell bounds, not node bounds: outgoing ink/focus may overflow
        // this pane. SrcAtop changes premultiplied colour, never opacity or holes.
        canvas.saveLayer(mapped.bounds, paint)
        try {
            drawContent()
            drawScrim(mapped, BlendMode.SrcAtop, 1f - layering.detailsAlpha())
        } finally {
            canvas.restore()
        }
    }
}

/** [opening] runs from 0 with the rail closed to 1 with the drawer open. */
internal class BrowseScrimFrame(val bounds: Rect, val start: Offset, val end: Offset, val edgeAlpha: Float, val opening: Float)

/**
 * The gradient's darkness at the screen edge. The closed rail's icons only need to read over the
 * backdrop; the open drawer's labels lie over the content, so it darkens like the kit's modal drawer.
 */
private const val ClosedRailEdgeAlpha = 0.6f
private const val OpenDrawerEdgeAlpha = 0.95f

/** The kit's modal drawer scrim, `colorScheme.scrim` at 50%: content under the drawer stays legible. */
private const val ForegroundUnderDrawerAlpha = 0.5f

private fun ContentDrawScope.drawScrim(frame: BrowseScrimFrame, blendMode: BlendMode, alpha: Float) {
    if (alpha <= 0f) return
    // Past its end the gradient is transparent, but painting it there still cost the G10's GPU
    // a full-screen gradient pass per frame. Only the strip between its ends is drawn.
    val left = maxOf(frame.bounds.left, minOf(frame.start.x, frame.end.x))
    val right = minOf(frame.bounds.right, maxOf(frame.start.x, frame.end.x))
    if (right <= left) return
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Black.copy(alpha = frame.edgeAlpha), Color.Transparent),
            start = frame.start,
            end = frame.end,
        ),
        topLeft = Offset(left, frame.bounds.top),
        size = Size(right - left, frame.bounds.height),
        blendMode = blendMode,
        alpha = alpha,
    )
}
