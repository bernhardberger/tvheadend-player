package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.LivePlayerAlert
import at.bernhardberger.tvhplayer.core.PlaybackRetryCommand
import at.bernhardberger.tvhplayer.ui.TvOverlayBoxAlpha
import at.bernhardberger.tvhplayer.ui.common.AppErrorText
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryAction
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import kotlinx.coroutines.delay

/**
 * The channel message: title, then why and what helps as one paragraph, then the number. It has
 * no action and nothing focusable, so every key keeps its usual meaning. Its exit keeps the
 * condition it showed while the failure clears.
 */
@Composable
internal fun PlayerChannelMessage(
    visible: Boolean,
    condition: AppErrorCondition?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate)),
        modifier = modifier.semanticsWhileShown(visible),
        label = PlayerChannelMessageTag,
    ) {
        val shown = rememberLastShown(condition.takeIf { visible }) ?: return@AnimatedVisibility
        val text = rememberAppErrorText()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .background(Color.Black.copy(alpha = TvOverlayBoxAlpha), MaterialTheme.shapes.large)
                .padding(24.dp)
                .testTag(PlayerChannelMessageTag),
        ) {
            Text(
                text = text.title(shown),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = listOfNotNull(text.reason(shown), text.hint(shown)).joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = text.number(shown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

internal const val PlayerChannelMessageTag = "player-channel-unavailable"

/**
 * The live player's full-screen alert for [alert]. Its buttons are the catalog's, in order, the
 * first focused; Retry runs the alert's [PlaybackRetryCommand]. While live playback retries by
 * itself it shows the countdown to the next attempt and only Close. The exit keeps what it showed.
 */
@Composable
internal fun LivePlayerAlertOverlay(
    visible: Boolean,
    alert: LivePlayerAlert?,
    onRetry: (PlaybackRetryCommand) -> Unit,
    onConnectionSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val shown = rememberLastShown(alert.takeIf { visible })
    val text = rememberAppErrorText()
    val seconds = rememberReconnectSeconds(shown as? LivePlayerAlert.Recovering, visible)
    val condition = (shown as? LivePlayerAlert.Error)?.error?.condition
    val actions = when (shown) {
        is LivePlayerAlert.Error -> shown.error.actions
        is LivePlayerAlert.Recovering -> listOf(AppErrorAction.CLOSE)
        null -> emptyList()
    }
    // One overlay for every alert, so a change between them does not fade it in again.
    TvRecoveryOverlay(
        visible = visible && shown != null,
        message = when {
            condition != null -> text.title(condition)
            shown is LivePlayerAlert.Recovering -> text.reconnecting(seconds)
            else -> ""
        },
        detail = condition?.let(text::reason),
        hint = condition?.let(text::hint),
        errorNumber = condition?.let(text::number),
        actions = actions.map { action ->
            TvRecoveryAction(text.label(action)) {
                when (action) {
                    AppErrorAction.RETRY -> (shown as? LivePlayerAlert.Error)?.let { onRetry(it.retry) }
                    AppErrorAction.CONNECTION_SETTINGS -> onConnectionSettings()
                    AppErrorAction.CLOSE -> onClose()
                }
            }
        },
    )
}

/** Whole seconds left of [remainingMillis], rounded up; null once the attempt is due. */
internal fun reconnectCountdownSeconds(remainingMillis: Long): Int? =
    if (remainingMillis <= 0L) null else ((remainingMillis + 999L) / 1_000L).toInt()

/** Counts down [recovering]'s delay from when it is shown; a hidden alert keeps its last value. */
@Composable
private fun rememberReconnectSeconds(recovering: LivePlayerAlert.Recovering?, visible: Boolean): Int? {
    var seconds by remember(recovering) {
        mutableIntStateOf(recovering?.let { reconnectCountdownSeconds(it.retryDelayMillis) } ?: 0)
    }
    LaunchedEffect(recovering, visible) {
        if (recovering == null || !visible) return@LaunchedEffect
        var remaining = recovering.retryDelayMillis
        seconds = reconnectCountdownSeconds(remaining) ?: 0
        while (remaining > 0L) {
            val step = (remaining % 1_000L).takeIf { it > 0L } ?: 1_000L
            delay(step)
            remaining -= step
            seconds = reconnectCountdownSeconds(remaining) ?: 0
        }
    }
    return seconds.takeIf { it > 0 }
}

@Composable
private fun rememberAppErrorText(): AppErrorText {
    val context = LocalContext.current
    return remember(context) { AppErrorText(context) }
}
