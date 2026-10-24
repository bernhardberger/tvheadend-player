package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabel
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelList
import at.bernhardberger.tvhplayer.ui.screens.BrowseSidePanel
import at.bernhardberger.tvhplayer.ui.tvGenreColors

/**
 * The Guide's Filter panel: All genres and the genre families, the highlighted one checked and
 * focused. OK applies and closes. [genreColors] marks each family with its color dot.
 */
@Composable
internal fun GuideFilterPanel(
    genre: ProgrammeCategory,
    genreColors: Boolean,
    onSelect: (ProgrammeCategory) -> Unit,
    onClose: () -> Unit,
) {
    val entries = ProgrammeCategory.entries.map { category ->
        BrowsePanelEntry.Choice(
            key = "guide-genre-${category.name.lowercase()}",
            label = programmeCategoryLabel(category),
            selected = category == genre,
            onClick = { onSelect(category) },
            dot = if (genreColors) tvGenreColors(category)?.base else null,
        )
    }
    BrowseSidePanel(stringResource(R.string.guide_filter_title), "guide-genre-panel", onClose) {
        BrowsePanelList(entries, radio = false)
    }
}
