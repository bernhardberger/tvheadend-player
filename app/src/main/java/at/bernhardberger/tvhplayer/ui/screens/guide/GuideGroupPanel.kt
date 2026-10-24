package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabel
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelList
import at.bernhardberger.tvhplayer.ui.screens.BrowseSidePanel
import at.bernhardberger.tvhplayer.ui.screens.ChannelsGroup
import at.bernhardberger.tvhplayer.ui.tvGenreColors

private const val GenreRowKey = "guide-genre"

/**
 * The Guide's Group panel: the groups when there is a choice, then the Genre row that opens the
 * Genre level in the same panel. Choosing a group or a genre applies it and closes; Back on the
 * Genre level returns to the Genre row, Back on the groups closes. [genreColors] marks each
 * family with its color dot.
 */
@Composable
internal fun GuideGroupPanel(
    groups: List<ChannelsGroup>,
    currentTagId: ChannelTagId?,
    genre: ProgrammeCategory,
    showGenre: Boolean,
    genreColors: Boolean,
    onSelectGroup: (ChannelTagId?) -> Unit,
    onSelectGenre: (ProgrammeCategory) -> Unit,
    onClose: () -> Unit,
) {
    var genreLevel by remember { mutableStateOf(false) }
    var returnedFromGenre by remember { mutableStateOf(false) }
    val title = stringResource(if (genreLevel) R.string.guide_genre else R.string.channels_group)
    val panelTag = if (genreLevel) "guide-genre-panel" else "channels-group-panel"
    val genreLabel = stringResource(R.string.guide_genre)
    val allChannels = stringResource(R.string.all_channels)
    val entries = if (genreLevel) {
        ProgrammeCategory.entries.map { category ->
            BrowsePanelEntry.Choice(
                key = "guide-genre-${category.name.lowercase()}",
                label = programmeCategoryLabel(category),
                selected = category == genre,
                onClick = { onSelectGenre(category) },
                dot = if (genreColors) tvGenreColors(category)?.base else null,
            )
        }
    } else {
        val currentGenre = programmeCategoryLabel(genre)
        buildList<BrowsePanelEntry> {
            if (groups.size > 1) {
                groups.forEach { group ->
                    add(BrowsePanelEntry.Choice(
                        key = "channels-group-${group.tagId?.value ?: "all"}",
                        label = group.name ?: allChannels,
                        selected = group.tagId == currentTagId,
                        onClick = { onSelectGroup(group.tagId) },
                    ))
                }
                if (showGenre) add(BrowsePanelEntry.Divider)
            }
            if (showGenre) add(BrowsePanelEntry.Choice(
                key = GenreRowKey,
                label = genreLabel,
                selected = false,
                onClick = { genreLevel = true },
                supporting = currentGenre,
                opensLevel = true,
            ))
        }
    }
    BrowseSidePanel(title, panelTag, onClose = {
        if (genreLevel) {
            genreLevel = false
            returnedFromGenre = true
        } else {
            onClose()
        }
    }) {
        // Each level is a fresh list, so it focuses its own row as it opens.
        key(genreLevel) {
            BrowsePanelList(entries, radio = false, focusKey = GenreRowKey.takeIf { returnedFromGenre && !genreLevel })
        }
    }
}
