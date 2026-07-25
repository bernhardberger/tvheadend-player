package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import at.bernhardberger.tvhplayer.repositories.TvhRepository
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsChannelTags(
    repository: TvhRepository = koinInject(),
    settingsStore: ChannelTagSettingsStore = koinInject(),
) {
    val tags by repository.tagsUi.collectAsStateWithLifecycle()
    val visibility by settingsStore.scopeVisibility.collectAsStateWithLifecycle(
        initialValue = ChannelScopeVisibility()
    )
    val scope = rememberCoroutineScope()
    val availableTagIds = tags.mapTo(mutableSetOf()) { it.id }
    val allChannelsVisible = visibility.isAllChannelsVisible()
    val visibleTagCount = tags.count { visibility.isTagVisible(it.id) }
    val visibleScopeCount = visibleTagCount + if (allChannelsVisible) 1 else 0

    SettingsPane(title = stringResource(R.string.settings_channel_tags)) {
        Text(stringResource(R.string.settings_channel_tags_description))
        Text(stringResource(R.string.settings_channel_tags_required))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item(key = "all-channels") {
                ChannelScopeToggle(
                    label = stringResource(R.string.all_channels),
                    checked = allChannelsVisible,
                    enabled = !allChannelsVisible || visibleScopeCount > 1,
                    onClick = {
                        scope.launch {
                            settingsStore.setScopeVisible(
                                tagId = null,
                                visible = !allChannelsVisible,
                                availableTagIds = availableTagIds,
                            )
                        }
                    },
                )
            }
            items(tags, key = { it.id }) { tag ->
                val checked = visibility.isTagVisible(tag.id)
                ChannelScopeToggle(
                    label = tag.name,
                    checked = checked,
                    enabled = !checked || visibleScopeCount > 1,
                    onClick = {
                        scope.launch {
                            settingsStore.setScopeVisible(
                                tagId = tag.id,
                                visible = !checked,
                                availableTagIds = availableTagIds,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ChannelScopeToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        selected = checked,
        enabled = enabled,
        onClick = onClick,
        headlineContent = { Text(label) },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
            )
        },
        scale = ListItemDefaults.scale(
            focusedScale = 1f,
            focusedSelectedScale = 1f,
        ),
        modifier = Modifier
            .width(640.dp)
            .fillMaxWidth(),
    )
}
