@file:Suppress("MagicNumber", "LongParameterList")

package at.bernhardberger.tvhplayer.touch.player

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow

private val OnPicture = Color.White
private val DarkControl = Color(0xA82C2E2E)

@Composable
internal fun BoxScope.PictureControls(
    state: LivePlayerState,
    layout: PlayerLayout,
    visible: Boolean,
    preview: Int?,
    onAction: (PlayerAction) -> Unit,
    onBrightness: (Float) -> Unit,
) {
    if (!visible) return
    val status = state.status
    val playing = status == LivePlayerStatus.Playing
    val tuning = status == LivePlayerStatus.Tuning
    val centerOffset = when (layout) {
        PlayerLayout.Tablet -> -30.dp
        PlayerLayout.PhoneLandscape -> -64.dp
        PlayerLayout.Phone -> -10.dp
        else -> 0.dp
    }
    if (preview == null) {
        PlayerTopBar(state, layout, onAction, Modifier.align(Alignment.TopCenter))
        if (playing || tuning) {
            Transport(state, layout, onAction, Modifier.align(Alignment.Center).offset(y = centerOffset))
            if (!layout.upright) {
                Brightness(
                    state.brightness, onBrightness, Modifier.align(Alignment.CenterEnd).offset(y = centerOffset)
                        .padding(end = if (layout == PlayerLayout.Tablet) 34.dp else 29.dp),
                    tablet = layout == PlayerLayout.Tablet,
                )
            }
        } else {
            StatusMessage(status, layout, onAction, Modifier.align(Alignment.Center))
        }
    }
    if (layout.upright && (playing || tuning)) {
        LiveOffer(
            state.timeline, preview, onAction, layout, showLive = playing,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, bottom = 22.dp),
        )
    }
}

@Composable
private fun PlayerTopBar(
    state: LivePlayerState,
    layout: PlayerLayout,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier,
) {
    val inset = when (layout) {
        PlayerLayout.Tablet -> 24.dp
        PlayerLayout.PhoneLandscape -> 30.dp
        else -> 0.dp
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = inset).padding(top = if (layout.upright) 0.dp else 11.dp)
            .testTag("player-top"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIconButton(R.drawable.connect_back_outline, R.string.player_back, { onAction(PlayerAction.Back) })
        if (!layout.upright) {
            val logoWidth = if (layout == PlayerLayout.Tablet) 86.dp else 64.dp
            ChannelLogo(state.channel, Modifier.width(logoWidth).height(40.dp))
            val type = MaterialTheme.typography
            val channelType = if (layout == PlayerLayout.Tablet) type.titleLarge else type.titleMedium.copy(
                fontSize = 18.sp,
            )
            Text(
                state.channel.name, Modifier.weight(1f).padding(horizontal = 12.dp), color = OnPicture,
                style = channelType, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        PlayerIconButton(R.drawable.channels_settings, R.string.player_settings, { onAction(PlayerAction.Settings) })
        PlayerIconButton(
            R.drawable.player_pip, R.string.player_picture_in_picture, { onAction(PlayerAction.PictureInPicture) },
            enabled = state.status != LivePlayerStatus.Unavailable,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Transport(
    state: LivePlayerState,
    layout: PlayerLayout,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier,
) {
    val main = when (layout) {
        PlayerLayout.Tablet -> 96.dp
        PlayerLayout.PhoneLandscape -> 76.dp
        PlayerLayout.Upright -> 72.dp
        else -> 60.dp
    }
    if (state.status == LivePlayerStatus.Tuning) {
        val loading = stringResource(R.string.player_tuning)
        LoadingIndicator(modifier.size(if (layout == PlayerLayout.Tablet) 64.dp else 48.dp)
            .semantics { contentDescription = loading }, color = OnPicture)
        return
    }
    val canSeek = state.timeline.bufferStart?.let { it < state.timeline.live } == true
    val skipSize = if (layout == PlayerLayout.Tablet) 72.dp else 52.dp
    val skipColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = DarkControl, contentColor = OnPicture,
    )
    Row(
        modifier.testTag("player-transport"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = { onAction(PlayerAction.BackTen) }, modifier = Modifier.size(skipSize),
            enabled = canSeek, colors = skipColors,
        ) {
            PlayerIcon(R.drawable.player_replay_ten, R.string.player_back_ten, Modifier.size(layout.skipIconSize))
        }
        FilledIconButton(
            onClick = { onAction(PlayerAction.TogglePlay) },
            modifier = Modifier.size(main),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = OnPicture, contentColor = Color.Black),
        ) {
            PlayerIcon(
                if (state.paused) R.drawable.channels_play else R.drawable.player_pause,
                if (state.paused) R.string.player_play else R.string.player_pause,
                Modifier.size(main * .4f),
            )
        }
        FilledTonalIconButton(
            onClick = { onAction(PlayerAction.ForwardThirty) }, modifier = Modifier.size(skipSize),
            enabled = canSeek, colors = skipColors,
        ) {
            PlayerIcon(
                R.drawable.player_forward_thirty, R.string.player_forward_thirty, Modifier.size(layout.skipIconSize),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Brightness(value: Float, onBrightness: (Float) -> Unit, modifier: Modifier, tablet: Boolean) {
    val state = remember(value) { SliderState(value = value) }
    val label = stringResource(R.string.player_brightness)
    val colors = SliderDefaults.colors(
        thumbColor = OnPicture, activeTrackColor = OnPicture, inactiveTrackColor = DarkControl,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val width = if (tablet) 44.dp else 34.dp
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        VerticalSlider(
            state = state, onValueChange = { state.value = it; onBrightness(it) }, topToBottom = false,
            modifier = Modifier.height(if (tablet) 216.dp else 134.dp).testTag("player-brightness")
                .semantics { contentDescription = label },
            colors = colors, interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource, isVertical = true, colors = colors, thumbSize = DpSize(width, 4.dp),
                )
            },
            track = {
                SliderDefaults.Track(
                    it, Modifier.width(width), colors = colors, drawStopIndicator = null, thumbTrackGapSize = 0.dp,
                )
            },
        )
        Icon(
            painterResource(R.drawable.player_brightness), null,
            Modifier.padding(bottom = 10.dp).size(if (tablet) 20.dp else 16.dp), tint = Color.Black,
        )
    }
}

@Composable
private fun StatusMessage(
    status: LivePlayerStatus,
    layout: PlayerLayout,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier,
) {
    val (icon, heading, message) = when (status) {
        is LivePlayerStatus.Reconnecting -> Triple(
            R.drawable.channels_cloud_off,
            R.string.player_disconnected,
            status.nextAttemptSeconds?.let { stringResource(R.string.player_reconnect_in, it) }
                ?: stringResource(R.string.player_reconnecting),
        )
        LivePlayerStatus.PauseEnded -> Triple(
            R.drawable.player_pause_ended, R.string.player_pause_ended, stringResource(R.string.player_pause_expired),
        )
        LivePlayerStatus.Unavailable -> Triple(
            R.drawable.player_no_video, R.string.player_no_tuner, stringResource(R.string.player_no_tuner_reason),
        )
        else -> return
    }
    val compact = layout.upright
    val type = MaterialTheme.typography
    Column(
        modifier.width(if (compact) 256.dp else 320.dp).padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        if (!layout.upright) Icon(painterResource(icon), null, Modifier.size(44.dp), tint = OnPicture)
        Text(
            stringResource(heading), color = OnPicture,
            style = if (compact) type.titleMedium else type.titleLarge, textAlign = TextAlign.Center,
        )
        Text(
            message, color = OnPicture.copy(alpha = .75f),
            style = if (compact) type.bodySmall else type.bodyMedium, textAlign = TextAlign.Center,
        )
        if (status == LivePlayerStatus.PauseEnded) {
            Button(
                onClick = { onAction(PlayerAction.WatchLive) },
                colors = ButtonDefaults.buttonColors(containerColor = OnPicture, contentColor = Color.Black),
            ) { Text(stringResource(R.string.player_watch_live)) }
        }
    }
}

@Composable
internal fun ChannelLogo(channel: ChannelRow, modifier: Modifier) {
    channel.logo?.let {
        Image(
            it, null, modifier.clip(RoundedCornerShape(8.dp))
                .background(channel.logoTint ?: MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

@Composable
internal fun PlayerIconButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick, enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = OnPicture),
    ) { PlayerIcon(icon, label) }
}

@Composable
internal fun PlayerIcon(@DrawableRes icon: Int, @StringRes label: Int, modifier: Modifier = Modifier) {
    Icon(painterResource(icon), stringResource(label), modifier)
}
