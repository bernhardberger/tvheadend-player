package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import kotlin.math.roundToInt

/** Gap between an endpoint label box and the track. */
internal val InlineEndpointGap = 8.dp

/** The end labels' style: tabular digits, so ticking numbers never change a width. */
@Composable
@ReadOnlyComposable
internal fun timelineLabelStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")

/** Whose bar row this is: each kind has its own label widths, fixed for every state it can show. */
enum class TimelineKind { LIVE, RECORDING }

/** Widths of the bar row's start and end boxes, and the behind-live label used by live TV and growing recordings. */
@Immutable
internal data class TimelineEndpointWidths(val leading: Dp, val trailing: Dp, val distance: Dp)

/** Clear space the info block's last line keeps before live TV's distance. */
internal val DistanceTextGap = 16.dp

/**
 * The label boxes of [kind], measured once from the widest text they can show in the actual label
 * style and font scale: live `88:88` before and after the bar, a recording `8:88:88`; the localized
 * `8:88:88 behind live`. Nothing the row shows, tuning included, changes them, so the bar never
 * moves. A longer text ellipsizes in its box.
 */
@Composable
internal fun rememberTimelineEndpointWidths(kind: TimelineKind): TimelineEndpointWidths {
    val measurer = rememberTextMeasurer()
    val style = timelineLabelStyle()
    val density = LocalDensity.current
    val distance = stringResource(R.string.timeshift_behind_live, "8:88:88")
    return remember(kind, measurer, style, density, distance) {
        fun width(text: String): Dp = with(density) {
            measurer.measure(text, style, softWrap = false, maxLines = 1, density = density).size.width.toDp()
        }
        val clock = width(if (kind == TimelineKind.LIVE) "88:88" else "8:88:88")
        TimelineEndpointWidths(leading = clock, trailing = clock, distance = width(distance))
    }
}

/**
 * The width a live source's info block keeps free at the end of its last line for the distance behind
 * live drawn there, in every state, so the line never re-wraps when playback falls behind.
 */
@Composable
internal fun liveDistanceReserve(): Dp = rememberTimelineEndpointWidths(TimelineKind.LIVE).distance + DistanceTextGap

/**
 * The state cell's room before the left label: the whole cell and its gap in the Banner, none in the
 * controls, animated between them by [LocalStateCellInset]. Read only in layout and draw.
 */
@Composable
private fun StateCellSlot(content: (@Composable () -> Unit)?) {
    val inset = LocalStateCellInset.current
    val room = playerStateCellSize() + InlineEndpointGap
    val shown by remember(inset) { derivedStateOf { inset.fraction() > 0f } }
    Box(
        Modifier
            .layout { measurable, constraints ->
                val child = measurable.measure(Constraints())
                layout((room.roundToPx() * inset.fraction()).roundToInt(), child.height) { child.place(0, 0) }
            }
            .clipToBounds()
            .graphicsLayer { alpha = inset.fraction() },
    ) { if (shown) content?.invoke() }
}

/**
 * The bar row: `[state cell] [start] bar [end]`. Every box is always there, at the widths of
 * [widths], and its content only fills it: the start label from the start, the end from the
 * content's end edge. The step readout's coordinates are the bar's own, so it lives in [center].
 */
@Composable
internal fun TimelineEndpointRow(
    widths: TimelineEndpointWidths,
    modifier: Modifier = Modifier,
    state: (@Composable () -> Unit)? = null,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    center: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StateCellSlot(state)
        Box(Modifier.width(widths.leading), contentAlignment = Alignment.CenterStart) { leading() }
        Spacer(Modifier.width(InlineEndpointGap))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { center() }
        Spacer(Modifier.width(InlineEndpointGap))
        Box(Modifier.width(widths.trailing), contentAlignment = Alignment.CenterEnd) { trailing() }
    }
}

@Composable
internal fun TimelineEndpointLabel(
    text: String,
    color: Color,
    testTag: String?,
    emphasis: State<Float>,
) {
    val modifier = Modifier
        .graphicsLayer { alpha = emphasis.value }
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    Text(
        text = text,
        style = timelineLabelStyle(), color = color, maxLines = 1,
        softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}
