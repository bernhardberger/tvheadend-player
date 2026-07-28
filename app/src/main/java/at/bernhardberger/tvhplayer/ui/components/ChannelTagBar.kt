package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.htsp.ChannelTagUi
import at.bernhardberger.tvhplayer.ui.CompactChannelCardWidth
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha

@Composable
fun ChannelTagSelector(
    tags: List<ChannelTagUi>,
    activeTagId: Int?,
    onSelectTag: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    allChannelsVisible: Boolean = true,
    activeFocusRequester: FocusRequester = remember { FocusRequester() },
    onMoveToContent: () -> Boolean = { false },
) {
    val allChannelsLabel = stringResource(R.string.all_channels)
    val scopes = remember(tags, allChannelsVisible, allChannelsLabel) {
        buildList {
            if (allChannelsVisible) add(null to allChannelsLabel)
            addAll(tags.map { it.id to it.name })
        }
    }
    if (scopes.isEmpty()) return

    val activeIndex = scopes.indexOfFirst { it.first == activeTagId }.coerceAtLeast(0)
    val scheme = MaterialTheme.colorScheme
    val tabColors = TabDefaults.pillIndicatorTabColors(
        contentColor = scheme.onSurface.copy(alpha = TvTextSecondaryAlpha),
        inactiveContentColor = scheme.onSurface.copy(alpha = TvTextSecondaryAlpha),
        selectedContentColor = scheme.onSurface,
        focusedContentColor = scheme.inverseOnSurface,
        focusedSelectedContentColor = scheme.inverseOnSurface,
        disabledContentColor = scheme.onSurface.copy(alpha = TvTextDisabledAlpha),
        disabledInactiveContentColor = scheme.onSurface.copy(alpha = TvTextDisabledAlpha),
        disabledSelectedContentColor = scheme.onSurface.copy(alpha = TvTextDisabledAlpha),
    )
    TabRow(
        selectedTabIndex = activeIndex,
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer(activeFocusRequester)
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    onMoveToContent()
            },
    ) {
        scopes.forEachIndexed { index, (tagId, label) ->
            Tab(
                selected = index == activeIndex,
                onFocus = {
                    if (tagId != activeTagId) onSelectTag(tagId)
                },
                onClick = {
                    if (tagId != activeTagId) onSelectTag(tagId)
                    onMoveToContent()
                },
                colors = tabColors,
                modifier = if (index == activeIndex) {
                    Modifier.focusRequester(activeFocusRequester)
                } else {
                    Modifier
                },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        horizontal = TvSpacing16,
                        vertical = TvSpacing8,
                    ).widthIn(max = CompactChannelCardWidth),
                )
            }
        }
    }
}

@Composable
fun UnavailableTagNotice(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.active_tag_unavailable),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}
