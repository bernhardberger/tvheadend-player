package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.Player
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.LivePauseState
import at.bernhardberger.tvhplayer.ui.player.livePlayWhenReady

/** Null means this drawer item no longer owns the transport. */
internal fun drawerControlPaused(
    target: AppPlaybackTarget?,
    activeTarget: AppPlaybackTarget?,
    livePause: LivePauseState,
    playerPlayWhenReady: Boolean,
): Boolean? {
    if (target == null || target != activeTarget) return null
    return when (target) {
        is AppPlaybackTarget.Live -> !livePlayWhenReady(livePause, playerPlayWhenReady)
        is AppPlaybackTarget.Recording -> !playerPlayWhenReady
    }
}

internal fun drawerTogglePlayWhenReady(
    target: AppPlaybackTarget?,
    activeTarget: AppPlaybackTarget?,
    livePause: LivePauseState,
    playerPlayWhenReady: Boolean,
): Boolean? = drawerControlPaused(target, activeTarget, livePause, playerPlayWhenReady)

internal fun shouldStopDrawerPlayback(
    target: AppPlaybackTarget?,
    activeTarget: AppPlaybackTarget?,
    stopInFlight: Boolean,
): Boolean = !stopInFlight && target != null && target == activeTarget

/** Presentation only: a stall retains pause intent; only the owned tune spins. */
enum class ChannelPlaybackIndicator { NONE, PLAYING, PAUSED, TUNING }

/** Tuning is a loading indication, not evidence of a playing item or a viewer position. */
internal val ChannelPlaybackIndicator.isPlayingItem: Boolean
    get() = this == ChannelPlaybackIndicator.PLAYING || this == ChannelPlaybackIndicator.PAUSED

fun channelPlaybackIndicator(
    channelId: ChannelId,
    target: AppPlaybackTarget?,
    state: AppPlaybackState,
    playWhenReady: Boolean,
): ChannelPlaybackIndicator {
    return playbackIndicator(AppPlaybackTarget.Live(channelId), target, state, playWhenReady)
}

/** The same presentation contract for live channels and recordings, independent of their surface. */
internal fun playbackIndicator(
    item: AppPlaybackTarget?,
    target: AppPlaybackTarget?,
    state: AppPlaybackState,
    playWhenReady: Boolean,
): ChannelPlaybackIndicator {
    if (item == null || target != item) return ChannelPlaybackIndicator.NONE
    return when (state) {
        AppPlaybackState.Playing, AppPlaybackState.Buffering ->
            if (playWhenReady) ChannelPlaybackIndicator.PLAYING else ChannelPlaybackIndicator.PAUSED
        AppPlaybackState.Starting -> ChannelPlaybackIndicator.TUNING
        AppPlaybackState.Idle, AppPlaybackState.Finished,
        is AppPlaybackState.Recovering, is AppPlaybackState.Failed -> ChannelPlaybackIndicator.NONE
    }
}

@Composable
internal fun rememberPlaybackIntent(player: Player): State<Boolean> {
    val intent = remember(player) { mutableStateOf(player.playWhenReady) }
    LifecycleStartEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                intent.value = playWhenReady
            }
        }
        player.addListener(listener)
        intent.value = player.playWhenReady
        onStopOrDispose { player.removeListener(listener) }
    }
    return intent
}

@Composable
internal fun ChannelPlaybackMarker(
    indicator: ChannelPlaybackIndicator,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    announceState: Boolean = true,
    animated: Boolean = true,
) {
    if (indicator == ChannelPlaybackIndicator.NONE) return
    val description = stringResource(when (indicator) {
        ChannelPlaybackIndicator.PAUSED -> R.string.player_paused
        ChannelPlaybackIndicator.TUNING -> R.string.loading
        else -> R.string.player_on_now
    })
    val slot = modifier.size(size).testTag("channel-${indicator.name.lowercase()}-indicator")
    if (indicator == ChannelPlaybackIndicator.TUNING) {
        // TV Material 1.1.0 has no progress primitive; use the installed Material one.
        Box(slot) {
            val progressModifier = Modifier.fillMaxSize().clearAndSetSemantics {
                if (announceState) contentDescription = description
            }
            if (animated) CircularProgressIndicator(
                modifier = progressModifier,
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            ) else CircularProgressIndicator(
                progress = { 0.75f },
                modifier = progressModifier,
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        }
    } else if (indicator == ChannelPlaybackIndicator.PLAYING) {
        val phase = if (animated) rememberInfiniteTransition(label = "watching").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
            label = "soundBars",
        ) else null
        val color = LocalContentColor.current
        Canvas(slot.clearAndSetSemantics {
            if (announceState) contentDescription = description
        }) {
            // Read animation state only while drawing, never while composing a row.
            val width = this.size.width / 7f
            repeat(3) { index ->
                val height = this.size.height * watchingBarHeight(index, phase?.value ?: 0f)
                drawRoundRect(color, Offset(width * (1 + index * 2), (this.size.height - height) / 2),
                    Size(width, height), CornerRadius(width / 2))
            }
        }
    } else {
        Icon(
            painterResource(if (indicator == ChannelPlaybackIndicator.PAUSED) R.drawable.ic_pause else R.drawable.ic_play_arrow),
            contentDescription = description.takeIf { announceState },
            tint = LocalContentColor.current,
            modifier = slot,
        )
    }
}

internal fun watchingBarHeight(index: Int, phase: Float): Float =
    0.3f + 0.45f * ((kotlin.math.sin((phase + index / 3f) * 2 * kotlin.math.PI) + 1) / 2).toFloat()
