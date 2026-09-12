package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.ui.components.ChannelSettingsNotice
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.components.SettingsSwitchRow

@Composable
fun SettingsChannelTags(
    initialFocusRequester: FocusRequester,
    channelsVm: ChannelsViewModel,
) {
    val observation by channelsVm.observation.collectAsStateWithLifecycle()
    val tags = observation.channelCatalogForDisplay?.tags.orEmpty()
    val state by channelsVm.scope.collectAsStateWithLifecycle()
    val failed by channelsVm.settingsFailure.collectAsStateWithLifecycle()
    val visibility = state.visibility
    val allChannelsVisible = visibility.isAllChannelsVisible()
    val visibleTagCount = tags.count { visibility.isTagVisible(it.id) }
    val visibleScopeCount = visibleTagCount + if (allChannelsVisible) 1 else 0
    val lastEnabledReason = stringResource(R.string.settings_channel_tags_last_enabled)

    SettingsPane(
        title = stringResource(R.string.settings_channel_tags),
        modifier = Modifier.focusRequester(initialFocusRequester),
    ) {
        // The pane's focus group stays attached during loading. Entry simply stays on
        // the category rail until it has a Retry button or a setting to focus.
        ChannelSettingsNotice(state.settingsLoaded, failed, channelsVm::retrySettings)
        if (!state.settingsLoaded) return@SettingsPane
        SettingsSwitchRow(
            label = stringResource(R.string.all_channels),
            checked = allChannelsVisible,
            enabled = !allChannelsVisible || visibleScopeCount > 1,
            supportingText = when {
                allChannelsVisible && visibleScopeCount <= 1 -> lastEnabledReason
                else -> stringResource(R.string.settings_channel_tags_description)
            },
            onClick = {
                channelsVm.toggleScopeVisibility(null)
            },
            modifier = Modifier
                .width(640.dp)
                .fillMaxWidth(),
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
                        channelsVm.toggleScopeVisibility(tagId)
                    },
                    modifier = Modifier
                        .width(640.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}
