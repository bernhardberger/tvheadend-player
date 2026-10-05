package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** The one shell gradient and the outgoing ink that must appear underneath it. */
internal class BrowseContentLayering {
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
        return BrowseScrimFrame(
            Rect(left, 0f, right, viewport.height.toFloat()),
            Offset(if (rtl) right else left, 0f),
            Offset(if (rtl) viewport.width - runout else runout, 0f),
        )
    }

    override fun ContentDrawScope.draw() {
        drawScrim(frame(), BlendMode.SrcOver)
        drawContent()
    }
}

private data class BrowseDepartureElement(val layering: BrowseContentLayering) :
    ModifierNodeElement<BrowseDepartureNode>() {
    override fun create() = BrowseDepartureNode(layering)
    override fun update(node: BrowseDepartureNode) = Unit
    override fun InspectorInfo.inspectableProperties() { name = "browseDepartureInk" }
}

internal class BrowseDepartureNode(private val layering: BrowseContentLayering) :
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
        )
        val canvas = drawContext.canvas
        // Full shell bounds, not node bounds: outgoing ink/focus may overflow
        // this pane. SrcAtop changes premultiplied colour, never opacity or holes.
        canvas.saveLayer(mapped.bounds, paint)
        try {
            drawContent()
            drawScrim(mapped, BlendMode.SrcAtop)
        } finally {
            canvas.restore()
        }
    }
}

internal class BrowseScrimFrame(val bounds: Rect, val start: Offset, val end: Offset)

private fun ContentDrawScope.drawScrim(frame: BrowseScrimFrame, blendMode: BlendMode) {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Black.copy(alpha = 0.95f), Color.Transparent),
            start = frame.start,
            end = frame.end,
        ),
        topLeft = frame.bounds.topLeft,
        size = frame.bounds.size,
        blendMode = blendMode,
    )
}
