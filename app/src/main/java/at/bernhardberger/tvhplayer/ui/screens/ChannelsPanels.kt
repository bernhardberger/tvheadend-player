package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView

/** Which Channels side panel is open. */
internal enum class ChannelsPanel { TAGS, FILTER, VIEW_OPTIONS }

/**
 * The Tags panel of Channels and the Guide: the app's channel sets, a divider, then the server's
 * tags, the current one checked and focused. OK applies and closes.
 */
@Composable
internal fun ChannelsTagsPanel(
    groups: List<ChannelsGroup>,
    currentTagId: ChannelTagId?,
    onSelect: (ChannelTagId?) -> Unit,
    onClose: () -> Unit,
) {
    val allChannels = stringResource(R.string.all_channels)
    val entries = buildList<BrowsePanelEntry> {
        groups.forEachIndexed { index, group ->
            if (index > 0 && group.tagId != null && groups[index - 1].tagId == null) add(BrowsePanelEntry.Divider)
            add(BrowsePanelEntry.Choice(
                key = "channels-tags-${group.tagId?.value ?: "all"}",
                label = group.name ?: allChannels,
                selected = group.tagId == currentTagId,
                onClick = { onSelect(group.tagId) },
            ))
        }
    }
    BrowseSidePanel(stringResource(R.string.browse_tags), "channels-tags-panel", onClose) {
        BrowsePanelList(entries, radio = false)
    }
}

/**
 * View options: the layouts, then the current layout's program art switch where it has one. Each
 * change applies at once; Back closes the panel.
 */
@Composable
internal fun ChannelsViewOptionsPanel(
    view: ChannelsView,
    onViewChange: (ChannelsView) -> Unit,
    onClose: () -> Unit,
) {
    val entries = buildList {
        add(BrowsePanelEntry.Section(stringResource(R.string.channels_view_layout)))
        ChannelsLayout.entries.forEach { layout ->
            add(BrowsePanelEntry.Choice(
                key = "channels-view-layout-${layout.name.lowercase()}",
                label = stringResource(layout.label()),
                selected = layout == view.layout,
                onClick = { onViewChange(view.copy(layout = layout)) },
                icon = layout.icon(),
            ))
        }
        if (view.layout.hasArtSwitch) {
            add(BrowsePanelEntry.Divider)
            add(BrowsePanelEntry.Choice(
                key = "channels-view-program-art",
                label = stringResource(R.string.channels_view_program_art),
                selected = false,
                onClick = { onViewChange(view.toggledProgramArt()) },
                checked = view.showsProgramArt,
            ))
        }
    }
    BrowseSidePanel(stringResource(R.string.channels_view_options), "channels-view-panel", onClose) {
        BrowsePanelList(entries, radio = true)
    }
}

private fun ChannelsLayout.label(): Int = when (this) {
    ChannelsLayout.LIST -> R.string.channels_layout_list
    ChannelsLayout.GRID -> R.string.channels_layout_grid
    ChannelsLayout.IMMERSIVE -> R.string.channels_layout_immersive
}

private fun ChannelsLayout.icon(): Int = when (this) {
    ChannelsLayout.LIST -> R.drawable.ic_view_list
    ChannelsLayout.GRID -> R.drawable.ic_grid_view
    ChannelsLayout.IMMERSIVE -> R.drawable.ic_view_carousel
}
