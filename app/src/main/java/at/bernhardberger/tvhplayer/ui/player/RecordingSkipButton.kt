package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.RecordingMark
import kotlin.math.roundToInt

/** How long the Skip prompt stays over the hidden chrome, counted from the break's offer. */
internal const val SKIP_PROMPT_MS = 10_000L

/**
 * The offered prompt comes forward only while the recording plays and can seek, and stands
 * back while the Banner shows (it is not part of the passive Banner). Paused, buffering or
 * unseekable, OK keeps its usual meaning; the offer's timeout keeps running meanwhile.
 */
internal fun recordingSkipPromptShown(offered: Boolean, playing: Boolean, canSeek: Boolean, bannerVisible: Boolean): Boolean =
    offered && playing && canSeek && !bannerVisible

internal val RecordingSkipActivationKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

private const val SkipFocusAllowance = 1.1f

internal fun recordingSkipRemainingSweep(elapsedFraction: Float): Float =
    360f * (1f - elapsedFraction.coerceIn(0f, 1f))

/**
 * The "Skip ad break" button over the hidden chrome, with its remaining time in a countdown ring.
 * A one-off: [PlayerActionRow] holds the
 * row's icon-only actions, and no shared component draws a labelled button over the video.
 */
@Composable
private fun RecordingSkipButton(
    onSkip: () -> Unit,
    elapsed: () -> Float,
    modifier: Modifier = Modifier,
) {
    // Only a press it saw begin activates it, never one that started before it appeared or took focus.
    val pressed = remember { mutableSetOf<Key>() }
    Button(
        onClick = onSkip,
        modifier = modifier.heightIn(min = 40.dp).semantics { role = Role.Button }.onPreviewKeyEvent { event ->
            if (event.key !in RecordingSkipActivationKeys) return@onPreviewKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (event.nativeKeyEvent.repeatCount == 0) pressed.add(event.key)
                    event.key !in pressed
                }
                KeyEventType.KeyUp -> !pressed.remove(event.key)
                else -> false
            }
        },
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        SkipCountdown(LocalContentColor.current, elapsed)
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.skip_ad_break), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SkipCountdown(color: Color, elapsed: () -> Float) {
    Box(Modifier.size(20.dp).testTag("recording-skip-countdown").drawBehind {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2f
        drawCircle(color.copy(alpha = color.alpha * .2f), radius = size.minDimension / 2f - inset, style = stroke)
        val sweep = recordingSkipRemainingSweep(elapsed())
        // The end stays at twelve; the leading edge drains clockwise from twelve.
        if (sweep > 0f) drawArc(
            color, startAngle = 270f - sweep, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(inset, inset), size = Size(size.width - stroke.width, size.height - stroke.width), style = stroke,
        )
    }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(13.dp * (8.5f / 12.5f), 8.5.dp).drawBehind {
            val triangleWidth = 9.5.dp.toPx() * (8.5f / 12.5f)
            val gap = 1.dp.toPx() * (8.5f / 12.5f)
            val barWidth = 2.5.dp.toPx() * (8.5f / 12.5f)
            val triangleArea = triangleWidth * size.height / 2f
            val barArea = barWidth * size.height
            // Centre the combined ink, using the triangle's centroid (one third of its
            // width), not the centre of its bounding box.
            val inkCentre = (triangleArea * triangleWidth / 3f +
                barArea * (triangleWidth + gap + barWidth / 2f)) / (triangleArea + barArea)
            val opticalOffset = size.width / 2f - inkCentre
            val triangle = Path().apply {
                moveTo(opticalOffset, 0f)
                lineTo(opticalOffset + triangleWidth, size.height / 2f)
                lineTo(opticalOffset, size.height)
                close()
            }
            drawPath(triangle, color)
            drawRect(color, topLeft = Offset(opticalOffset + triangleWidth + gap, 0f), size = Size(barWidth, size.height))
        })
    }
}

/**
 * The Skip prompt over the hidden chrome while [shown]: it takes focus when it appears and
 * announces itself. Its time runs from [prompt]'s offer, also while another layer hides it;
 * then [onTimeout] withdraws the offer.
 */
@Composable
internal fun RecordingSkipPrompt(
    prompt: RecordingMark?,
    shown: Boolean,
    onSkip: (RecordingMark) -> Unit,
    onTimeout: (RecordingMark) -> Unit,
    modifier: Modifier = Modifier,
) {
    var departing by remember { mutableStateOf(prompt) }
    SideEffect { if (prompt != null) departing = prompt }
    // Clearing the offer must not replace the presentation still leaving the screen.
    val presentation = prompt ?: departing
    var elapsedMs by remember(presentation) { mutableLongStateOf(0L) }
    val timeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(prompt) {
        val offered = prompt ?: return@LaunchedEffect
        elapsedMs = 0L
        // Frame time, so the prompt's time does not follow the animator duration scale.
        val start = withFrameMillis { it }
        while (elapsedMs < SKIP_PROMPT_MS) elapsedMs = withFrameMillis { it } - start
        timeout(offered)
    }
    val visible = prompt != null && shown
    val visibility = remember { MutableTransitionState(false) }
    SideEffect { visibility.targetState = visible }
    LaunchedEffect(prompt, visibility.isIdle, visibility.currentState) {
        if (prompt == null && visibility.isIdle && !visibility.currentState) departing = null
    }
    val rise = with(LocalDensity.current) { PlayerMotion.ChromeOffset.roundToPx() }
    AnimatedVisibility(
        visibleState = visibility,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        // Keep the corner anchor at the button's size while the visibility layer has enough
        // room for native focus growth and the entry's downward offset.
        modifier = modifier.layout { measurable, constraints ->
            val child = measurable.measure(constraints)
            val width = ((child.width - 2) / SkipFocusAllowance).roundToInt()
            val height = ((child.height - 2 - rise) / SkipFocusAllowance).roundToInt()
            layout(width, height) { child.placeRelative(-(child.width - width) / 2, -(child.height - rise - height) / 2) }
        },
    ) {
        val enter = tween<Float>(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate)
        val exit = tween<Float>(PlayerMotion.ShortMs, easing = PlayerMotion.EmphasizedAccelerate)
        val opacity = animateShown(enter, exit, "skip prompt opacity")
        val travel = transition.animateFloat(
            transitionSpec = { if (targetState == EnterExitState.Visible) enter else exit },
            label = "skip prompt rise",
        ) { if (it == EnterExitState.PreEnter) rise.toFloat() else 0f }
        val focus = remember { FocusRequester() }
        LaunchedEffect(visible) {
            if (visible) {
                withFrameNanos { }
                focus.requestFocus()
            }
        }
        // The two extra pixels leave one antialiasing pixel per edge; the rise fits below it.
        Box(Modifier
            .graphicsLayer {
                alpha = opacity.value
                translationY = travel.value.roundToInt().toFloat()
            }
            .layout { measurable, constraints ->
                val child = measurable.measure(constraints)
                val width = (child.width * SkipFocusAllowance).roundToInt() + 2
                val height = (child.height * SkipFocusAllowance).roundToInt() + 2 + rise
                layout(width, height) { child.placeRelative((width - child.width) / 2, (height - rise - child.height) / 2) }
            },
        ) {
            RecordingSkipButton(
                onSkip = { prompt?.let(onSkip) },
                elapsed = { elapsedMs / SKIP_PROMPT_MS.toFloat() },
                modifier = Modifier.testTag("recording-skip-prompt").focusRequester(focus)
                    .focusProperties { canFocus = visible }
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
