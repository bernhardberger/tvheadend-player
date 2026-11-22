@file:Suppress("MagicNumber", "LongParameterList")

package at.bernhardberger.tvhplayer.touch.player

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.ui.common.hintRes
import at.bernhardberger.tvhplayer.ui.common.AppErrorText
import at.bernhardberger.tvhplayer.ui.common.titleRes
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow
import at.bernhardberger.tvhplayer.touch.ui.channelLogoSlot

private val OnPicture = Color.White
private val DarkControl = Color(0xA82C2E2E)

@Composable
internal fun BoxScope.PictureControls(
    state: PlayerFaceState,
    layout: PlayerLayout,
    preview: Long?,
    onAction: (PlayerAction) -> Unit,
    onBrightness: (Float) -> Unit,
    onSettings: () -> Unit,
    onChoice: (PlayerChoice) -> Unit = {},
) {
    val status = state.status
    val playing = status == LivePlayerStatus.Playing
    val tuning = status == LivePlayerStatus.Tuning
    if (preview == null) {
        PlayerTopBar(state, layout, onAction, onSettings, Modifier.align(Alignment.TopCenter))
        if (playing || tuning) PictureCenter(state, layout, onAction, onBrightness)
    }
    if (layout.upright && (playing || tuning)) {
        val line = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, bottom = 22.dp)
        when (state) {
            is LivePlayerState -> LiveOffer(
                state.timeline, state.offered, preview?.let { (it / 1_000).toInt() }, onAction, layout, line, playing,
            )
            is RecordingPlayerState -> RecordingPositionLine(state, preview, layout, onAction, onChoice, line)
        }
    }
}

@Composable
private fun BoxScope.PictureCenter(
    state: PlayerFaceState,
    layout: PlayerLayout,
    onAction: (PlayerAction) -> Unit,
    onBrightness: (Float) -> Unit,
) {
    val centerOffset = layout.transportOffset
    if (state.status == LivePlayerStatus.Playing && PlayerControl.Timeshift in state.offered) {
        Transport(state, layout, onAction, Modifier.align(Alignment.Center).offset(y = centerOffset))
    }
    if (!layout.upright) {
        Brightness(
            state.brightness, onBrightness, Modifier.align(Alignment.CenterEnd).offset(y = centerOffset)
                .padding(end = if (layout == PlayerLayout.Tablet) 34.dp else 29.dp),
            tablet = layout == PlayerLayout.Tablet,
        )
    }
}

private val PlayerLayout.transportOffset get() = when (this) {
    PlayerLayout.Tablet -> -30.dp
    PlayerLayout.PhoneLandscape -> -64.dp
    PlayerLayout.Phone -> -10.dp
    else -> 0.dp
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun BoxScope.PictureStatus(state: PlayerFaceState, layout: PlayerLayout, onAction: (PlayerAction) -> Unit) {
    if (state.status == LivePlayerStatus.Tuning) {
        val loading = stringResource(R.string.player_tuning)
        LoadingIndicator(
            Modifier.align(Alignment.Center).offset(y = layout.transportOffset)
                .size(if (layout == PlayerLayout.Tablet) 64.dp else 48.dp).semantics { contentDescription = loading },
            color = OnPicture,
        )
    } else if (state.status != LivePlayerStatus.Playing) {
        StatusMessage(state, layout, onAction, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun PlayerTopBar(
    state: PlayerFaceState,
    layout: PlayerLayout,
    onAction: (PlayerAction) -> Unit,
    onSettings: () -> Unit,
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
            val logo = playerChannelLogo(state.channel, Modifier.width(logoWidth).height(40.dp))
            logo?.invoke()
            val type = MaterialTheme.typography
            val channelType = if (layout == PlayerLayout.Tablet) type.titleLarge else type.titleMedium
            Text(
                state.channel.name, Modifier.weight(1f).padding(start = if (logo != null) 12.dp else 0.dp, end = 12.dp),
                color = OnPicture,
                style = channelType, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (PlayerControl.Settings in state.offered) {
            PlayerIconButton(R.drawable.channels_settings, R.string.player_settings, onSettings)
        }
        if (PlayerControl.PictureInPicture in state.offered) {
            PlayerIconButton(
                R.drawable.player_pip, R.string.player_picture_in_picture, { onAction(PlayerAction.PictureInPicture) },
                enabled = state.status !is LivePlayerStatus.Unavailable,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Transport(
    state: PlayerFaceState,
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
    val canSeek by remember(state) { derivedStateOf { state.seekable } }
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
        FilledTonalIconToggleButton(
            checked = state.paused,
            onCheckedChange = { onAction(PlayerAction.TogglePlay) },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(main),
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = DarkControl,
                contentColor = OnPicture,
                checkedContainerColor = OnPicture,
                checkedContentColor = Color.Black,
            ),
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
    val state = remember { SliderState(value = value) }
    // Keep the slider's drag offset when the player echoes back a change.
    if (!state.isDragging) SideEffect { state.value = value }
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
    state: PlayerFaceState,
    layout: PlayerLayout,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier,
) {
    val status = state.status
    val (icon, heading, message) = if (state is RecordingPlayerState && status is LivePlayerStatus.Unavailable) {
        val reason = status.condition?.let { AppErrorText(LocalContext.current).reason(it) }
        Triple(R.drawable.player_no_video, SharedR.string.error_306_title, reason)
    } else statusText(status) ?: return
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
        message?.let {
            Text(
                it, color = OnPicture.copy(alpha = .75f),
                style = if (compact) type.bodySmall else type.bodyMedium, textAlign = TextAlign.Center,
            )
        }
        (state as? RecordingPlayerState)?.failureDetail?.let {
            Text(it, color = OnPicture.copy(alpha = .75f),
                style = if (compact) type.bodySmall else type.bodyMedium, textAlign = TextAlign.Center)
        }
        if (status == LivePlayerStatus.PauseEnded) {
            Button(
                onClick = { onAction(PlayerAction.WatchLive) },
                colors = ButtonDefaults.buttonColors(containerColor = OnPicture, contentColor = Color.Black),
            ) { Text(stringResource(R.string.player_watch_live)) }
        }
    }
}

@Composable
private fun statusText(status: LivePlayerStatus): Triple<Int, Int, String?>? = when (status) {
    is LivePlayerStatus.Reconnecting -> Triple(
        R.drawable.channels_cloud_off,
        R.string.player_disconnected,
        status.nextAttemptSeconds?.let { stringResource(R.string.player_reconnect_in, it) }
            ?: stringResource(R.string.player_reconnecting),
    )
    LivePlayerStatus.PauseEnded -> Triple(
        R.drawable.player_pause_ended, R.string.player_pause_ended, stringResource(R.string.player_pause_expired),
    )
    is LivePlayerStatus.Unavailable -> when (val condition = status.condition) {
        // The drawn message is the busy-tuner one; any other cause says what the TV app says for it.
        null, AppErrorCondition.NO_FREE_ADAPTER -> Triple(
            R.drawable.player_no_video, R.string.player_no_tuner, stringResource(R.string.player_no_tuner_reason),
        )
        else -> Triple(
            R.drawable.player_no_video, condition.titleRes(), condition.hintRes()?.let { stringResource(it) },
        )
    }
    else -> null
}

internal fun playerChannelLogo(channel: ChannelRow, modifier: Modifier): (@Composable () -> Unit)? =
    channelLogoSlot(channel.logo, channel.logoTint, modifier, RoundedCornerShape(8.dp), channel.logoModel)

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
