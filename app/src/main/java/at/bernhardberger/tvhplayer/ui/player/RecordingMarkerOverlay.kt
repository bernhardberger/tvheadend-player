package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.core.RecordingMarkHint
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.core.RecordingMarkStop
import at.bernhardberger.tvhplayer.core.leftPx
import at.bernhardberger.tvhplayer.core.visibleRecordingMarkHints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration

/** Shared with the screen's ancestor Back interceptor; survives focus relocation. */
internal class RecordingMarkerNavigation {
    var selectedMs by mutableStateOf<Long?>(null)
        private set
    val open: Boolean get() = selectedMs != null
    var restoration by mutableIntStateOf(0)
        private set
    var ownerRevision: Long = 0L
        private set
    /** The last close was Up's: focus moves on to what stands above the timeline. */
    var closedUpward: Boolean = false
        private set
    private val heldKeys = mutableSetOf<Key>()

    fun show(markers: List<Long>, positionMs: Long, openingKey: Key? = null, revision: Long = 0L) {
        selectedMs = markers.firstOrNull { it > positionMs } ?: markers.lastOrNull() ?: return
        ownerRevision = revision
        openingKey?.let(heldKeys::add)
    }

    fun dismiss(upward: Boolean = false) {
        if (!open) return
        selectedMs = null
        closedUpward = upward
        restoration++
    }

    fun move(markers: List<Long>, direction: Int) {
        val index = markers.indexOf(selectedMs)
        if (index >= 0) selectedMs = markers[(index + direction).coerceIn(0, markers.lastIndex)]
    }

    fun handle(event: KeyEvent, markers: List<Long>, onSeek: (Long) -> Unit): Boolean {
        if (event.key in heldKeys) {
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                // A fresh press after a lost release (for example HOME) starts a new cycle.
                heldKeys.remove(event.key)
            } else {
                if (event.type == KeyEventType.KeyUp) heldKeys.remove(event.key)
                return true
            }
        }
        if (!open) return false
        if (event.key !in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft,
                Key.DirectionRight, Key.Enter, Key.NumPadEnter, Key.DirectionCenter)) return false
        if (event.type == KeyEventType.KeyDown) {
            heldKeys.add(event.key)
            if (event.nativeKeyEvent.repeatCount == 0) {
                when (event.key) {
                    Key.DirectionLeft -> move(markers, -1)
                    Key.DirectionRight -> move(markers, 1)
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        selectedMs?.takeIf { it in markers }?.let(onSeek)
                        dismiss()
                    }
                    Key.DirectionDown -> dismiss()
                    Key.DirectionUp -> dismiss(upward = true)
                    else -> Unit
                }
            }
        }
        return true
    }
}

/** The pill's slot in the overlay's layout; the symbols' slots are their stops. */
private object PillSlot

/** Symbols at rest stand faint above the bar; with the marks row open they are opaque. */
private const val HintAlpha = 0.47f
/**
 * A symbol's centre above the bar row's centre: the open row's larger symbols clear the info
 * bar's artwork, the resting ones the focused thumb.
 */
private val HintLift = 16.dp
/** Clear space between neighbouring symbols, edge to edge; the open row's symbols are larger. */
private val HintGap = 7.dp
private val OpenHintGap = 5.dp
private val PillMaxWidth = 280.dp
private val PillPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

/**
 * Above the recording's bar: each mark's symbol at its start while the seekbar is focused ([hints]) or
 * the marks row is open, and the open row's pill at the selected stop. Symbols thin where they would
 * crowd ([visibleRecordingMarkHints]); the bar's ticks and every stop stay. The selected stop's symbol
 * gives way to the pill, and so does any neighbour the pill covers.
 */
@Composable
internal fun RecordingMarkerOverlay(
    navigation: RecordingMarkerNavigation,
    stops: List<RecordingMarkStop>,
    onSeek: (Long) -> Unit,
    displayDurationMs: Long,
    hints: Boolean,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = stops.indexOfFirst { it.positionMs == navigation.selectedMs }
    val open = selectedIndex >= 0
    // Symbols stand at mark starts only; an ad break's end stop has none.
    val marked = stops.filter { it.mark != null && !it.atEnd }
    if (!open && (!hints || marked.isEmpty())) return
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (open) 1f else HintAlpha)
    // The timeline's track slot supplies the measured width after endpoint insets.
    // Position in that coordinate space; no endpoint measurement or UI-state feedback.
    Layout(modifier = modifier, content = {
        marked.forEach { stop ->
            key(stop.positionMs) {
                RecordingMarkSymbol(requireNotNull(stop.mark).kind, large = open, tint = tint,
                    modifier = Modifier.layoutId(stop).testTag("recording-mark-hint").clearAndSetSemantics { })
            }
        }
        if (open) RecordingMarkPill(navigation, stops, selectedIndex, onSeek, Modifier.layoutId(PillSlot))
    }) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val width = constraints.maxWidth
        fun centre(positionMs: Long): Float =
            width * (if (displayDurationMs > 0) (positionMs.toDouble() / displayDurationMs).coerceIn(0.0, 1.0) else 0.0).toFloat()
        val pill = measurables.firstOrNull { it.layoutId == PillSlot }?.measure(loose)
        val selectedMs = stops.getOrNull(selectedIndex)?.positionMs
        val pillX = pill?.let { (centre(requireNotNull(selectedMs)) - it.width / 2f).roundToInt().coerceIn(0, maxOf(0, width - it.width)) }
        val symbols = measurables.mapNotNull { measurable ->
            (measurable.layoutId as? RecordingMarkStop)?.takeIf { it.positionMs != selectedMs }?.let { it to measurable.measure(loose) }
        }
        val gap = (if (open) OpenHintGap else HintGap).toPx()
        val hintsPx = symbols.map { (stop, placeable) ->
            RecordingMarkHint(centre(stop.positionMs), placeable.width.toFloat(), requireNotNull(stop.mark).kind)
        }
        val shown = visibleRecordingMarkHints(
            hintsPx,
            gapPx = gap,
            trackWidthPx = width.toFloat(),
            blockedPx = if (pill != null && pillX != null) (pillX - gap)..(pillX + pill.width + gap) else null,
        )
        layout(width, constraints.maxHeight) {
            val symbolCentre = constraints.maxHeight / 2 - HintLift.roundToPx()
            symbols.forEachIndexed { index, (_, placeable) ->
                // Placed where the thinning measured it: centred on its mark, held inside the track.
                if (index in shown) placeable.placeRelative(
                    hintsPx[index].leftPx(width.toFloat()).roundToInt(),
                    symbolCentre - placeable.height / 2,
                )
            }
            // Bottom-anchored: the time row keeps its place whether a name line stands above it.
            if (pill != null && pillX != null) pill.placeRelative(pillX, -pill.height)
        }
    }
}

/** The open marks row's focus: the selected stop's name, when it has one, above its icon and time. */
@Composable
private fun RecordingMarkPill(
    navigation: RecordingMarkerNavigation,
    stops: List<RecordingMarkStop>,
    index: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val stop = stops[index]
    val markers = stops.map { it.positionMs }
    val focus = remember { FocusRequester() }
    val previousLabel = stringResource(R.string.recording_marker_previous)
    val nextLabel = stringResource(R.string.recording_marker_next)
    val closeLabel = stringResource(R.string.close)
    LaunchedEffect(focus) {
        // Wait one frame so the marker is ready to draw its focused state.
        withFrameNanos { }
        focus.requestFocus()
    }
    val mark = stop.mark
    val name = when (mark?.kind) {
        RecordingMarkKind.AD_BREAK -> stringResource(
            if (stop.atEnd) R.string.recording_mark_ads_end else R.string.recording_mark_ad_break,
        )
        RecordingMarkKind.CUT -> stringResource(if (stop.atEnd) R.string.recording_mark_cut_ends else R.string.recording_mark_cut)
        RecordingMarkKind.CHAPTER -> mark.name
        null -> null
    }
    val position = if (stop.positionMs == 0L) stringResource(R.string.recording_marker_start)
        // Stops begin with the start, so the first mark after it is marker 1.
        else stringResource(R.string.recording_marker_label, index, formatPlaybackDuration(stop.positionMs))
    val description = listOfNotNull(name, position).joinToString(", ")
    // Compact: a name line and a time line stay clear of the info bar's text above the timeline.
    val shape = MaterialTheme.shapes.small
    Button(
        onClick = { onSeek(stop.positionMs); navigation.dismiss() },
        shape = ButtonDefaults.shape(shape = shape, focusedShape = shape, pressedShape = shape),
        contentPadding = PillPadding,
        modifier = modifier.widthIn(max = PillMaxWidth).focusRequester(focus).testTag("recording-marker-target")
            .semantics {
                contentDescription = description
                customActions = buildList {
                    if (index > 0) add(CustomAccessibilityAction(previousLabel) { navigation.move(markers, -1); true })
                    if (index < markers.lastIndex) add(CustomAccessibilityAction(nextLabel) { navigation.move(markers, 1); true })
                    add(CustomAccessibilityAction(closeLabel) { navigation.dismiss(); true })
                }
            }
            .focusProperties {
                up = FocusRequester.Cancel; down = FocusRequester.Cancel
                left = FocusRequester.Cancel; right = FocusRequester.Cancel
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (name != null) Text(
                name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = LocalContentColor.current.copy(alpha = 0.8f), modifier = Modifier.testTag("recording-marker-name"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mark != null && mark.kind != RecordingMarkKind.CHAPTER) {
                    RecordingMarkSymbol(mark.kind, large = true, tint = LocalContentColor.current)
                }
                Text(
                    formatPlaybackDuration(stop.positionMs),
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("recording-marker-overlay"),
                )
            }
        }
    }
}

/**
 * A mark's symbol: a dot for a chapter, scissors for a cut, an outlined "Ad" for an ad break. Its own
 * drawing because no shared icon or badge comes at these sizes or carries a text mark this small.
 */
@Composable
private fun RecordingMarkSymbol(kind: RecordingMarkKind, large: Boolean, tint: Color, modifier: Modifier = Modifier) {
    when (kind) {
        RecordingMarkKind.CHAPTER -> Box(modifier.size(if (large) 6.dp else 4.dp).background(tint, CircleShape))
        RecordingMarkKind.CUT -> Icon(painterResource(R.drawable.ic_content_cut), contentDescription = null, tint = tint,
            modifier = modifier.size(if (large) 14.dp else 10.dp))
        RecordingMarkKind.AD_BREAK -> {
            // Sized as a symbol, not as text: it keeps its size at every font scale, like the icons beside it.
            val (font, line) = with(LocalDensity.current) {
                if (large) 9.dp.toSp() to 11.dp.toSp() else 7.dp.toSp() to 8.dp.toSp()
            }
            Box(modifier.border(1.dp, tint, RoundedCornerShape(3.dp)).padding(horizontal = if (large) 3.dp else 2.dp, vertical = 1.dp)) {
                Text(stringResource(R.string.recording_mark_ad_badge), color = tint, maxLines = 1,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = font, lineHeight = line, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
