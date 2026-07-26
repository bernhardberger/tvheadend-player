package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelBrowseLayout
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.components.SettingsSectionTitle
import at.bernhardberger.tvhplayer.ui.components.SettingsSwitchRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsOptions(
    settingsStore: UiSettingsStore = koinInject(),
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val scope = rememberCoroutineScope()

    SettingsPane(title = stringResource(R.string.settings_options)) {
        SettingsSwitchRow(
            label = stringResource(R.string.show_epg_menu),
            checked = settings.showEpgMenu,
            supportingText = stringResource(R.string.show_epg_menu_description),
            onClick = {
                scope.launch { settingsStore.setShowEpgMenu(!settings.showEpgMenu) }
            },
            modifier = Modifier
                .width(480.dp)
                .fillMaxWidth(),
        )

        SettingsSectionTitle(stringResource(R.string.channels_layout_section))
        Column(
            modifier = Modifier
                .width(480.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChannelLayoutOption(
                label = stringResource(R.string.channels_layout_list),
                supporting = stringResource(R.string.channels_layout_list_description),
                selected = settings.channelBrowseLayout == ChannelBrowseLayout.LIST_WITH_DETAILS,
                onClick = {
                    scope.launch {
                        settingsStore.setChannelBrowseLayout(ChannelBrowseLayout.LIST_WITH_DETAILS)
                    }
                },
            )
            ChannelLayoutOption(
                label = stringResource(R.string.channels_layout_cards),
                supporting = stringResource(R.string.channels_layout_cards_description),
                selected = settings.channelBrowseLayout == ChannelBrowseLayout.LARGE_CARDS,
                onClick = {
                    scope.launch {
                        settingsStore.setChannelBrowseLayout(ChannelBrowseLayout.LARGE_CARDS)
                    }
                },
            )
        }
    }
}

@Composable
private fun ChannelLayoutOption(
    label: String,
    supporting: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = { Text(label) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            RadioButton(selected = selected, onClick = null)
        },
        scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    )
}
