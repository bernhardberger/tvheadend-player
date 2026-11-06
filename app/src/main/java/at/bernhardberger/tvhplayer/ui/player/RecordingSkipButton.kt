package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
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

/** The button's focused scale, which the prompt's corner placement allows for. */
private const val SkipFocusScale = 1.1f

internal val RecordingSkipActivationKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

private val SkipGhost = Color(0xA83C3C3C)
private val SkipFilled = Color(0xDEC7C7C7)
private val SkipDarkLabel = Color(0xFF1F1F1F)

/** The actual label metrics and the same icon, spacing and padding as the Surface below. */
@Composable
internal fun recordingSkipWidth(): Dp {
    val text = stringResource(R.string.skip_ad_break)
    val style = MaterialTheme.typography.labelLarge.copy(
        fontSize = MaterialTheme.typography.titleMedium.fontSize, fontWeight = FontWeight.Medium,
    )
    val measured = rememberTextMeasurer().measure(text, style = style, maxLines = 1)
    return with(LocalDensity.current) { measured.size.width.toDp() } + 18.dp + 6.dp + 25.dp
}

/**
 * The "Skip ad break" button, over the hidden chrome as the prompt, with the time it stays as a
 * full-height fill, and resting beside the controls' info strip. A one-off: [PlayerActionRow] holds the
 * row's icon-only actions, and no shared component draws a labelled button over the video.
 */
@Composable
private fun RecordingSkipButton(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    /** The prompt's elapsed share of its time; null for the resting button, which has no fill. */
    elapsed: (() -> Float)? = null,
) {
    // Only a press it saw begin activates it, never one that started before it appeared or took focus.
    val pressed = remember { mutableSetOf<Key>() }
    Surface(
        onClick = onSkip,
        modifier = modifier.semantics { role = Role.Button }.onPreviewKeyEvent { event ->
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
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SkipGhost, contentColor = Color.White,
            focusedContainerColor = if (elapsed == null) SkipFilled else SkipGhost,
            focusedContentColor = if (elapsed == null) SkipDarkLabel else Color.White,
            pressedContainerColor = if (elapsed == null) SkipFilled else SkipGhost,
            pressedContentColor = if (elapsed == null) SkipDarkLabel else Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (elapsed == null) SkipFocusScale else 1f, pressedScale = 1f),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None, pressedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
    ) {
        Box {
            if (elapsed != null) Box(Modifier.matchParentSize().testTag("recording-skip-fill").drawBehind {
                drawRect(Color.White.copy(alpha = 0.62f), size = Size(size.width * elapsed().coerceIn(0f, 1f), size.height))
            })
            SkipLabel(LocalContentColor.current)
            if (elapsed != null) SkipLabel(SkipDarkLabel, Modifier.matchParentSize().drawWithContent {
                val content = this
                clipRect(right = size.width * elapsed().coerceIn(0f, 1f)) { content.drawContent() }
            }.clearAndSetSemantics { })
        }
    }
}

@Composable
private fun SkipLabel(color: Color, modifier: Modifier = Modifier) {
    // Reference 28: ~12 dp caps, 12.5 dp glyph ink, 16 dp ink inset and 10 dp ink gap in a 150×40 dp pill.
    // Use the same content for prompt and resting Skip; retain labelLarge's 20 sp line height.
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize)
    Row(modifier.padding(start = 13.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        // Reference ink: 9.5×12.5 dp triangle, 1 dp gap, 2.5 dp bar. The stock icon's bar is thinner.
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(13.dp, 12.5.dp).drawBehind {
                val triangle = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(9.5.dp.toPx(), size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(triangle, color)
                drawRect(color, topLeft = Offset(10.5.dp.toPx(), 0f), size = Size(2.5.dp.toPx(), size.height))
            })
        }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.skip_ad_break), color = color, style = style,
            fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
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
        modifier = modifier,
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
        RecordingSkipButton(
            onSkip = { prompt?.let(onSkip) },
            elapsed = { elapsedMs / SKIP_PROMPT_MS.toFloat() },
            modifier = Modifier.graphicsLayer {
                alpha = opacity.value
                translationY = travel.value.roundToInt().toFloat()
            }.testTag("recording-skip-prompt").focusRequester(focus)
                .focusProperties { canFocus = visible }
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * Skip resting above the controls' info strip, on the right, while the recording plays an ad
 * break. It is only entered deliberately; Down returns there through [onDown], and Left, Right
 * and Up stay on it. It takes no room: the info strip below does not move.
 */
@Composable
internal fun RecordingSkipRest(
    onSkip: () -> Unit,
    focus: FocusRequester,
    onDown: () -> Unit,
    canFocus: Boolean,
    alpha: () -> Float,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    marksOpen: Boolean = false,
) {
    val lift = animateFloatAsState(if (marksOpen) 1f else 0f,
        tween(if (marksOpen) PlayerMotion.MediumMs else PlayerMotion.ShortMs, easing = PlayerMotion.Standard), label = "skip marks lift")
    RecordingSkipButton(
        onSkip = onSkip,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha(); translationY = -32.dp.toPx() * lift.value }
            .testTag("recording-skip")
            .focusRequester(focus)
            .focusProperties {
                this.canFocus = canFocus
                left = FocusRequester.Cancel; right = FocusRequester.Cancel
                up = FocusRequester.Cancel; down = FocusRequester.Cancel
            }
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionDown -> {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) onDown()
                        true
                    }
                    Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> true
                    else -> false
                }
            },
    )
}
