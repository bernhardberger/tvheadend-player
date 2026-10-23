package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.GuideRows
import at.bernhardberger.tvhplayer.settings.GuideView
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelList
import at.bernhardberger.tvhplayer.ui.screens.BrowseSidePanel

/**
 * The Guide's View options: the row density as a single choice, then the headline switch. Each
 * change applies at once; Back closes.
 */
@Composable
internal fun GuideViewOptionsPanel(
    view: GuideView,
    onViewChange: (GuideView) -> Unit,
    onClose: () -> Unit,
) {
    val entries = buildList {
        add(BrowsePanelEntry.Section(stringResource(R.string.guide_view_rows)))
        GuideRows.entries.forEach { rows ->
            add(BrowsePanelEntry.Choice(
                key = "guide-view-rows-${rows.name.lowercase()}",
                label = stringResource(rows.label()),
                selected = rows == view.rows,
                onClick = { onViewChange(view.copy(rows = rows)) },
            ))
        }
        add(BrowsePanelEntry.Divider)
        add(BrowsePanelEntry.Choice(
            key = "guide-view-headline",
            label = stringResource(R.string.guide_view_headline),
            selected = false,
            onClick = { onViewChange(view.copy(headline = !view.headline)) },
            checked = view.headline,
        ))
    }
    BrowseSidePanel(stringResource(R.string.channels_view_options), "guide-view-panel", onClose) {
        BrowsePanelList(entries, radio = true)
    }
}

private fun GuideRows.label(): Int = when (this) {
    GuideRows.DENSE -> R.string.guide_view_rows_dense
    GuideRows.REGULAR -> R.string.guide_view_rows_regular
}
