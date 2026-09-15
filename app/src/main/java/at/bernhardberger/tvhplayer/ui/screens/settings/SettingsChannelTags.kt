package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId

@Composable
internal fun settingsChannelTagsLevel(channelsVm: ChannelsViewModel): DepthLevel {
    val observation by channelsVm.observation.collectAsStateWithLifecycle()
    val tags = observation.channelCatalogForDisplay?.tags.orEmpty()
    val state by channelsVm.scope.collectAsStateWithLifecycle()
    val failed by channelsVm.settingsFailure.collectAsStateWithLifecycle()
    return settingsChannelTagsLevel(tags, state.visibility, state.settingsLoaded, failed,
        channelsVm::retrySettings, channelsVm::toggleScopeVisibility)
}

@Composable
internal fun settingsChannelTagsLevel(
    tags: List<ChannelTag>,
    visibility: ChannelScopeVisibility,
    loaded: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onToggle: (ChannelTagId?) -> Unit,
): DepthLevel {
    val allVisible = visibility.isAllChannelsVisible()
    val count = tags.count { visibility.isTagVisible(it.id) } + if (allVisible) 1 else 0
    val lastReason = stringResource(R.string.settings_channel_tags_last_enabled)
    return settingsLevel(SettingsSection.CHANNEL_TAGS.name, stringResource(R.string.settings_channel_tags_nav), buildList {
        if (failed) add(settingsRow("retry", stringResource(R.string.retry),
            supporting = stringResource(if (loaded) R.string.channel_settings_save_failed else R.string.channel_settings_load_failed),
            onClick = onRetry))
        if (!loaded) {
            if (!failed) add(settingsRow("loading", stringResource(R.string.settings_channel_groups_loading)))
        } else {
            add(settingsRow("all-channels", stringResource(R.string.all_channels), checked = allVisible,
                supporting = if (allVisible && count <= 1) lastReason else null,
                enabled = !allVisible || count > 1, onClick = { onToggle(null) }))
            tags.forEach { tag ->
                val checked = visibility.isTagVisible(tag.id)
                val enabled = !checked || count > 1
                add(settingsRow(tag.id.value.toString(), tag.name.orEmpty(), checked = checked, enabled = enabled,
                    supporting = if (!enabled) lastReason else null,
                    onClick = { onToggle(tag.id) }))
            }
        }
    })
}
