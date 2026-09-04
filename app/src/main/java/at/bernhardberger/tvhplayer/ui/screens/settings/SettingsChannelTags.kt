package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.components.SettingsSwitchRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsChannelTags(
    initialFocusRequester: FocusRequester,
    session: TvheadendSession = koinInject(),
    settingsStore: ChannelTagSettingsStore = koinInject(),
) {
    val observation by session.observation.collectAsStateWithLifecycle()
    val tags = observation.channelCatalogForDisplay?.tags.orEmpty()
    val visibility by settingsStore.scopeVisibility.collectAsStateWithLifecycle(
        initialValue = ChannelScopeVisibility()
    )
    val scope = rememberCoroutineScope()
    val availableTagIds = tags.mapTo(mutableSetOf()) { it.id }
    val allChannelsVisible = visibility.isAllChannelsVisible()
    val visibleTagCount = tags.count { visibility.isTagVisible(it.id) }
    val visibleScopeCount = visibleTagCount + if (allChannelsVisible) 1 else 0
    val lastEnabledReason = stringResource(R.string.settings_channel_tags_last_enabled)

    SettingsPane(title = stringResource(R.string.settings_channel_tags)) {
        SettingsSwitchRow(
            label = stringResource(R.string.all_channels),
            checked = allChannelsVisible,
            enabled = !allChannelsVisible || visibleScopeCount > 1,
            supportingText = when {
                allChannelsVisible && visibleScopeCount <= 1 -> lastEnabledReason
                else -> stringResource(R.string.settings_channel_tags_description)
            },
            onClick = {
                scope.launch {
                    settingsStore.setScopeVisible(
                        tagId = null,
                        visible = !allChannelsVisible,
                        availableTagIds = availableTagIds,
                    )
                }
            },
            modifier = Modifier
                .width(640.dp)
                .fillMaxWidth()
                .focusRequester(initialFocusRequester),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(tags, key = { it.id.value }) { tag ->
                val tagId = tag.id
                val checked = visibility.isTagVisible(tagId)
                val enabled = !checked || visibleScopeCount > 1
                SettingsSwitchRow(
                    label = tag.name.orEmpty(),
                    checked = checked,
                    enabled = enabled,
                    supportingText = if (!enabled) lastEnabledReason else null,
                    onClick = {
                        scope.launch {
                            settingsStore.setScopeVisible(
                                tagId = tagId,
                                visible = !checked,
                                availableTagIds = availableTagIds,
                            )
                        }
                    },
                    modifier = Modifier
                        .width(640.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}
