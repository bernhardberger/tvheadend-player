package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.size
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

/** Presentation only: a stall retains pause intent; only the owned tune spins. */
enum class ChannelPlaybackIndicator { NONE, PLAYING, PAUSED, TUNING }

fun channelPlaybackIndicator(
    channelId: ChannelId,
    target: AppPlaybackTarget?,
    state: AppPlaybackState,
    playWhenReady: Boolean,
): ChannelPlaybackIndicator {
    if (target != AppPlaybackTarget.Live(channelId)) return ChannelPlaybackIndicator.NONE
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
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {
                    if (announceState) contentDescription = description
                },
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
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
