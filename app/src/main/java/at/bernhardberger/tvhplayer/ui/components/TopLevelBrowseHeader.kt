package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvTextColors

/**
 * A top-level destination's [title], followed after a bullet by the [filter] narrowing it, if any.
 * The filter qualifies the destination, so it keeps the title's size and baseline but steps down
 * to the secondary text tone.
 */
@Composable
fun TopLevelBrowseHeader(
    title: String,
    modifier: Modifier = Modifier,
    filter: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val full = filter?.let { stringResource(R.string.browse_filtered_title, title, it) } ?: title
    // The destination leads in both languages; a translation that moves it quiets only the filter.
    val quiet = when {
        filter == null -> null
        full.startsWith(title) -> title.length until full.length
        else -> full.lastIndexOf(filter).takeIf { it >= 0 }?.let { it until it + filter.length }
    }
    val secondary = TvTextColors.secondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TvBrowseHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing12),
    ) {
        Text(
            text = buildAnnotatedString {
                append(full)
                quiet?.let { addStyle(SpanStyle(color = secondary), it.first, it.last + 1) }
            },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        actions?.invoke(this)
    }
}
