package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate

/** The time and date of the top-level headers. Given a weight in its row, it gives way before the buttons. */
@Composable
internal fun BrowseHeaderClock(nowSec: Long, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.testTag("browse-header-clock"), horizontalAlignment = Alignment.End) {
        Text(formatClock(nowSec), style = MaterialTheme.typography.titleMedium,
            color = TvTextColors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatRecordingDate(nowSec, dateOnly = true, locale = locale), style = MaterialTheme.typography.bodySmall,
            color = TvTextColors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The height a header row holding the [BrowseHeaderClock] needs: the slot, or the clock's two lines
 * once text grows. It is the slot itself at the default text size.
 */
@Composable
@ReadOnlyComposable
internal fun browseHeaderMinHeight(): Dp = with(LocalDensity.current) {
    maxOf(TvBrowseHeaderHeight, MaterialTheme.typography.titleMedium.lineHeight.toDp() +
        MaterialTheme.typography.bodySmall.lineHeight.toDp())
}

/**
 * The header row of a top-level destination. The drawer says where you are, so nothing draws the
 * [title]; it is the row's pane title for accessibility. The [filter] narrowing the destination, if
 * any, stays visible at the leading edge in the secondary text tone, and [actions] end the row.
 * [leading] content takes the leading edge instead of the filter; the actions then fill the rest of
 * the row themselves.
 */
@Composable
fun TopLevelBrowseHeader(
    title: String,
    modifier: Modifier = Modifier,
    filter: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TvBrowseHeaderHeight)
            .semantics { paneTitle = title },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing12),
    ) {
        if (leading != null) {
            leading()
        } else if (filter != null) {
            Text(
                text = filter,
                style = MaterialTheme.typography.headlineMedium,
                color = TvTextColors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions?.invoke(this)
    }
}
