package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.playback.RecordingSkipAction
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import kotlin.math.roundToInt

/** Total time the Skip prompt can be shown, excluding time behind other layers. */
internal const val SKIP_PROMPT_MS = 10_000L

/**
 * The offer can take focus once playback is presented and seekable, independently of the Banner.
 */
internal fun recordingSkipPromptShown(offered: Boolean, playing: Boolean, canSeek: Boolean, presented: Boolean): Boolean =
    offered && playing && canSeek && presented

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
    action: RecordingSkipAction?,
    visible: Boolean,
    elapsed: () -> Float,
    modifier: Modifier = Modifier,
) {
    // A key-up can only activate the same visible offer that received key-down.
    val pressed = remember(action, visible) { mutableSetOf<Key>() }
    Button(
        onClick = onSkip,
        modifier = modifier.heightIn(min = 40.dp).semantics { role = Role.Button }.onPreviewKeyEvent { event ->
            if (event.key !in RecordingSkipActivationKeys) return@onPreviewKeyEvent false
            if (!visible) return@onPreviewKeyEvent true
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
        val label = if (action?.mark?.kind == RecordingMarkKind.PROGRAM_START) {
            R.string.skip_to_program_start
        } else R.string.skip_ad_break
        Text(stringResource(label), maxLines = 1, softWrap = false)
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
 * The Skip prompt over the video while [shown]: it takes focus when it appears and
 * announces itself. The countdown pauses while hidden and resumes with the remaining time;
 * after [SKIP_PROMPT_MS] of shown time, [onTimeout] withdraws the offer.
 */
@Composable
internal fun RecordingSkipPrompt(
    prompt: RecordingSkipAction?,
    shown: Boolean,
    onSkip: (RecordingSkipAction) -> Unit,
    onTimeout: (RecordingSkipAction) -> Unit,
    modifier: Modifier = Modifier,
    bannerShown: Boolean = false,
    bannerInfoBarBottomDistance: () -> Int = { 0 },
) {
    var departing by remember { mutableStateOf(prompt) }
    SideEffect { if (prompt != null) departing = prompt }
    // Clearing the offer must not replace the presentation still leaving the screen.
    val presentation = prompt ?: departing
    var elapsedMs by remember(presentation) { mutableLongStateOf(0L) }
    val timeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(prompt, shown) {
        if (prompt == null || !shown) return@LaunchedEffect
        // Frame time, so the prompt's time does not follow the animator duration scale.
        val start = withFrameMillis { it } - elapsedMs
        if (elapsedMs >= SKIP_PROMPT_MS) return@LaunchedEffect
        while (elapsedMs < SKIP_PROMPT_MS) elapsedMs = withFrameMillis { it } - start
        timeout(prompt)
    }
    val visible = prompt != null && shown
    val bannerLift = rememberSkipBannerLift(visible, bannerShown, bannerInfoBarBottomDistance)
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
        val travel = animateSkipPromptRise(enter, exit, rise)
        val focus = remember { FocusRequester() }
        LaunchedEffect(visible) {
            if (visible) {
                withFrameNanos { }
                focus.requestFocus()
            }
        }
        // The two extra pixels leave one antialiasing pixel per edge; the rise fits below it.
        Box((if (visible) Modifier else Modifier.clearAndSetSemantics {})
            .graphicsLayer {
                alpha = opacity.value
                translationY = travel.value.roundToInt() - bannerLift()
            }
            .layout { measurable, constraints ->
                val child = measurable.measure(constraints)
                val width = (child.width * SkipFocusAllowance).roundToInt() + 2
                val height = (child.height * SkipFocusAllowance).roundToInt() + 2 + rise
                layout(width, height) { child.placeRelative((width - child.width) / 2, (height - rise - child.height) / 2) }
            },
        ) {
            RecordingSkipButton(
                onSkip = { prompt?.takeIf { visible }?.let(onSkip) },
                action = presentation,
                visible = visible,
                elapsed = { elapsedMs / SKIP_PROMPT_MS.toFloat() },
                modifier = Modifier.testTag("recording-skip-prompt").focusRequester(focus)
                    .focusProperties { canFocus = visible }
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun rememberSkipBannerLift(
    visible: Boolean,
    bannerShown: Boolean,
    infoBarBottomDistance: () -> Int,
): () -> Float {
    val target = if (bannerShown) 1f else 0f
    val lift = remember { Animatable(target) }
    var wasVisible by remember { mutableStateOf(false) }
    val gapFromCorner = with(LocalDensity.current) { (20.dp - TvGridVerticalMargin).toPx() }
    LaunchedEffect(visible, target) {
        if (visible) {
            if (wasVisible) lift.animateTo(target,
                tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate))
            else lift.snapTo(target)
        }
        wasVisible = visible
    }
    return {
        val fraction = if (visible && !wasVisible) target else lift.value
        val distance = infoBarBottomDistance()
        if (distance > 0) fraction * (distance + gapFromCorner) else 0f
    }
}

@Composable
private fun AnimatedVisibilityScope.animateSkipPromptRise(
    enter: TweenSpec<Float>,
    exit: TweenSpec<Float>,
    rise: Int,
) = transition.animateFloat(
    transitionSpec = { if (targetState == EnterExitState.Visible) enter else exit },
    label = "skip prompt rise",
) { if (it == EnterExitState.PreEnter) rise.toFloat() else 0f }
