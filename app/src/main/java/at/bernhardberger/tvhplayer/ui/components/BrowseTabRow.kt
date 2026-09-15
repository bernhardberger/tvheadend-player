package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowScope

/**
 * Page-scope tabs shared by the browse destinations: the TV Material [TabRow] with its
 * default travelling [androidx.tv.material3.TabRowDefaults.PillIndicator] and default tab colours
 * (`docs/DESIGN.md` §3). Only layout anchoring is added here.
 */
@Composable
internal fun BrowseTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable TabRowScope.() -> Unit,
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        // TabRow measures its content at natural width. Don't let a caller's minimum
        // width implicitly centre a short row inside the title's leading anchor.
        modifier = modifier.wrapContentWidth(Alignment.Start),
        tabs = tabs,
    )
}
