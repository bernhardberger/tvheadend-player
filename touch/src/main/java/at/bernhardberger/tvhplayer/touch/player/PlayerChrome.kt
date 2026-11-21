package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/** With motion disabled the target is drawn in the same frame, without scheduling an animation. */
@Composable
internal fun playerMotion(target: Boolean, spec: FiniteAnimationSpec<Float>, label: String): State<Float> {
    val value = if (target) 1f else 0f
    return if (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
        rememberUpdatedState(value)
    } else {
        updateTransition(target, label).animateFloat(transitionSpec = { spec }) { if (it) 1f else 0f }
    }
}

@Composable
internal fun BoxScope.PlayerChrome(
    shown: Boolean,
    opacity: State<Float>,
    onShow: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val drawn by remember(opacity) { derivedStateOf { opacity.value > 0f } }
    if (!shown && !drawn) return
    val latestShow by rememberUpdatedState(onShow)
    // Keep painting the exiting pixels, but don't let hidden or exiting buttons handle a gesture.
    val exitGate = if (shown && drawn) Modifier else Modifier
        .clearAndSetSemantics { hideFromAccessibility() }
        .focusProperties { canFocus = false }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
                latestShow?.invoke()
            }
        }
    Box(
        Modifier.matchParentSize().then(exitGate).graphicsLayer { alpha = opacity.value.coerceIn(0f, 1f) },
        content = content,
    )
}

/** The shown anchor is a distance from the bottom, measured with the program in the footer. */
internal class LandscapeNoticeAnchor {
    val programInset = mutableIntStateOf(0)
    val messageHeight = mutableIntStateOf(0)
}

@Composable
internal fun BoxScope.LandscapeNotice(
    anchor: LandscapeNoticeAnchor,
    layout: PlayerLayout,
    aboveFooter: Boolean,
    notices: @Composable (Modifier) -> Unit,
) {
    val tablet = layout == PlayerLayout.Tablet
    val position = playerMotion(
        aboveFooter, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "Player notice position",
    )
    Layout(
        content = { Box { notices(Modifier) } },
        modifier = Modifier.matchParentSize(),
    ) { (host), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = (if (tablet) TabletNoticeMargin else PhoneNoticeMargin).roundToPx()
        val side = NoticeSidePadding.roundToPx()
        val bottom = NoticeBottomPadding.roundToPx()
        val slotWidth = width - 2 * margin
        val visibleWidth = if (tablet) slotWidth else (slotWidth * NOTICE_SLOT_FRACTION).roundToInt()
        val available = if (aboveFooter) visibleWidth + 2 * side else width - margin + side
        val message = host.measure(Constraints(maxWidth = available.coerceAtLeast(0)))
        anchor.messageHeight.intValue = message.height
        layout(width, height) {
            if (message.height > 0) {
                val shownInset = anchor.programInset.intValue
                val inset = lerp(margin, shownInset, position.value)
                message.placeRelative(margin - side, height - inset - message.height + bottom)
            }
        }
    }
}

private val TabletNoticeMargin = 40.dp
private val PhoneNoticeMargin = 48.dp
private val NoticeSidePadding = 16.dp
private val NoticeBottomPadding = 12.dp
private const val NOTICE_SLOT_FRACTION = .62f
