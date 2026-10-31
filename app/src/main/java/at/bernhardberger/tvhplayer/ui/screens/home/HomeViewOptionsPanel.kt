package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.HomeSectionKind
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelList
import at.bernhardberger.tvhplayer.ui.screens.BrowseSidePanel
import at.bernhardberger.tvhplayer.ui.screens.ChannelsGroup

@Composable
internal fun HomeViewOptionsPanel(
    hidden: Set<HomeSectionKind>,
    onSectionVisibleChange: (HomeSectionKind, Boolean) -> Unit,
    groups: List<ChannelsGroup>,
    activeTagId: ChannelTagId?,
    onSelectTag: (ChannelTagId?) -> Unit,
    onClose: () -> Unit,
) {
    val entries = buildList {
        HomeSectionKind.entries.forEach { kind ->
            add(BrowsePanelEntry.Choice(
                key = "home-view-${kind.name}", label = stringResource(kind.homeLabel()),
                selected = false, checked = kind !in hidden,
                onClick = { onSectionVisibleChange(kind, kind in hidden) },
            ))
        }
        add(BrowsePanelEntry.Divider)
        add(BrowsePanelEntry.Section(stringResource(R.string.browse_tags)))
        groups.forEach { group ->
            add(BrowsePanelEntry.Choice(
                key = "home-tag-${group.tagId?.value ?: "all"}",
                label = group.name ?: stringResource(R.string.all_channels),
                selected = group.tagId == activeTagId,
                onClick = { onSelectTag(group.tagId) },
            ))
        }
    }
    BrowseSidePanel(stringResource(R.string.channels_view_options), "home-view-panel", onClose) {
        BrowsePanelList(entries, radio = false, focusKey = "home-view-${HomeSectionKind.CURRENT_ACTIVITY.name}")
    }
}

private fun HomeSectionKind.homeLabel() = when (this) {
    HomeSectionKind.CURRENT_ACTIVITY -> R.string.home_current_activity
    HomeSectionKind.RECENT_CHANNELS -> R.string.home_recent_channels
    HomeSectionKind.ON_NOW -> R.string.home_on_now
    HomeSectionKind.GENRES -> R.string.home_browse_by_genre
    HomeSectionKind.CONTINUE_WATCHING -> R.string.home_continue_watching
    HomeSectionKind.RECENTLY_RECORDED -> R.string.home_recently_recorded
    HomeSectionKind.UPCOMING -> R.string.home_upcoming_recordings
}
