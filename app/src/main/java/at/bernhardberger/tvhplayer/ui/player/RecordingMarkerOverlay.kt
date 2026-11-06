package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
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
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin

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
                if (event.type == KeyEventType.KeyDown && open) {
                    if (event.key == Key.DirectionLeft) move(markers, -1)
                    if (event.key == Key.DirectionRight) move(markers, 1)
                }
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
    val density = LocalDensity.current
    var lastShownIndex by remember { mutableIntStateOf(-1) }
    val shownIndex = if (open) selectedIndex else lastShownIndex
    SideEffect { if (open) lastShownIndex = selectedIndex }
    val visibility = remember { MutableTransitionState(false) }
    // A fresh opening starts at its stop. Closing or reversing an in-flight exit keeps
    // the current horizontal animation rather than snapping to the last selected stop.
    SideEffect { visibility.targetState = open }
    val selectedPosition = stops.getOrNull(if (open) selectedIndex else shownIndex)?.positionMs ?: 0L
    val pillPosition = if (open || visibility.currentState || !visibility.isIdle) {
        animateFloatAsState(selectedPosition.toFloat(),
            tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard), label = "marks pill position")
    } else null
    val reachedLabel = remember(stops, pillPosition) {
        derivedStateOf {
            val selectedIndex = stops.indexOfFirst { it.positionMs == navigation.selectedMs }
            val target = stops.getOrNull(selectedIndex)?.positionMs?.toFloat()
            if (target == null) -1 else {
                val position = pillPosition?.value ?: target
                val neighbour = stops.getOrNull(if (position < target) selectedIndex - 1 else selectedIndex + 1)
                val distance = kotlin.math.abs(target - (neighbour?.positionMs?.toFloat() ?: target))
                selectedIndex.takeIf { kotlin.math.abs(position - target) <= distance } ?: -1
            }
        }
    }
    // Symbols stand at mark starts only; an ad break's end stop has none.
    val marked = stops.filter {
        val kind = it.mark?.kind
        kind != null && !it.atEnd && kind != RecordingMarkKind.PROGRAM_START && kind != RecordingMarkKind.PROGRAM_END
    }
    // The timeline's track slot supplies the measured width after endpoint insets.
    // Position in that coordinate space; no endpoint measurement or UI-state feedback.
    SubcomposeLayout(modifier = modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val width = constraints.maxWidth
        fun centre(positionMs: Long): Float =
            width * (if (displayDurationMs > 0) (positionMs.toDouble() / displayDurationMs).coerceIn(0.0, 1.0) else 0.0).toFloat()
        val pill = subcompose(PillSlot) {
        Box {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate)),
            exit = fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate)),
        ) {
            val enter = tween<Float>(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate)
            val exit = tween<Float>(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate)
            val scale = transition.animateFloat(
                transitionSpec = { if (targetState == EnterExitState.Visible) enter else exit },
                label = "marks pill scale",
            ) { when (it) {
                EnterExitState.PreEnter -> 0.85f
                EnterExitState.Visible -> 1f
                EnterExitState.PostExit -> 0.9f
            } }
            // The hidden states have different travel, so keep them distinct across reversals.
            val travel = transition.animateFloat(
                transitionSpec = { if (targetState == EnterExitState.Visible) enter else exit },
                label = "marks pill rise",
            ) { with(density) { when (it) {
                EnterExitState.PreEnter -> 8.dp.roundToPx().toFloat()
                EnterExitState.Visible -> 0f
                EnterExitState.PostExit -> 6.dp.roundToPx().toFloat()
            } } }
            // Alpha creates an offscreen layer. Reserve the TV Button's focused overflow in
            // that layer: 8 dp for focus overflow plus 8 dp for travel. Keep the old scale
            // anchor (8 dp below the button) and combine scale/travel so zoom does not shorten it.
            Box(Modifier.graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
                transformOrigin = TransformOrigin(0.5f, 1f - 8.dp.toPx() / size.height)
                translationY = travel.value.roundToInt().toFloat()
            }.padding(16.dp)) {
                if (shownIndex in stops.indices) RecordingMarkPill(navigation, stops, shownIndex,
                    { reachedLabel.value }, onSeek,
                    Modifier.semanticsWhileShown(open).focusProperties { canFocus = open })
            }
        }
        }
        }.single().measure(loose)
        val selectedMs = if (pill.height > 0)
            stops.getOrNull(if (open) selectedIndex else shownIndex)?.positionMs else null
        // Measure the target symbol set separately from its retained fades. Thinning therefore
        // keeps the existing sizes/gaps, without writing layout measurements back into composition.
        val symbols = marked.map { stop ->
            stop to subcompose(stop.positionMs to "size") {
                RecordingMarkSymbol(requireNotNull(stop.mark).kind, large = open, tint = Color.Transparent,
                    modifier = Modifier.clearAndSetSemantics { })
            }.single().measure(loose)
        }
        val gap = (if (open) OpenHintGap else HintGap).toPx()
        val eligible = symbols.filter { it.first.positionMs != selectedMs }
        val hintsPx = eligible.map { (stop, placeable) ->
            RecordingMarkHint(centre(stop.positionMs), placeable.width.toFloat(), requireNotNull(stop.mark).kind)
        }
        val margin = TvGridHorizontalMargin.toPx()
        layout(width, constraints.maxHeight) {
            // The pill centres on its tick and may reach past the track over the endpoint labels,
            // up to the timeline row's ends at the screen's safe margins.
            val pillX = pill.let {
                val left = coordinates?.let { here -> here.positionInRoot().x } ?: 0f
                val rootWidth = coordinates?.findRootCoordinates()?.size?.width?.toFloat() ?: width.toFloat()
                val min = minOf(0f, margin - left)
                val max = maxOf(width - it.width.toFloat(), rootWidth - margin - left - it.width)
                (width * ((pillPosition?.value ?: selectedPosition.toFloat()) / displayDurationMs.coerceAtLeast(1)).coerceIn(0f, 1f) - it.width / 2f)
                    .coerceIn(min, maxOf(min, max)).roundToInt()
            }
            val shown = visibleRecordingMarkHints(
                hintsPx,
                gapPx = gap,
                trackWidthPx = width.toFloat(),
                blockedPx = if (pill.height > 0) (pillX - gap)..(pillX + pill.width + gap) else null,
            )
            val shownStops = shown.map { eligible[it].first.positionMs }.toSet()
            val symbolCentre = constraints.maxHeight / 2 - HintLift.roundToPx()
            symbols.forEach { (stop, size) ->
                val placeable = subcompose(stop.positionMs to "fade") {
                    Box {
                    RecordingMarkHintSymbol(requireNotNull(stop.mark).kind, open,
                        visible = hints || open, unblocked = stop.positionMs in shownStops)
                    }
                }.single().measure(loose)
                // Keep both size variants centred on the original mark during the crossfade.
                val hint = RecordingMarkHint(centre(stop.positionMs), size.width.toFloat(), requireNotNull(stop.mark).kind)
                val x = hint.leftPx(width.toFloat()) + size.width / 2f - placeable.width / 2f
                placeable.placeRelative(x.roundToInt(), symbolCentre - placeable.height / 2)
            }
            // Bottom-anchored: the time row keeps its place whether a name line stands above it.
            pill.placeRelative(pillX, -pill.height + 16.dp.roundToPx())
        }
    }
}

/** Global focus/size fades are 150 ms; only collision thinning uses the shorter 100 ms fade. */
@Composable
private fun RecordingMarkHintSymbol(kind: RecordingMarkKind, large: Boolean, visible: Boolean, unblocked: Boolean) {
    AnimatedVisibility(visible,
        enter = fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard)),
        modifier = Modifier.testTag("recording-mark-hint").clearAndSetSemantics { },
    ) {
        val thinning = animateFloatAsState(if (unblocked) 1f else 0f,
            tween(PlayerMotion.FastMs, easing = PlayerMotion.Standard), label = "mark hint thinning")
        Box(contentAlignment = Alignment.Center) {
            // One shared large footprint; the fading small drawing must not sit at its top-start.
            RecordingMarkSymbol(kind, large = true, tint = Color.Transparent)
            Crossfade(large, modifier = Modifier.matchParentSize(),
                animationSpec = tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard), label = "mark hint size") { open ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RecordingMarkSymbol(kind, open, MaterialTheme.colorScheme.onSurface.copy(alpha = if (open) 1f else HintAlpha),
                        Modifier.graphicsLayer { alpha = thinning.value })
                }
            }
        }
    }
}

/** The open marks row's focus: the selected stop's name, when it has one, above its icon and time. */
@Composable
private fun RecordingMarkPill(
    navigation: RecordingMarkerNavigation,
    stops: List<RecordingMarkStop>,
    shownIndex: Int,
    reachedLabel: () -> Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    // Observe selection here as well: waiting for a new subcomposition/layout pass would
    // start the label later than travel. Only a closed pill uses the retained stop.
    val index = stops.indexOfFirst { it.positionMs == navigation.selectedMs }.takeIf { it >= 0 } ?: shownIndex
    val stop = stops[index]
    val markers = stops.map { it.positionMs }
    val focus = remember { FocusRequester() }
    val previousLabel = stringResource(R.string.recording_marker_previous)
    val nextLabel = stringResource(R.string.recording_marker_next)
    val closeLabel = stringResource(R.string.close)
    LaunchedEffect(navigation.open) {
        if (!navigation.open) return@LaunchedEffect
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
        RecordingMarkKind.PROGRAM_START -> stringResource(R.string.recording_mark_program_starts)
        RecordingMarkKind.PROGRAM_END -> stringResource(R.string.recording_mark_program_ends)
        null -> null
    }
    val position = if (stop.positionMs == 0L) stringResource(R.string.recording_marker_start)
        // Stops begin with the start, so the first mark after it is marker 1.
        else stringResource(R.string.recording_marker_label, index, formatPlaybackDuration(stop.positionMs))
    val description = listOfNotNull(name, position).joinToString(", ")
    // While held travel is more than one stop behind, retain the last label. The target
    // description and click action still follow the selected stop immediately.
    var lastLabelIndex by remember { mutableIntStateOf(index) }
    val reached = reachedLabel()
    val labelIndex = reached.takeIf { it >= 0 } ?: lastLabelIndex
    SideEffect { if (reached >= 0) lastLabelIndex = reached }
    val label = remember(labelIndex, navigation.open) {
        Triple(name, mark?.kind, formatPlaybackDuration(stop.positionMs))
    }
    // Compact: a name line and a time line stay clear of the info bar's text above the timeline.
    val shape = MaterialTheme.shapes.small
    // Without a name the pill shrinks from the top to its time row; the Button's minimum height
    // would otherwise centre that row higher than on every other pill.
    val timeRowHeight = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.lineHeight.toDp() }
    val nameless = if (label.first == null) Modifier.heightIn(max = timeRowHeight + PillPadding.calculateTopPadding() +
        PillPadding.calculateBottomPadding()) else Modifier
    Button(
        onClick = { onSeek(stop.positionMs); navigation.dismiss() },
        shape = ButtonDefaults.shape(shape = shape, focusedShape = shape, pressedShape = shape),
        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface),
        contentPadding = PillPadding,
        modifier = modifier.widthIn(max = PillMaxWidth).then(nameless).focusRequester(focus).testTag("recording-marker-target")
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
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (fadeIn(tween(140, delayMillis = 60, easing = PlayerMotion.StandardDecelerate)) togetherWith
                    fadeOut(tween(60, easing = PlayerMotion.StandardAccelerate))).using(
                    SizeTransform(clip = false) { _, _ -> tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard) })
            },
            contentAlignment = Alignment.BottomCenter,
            label = "marks pill label",
        ) { label ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (label.first != null) Text(
                requireNotNull(label.first), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = LocalContentColor.current.copy(alpha = 0.8f), modifier = Modifier.testTag("recording-marker-name"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (label.second in listOf(RecordingMarkKind.AD_BREAK, RecordingMarkKind.CUT)) {
                    RecordingMarkSymbol(requireNotNull(label.second), large = true, tint = LocalContentColor.current)
                }
                Text(
                    label.third,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("recording-marker-overlay"),
                )
            }
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
        RecordingMarkKind.PROGRAM_START, RecordingMarkKind.PROGRAM_END -> Unit
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
