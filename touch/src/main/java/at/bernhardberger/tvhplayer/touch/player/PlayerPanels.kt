@file:Suppress("LongParameterList")

package at.bernhardberger.tvhplayer.touch.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemShapes
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelsLayout
import at.bernhardberger.tvhplayer.touch.channels.TagFilter
import at.bernhardberger.tvhplayer.touch.ui.TouchSideSheet
import at.bernhardberger.tvhplayer.presentation.R as SharedR

/** Menus open at their button except on the upright phone, which shows every list as a bottom sheet. */
internal fun PlayerPanel.atButton(layout: PlayerLayout) = menu && layout != PlayerLayout.Phone

private val PlayerPanel.title: Int
    get() = when (this) {
        PlayerPanel.Audio -> R.string.player_audio
        PlayerPanel.Subtitles -> R.string.player_subtitles
        PlayerPanel.Channels -> R.string.player_channels
        PlayerPanel.Info, PlayerPanel.InfoDetails -> R.string.player_info
        PlayerPanel.Options -> R.string.player_settings
        PlayerPanel.DisplayMode -> R.string.player_display_mode
        PlayerPanel.Profile -> R.string.player_stream_profile
        PlayerPanel.Stats -> R.string.player_stats
        PlayerPanel.Markers -> R.string.player_markers
        PlayerPanel.AdBreaks -> SharedR.string.ad_breaks_setting
    }

/** The space between a menu and its button. */
private val MenuGap = 8.dp

// BottomSheetDefaults.DragHandle: a 4 dp handle with 22 dp above and below it.
private val DragHandleHeight = 48.dp

/** An Audio or Subtitles menu, opening above the button it belongs to. */
@Composable
internal fun PlayerMenu(expanded: Boolean, entries: List<PanelEntry>, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismiss,
        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(
            MenuAnchorPosition.Above, DpOffset(0.dp, -MenuGap),
        ),
    ) {
        DropdownMenuGroup(MenuDefaults.groupShape(0, 1)) {
            entries.forEachIndexed { index, entry ->
                val shapes = MenuDefaults.itemShape(index, entries.size)
                val detail: (@Composable () -> Unit)? = entry.detail?.let { { Text(it) } }
                val selected = entry.selected
                if (selected == null) {
                    DropdownMenuItem(
                        onClick = entry.onClick, text = { Text(entry.label) }, shape = shapes.shape,
                        supportingText = detail,
                    )
                } else {
                    SelectableMenuItem(entry, selected, shapes, detail, colors)
                }
            }
        }
    }
}

@Composable
private fun SelectableMenuItem(
    entry: PanelEntry,
    selected: Boolean,
    shapes: MenuItemShapes,
    detail: (@Composable () -> Unit)?,
    colors: ColorScheme,
) {
    SelectableDropdownMenuItem(
        selected = selected,
        onClick = entry.onClick,
        text = { Text(entry.label) },
        shapes = shapes,
        selectedLeadingIcon = { Icon(painterResource(R.drawable.channels_check), null) },
        supportingText = detail,
        colors = MenuDefaults.selectableItemColors(
            selectedContainerColor = colors.secondaryContainer,
            selectedTextColor = colors.onSecondaryContainer,
            selectedLeadingIconColor = colors.onSecondaryContainer,
            selectedTrailingContentColor = colors.onSecondaryContainer,
        ),
    )
}

/**
 * The sheets over the player: Channels and Playback options as side sheets that leave the picture
 * clear, and on the upright phone every list, Playback options included, as a bottom sheet.
 */
@Composable
internal fun PlayerSheets(
    panel: PlayerPanel?,
    state: PlayerFaceState,
    layout: PlayerLayout,
    onPanel: (PlayerPanel?) -> Unit,
    onAction: (PlayerAction) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
    onChannel: (String) -> Unit,
    info: InfoPage,
    phoneSheetHeight: Dp,
    onSeek: (Long) -> Unit,
) {
    if (panel == null || panel.atButton(layout)) return
    val close = { onPanel(null) }
    val (title, leading) = sheetHead(panel, state, info)
    val back = when {
        panel == PlayerPanel.InfoDetails -> ({ info.back(onPanel) })
        else -> panel.parent?.let { parent -> { onPanel(parent) } }
    }
    val done = if (panel.optionsPage) ({ onPanel(PlayerPanel.Options) }) else close
    val body: @Composable ColumnScope.() -> Unit = {
        SheetBody(panel, state, onPanel, onAction, onChoice, onChannel, info, done, onSeek)
    }
    if (layout == PlayerLayout.Phone) {
        PlayerBottomSheet(title, back, leading, close, phoneSheetHeight, body)
    } else {
        TouchSideSheet(
            onDismissRequest = close, title = title, onBack = back, scrim = false, leading = leading,
            actions = {
                if (panel == PlayerPanel.Channels && state is LivePlayerState) {
                    state.channels.displayedList?.takeIf { it.tags.isNotEmpty() }?.let { list ->
                        TagFilter(list, { onChoice(PlayerChoice.ChannelTag(it)) })
                    }
                }
            },
            content = body,
        )
    }
}

/**
 * Info's title and leading logo follow the watched channel's day or the channel of the Details shown.
 * An airing may be on another channel.
 */
@Composable
private fun sheetHead(
    panel: PlayerPanel,
    state: PlayerFaceState,
    info: InfoPage,
): Pair<String, (@Composable () -> Unit)?> {
    val shown = info.stack.shown?.takeIf { panel == PlayerPanel.InfoDetails }?.let { id ->
        when (state) {
            is LivePlayerState -> state.schedule?.details?.invoke(id)
            is RecordingPlayerState -> state.info?.airingDetails?.get(id)
        }
    }
    return when {
        shown != null -> shown.channel to infoLogoSlot(shown.channelLogo, shown.logoTint, shown.channelLogoModel)
        panel.button == PlayerPanel.Info -> state.channel.run { name to infoLogoSlot(logo, logoTint, logoModel) }
        else -> stringResource(panel.title) to null
    }
}

// The page a sheet's Back returns to: the Playback options rows (Info's pages keep their own stack).
private val PlayerPanel.parent: PlayerPanel?
    get() = if (optionsPage && this != PlayerPanel.Options) PlayerPanel.Options else null

@Composable
private fun ColumnScope.SheetBody(
    panel: PlayerPanel,
    state: PlayerFaceState,
    onPanel: (PlayerPanel?) -> Unit,
    onAction: (PlayerAction) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
    onChannel: (String) -> Unit,
    info: InfoPage,
    done: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    when (panel) {
        PlayerPanel.Channels -> if (state is LivePlayerState) Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            PlayerChannelsContent(
                state.channels, ChannelsLayout.Phone, onChannel, { onChoice(PlayerChoice.ChannelTag(it)) },
                { onAction(PlayerAction.RetryChannels) },
            )
        }
        PlayerPanel.Options -> OptionRows(state, onPanel)
        PlayerPanel.Info, PlayerPanel.InfoDetails -> when (state) {
            is LivePlayerState -> key(state.channel.id) {
                InfoBody(panel, state.schedule, info, onPanel, onChoice) { onAction(PlayerAction.RetryChannels) }
            }
            is RecordingPlayerState -> state.info?.let { RecordingInfo(it, panel, info, onPanel, onChoice) }
        }
        PlayerPanel.Markers -> if (state is RecordingPlayerState) {
            RecordingMarkerRows(state, onSeek, Modifier.weight(1f))
        }
        else -> EntryRows(panelEntries(panel, state, onChoice, done))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBottomSheet(
    title: String,
    back: (() -> Unit)?,
    leading: (@Composable () -> Unit)?,
    onClose: () -> Unit,
    maxHeight: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        // The content pads for the navigation bar itself, inside its height limit.
        contentWindowInsets = { WindowInsets(0) },
    ) {
        if (back != null) BackHandler(onBack = back)
        // The limit sits on the content: on the sheet's own modifier it also bounds the sheet's anchors.
        Column(
            Modifier.heightIn(max = maxHeight - DragHandleHeight).semantics { paneTitle = title }
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (back != null) {
                    IconButton(onClick = back) {
                        Icon(painterResource(R.drawable.connect_back_outline), stringResource(R.string.side_sheet_back))
                    }
                }
                leading?.let {
                    Box(Modifier.padding(start = if (back == null) 8.dp else 4.dp, end = 12.dp)) { it() }
                }
                val start = when {
                    leading != null -> 0.dp
                    back == null -> 8.dp
                    else -> 4.dp
                }
                Text(
                    title,
                    Modifier.weight(1f).padding(start = start).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionRows(state: PlayerFaceState, onPanel: (PlayerPanel?) -> Unit) {
    val options = state.options
    val profile = options.profilePick?.let { options.profiles.getOrNull(it) }
    val rows = listOfNotNull(
        Triple(R.string.player_display_mode, stringResource(options.displayMode.label), PlayerPanel.DisplayMode),
        if (state is RecordingPlayerState) {
            val selected = state.adBreakPick?.let { stringResource(it.label) } ?: adBreakDefault(state)
            Triple(SharedR.string.ad_breaks_setting, selected, PlayerPanel.AdBreaks)
        } else options.profiles.takeIf { it.isNotEmpty() }?.let {
            Triple(
                R.string.player_stream_profile,
                profile?.let { profileName(it) }
                    ?: stringResource(R.string.player_option_default, defaultProfileName(options)),
                PlayerPanel.Profile,
            )
        },
        options.stats?.let { Triple(R.string.player_stats, stringResource(it.label), PlayerPanel.Stats) },
    )
    Rows {
        rows.forEachIndexed { index, (label, value, target) ->
            Segment(index, rows.size) {
                ListItem(
                    onClick = { onPanel(target) },
                    supportingContent = { Text(value) },
                    trailingContent = { Icon(painterResource(R.drawable.connect_next_outline), null) },
                    shapes = ListItemDefaults.segmentedShapes(index, rows.size),
                    colors = ListItemDefaults.segmentedColors(),
                ) { Text(stringResource(label)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EntryRows(entries: List<PanelEntry>) {
    Rows {
        entries.forEachIndexed { index, entry ->
            val selected = entry.selected
            val shapes = ListItemDefaults.segmentedShapes(index, entries.size)
            val detail: (@Composable () -> Unit)? = entry.detail?.let { { Text(it) } }
            Segment(index, entries.size) {
                if (selected == null) {
                    ListItem(
                        onClick = entry.onClick, supportingContent = detail, shapes = shapes,
                        colors = ListItemDefaults.segmentedColors(),
                    ) { Text(entry.label) }
                } else {
                    ListItem(
                        selected = selected, onClick = entry.onClick,
                        leadingContent = { RadioButton(selected = selected, onClick = null) },
                        supportingContent = detail, shapes = shapes, colors = ListItemDefaults.segmentedColors(),
                    ) { Text(entry.label) }
                }
            }
        }
    }
}

@Composable
private fun Rows(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)
            .padding(bottom = 16.dp),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Segment(index: Int, count: Int, content: @Composable () -> Unit) {
    Box(Modifier.padding(bottom = if (index < count - 1) ListItemDefaults.SegmentedGap else 0.dp)) { content() }
}
