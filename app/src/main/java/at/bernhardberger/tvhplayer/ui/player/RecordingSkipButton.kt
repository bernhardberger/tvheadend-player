package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
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

/** Above the info strip, the resting Skip keeps the gap the action row keeps below it, doubled. */
private val SkipRestGap = 16.dp

/** The button's focused scale, which the prompt's corner placement allows for. */
private const val SkipFocusScale = 1.1f

internal val RecordingSkipActivationKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * The "Skip ad break" button, over the hidden chrome as the prompt, with the time it stays as a
 * thin fill, and resting above the controls' info strip. A one-off: [PlayerActionRow] holds the
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
    OutlinedButton(
        onClick = onSkip,
        modifier = modifier.onPreviewKeyEvent { event ->
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
        colors = OutlinedButtonDefaults.colors(containerColor = PlayerChromeTokens.chipScrim),
        scale = OutlinedButtonDefaults.scale(focusedScale = SkipFocusScale),
        contentPadding = OutlinedButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_skip_next), contentDescription = null,
                    modifier = Modifier.size(OutlinedButtonDefaults.IconSize))
                Spacer(Modifier.width(OutlinedButtonDefaults.IconSpacing))
                Text(stringResource(R.string.skip_ad_break), maxLines = 1, softWrap = false)
            }
            if (elapsed != null) {
                val color = LocalContentColor.current
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(2.dp).testTag("recording-skip-fill").drawBehind {
                    drawRect(color.copy(alpha = 0.24f))
                    drawRect(color, size = Size(size.width * elapsed().coerceIn(0f, 1f), size.height))
                })
            }
        }
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
    var elapsedMs by remember(prompt) { mutableLongStateOf(0L) }
    val timeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(prompt) {
        val offered = prompt ?: return@LaunchedEffect
        // Frame time, so the prompt's time does not follow the animator duration scale.
        val start = withFrameMillis { it }
        while (elapsedMs < SKIP_PROMPT_MS) elapsedMs = withFrameMillis { it } - start
        timeout(offered)
    }
    val visible = prompt != null && shown
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate)),
        // The focused prompt grows about its centre; reserve that growth so it stays inside the
        // safe margins the caller places it at.
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val growth = (SkipFocusScale - 1f) / 2f
            layout(
                placeable.width + (placeable.width * growth).roundToInt(),
                placeable.height + (placeable.height * growth).roundToInt(),
            ) { placeable.placeRelative(0, 0) }
        },
    ) {
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
            modifier = Modifier.testTag("recording-skip-prompt").focusRequester(focus)
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
    gap: Dp = SkipRestGap,
) {
    RecordingSkipButton(
        onSkip = onSkip,
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(placeable.width, 0) { placeable.placeRelative(0, -placeable.height - gap.roundToPx()) }
            }
            .graphicsLayer { this.alpha = alpha() }
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
