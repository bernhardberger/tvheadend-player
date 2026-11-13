package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.GuideRows
import at.bernhardberger.tvhplayer.settings.GuideView
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry
import at.bernhardberger.tvhplayer.ui.screens.BrowseLevelsPanel
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelLevel

/**
 * The Guide's View options: the row density opens a level of its two choices, and the headline and
 * genre colors switches sit on the root. Each change applies at once; Back steps out of the level,
 * then closes.
 */
@Composable
internal fun GuideViewOptionsPanel(
    view: GuideView,
    onViewChange: (GuideView) -> Unit,
    onClose: () -> Unit,
) {
    val rowsTitle = stringResource(R.string.guide_view_rows)
    val entries = listOf(
        BrowsePanelEntry.Choice(
            key = "guide-view-rows",
            label = rowsTitle,
            selected = false,
            onClick = {},
            level = BrowsePanelLevel(
                title = rowsTitle,
                currentValue = stringResource(view.rows.label()),
                entries = GuideRows.entries.map { rows ->
                    BrowsePanelEntry.Choice(
                        key = "guide-view-rows-${rows.name.lowercase()}",
                        label = stringResource(rows.label()),
                        selected = rows == view.rows,
                        onClick = { onViewChange(view.copy(rows = rows)) },
                    )
                },
            ),
        ),
        BrowsePanelEntry.Divider,
        BrowsePanelEntry.Choice(
            key = "guide-view-headline",
            label = stringResource(R.string.guide_view_headline),
            selected = false,
            onClick = { onViewChange(view.copy(headline = !view.headline)) },
            checked = view.headline,
        ),
        BrowsePanelEntry.Choice(
            key = "guide-view-genre-colors",
            label = stringResource(R.string.guide_view_genre_colors),
            selected = false,
            onClick = { onViewChange(view.copy(genreColors = !view.genreColors)) },
            checked = view.genreColors,
        ),
    )
    BrowseLevelsPanel(stringResource(R.string.channels_view_options), "guide-view-panel", onClose, entries)
}

private fun GuideRows.label(): Int = when (this) {
    GuideRows.REGULAR -> R.string.guide_view_rows_regular
    GuideRows.DENSE -> R.string.guide_view_rows_dense
}
