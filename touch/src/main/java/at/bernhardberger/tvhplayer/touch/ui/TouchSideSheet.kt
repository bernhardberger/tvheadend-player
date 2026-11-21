package at.bernhardberger.tvhplayer.touch.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import at.bernhardberger.tvhplayer.touch.R
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class SideSheetValue { Hidden, Expanded }

/**
 * A modal side sheet detached from the end edge (m3.material.io side sheets: 400dp at most, 16dp
 * margins, 16dp corners, surface container low). Material 3 1.5 has no side sheet; this is built
 * from the pieces its ModalBottomSheet uses: an anchored drag toward the edge, predictive Back and
 * the theme's motion scheme. A tap outside always dismisses; [scrim] only decides whether the
 * content behind it is dimmed. The head always has Close, after any [actions]; with [onBack] it
 * also starts with Back, which system Back then calls.
 */
@Composable
internal fun TouchSideSheet(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scrim: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val state = remember { AnchoredDraggableState(SideSheetValue.Hidden) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismissRequest)
    val focus = remember { FocusRequester() }
    var backProgress by remember { mutableFloatStateOf(0f) }
    val hide: () -> Unit = { scope.launch { state.animateTo(SideSheetValue.Hidden, motion.defaultSpatialSpec()) } }
    val closeLabel = stringResource(R.string.side_sheet_close)

    LaunchedEffect(state) {
        snapshotFlow { state.anchors.size }.first { it > 0 }
        state.animateTo(SideSheetValue.Expanded, motion.defaultSpatialSpec())
        runCatching { focus.requestFocus() }
        snapshotFlow { state.settledValue }.first { it == SideSheetValue.Hidden }
        dismiss()
    }
    SheetBack(onBack, { backProgress = it }, hide)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val width = min(MaxWidth, maxWidth - Margin * 2)
        val hiddenOffset = with(density) { (width + Margin).toPx() }
        LaunchedEffect(hiddenOffset) {
            state.updateAnchors(
                DraggableAnchors {
                    SideSheetValue.Hidden at hiddenOffset
                    SideSheetValue.Expanded at 0f
                },
            )
        }
        val shown = { (1f - state.offset / hiddenOffset).takeIf { !it.isNaN() }?.coerceIn(0f, 1f) ?: 0f }
        Scrim(scrim, shown, closeLabel, hide)
        val fling = AnchoredDraggableDefaults.flingBehavior(state, animationSpec = motion.defaultSpatialSpec())
        Surface(
            Modifier.align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Margin)
                .width(width)
                .fillMaxHeight()
                .sheetMotion(state, hiddenOffset, { backProgress }, rtl, fling)
                .sheetSemantics(title, closeLabel, hide)
                .focusRequester(focus)
                .focusGroup(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.fillMaxSize()) {
                SheetHead(title, onBack, closeLabel, hide, actions)
                content()
            }
        }
    }
}

/** System Back steps back inside the sheet when it can, else closes it with the predictive gesture. */
@Composable
private fun SheetBack(onBack: (() -> Unit)?, onProgress: (Float) -> Unit, onClose: () -> Unit) {
    PredictiveBackHandler(enabled = onBack == null) { events ->
        try {
            events.collect { onProgress(it.progress) }
            onClose()
        } catch (cancelled: CancellationException) {
            onProgress(0f)
            throw cancelled
        }
    }
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
}

@Composable
private fun Scrim(dim: Boolean, shown: () -> Float, closeLabel: String, onClose: () -> Unit) {
    val color = if (dim) MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA) else Color.Transparent
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = shown() }
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose,
            )
            .semantics { onClick(closeLabel) { onClose(); true } },
    )
}

/** Where the drag and the slide put the sheet, and ModalBottomSheet's predictive-back shrink. */
private fun Modifier.sheetMotion(
    state: AnchoredDraggableState<SideSheetValue>,
    hiddenOffset: Float,
    backProgress: () -> Float,
    rtl: Boolean,
    fling: TargetedFlingBehavior,
) = offset { IntOffset(state.offset.takeIf { !it.isNaN() }?.roundToInt() ?: hiddenOffset.roundToInt(), 0) }
    .predictiveBackShrink(backProgress, rtl)
    .anchoredDraggable(
        state = state, orientation = Orientation.Horizontal, reverseDirection = rtl, flingBehavior = fling,
    )

private fun Modifier.sheetSemantics(title: String, closeLabel: String, onClose: () -> Unit) = semantics {
    paneTitle = title
    isTraversalGroup = true
    customActions = listOf(CustomAccessibilityAction(closeLabel) { onClose(); true })
}

/** ModalBottomSheet's predictive-back shrink, toward the edge the sheet came from. */
private fun Modifier.predictiveBackShrink(progress: () -> Float, rtl: Boolean) = graphicsLayer {
    val value = progress()
    if (value > 0f && size.width > 0f) {
        scaleX = 1f - BackScaleX.toPx() * value / size.width
        scaleY = 1f - BackScaleY.toPx() * value / size.height
        transformOrigin = TransformOrigin(if (rtl) 0f else 1f, 0.5f)
    }
}

@Composable
private fun SheetHead(
    title: String,
    onBack: (() -> Unit)?,
    closeLabel: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    // Spec: 24dp start padding, 16dp beside a leading icon, 12dp between the top elements.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .padding(start = if (onBack == null) 24.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.connect_back_outline), stringResource(R.string.side_sheet_back))
            }
        }
        Text(
            title,
            Modifier.weight(1f).padding(start = if (onBack == null) 0.dp else 4.dp).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        actions()
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.connect_close_outline), closeLabel)
        }
    }
}

private val MaxWidth = 400.dp
private val Margin = 16.dp
private val BackScaleX = 48.dp
private val BackScaleY = 24.dp
private const val SCRIM_ALPHA = 0.32f
