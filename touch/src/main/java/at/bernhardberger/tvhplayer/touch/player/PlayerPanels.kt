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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelRows
import at.bernhardberger.tvhplayer.touch.channels.ChannelsActions
import at.bernhardberger.tvhplayer.touch.channels.ChannelsLayout
import at.bernhardberger.tvhplayer.touch.channels.TagFilter
import at.bernhardberger.tvhplayer.touch.ui.TouchSideSheet

/** Menus open at their button except on the upright phone, which shows every list as a bottom sheet. */
internal fun PlayerPanel.atButton(layout: PlayerLayout) = menu && layout != PlayerLayout.Phone

private val PlayerPanel.title: Int
    get() = when (this) {
        PlayerPanel.Record -> R.string.player_record
        PlayerPanel.Audio -> R.string.player_audio
        PlayerPanel.Subtitles -> R.string.player_subtitles
        PlayerPanel.Channels -> R.string.player_channels
        PlayerPanel.Options -> R.string.player_settings
        PlayerPanel.DisplayMode -> R.string.player_display_mode
        PlayerPanel.Profile -> R.string.player_stream_profile
        PlayerPanel.Stats -> R.string.player_stats
    }

/** The space between a menu and its button. */
private val MenuGap = 8.dp

/** A Record, Audio or Subtitles menu, opening above the button it belongs to. */
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
    state: LivePlayerState,
    layout: PlayerLayout,
    onPanel: (PlayerPanel?) -> Unit,
    onAction: (PlayerAction) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
    onChannel: (String) -> Unit,
) {
    if (panel == null || panel.atButton(layout)) return
    val close = { onPanel(null) }
    val title = stringResource(panel.title)
    val back = if (panel.optionsPage && panel != PlayerPanel.Options) ({ onPanel(PlayerPanel.Options) }) else null
    val done = if (panel.optionsPage) ({ onPanel(PlayerPanel.Options) }) else close
    val body: @Composable ColumnScope.() -> Unit = {
        when (panel) {
            PlayerPanel.Channels -> Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                ChannelRows(
                    state.channels, ChannelsLayout.Phone,
                    ChannelsActions(onPlay = onChannel, onTag = {}, onRetry = {}, onOpenConnectionSettings = {}),
                    showColumnLabels = false,
                )
            }
            PlayerPanel.Options -> OptionRows(state.options, onPanel)
            else -> EntryRows(panelEntries(panel, state, onAction, onChoice, done))
        }
    }
    if (layout == PlayerLayout.Phone) {
        PlayerBottomSheet(title, back, close, body)
    } else {
        TouchSideSheet(
            onDismissRequest = close, title = title, onBack = back, scrim = false,
            actions = {
                if (panel == PlayerPanel.Channels && state.channels.tags.isNotEmpty()) {
                    TagFilter(state.channels, { onChoice(PlayerChoice.ChannelTag(it)) })
                }
            },
            content = body,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBottomSheet(
    title: String,
    back: (() -> Unit)?,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        if (back != null) BackHandler(onBack = back)
        Column(Modifier.semantics { paneTitle = title }.windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (back != null) {
                    IconButton(onClick = back) {
                        Icon(painterResource(R.drawable.connect_back_outline), stringResource(R.string.side_sheet_back))
                    }
                }
                Text(
                    title,
                    Modifier.weight(1f).padding(start = if (back == null) 8.dp else 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionRows(options: PlaybackOptions, onPanel: (PlayerPanel?) -> Unit) {
    val profile = options.profilePick?.let { options.profiles.getOrNull(it) }
    val rows = listOf(
        Triple(R.string.player_display_mode, stringResource(options.displayMode.label), PlayerPanel.DisplayMode),
        Triple(
            R.string.player_stream_profile,
            profile?.let { profileName(it) } ?: stringResource(
                R.string.player_option_default, profileName(options.profiles.getOrNull(options.defaultProfile)),
            ),
            PlayerPanel.Profile,
        ),
        Triple(R.string.player_stats, stringResource(options.stats.label), PlayerPanel.Stats),
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
