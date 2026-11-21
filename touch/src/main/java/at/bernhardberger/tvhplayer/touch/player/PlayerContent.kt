@file:Suppress("MagicNumber", "LongParameterList")

package at.bernhardberger.tvhplayer.touch.player

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow
import at.bernhardberger.tvhplayer.touch.channels.ChannelRows
import at.bernhardberger.tvhplayer.touch.channels.ChannelsActions
import at.bernhardberger.tvhplayer.touch.channels.ChannelsLayout

private const val DRAG_ALPHA = .38f

@Composable
internal fun LandscapePlayerContent(
    state: LivePlayerState,
    layout: PlayerLayout,
    preview: Int?,
    onAction: (PlayerAction) -> Unit,
    onPreviewChange: (Int?) -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier,
) {
    val tablet = layout == PlayerLayout.Tablet
    val dragging = preview != null
    val footerMargin = if (tablet) 40.dp else 48.dp
    val trackGutter = if (tablet) 80.dp else 66.dp
    val trackBottom = if (tablet) 108.dp else 90.dp
    val rowBottom = if (tablet) 30.dp else 20.dp
    Box(modifier.fillMaxWidth().padding(horizontal = footerMargin).height(if (tablet) 270.dp else 190.dp)) {
        LandscapeProgram(
            state.channel, layout,
            Modifier.align(Alignment.BottomStart).padding(bottom = trackBottom + if (tablet) 28.dp else 22.dp)
                .fillMaxWidth(.62f).alpha(if (dragging) DRAG_ALPHA else 1f),
        )
        if (state.status == LivePlayerStatus.Playing) {
            LiveOffer(
                state.timeline, preview, onAction, layout,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(bottom = trackBottom + if (tablet) 26.dp else 16.dp),
            )
        }
        val clockModifier = Modifier.padding(bottom = trackBottom - 8.dp)
        ClockText(playerClock(state.timeline, 0), layout, clockModifier.align(Alignment.BottomStart))
        ClockText(
            playerClock(state.timeline, state.timeline.duration), layout, clockModifier.align(Alignment.BottomEnd),
        )
        PlayerTimeline(
            state.timeline, state.status, layout, true, preview, onPreviewChange, onSeek,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = trackGutter)
                .padding(bottom = trackBottom - 24.dp).height(44.dp),
        )
        PlayerActionRow(
            state, layout, dragging, onAction,
            Modifier.align(Alignment.BottomCenter).padding(bottom = rowBottom),
        )
    }
}

@Composable
private fun LandscapeProgram(channel: ChannelRow, layout: PlayerLayout, modifier: Modifier) {
    val tablet = layout == PlayerLayout.Tablet
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (tablet) 20.dp else 14.dp)) {
        channel.details?.artwork?.let {
            Image(
                it, null, Modifier.size(if (tablet) 196.dp else 112.dp, if (tablet) 112.dp else 68.dp)
                    .clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop,
            )
        }
        Column {
            val type = MaterialTheme.typography
            val time = channel.now?.timeRange
            val genre = channel.details?.metadata
            val summary = if (time != null && genre != null) {
                stringResource(R.string.player_time_and_genre, time, genre)
            } else {
                time ?: genre
            }
            summary?.let {
                ProgramLine(it, style = if (tablet) type.bodyMedium.copy(fontSize = 15.sp) else
                    type.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp))
            }
            ProgramTitle(channel.now?.title, layout)
            channel.details?.episode?.let {
                ProgramLine(it, style = if (tablet) type.bodyLarge.copy(fontSize = 17.sp) else type.bodyMedium)
            }
            if (tablet) {
                channel.next?.let { ProgramLine(stringResource(R.string.player_next_program, it.start, it.title)) }
            }
        }
    }
}

@Composable
internal fun UprightPlayerContent(
    state: LivePlayerState,
    layout: PlayerLayout,
    dragging: Boolean,
    onAction: (PlayerAction) -> Unit,
    onChannel: (String) -> Unit,
) {
    val inset = if (layout == PlayerLayout.Phone) 16.dp else 24.dp
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = inset).padding(top = 16.dp)
                .alpha(if (dragging) DRAG_ALPHA else 1f),
        ) {
            val channel = state.channel
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ChannelLogo(channel, Modifier.size(64.dp, 36.dp))
                Text(
                    channel.name, Modifier.weight(1f).padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                channel.now?.timeRange?.let { ProgramLine(it, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(10.dp))
            ProgramTitle(channel.now?.title, layout, MaterialTheme.colorScheme.onSurface)
            channel.details?.episode?.let { ProgramLine(it, MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        PlayerActionRow(
            state, layout, dragging, onAction,
            Modifier.padding(horizontal = inset - 8.dp).padding(top = 14.dp, bottom = 12.dp),
        )
        PlayerChannelList(state, layout, onAction, onChannel, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerChannelList(
    state: LivePlayerState,
    layout: PlayerLayout,
    onAction: (PlayerAction) -> Unit,
    onChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val inset = if (layout == PlayerLayout.Phone) 12.dp else 24.dp
    Surface(
        modifier = modifier.fillMaxWidth().testTag("player-channel-list"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colors.surfaceContainer,
        contentColor = colors.onSurface,
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = inset)) {
            // A static list in this slice, so don't expose the drawn grip as a drag affordance.
            BottomSheetDefaults.DragHandle(
                Modifier.align(Alignment.CenterHorizontally).height(16.dp)
                    .wrapContentHeight(unbounded = true).clearAndSetSemantics {},
                color = colors.onSurfaceVariant.copy(alpha = .55f),
            )
            Row(
                Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.player_channels), Modifier.semantics { heading() },
                    color = colors.onSurface, style = MaterialTheme.typography.titleMedium,
                )
                if (state.channels.tags.isNotEmpty()) {
                    ChannelGroupButton(state, onAction)
                }
            }
            ChannelRows(
                state.channels, if (layout == PlayerLayout.Phone) ChannelsLayout.Phone else ChannelsLayout.Upright,
                ChannelsActions(
                    onPlay = onChannel, onTag = { onAction(PlayerAction.ChannelGroups) }, onRetry = {},
                    onOpenConnectionSettings = { onAction(PlayerAction.Settings) },
                ),
                showColumnLabels = false,
            )
        }
    }
}

@Composable
private fun ChannelGroupButton(state: LivePlayerState, onAction: (PlayerAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilledTonalButton(
        onClick = { onAction(PlayerAction.ChannelGroups) },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = colors.surfaceContainerHigh, contentColor = colors.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Icon(painterResource(R.drawable.channels_tag), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(state.channels.tags.firstOrNull { it.id == state.channels.selectedTagId }?.label
            ?: stringResource(R.string.channels_all))
        Spacer(Modifier.width(4.dp))
        Icon(painterResource(R.drawable.channels_expand), null, Modifier.size(18.dp))
    }
}

@Composable
internal fun LiveOffer(
    timeline: LiveTimeline,
    preview: Int?,
    onAction: (PlayerAction) -> Unit,
    layout: PlayerLayout,
    modifier: Modifier = Modifier,
    showLive: Boolean = true,
) {
    val behind = timeline.live - (preview ?: timeline.position)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showLive && (behind > 0 || preview == null)) {
            Text(
                behindLive(behind), color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (layout.upright) Spacer(Modifier.weight(1f))
        if (showLive && behind > 0) {
            val buttonFill = Color(0xCC37393A)
            Button(
                onClick = { onAction(PlayerAction.GoLive) }, enabled = preview == null,
                modifier = Modifier.alpha(if (preview == null) 1f else DRAG_ALPHA),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonFill, contentColor = Color.White,
                    disabledContainerColor = buttonFill, disabledContentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 13.dp),
            ) {
                Icon(painterResource(R.drawable.player_go_live), null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.player_go_live), maxLines = 1)
            }
        }
        if (layout == PlayerLayout.Phone && preview == null) {
            PlayerIconButton(
                R.drawable.player_fullscreen, R.string.player_full_screen, { onAction(PlayerAction.FullScreen) },
            )
        }
    }
}

@Composable
private fun PlayerActionRow(
    state: LivePlayerState,
    layout: PlayerLayout,
    dragging: Boolean,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier,
) {
    val label = layout != PlayerLayout.PhoneLandscape &&
        (layout != PlayerLayout.Phone || LocalDensity.current.fontScale <= 1.1f)
    val ink = if (layout.upright) MaterialTheme.colorScheme.onSurface else Color.White
    val listening = state.status == LivePlayerStatus.Playing || state.status == LivePlayerStatus.Tuning
    val colors = ButtonDefaults.textButtonColors(
        contentColor = ink, disabledContentColor = if (dragging) ink else ink.copy(alpha = .36f),
    )
    Row(
        modifier.fillMaxWidth().alpha(if (dragging) DRAG_ALPHA else 1f).testTag("player-actions"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(R.drawable.channels_record, R.string.player_record, label, !dragging, colors) {
            onAction(PlayerAction.Record)
        }
        ActionButton(R.drawable.player_audio, R.string.player_audio, label, !dragging && listening, colors) {
            onAction(PlayerAction.Audio)
        }
        ActionButton(R.drawable.player_subtitles, R.string.player_subtitles, label, !dragging && listening, colors) {
            onAction(PlayerAction.Subtitles)
        }
        Spacer(Modifier.weight(1f))
        ActionButton(R.drawable.channels_info, R.string.player_info, label, !dragging, colors) {
            onAction(PlayerAction.Info)
        }
        if (!layout.upright) {
            ActionButton(R.drawable.player_channels, R.string.player_channels, true, !dragging, colors) {
                onAction(PlayerAction.Channels)
            }
        }
    }
}

@Composable
private fun ActionButton(
    @DrawableRes icon: Int,
    @StringRes name: Int,
    label: Boolean,
    enabled: Boolean,
    colors: ButtonColors,
    onClick: () -> Unit,
) {
    val description = stringResource(name)
    TextButton(
        onClick = onClick, enabled = enabled, colors = colors,
        modifier = if (label) Modifier else Modifier.semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = if (label) 8.dp else 2.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(20.dp))
        if (label) {
            Spacer(Modifier.width(6.dp))
            Text(description, maxLines = 1, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ProgramTitle(title: String?, layout: PlayerLayout, color: Color = Color.White) {
    title?.let {
        val type = MaterialTheme.typography
        Text(
            it, color = color, style = when (layout) {
                PlayerLayout.Tablet -> type.headlineLarge.copy(fontSize = 34.sp, lineHeight = 42.sp)
                PlayerLayout.PhoneLandscape -> type.headlineSmall.copy(lineHeight = 30.sp)
                else -> type.headlineSmall
            },
            maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ClockText(value: String, layout: PlayerLayout, modifier: Modifier) {
    Text(
        value, modifier, color = Color.White.copy(alpha = .8f),
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = if (layout == PlayerLayout.Tablet) 15.sp else 13.sp,
        ),
        maxLines = 1,
    )
}

@Composable
private fun ProgramLine(
    value: String,
    color: Color = Color.White.copy(alpha = .84f),
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Text(
        value, color = color, style = style,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}
