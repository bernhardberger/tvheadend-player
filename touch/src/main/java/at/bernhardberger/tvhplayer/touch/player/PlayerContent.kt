@file:Suppress("MagicNumber", "LongParameterList", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.player

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow
import at.bernhardberger.tvhplayer.touch.ui.rememberStandalonePicture
import at.bernhardberger.tvhplayer.touch.channels.ChannelsLayout
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.channels.TagFilter
import kotlin.math.roundToInt
import at.bernhardberger.tvhplayer.presentation.R as SharedR

internal const val DRAG_ALPHA = .38f

/** The open menu or sheet and what its lists report back; shared by the player's two faces. */
internal class PanelHost(
    val panel: PlayerPanel?,
    val onPanel: (PlayerPanel?) -> Unit,
    val onChoice: (PlayerChoice) -> Unit,
)

@Composable
internal fun LandscapePlayerContent(
    state: PlayerFaceState,
    layout: PlayerLayout,
    preview: Long?,
    onAction: (PlayerAction) -> Unit,
    panels: PanelHost,
    onPreviewChange: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
    notice: @Composable (Modifier) -> Unit,
) {
    val tablet = layout == PlayerLayout.Tablet
    val dragging = preview != null
    val footerMargin = if (tablet) 40.dp else 48.dp
    val trackGutter = if (tablet) 80.dp else 66.dp
    val trackBottom = if (tablet) 108.dp else 90.dp
    val rowBottom = if (tablet) 30.dp else 20.dp
    val hintInset = if (state is RecordingPlayerState) {
        val marks = remember(state.marks, state.durationMs) { recordingTrackMarks(state.marks, state.durationMs) }
        recordingHintInset(layout, marks)
    } else 0.dp
    val programGap = maxOf(if (tablet) 28.dp else 22.dp, hintInset + 4.dp)
    val offerGap = maxOf(if (tablet) 26.dp else 16.dp, hintInset + 8.dp)
    val footerHeight = landscapeFooterHeight(tablet, state)
    Box(modifier.fillMaxWidth().padding(horizontal = footerMargin).then(footerHeight)) {
        val programSlot = Modifier.align(Alignment.BottomStart)
            .padding(bottom = trackBottom + programGap)
        // Keep the notice and its anchor in one layout so measuring the program never
        // asks Compose to place unrelated footer siblings (including the drag label).
        LandscapeProgramSlot(notice, tablet, programSlot.fillMaxWidth()) {
            val programModifier = Modifier.fillMaxWidth(.62f).alpha(if (dragging) DRAG_ALPHA else 1f)
            LandscapeProgram(state, layout, programModifier)
        }
        if (state.status == LivePlayerStatus.Playing) {
            val offer = Modifier.align(Alignment.BottomEnd)
                .padding(bottom = trackBottom + offerGap)
            when (state) {
                is LivePlayerState -> LiveOffer(
                    state.timeline, state.offered, preview?.let { (it / 1_000).toInt() }, onAction, layout, offer,
                )
                is RecordingPlayerState -> RecordingAdAction(state, dragging, panels.onChoice, offer)
            }
        }
        LandscapeTimeline(state, layout, preview, onPreviewChange, onSeek, trackBottom, trackGutter, panels.onChoice)
        PlayerActionRow(
            state, layout, dragging, panels,
            Modifier.align(Alignment.BottomCenter).padding(bottom = rowBottom),
        )
    }
}

private fun landscapeFooterHeight(tablet: Boolean, state: PlayerFaceState): Modifier = when {
    tablet -> Modifier.height(270.dp)
    // Grow upward from the same bottom anchor rather than compressing the program slot.
    state is RecordingPlayerState -> Modifier.heightIn(min = 190.dp)
    else -> Modifier.height(190.dp)
}

@Composable
private fun BoxScope.LandscapeTimeline(
    state: PlayerFaceState,
    layout: PlayerLayout,
    preview: Long?,
    onPreviewChange: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    trackBottom: Dp,
    trackGutter: Dp,
    onChoice: (PlayerChoice) -> Unit,
) {
    val clockModifier = Modifier.padding(bottom = trackBottom - 8.dp)
    val track = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = trackGutter)
        .padding(bottom = trackBottom - 24.dp).height(44.dp)
    when (state) {
        is LivePlayerState -> {
            // Clocks follow the programme, not each position sample.
            val program by remember(state.timeline) {
                derivedStateOf { state.timeline.value?.let { it.programStart to it.duration } }
            }
            program?.let { (start, duration) ->
                ClockText(playerClock(start, 0), clockModifier.align(Alignment.BottomStart))
                ClockText(playerClock(start, duration), clockModifier.align(Alignment.BottomEnd))
                FaceTimeline(state, layout, true, preview, onPreviewChange, onSeek, track)
            }
        }
        is RecordingPlayerState -> if (state.durationMs > 0) {
            RecordingClock(state, preview, clockModifier.align(Alignment.BottomStart))
            ClockText(formatPlaybackDuration(state.durationMs), clockModifier.align(Alignment.BottomEnd))
            FaceTimeline(state, layout, true, preview, onPreviewChange, onSeek, track, onChoice)
        }
    }
}

/** On tablets the notice stands above the measured program; on phones it takes its place. */
@Composable
private fun LandscapeProgramSlot(
    notice: @Composable (Modifier) -> Unit,
    tablet: Boolean,
    modifier: Modifier,
    program: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { program() }
            Box { notice(Modifier) }
        },
    ) { (block, host), constraints ->
        val side = 16.dp.roundToPx()
        val bottom = 12.dp.roundToPx()
        val width = constraints.maxWidth
        val noticeWidth = if (tablet) width else (width * .62f).roundToInt()
        val message = host.measure(Constraints(maxWidth = noticeWidth + 2 * side))
        if (!tablet && message.height > 0) {
            val height = (message.height - bottom).coerceAtMost(constraints.maxHeight)
            layout(width, height) {
                message.placeRelative(-side, height - message.height + bottom)
            }
        } else {
            val programBlock = block.measure(constraints)
            layout(width, programBlock.height) {
                programBlock.placeRelative(0, 0)
                if (message.height > 0) message.placeRelative(-side, bottom - message.height - 8.dp.roundToPx())
            }
        }
    }
}

@Composable
private fun LandscapeProgram(state: PlayerFaceState, layout: PlayerLayout, modifier: Modifier) {
    val channel = state.channel
    val tablet = layout == PlayerLayout.Tablet
    Row(
        modifier.testTag("player-program"),
        horizontalArrangement = Arrangement.spacedBy(if (tablet) 20.dp else 14.dp),
    ) {
        val artwork = when (state) {
            is LivePlayerState -> rememberStandalonePicture(channel.details?.artwork, channel.details?.artworkModel)
            is RecordingPlayerState -> rememberStandalonePicture(state.artwork, state.artworkModel)
        }
        artwork?.let {
            Image(
                it, null, Modifier.size(if (tablet) 196.dp else 112.dp, if (tablet) 112.dp else 68.dp)
                    .testTag("player-program-artwork")
                    .clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop,
            )
        }
        Column {
            if (state is RecordingPlayerState) {
                val type = MaterialTheme.typography
                ProgramLine(recordedDate(state))
                ProgramTitle(state.title, layout)
                state.episode?.let { ProgramLine(it, style = if (tablet) type.bodyLarge else type.bodyMedium) }
            } else {
                LiveProgramLines(channel, layout)
            }
        }
    }
}

@Composable
private fun LiveProgramLines(channel: ChannelRow, layout: PlayerLayout) {
    val type = MaterialTheme.typography
    val time = channel.now?.timeRange
    val genre = channel.details?.metadata
    val summary = if (time != null && genre != null) {
        stringResource(R.string.player_time_and_genre, time, genre)
    } else {
        time ?: genre
    }
    summary?.let { ProgramLine(it) }
    ProgramTitle(channel.now?.title, layout)
    val tablet = layout == PlayerLayout.Tablet
    channel.details?.episode?.let { ProgramLine(it, style = if (tablet) type.bodyLarge else type.bodyMedium) }
    if (tablet) {
        // The line reads "<start> <title>"; without a known start it is left out.
        val next = channel.next
        val start = next?.start
        if (next != null && start != null) {
            ProgramLine(stringResource(R.string.player_next_program, start, next.title))
        }
    }
}

@Composable
internal fun UprightPlayerContent(
    state: PlayerFaceState,
    layout: PlayerLayout,
    dragging: Boolean,
    onAction: (PlayerAction) -> Unit,
    panels: PanelHost,
    onChannel: (String) -> Unit,
    onSeek: (Long) -> Unit,
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val time = when (state) {
                    is RecordingPlayerState -> stringResource(SharedR.string.player_recorded_on, state.recordedOn)
                    is LivePlayerState -> channel.now?.timeRange
                }
                time?.let { ProgramLine(it, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(10.dp))
            val title = if (state is RecordingPlayerState) state.title else channel.now?.title
            val episode = if (state is RecordingPlayerState) state.episode else channel.details?.episode
            ProgramTitle(title, layout, MaterialTheme.colorScheme.onSurface)
            episode?.let { ProgramLine(it, MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        PlayerActionRow(
            state, layout, dragging, panels,
            Modifier.padding(horizontal = inset - 8.dp).padding(top = 14.dp, bottom = 12.dp),
        )
        when (state) {
            is LivePlayerState -> PlayerChannelList(
                state.channels, layout, panels.onChoice, onChannel, { onAction(PlayerAction.RetryChannels) },
                Modifier.weight(1f),
            )
            is RecordingPlayerState -> if (state.hasMarkers) {
                InlineRecordingMarkers(state, recordingMarkerTap(state, onSeek, panels.onChoice), Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerChannelList(
    state: ChannelsState,
    layout: PlayerLayout,
    onChoice: (PlayerChoice) -> Unit,
    onChannel: (String) -> Unit,
    onRetry: () -> Unit,
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
                state.displayedList?.takeIf { it.tags.isNotEmpty() }?.let { list ->
                    TagFilter(list, { onChoice(PlayerChoice.ChannelTag(it)) })
                }
            }
            PlayerChannelsContent(
                state, if (layout == PlayerLayout.Phone) ChannelsLayout.Phone else ChannelsLayout.Upright,
                onChannel, { onChoice(PlayerChoice.ChannelTag(it)) }, onRetry,
            )
        }
    }
}

@Composable
private fun PlayerActionRow(
    state: PlayerFaceState,
    layout: PlayerLayout,
    dragging: Boolean,
    panels: PanelHost,
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
        val menu = MenuButtonStyle(state, layout, label, colors, panels)
        (state as? LivePlayerState)?.liveRecord?.let { record ->
            if (PlayerControl.Record in state.offered) {
                RecordAction(record, label, !dragging, colors) { panels.onChoice(PlayerChoice.LiveRecord(record)) }
            }
        }
        MenuButton(menu, R.drawable.player_audio, R.string.player_audio, !dragging && listening, PlayerPanel.Audio)
        MenuButton(
            menu, R.drawable.player_subtitles, R.string.player_subtitles, !dragging && listening,
            PlayerPanel.Subtitles,
        )
        Spacer(Modifier.weight(1f))
        MenuButton(menu, R.drawable.channels_info, R.string.player_info, !dragging, PlayerPanel.Info)
        if (!layout.upright) BrowseButton(state, dragging, colors, panels)
    }
}

@Composable
private fun BrowseButton(state: PlayerFaceState, dragging: Boolean, colors: ButtonColors, panels: PanelHost) {
    when (state) {
        is LivePlayerState -> if (PlayerControl.Channels in state.offered) {
            ActionButton(R.drawable.player_channels, R.string.player_channels, true, !dragging, colors, false, {
                panels.onPanel(PlayerPanel.Channels)
            })
        }
        is RecordingPlayerState -> if (state.hasMarkers) {
            ActionButton(R.drawable.player_markers, R.string.player_markers, true, !dragging, colors, false, {
                panels.onPanel(PlayerPanel.Markers)
            })
        }
    }
}

internal val PlayerPanel.control: PlayerControl?
    get() = when (this) {
        PlayerPanel.Audio -> PlayerControl.Audio
        PlayerPanel.Subtitles -> PlayerControl.Subtitles
        PlayerPanel.Channels -> PlayerControl.Channels
        PlayerPanel.Info -> PlayerControl.Info
        PlayerPanel.Markers -> PlayerControl.Markers
        else -> null
    }

private class MenuButtonStyle(
    val state: PlayerFaceState,
    val layout: PlayerLayout,
    val label: Boolean,
    val colors: ButtonColors,
    val panels: PanelHost,
)

/** Audio, Subtitles or Info: a menu at the button or a sheet, the button showing it open. */
@Composable
private fun MenuButton(
    style: MenuButtonStyle,
    @DrawableRes icon: Int,
    @StringRes name: Int,
    enabled: Boolean,
    target: PlayerPanel,
) {
    if (target.control !in style.state.offered) return
    val panels = style.panels
    val open = panels.panel?.button == target
    val toggle = { panels.onPanel(if (open) null else target) }
    val menu: @Composable () -> Unit = {
        if (target.atButton(style.layout)) {
            val close = { panels.onPanel(null) }
            PlayerMenu(open, panelEntries(target, style.state, panels.onChoice, close), close)
        }
    }
    if (style.label) {
        ActionButton(icon, name, true, enabled, style.colors, open, toggle, menu)
    } else {
        Box {
            val colors = style.colors
            IconToggleButton(
                checked = open, onCheckedChange = { toggle() }, enabled = enabled,
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = colors.contentColor, disabledContentColor = colors.disabledContentColor,
                    checkedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    checkedContentColor = colors.contentColor,
                ),
            ) {
                Icon(painterResource(icon), stringResource(name), Modifier.size(20.dp))
            }
            menu()
        }
    }
}

/** [open] marks the button whose menu is showing; [menu] is that menu, anchored to the button. */
@Composable
internal fun ActionButton(
    @DrawableRes icon: Int,
    @StringRes name: Int,
    label: Boolean,
    enabled: Boolean,
    colors: ButtonColors,
    open: Boolean,
    onClick: () -> Unit,
    menu: @Composable () -> Unit = {},
) {
    val description = stringResource(name)
    val openColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Box {
        TextButton(
            onClick = onClick, enabled = enabled,
            colors = if (open) colors.copy(containerColor = openColor) else colors,
            modifier = if (label) Modifier else Modifier.semantics { contentDescription = description },
            contentPadding = PaddingValues(horizontal = if (label) 8.dp else 2.dp),
        ) {
            Icon(painterResource(icon), null, Modifier.size(20.dp))
            if (label) {
                Spacer(Modifier.width(6.dp))
                Text(description, maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
        menu()
    }
}

@Composable
private fun ProgramTitle(title: String?, layout: PlayerLayout, color: Color = Color.White) {
    title?.let {
        val type = MaterialTheme.typography
        Text(
            it, color = color, style = if (layout == PlayerLayout.Tablet) type.displaySmall else type.headlineSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ClockText(value: String, modifier: Modifier) {
    Text(
        value, modifier, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium,
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
