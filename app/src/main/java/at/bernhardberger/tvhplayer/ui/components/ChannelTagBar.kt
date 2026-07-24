package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.htsp.ChannelTagUi

@Composable
fun ChannelTagBar(
    tags: List<ChannelTagUi>,
    activeTagId: Int?,
    onSelectTag: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "all-channels") {
            TagButton(
                selected = activeTagId == null,
                text = stringResource(R.string.all_channels),
                onClick = { onSelectTag(null) },
            )
        }
        items(tags, key = { it.id }) { tag ->
            TagButton(
                selected = activeTagId == tag.id,
                text = tag.name,
                onClick = { onSelectTag(tag.id) },
            )
        }
    }
}

@Composable
private fun TagButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text) }
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
